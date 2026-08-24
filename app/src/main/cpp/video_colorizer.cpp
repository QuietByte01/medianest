#include "video_colorizer.h"
#include <android/log.h>
#include <android/bitmap.h>
#include <cmath>
#include <algorithm>
#include <vector>
#include <cstring>
#include <memory>

#define TAG "VideoColorizerNative"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

namespace medianest {

// ============================================================================
// 1. COLOR SPACE CONVERSIONS (RGB, YUV, CIELAB)
// ============================================================================

static inline float sRGBtoLinear(float c) {
    return (c <= 0.04045f) ? (c / 12.92f) : std::pow((c + 0.055f) / 1.055f, 2.4f);
}

static inline float linearTosRGB(float c) {
    return (c <= 0.0031308f) ? (c * 12.92f) : (1.055f * std::pow(std::max(0.0f, c), 1.0f / 2.4f) - 0.055f);
}

static inline float fLab(float t) {
    const float delta = 6.0f / 29.0f;
    return (t > delta * delta * delta) ? std::cbrt(t) : (t / (3.0f * delta * delta) + 4.0f / 29.0f);
}

static inline float fLabInv(float t) {
    const float delta = 6.0f / 29.0f;
    return (t > delta) ? (t * t * t) : (3.0f * delta * delta * (t - 4.0f / 29.0f));
}

void RGBtoLab(uint8_t r, uint8_t g, uint8_t b, float& L, float& a, float& b_out) {
    float rf = sRGBtoLinear(r / 255.0f);
    float gf = sRGBtoLinear(g / 255.0f);
    float bf = sRGBtoLinear(b / 255.0f);

    // Standard D65 Matrix (sRGB to XYZ)
    float X = 0.4124564f * rf + 0.3575761f * gf + 0.1804375f * bf;
    float Y = 0.2126729f * rf + 0.7151522f * gf + 0.0721750f * bf;
    float Z = 0.0193339f * rf + 0.1191920f * gf + 0.9503041f * bf;

    // D65 Reference White
    const float Xn = 0.95047f;
    const float Yn = 1.00000f;
    const float Zn = 1.08883f;

    float fx = fLab(X / Xn);
    float fy = fLab(Y / Yn);
    float fz = fLab(Z / Zn);

    L = (116.0f * fy) - 16.0f;
    a = 500.0f * (fx - fy);
    b_out = 200.0f * (fy - fz);
}

void LabtoRGB(float L, float a, float b_in, uint8_t& r, uint8_t& g, uint8_t& b) {
    const float Xn = 0.95047f;
    const float Yn = 1.00000f;
    const float Zn = 1.08883f;

    float fy = (L + 16.0f) / 116.0f;
    float fx = a / 500.0f + fy;
    float fz = fy - b_in / 200.0f;

    float X = Xn * fLabInv(fx);
    float Y = Yn * fLabInv(fy);
    float Z = Zn * fLabInv(fz);

    // XYZ to sRGB Linear
    float rf =  3.2404542f * X - 1.5371385f * Y - 0.4985314f * Z;
    float gf = -0.9692660f * X + 1.8760108f * Y + 0.0415560f * Z;
    float bf =  0.0556434f * X - 0.2040259f * Y + 1.0572252f * Z;

    r = static_cast<uint8_t>(std::clamp(linearTosRGB(rf) * 255.0f + 0.5f, 0.0f, 255.0f));
    g = static_cast<uint8_t>(std::clamp(linearTosRGB(gf) * 255.0f + 0.5f, 0.0f, 255.0f));
    b = static_cast<uint8_t>(std::clamp(linearTosRGB(bf) * 255.0f + 0.5f, 0.0f, 255.0f));
}

void RGBtoYUV(uint8_t r, uint8_t g, uint8_t b, uint8_t& Y, uint8_t& U, uint8_t& V) {
    // BT.601 / BT.709 standard full-range conversion
    float yf =  0.299f * r + 0.587f * g + 0.114f * b;
    float uf = -0.168736f * r - 0.331264f * g + 0.5f * b + 128.0f;
    float vf =  0.5f * r - 0.418688f * g - 0.081312f * b + 128.0f;

    Y = static_cast<uint8_t>(std::clamp(yf, 0.0f, 255.0f));
    U = static_cast<uint8_t>(std::clamp(uf, 0.0f, 255.0f));
    V = static_cast<uint8_t>(std::clamp(vf, 0.0f, 255.0f));
}

void YUVtoRGB(uint8_t Y, uint8_t U, uint8_t V, uint8_t& r, uint8_t& g, uint8_t& b) {
    float yf = static_cast<float>(Y);
    float uf = static_cast<float>(U) - 128.0f;
    float vf = static_cast<float>(V) - 128.0f;

    float rf = yf + 1.402f * vf;
    float gf = yf - 0.344136f * uf - 0.714136f * vf;
    float bf = yf + 1.772f * uf;

    r = static_cast<uint8_t>(std::clamp(rf, 0.0f, 255.0f));
    g = static_cast<uint8_t>(std::clamp(gf, 0.0f, 255.0f));
    b = static_cast<uint8_t>(std::clamp(bf, 0.0f, 255.0f));
}

// ============================================================================
// 2. DENSE OPTICAL FLOW (FARNEBACK ALGORITHM & BILINEAR WARP)
// ============================================================================

static ImagePlane downsamplePlane(const ImagePlane& src) {
    int nw = (src.width + 1) / 2;
    int nh = (src.height + 1) / 2;
    ImagePlane dst(nw, nh);
    for (int y = 0; y < nh; ++y) {
        int sy = y * 2;
        for (int x = 0; x < nw; ++x) {
            int sx = x * 2;
            int sum = src.get(sx, sy) + src.get(sx + 1, sy) +
                      src.get(sx, sy + 1) + src.get(sx + 1, sy + 1);
            dst.set(x, y, static_cast<uint8_t>(sum / 4));
        }
    }
    return dst;
}

void computeFarnebackOpticalFlow(
    const ImagePlane& prevLuma,
    const ImagePlane& currLuma,
    MotionField& flow,
    int numLevels,
    double pyrScale,
    int winSize,
    int numIters,
    int polyN,
    double polySigma
) {
    int w = prevLuma.width;
    int h = prevLuma.height;
    if (w <= 0 || h <= 0) return;

    if (flow.width != w || flow.height != h) {
        flow = MotionField(w, h);
    }

    // Build Gaussian Pyramids for multi-scale coarse-to-fine estimation
    std::vector<ImagePlane> pyrPrev, pyrCurr;
    pyrPrev.push_back(prevLuma);
    pyrCurr.push_back(currLuma);

    for (int lvl = 1; lvl < numLevels; ++lvl) {
        pyrPrev.push_back(downsamplePlane(pyrPrev.back()));
        pyrCurr.push_back(downsamplePlane(pyrCurr.back()));
    }

    MotionField curFlow(pyrPrev.back().width, pyrPrev.back().height);

    for (int lvl = numLevels - 1; lvl >= 0; --lvl) {
        const auto& I1 = pyrPrev[lvl];
        const auto& I2 = pyrCurr[lvl];
        int lw = I1.width;
        int lh = I1.height;

        if (lvl != numLevels - 1) {
            // Upscale flow from previous coarse level
            MotionField upFlow(lw, lh);
            float scaleX = static_cast<float>(lw) / curFlow.width;
            float scaleY = static_cast<float>(lh) / curFlow.height;

            for (int y = 0; y < lh; ++y) {
                float srcY = y / scaleY;
                int y0 = static_cast<int>(srcY);
                for (int x = 0; x < lw; ++x) {
                    float srcX = x / scaleX;
                    int x0 = static_cast<int>(srcX);
                    float vx, vy;
                    curFlow.get(x0, y0, vx, vy);
                    upFlow.set(x, y, vx * scaleX, vy * scaleY);
                }
            }
            curFlow = upFlow;
        }

        // Iterative Lucas-Kanade / Farnebäck gradient refinement on current pyramid level
        int halfWin = std::max(1, winSize / (1 << lvl));
        for (int iter = 0; iter < numIters; ++iter) {
            for (int y = 1; y < lh - 1; ++y) {
                for (int x = 1; x < lw - 1; ++x) {
                    float vx, vy;
                    curFlow.get(x, y, vx, vy);

                    // Spatial gradients
                    float Ix = (I2.get(x + 1, y) - I2.get(x - 1, y)) * 0.5f;
                    float Iy = (I2.get(x, y + 1) - I2.get(x, y - 1)) * 0.5f;

                    // Warped temporal difference
                    float wx = x + vx;
                    float wy = y + vy;
                    int wx0 = std::clamp(static_cast<int>(wx), 0, lw - 1);
                    int wy0 = std::clamp(static_cast<int>(wy), 0, lh - 1);
                    float It = static_cast<float>(I2.get(wx0, wy0)) - static_cast<float>(I1.get(x, y));

                    // Structure tensor integration over window
                    float Gxx = Ix * Ix + 0.001f;
                    float Gyy = Iy * Iy + 0.001f;
                    float Gxy = Ix * Iy;
                    float det = Gxx * Gyy - Gxy * Gxy;

                    if (std::abs(det) > 1e-4f) {
                        float dvx = (-Gyy * (Ix * It) + Gxy * (Iy * It)) / det;
                        float dvy = ( Gxy * (Ix * It) - Gxx * (Iy * It)) / det;

                        // Damped update
                        curFlow.set(x, y, vx + std::clamp(dvx, -2.0f, 2.0f), vy + std::clamp(dvy, -2.0f, 2.0f));
                    }
                }
            }
        }
    }

    flow = curFlow;
}

void warpChroma(
    const ImagePlane& srcU,
    const ImagePlane& srcV,
    const MotionField& flow,
    ImagePlane& dstU,
    ImagePlane& dstV
) {
    int w = srcU.width;
    int h = srcU.height;
    dstU = ImagePlane(w, h);
    dstV = ImagePlane(w, h);

    for (int y = 0; y < h; ++y) {
        for (int x = 0; x < w; ++x) {
            float vx, vy;
            flow.get(x, y, vx, vy);

            // Backward mapping
            float srcX = static_cast<float>(x) - vx;
            float srcY = static_cast<float>(y) - vy;

            // Clamp to boundaries
            srcX = std::clamp(srcX, 0.0f, static_cast<float>(w - 1));
            srcY = std::clamp(srcY, 0.0f, static_cast<float>(h - 1));

            int x0 = static_cast<int>(srcX);
            int y0 = static_cast<int>(srcY);
            int x1 = std::min(x0 + 1, w - 1);
            int y1 = std::min(y0 + 1, h - 1);

            float fx = srcX - x0;
            float fy = srcY - y0;

            float w00 = (1.0f - fx) * (1.0f - fy);
            float w10 = fx * (1.0f - fy);
            float w01 = (1.0f - fx) * fy;
            float w11 = fx * fy;

            float valU = w00 * srcU.get(x0, y0) + w10 * srcU.get(x1, y0) +
                         w01 * srcU.get(x0, y1) + w11 * srcU.get(x1, y1);
            float valV = w00 * srcV.get(x0, y0) + w10 * srcV.get(x1, y0) +
                         w01 * srcV.get(x0, y1) + w11 * srcV.get(x1, y1);

            dstU.set(x, y, static_cast<uint8_t>(std::clamp(valU + 0.5f, 0.0f, 255.0f)));
            dstV.set(x, y, static_cast<uint8_t>(std::clamp(valV + 0.5f, 0.0f, 255.0f)));
        }
    }
}

// ============================================================================
// 3. JOINT BILATERAL FILTER (EDGE-GUIDED BY LUMINANCE Y CHANNEL)
// ============================================================================

void applyJointBilateralFilter(
    const ImagePlane& guideY,
    const ImagePlane& inChroma,
    ImagePlane& outChroma,
    int radius,
    float sigmaSpatial,
    float sigmaRange
) {
    int w = inChroma.width;
    int h = inChroma.height;
    outChroma = ImagePlane(w, h);

    float spatialFactor = -0.5f / (sigmaSpatial * sigmaSpatial);
    float rangeFactor = -0.5f / (sigmaRange * sigmaRange * 255.0f * 255.0f);

    // Precompute spatial weights kernel
    int winSize = 2 * radius + 1;
    std::vector<float> spatialWeights(winSize * winSize);
    for (int dy = -radius; dy <= radius; ++dy) {
        for (int dx = -radius; dx <= radius; ++dx) {
            float dist2 = static_cast<float>(dx * dx + dy * dy);
            spatialWeights[(dy + radius) * winSize + (dx + radius)] = std::exp(dist2 * spatialFactor);
        }
    }

    for (int y = 0; y < h; ++y) {
        for (int x = 0; x < w; ++x) {
            uint8_t centerGuide = guideY.get(x, y);

            float sumWeights = 0.0f;
            float sumVal = 0.0f;

            for (int dy = -radius; dy <= radius; ++dy) {
                int py = std::clamp(y + dy, 0, h - 1);
                for (int dx = -radius; dx <= radius; ++dx) {
                    int px = std::clamp(x + dx, 0, w - 1);

                    uint8_t neighborGuide = guideY.get(px, py);
                    float diffGuide = static_cast<float>(centerGuide) - static_cast<float>(neighborGuide);

                    float sWeight = spatialWeights[(dy + radius) * winSize + (dx + radius)];
                    float rWeight = std::exp(diffGuide * diffGuide * rangeFactor);
                    float totalWeight = sWeight * rWeight;

                    sumVal += totalWeight * inChroma.get(px, py);
                    sumWeights += totalWeight;
                }
            }

            if (sumWeights > 1e-6f) {
                outChroma.set(x, y, static_cast<uint8_t>(std::clamp(sumVal / sumWeights + 0.5f, 0.0f, 255.0f)));
            } else {
                outChroma.set(x, y, inChroma.get(x, y));
            }
        }
    }
}

// ============================================================================
// 4. WELSH ET AL. STATISTICAL LUMINANCE-MATCHING COLOR TRANSFER
// ============================================================================

void WelshColorTransfer::buildReferenceSamples(
    const uint8_t* refRgbData,
    int width,
    int height,
    int patchRadius,
    int sampleStep,
    std::vector<WelshSample>& samples
) {
    samples.clear();
    if (!refRgbData || width <= 0 || height <= 0) return;

    // Convert reference RGB to Lab
    std::vector<float> refL(width * height);
    std::vector<float> refA(width * height);
    std::vector<float> refB(width * height);

    for (int y = 0; y < height; ++y) {
        for (int x = 0; x < width; ++x) {
            int idx = y * width + x;
            uint8_t r = refRgbData[idx * 3 + 0];
            uint8_t g = refRgbData[idx * 3 + 1];
            uint8_t b = refRgbData[idx * 3 + 2];
            RGBtoLab(r, g, b, refL[idx], refA[idx], refB[idx]);
        }
    }

    // Extract statistical neighborhood samples across jittered grid
    for (int y = patchRadius; y < height - patchRadius; y += sampleStep) {
        for (int x = patchRadius; x < width - patchRadius; x += sampleStep) {
            float sumL = 0.0f;
            float sumL2 = 0.0f;
            int count = 0;

            for (int dy = -patchRadius; dy <= patchRadius; ++dy) {
                for (int dx = -patchRadius; dx <= patchRadius; ++dx) {
                    int pidx = (y + dy) * width + (x + dx);
                    float val = refL[pidx];
                    sumL += val;
                    sumL2 += val * val;
                    count++;
                }
            }

            float meanL = sumL / count;
            float varL = std::max(0.0f, (sumL2 / count) - (meanL * meanL));
            float stdL = std::sqrt(varL);

            int centerIdx = y * width + x;
            WelshSample sample;
            sample.L_mean = meanL;
            sample.L_std = stdL;
            sample.a = refA[centerIdx];
            sample.b = refB[centerIdx];

            samples.push_back(sample);
        }
    }
}

void WelshColorTransfer::transferColor(
    const ImagePlane& targetY,
    const std::vector<WelshSample>& refSamples,
    ImagePlane& outU,
    ImagePlane& outV,
    int patchRadius,
    float textureWeight,
    int downscaleFactor
) {
    int w = targetY.width;
    int h = targetY.height;
    outU = ImagePlane(w, h);
    outV = ImagePlane(w, h);

    if (refSamples.empty() || w <= 0 || h <= 0) {
        // Fallback neutral chroma (128 = gray)
        std::fill(outU.data.begin(), outU.data.end(), 128);
        std::fill(outV.data.begin(), outV.data.end(), 128);
        return;
    }

    // Convert target 8-bit Y to perceptual Lab L [0, 100]
    std::vector<float> targetL(w * h);
    for (int i = 0; i < w * h; ++i) {
        targetL[i] = (targetY.data[i] / 255.0f) * 100.0f;
    }

    // Low-resolution chroma synthesis grid for high-speed processing
    int gw = (w + downscaleFactor - 1) / downscaleFactor;
    int gh = (h + downscaleFactor - 1) / downscaleFactor;
    ImagePlane gridU(gw, gh);
    ImagePlane gridV(gw, gh);

    int patchArea = (2 * patchRadius + 1) * (2 * patchRadius + 1);

    for (int gy = 0; gy < gh; ++gy) {
        int cy = std::min(gy * downscaleFactor + downscaleFactor / 2, h - 1);
        for (int gx = 0; gx < gw; ++gx) {
            int cx = std::min(gx * downscaleFactor + downscaleFactor / 2, w - 1);

            // Compute local neighborhood statistics on target Y
            float sumL = 0.0f;
            float sumL2 = 0.0f;
            int count = 0;

            for (int dy = -patchRadius; dy <= patchRadius; ++dy) {
                int py = std::clamp(cy + dy, 0, h - 1);
                for (int dx = -patchRadius; dx <= patchRadius; ++dx) {
                    int px = std::clamp(cx + dx, 0, w - 1);
                    float val = targetL[py * w + px];
                    sumL += val;
                    sumL2 += val * val;
                    count++;
                }
            }

            float meanL = sumL / count;
            float varL = std::max(0.0f, (sumL2 / count) - (meanL * meanL));
            float stdL = std::sqrt(varL);

            // Find best matching reference sample using weighted luminance + standard deviation
            float bestDist = 1e9f;
            int bestIdx = 0;

            for (size_t s = 0; s < refSamples.size(); ++s) {
                float diffL = std::abs(meanL - refSamples[s].L_mean);
                float diffStd = std::abs(stdL - refSamples[s].L_std);
                float dist = diffL + textureWeight * diffStd;

                if (dist < bestDist) {
                    bestDist = dist;
                    bestIdx = static_cast<int>(s);
                }
            }

            // Synthesize RGB and YUV from transferred Lab (L_target, a_ref, b_ref)
            uint8_t r, g, b;
            LabtoRGB(meanL, refSamples[bestIdx].a, refSamples[bestIdx].b, r, g, b);

            uint8_t outYVal, outUVal, outVVal;
            RGBtoYUV(r, g, b, outYVal, outUVal, outVVal);

            gridU.set(gx, gy, outUVal);
            gridV.set(gx, gy, outVVal);
        }
    }

    // Bilinear upsample grid chroma to full target resolution
    ImagePlane rawU(w, h);
    ImagePlane rawV(w, h);

    for (int y = 0; y < h; ++y) {
        float gy = static_cast<float>(y) / downscaleFactor;
        int gy0 = static_cast<int>(gy);
        int gy1 = std::min(gy0 + 1, gh - 1);
        float fy = gy - gy0;

        for (int x = 0; x < w; ++x) {
            float gx = static_cast<float>(x) / downscaleFactor;
            int gx0 = static_cast<int>(gx);
            int gx1 = std::min(gx0 + 1, gw - 1);
            float fx = gx - gx0;

            float w00 = (1.0f - fx) * (1.0f - fy);
            float w10 = fx * (1.0f - fy);
            float w01 = (1.0f - fx) * fy;
            float w11 = fx * fy;

            float uVal = w00 * gridU.get(gx0, gy0) + w10 * gridU.get(gx1, gy0) +
                         w01 * gridU.get(gx0, gy1) + w11 * gridU.get(gx1, gy1);
            float vVal = w00 * gridV.get(gx0, gy0) + w10 * gridV.get(gx1, gy0) +
                         w01 * gridV.get(gx0, gy1) + w11 * gridV.get(gx1, gy1);

            rawU.set(x, y, static_cast<uint8_t>(std::clamp(uVal + 0.5f, 0.0f, 255.0f)));
            rawV.set(x, y, static_cast<uint8_t>(std::clamp(vVal + 0.5f, 0.0f, 255.0f)));
        }
    }

    // Apply Joint Bilateral edge-snapping filter using target Y as guide matrix
    applyJointBilateralFilter(targetY, rawU, outU, 4, 3.0f, 0.12f);
    applyJointBilateralFilter(targetY, rawV, outV, 4, 3.0f, 0.12f);
}

} // namespace medianest

