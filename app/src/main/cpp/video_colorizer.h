#ifndef MEDIANEST_VIDEO_COLORIZER_H
#define MEDIANEST_VIDEO_COLORIZER_H

#include <vector>
#include <cstdint>
#include <cmath>
#include <string>
#include <jni.h>

namespace medianest {

// Representation of an image plane (8-bit grayscale or single channel)
struct ImagePlane {
    int width = 0;
    int height = 0;
    int stride = 0;
    std::vector<uint8_t> data;

    ImagePlane() = default;
    ImagePlane(int w, int h) : width(w), height(h), stride(w), data(w * h, 0) {}
    ImagePlane(int w, int h, int s) : width(w), height(h), stride(s), data(s * h, 0) {}

    inline uint8_t get(int x, int y) const {
        if (x < 0) x = 0; else if (x >= width) x = width - 1;
        if (y < 0) y = 0; else if (y >= height) y = height - 1;
        return data[y * stride + x];
    }

    inline void set(int x, int y, uint8_t val) {
        if (x >= 0 && x < width && y >= 0 && y < height) {
            data[y * stride + x] = val;
        }
    }
};

// 2D Motion Vector Field
struct MotionField {
    int width = 0;
    int height = 0;
    std::vector<float> vx;
    std::vector<float> vy;

    MotionField() = default;
    MotionField(int w, int h) : width(w), height(h), vx(w * h, 0.0f), vy(w * h, 0.0f) {}

    inline void get(int x, int y, float& out_vx, float& out_vy) const {
        if (x < 0) x = 0; else if (x >= width) x = width - 1;
        if (y < 0) y = 0; else if (y >= height) y = height - 1;
        int idx = y * width + x;
        out_vx = vx[idx];
        out_vy = vy[idx];
    }

    inline void set(int x, int y, float in_vx, float in_vy) {
        if (x >= 0 && x < width && y >= 0 && y < height) {
            int idx = y * width + x;
            vx[idx] = in_vx;
            vy[idx] = in_vy;
        }
    }
};

// Lab Color pixel
struct LabPixel {
    float L; // [0, 100]
    float a; // [-128, 127]
    float b; // [-128, 127]
};

// RGB to Lab & YUV conversions
void RGBtoLab(uint8_t r, uint8_t g, uint8_t b, float& L, float& a, float& b_out);
void LabtoRGB(float L, float a, float b_in, uint8_t& r, uint8_t& g, uint8_t& b);
void RGBtoYUV(uint8_t r, uint8_t g, uint8_t b, uint8_t& Y, uint8_t& U, uint8_t& V);
void YUVtoRGB(uint8_t Y, uint8_t U, uint8_t V, uint8_t& r, uint8_t& g, uint8_t& b);

// Farnebäck Dense Optical Flow
void computeFarnebackOpticalFlow(
    const ImagePlane& prevLuma,
    const ImagePlane& currLuma,
    MotionField& flow,
    int numLevels = 3,
    double pyrScale = 0.5,
    int winSize = 15,
    int numIters = 3,
    int polyN = 5,
    double polySigma = 1.2
);

// Bilinear Chroma Warping along motion vectors
void warpChroma(
    const ImagePlane& srcU,
    const ImagePlane& srcV,
    const MotionField& flow,
    ImagePlane& dstU,
    ImagePlane& dstV
);

// Joint Bilateral Filter (Guided by Luminance Y channel)
void applyJointBilateralFilter(
    const ImagePlane& guideY,
    const ImagePlane& inChroma,
    ImagePlane& outChroma,
    int radius = 4,
    float sigmaSpatial = 3.0f,
    float sigmaRange = 0.1f // in normalized [0, 1] range
);

// Welsh et al. Statistical Luminance Matching
struct WelshSample {
    float L_mean;
    float L_std;
    float a;
    float b;
};

class WelshColorTransfer {
public:
    static void buildReferenceSamples(
        const uint8_t* refRgbData,
        int width,
        int height,
        int patchRadius,
        int sampleStep,
        std::vector<WelshSample>& samples
    );

    static void transferColor(
        const ImagePlane& targetY,
        const std::vector<WelshSample>& refSamples,
        ImagePlane& outU,
        ImagePlane& outV,
        int patchRadius = 2,
        float textureWeight = 0.5f,
        int downscaleFactor = 2
    );
};

} // namespace medianest

#endif // MEDIANEST_VIDEO_COLORIZER_H