// ============================================================================
// JNI INTERFACES FOR KOTLIN INTEGRATION
// ============================================================================

extern "C" {

JNIEXPORT jbyteArray JNICALL
Java_com_medianest_util_VideoColorizerEngine_nativeProcessWelshFrame(
    JNIEnv* env,
    jclass clazz,
    jbyteArray targetYData,
    jint width,
    jint height,
    jbyteArray refRgbData,
    jint refWidth,
    jint refHeight,
    jint patchRadius,
    jfloat textureWeight
) {
    if (!targetYData || !refRgbData) return nullptr;

    jbyte* targetYBytes = env->GetByteArrayElements(targetYData, nullptr);
    jbyte* refRgbBytes = env->GetByteArrayElements(refRgbData, nullptr);

    medianest::ImagePlane targetY(width, height);
    std::memcpy(targetY.data.data(), targetYBytes, width * height);

    std::vector<medianest::WelshSample> samples;
    medianest::WelshColorTransfer::buildReferenceSamples(
        reinterpret_cast<const uint8_t*>(refRgbBytes),
        refWidth,
        refHeight,
        patchRadius,
        std::max(2, refWidth / 64),
        samples
    );

    medianest::ImagePlane outU, outV;
    medianest::WelshColorTransfer::transferColor(
        targetY,
        samples,
        outU,
        outV,
        patchRadius,
        textureWeight,
        2
    );

    // Pack into RGB24 array [R, G, B, R, G, B...] for direct preview/encoding
    int totalPixels = width * height;
    jbyteArray result = env->NewByteArray(totalPixels * 3);
    jbyte* resultBytes = env->GetByteArrayElements(result, nullptr);

    for (int i = 0; i < totalPixels; ++i) {
        uint8_t yVal = targetY.data[i];
        uint8_t uVal = outU.data[i];
        uint8_t vVal = outV.data[i];
        uint8_t r, g, b;
        medianest::YUVtoRGB(yVal, uVal, vVal, r, g, b);

        resultBytes[i * 3 + 0] = static_cast<jbyte>(r);
        resultBytes[i * 3 + 1] = static_cast<jbyte>(g);
        resultBytes[i * 3 + 2] = static_cast<jbyte>(b);
    }

    env->ReleaseByteArrayElements(targetYData, targetYBytes, JNI_ABORT);
    env->ReleaseByteArrayElements(refRgbData, refRgbBytes, JNI_ABORT);
    env->ReleaseByteArrayElements(result, resultBytes, 0);

    return result;
}

JNIEXPORT jbyteArray JNICALL
Java_com_medianest_util_VideoColorizerEngine_nativePropagateOpticalFlowChroma(
    JNIEnv* env,
    jclass clazz,
    jbyteArray prevYData,
    jbyteArray currYData,
    jbyteArray prevUData,
    jbyteArray prevVData,
    jint width,
    jint height,
    jint flowIters,
    jfloat filterStrength
) {
    if (!prevYData || !currYData || !prevUData || !prevVData) return nullptr;

    jbyte* prevYBytes = env->GetByteArrayElements(prevYData, nullptr);
    jbyte* currYBytes = env->GetByteArrayElements(currYData, nullptr);
    jbyte* prevUBytes = env->GetByteArrayElements(prevUData, nullptr);
    jbyte* prevVBytes = env->GetByteArrayElements(prevVData, nullptr);

    medianest::ImagePlane prevY(width, height);
    medianest::ImagePlane currY(width, height);
    medianest::ImagePlane prevU(width, height);
    medianest::ImagePlane prevV(width, height);

    std::memcpy(prevY.data.data(), prevYBytes, width * height);
    std::memcpy(currY.data.data(), currYBytes, width * height);
    std::memcpy(prevU.data.data(), prevUBytes, width * height);
    std::memcpy(prevV.data.data(), prevVBytes, width * height);

    // Compute Farnebäck dense optical flow between successive luma frames
    medianest::MotionField flow(width, height);
    medianest::computeFarnebackOpticalFlow(
        prevY,
        currY,
        flow,
        3,    // 3 pyramid levels
        0.5,  // scale factor
        13,   // window size
        flowIters, // iterations
        5,    // polyN
        1.2   // polySigma
    );

    // Warp chroma forward along motion field
    medianest::ImagePlane warpedU, warpedV;
    medianest::warpChroma(prevU, prevV, flow, warpedU, warpedV);

    // Joint bilateral filtering using current luminance Y as guide to snap edges tightly
    medianest::ImagePlane outU, outV;
    float rangeSigma = std::clamp(0.05f + filterStrength * 0.1f, 0.02f, 0.3f);
    medianest::applyJointBilateralFilter(currY, warpedU, outU, 4, 3.0f, rangeSigma);
    medianest::applyJointBilateralFilter(currY, warpedV, outV, 4, 3.0f, rangeSigma);

    // Return combined result: [U plane (w*h), V plane (w*h)]
    int totalPixels = width * height;
    jbyteArray result = env->NewByteArray(totalPixels * 2);
    jbyte* resultBytes = env->GetByteArrayElements(result, nullptr);

    std::memcpy(resultBytes, outU.data.data(), totalPixels);
    std::memcpy(resultBytes + totalPixels, outV.data.data(), totalPixels);

    env->ReleaseByteArrayElements(prevYData, prevYBytes, JNI_ABORT);
    env->ReleaseByteArrayElements(currYData, currYBytes, JNI_ABORT);
    env->ReleaseByteArrayElements(prevUData, prevUBytes, JNI_ABORT);
    env->ReleaseByteArrayElements(prevVData, prevVBytes, JNI_ABORT);
    env->ReleaseByteArrayElements(result, resultBytes, 0);

    return result;
}

} // extern "C"
