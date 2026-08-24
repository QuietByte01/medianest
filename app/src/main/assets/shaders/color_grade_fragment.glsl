precision mediump float;

uniform sampler2D u_sTexture;
uniform float u_effect_mode;
varying vec2 vTexCoords;

// RGB to HSL / HSV conversions
vec3 rgb2hsv(vec3 c) {
    vec4 K = vec4(0.0, -1.0 / 3.0, 2.0 / 3.0, -1.0);
    vec4 p = mix(vec4(c.bg, K.wz), vec4(c.gb, K.xy), step(c.b, c.g));
    vec4 q = mix(vec4(p.xyw, c.r), vec4(c.r, p.yzx), step(p.x, c.r));

    float d = q.x - min(q.w, q.y);
    float e = 1.0e-10;
    return vec3(abs(q.z + (q.w - q.y) / (6.0 * d + e)), d / (q.x + e), q.x);
}

vec3 hsv2rgb(vec3 c) {
    vec4 K = vec4(1.0, 2.0 / 3.0, 1.0 / 3.0, 3.0);
    vec3 p = abs(fract(c.xxx + K.xyz) * 6.0 - K.www);
    return c.z * mix(K.xxx, clamp(p - K.xxx, 0.0, 1.0), c.y);
}

void main() {
    vec4 color = texture2D(u_sTexture, vTexCoords);
    vec3 rgb = color.rgb;

    int mode = int(u_effect_mode + 0.5);

    if (mode == 0) {
        // Normal / Bypass
        gl_FragColor = color;
        return;
    } 
    else if (mode == 1) {
        // True Tone (Warm White-Point)
        rgb.r = min(rgb.r * 1.05, 1.0);
        rgb.g = rgb.g * 0.98;
        rgb.b = rgb.b * 0.90;
    } 
    else if (mode == 2) {
        // Cinema (Teal & Orange)
        float luma = dot(rgb, vec3(0.2126, 0.7152, 0.0722));
        vec3 shadows = vec3(0.0, 0.2, 0.3) * (1.0 - luma);
        vec3 highlights = vec3(1.0, 0.6, 0.2) * luma;
        rgb = mix(rgb, rgb * (shadows + highlights), 0.5);
    } 
    else if (mode == 3) {
        // Black & White (Monochrome)
        float luma = dot(rgb, vec3(0.2126, 0.7152, 0.0722));
        rgb = vec3(luma);
    } 
    else if (mode == 4) {
        // Vibrance (Smart Saturation)
        vec3 hsv = rgb2hsv(rgb);
        // avoid shifting skin tones too much (around hue 0.0-0.1)
        float skinDist = abs(hsv.x - 0.05);
        float vibBoost = smoothstep(0.0, 0.1, skinDist) * 1.5;
        hsv.y = min(hsv.y * (1.0 + 0.5 * vibBoost), 1.0);
        rgb = hsv2rgb(hsv);
    } 
    else if (mode == 5) {
        // Night Shift (Blue Light Filter)
        rgb.g = rgb.g * 0.9;
        rgb.b = rgb.b * 0.6;
    } 
    else if (mode == 6) {
        // Vintage (35mm)
        rgb.r = min(rgb.r * 1.05 + 0.05, 1.0);
        rgb.g = min(rgb.g * 0.95 + 0.02, 1.0);
        rgb.b = max(rgb.b * 0.80 - 0.05, 0.0);
        float luma = dot(rgb, vec3(0.2126, 0.7152, 0.0722));
        rgb = mix(rgb, vec3(luma), 0.2); // desaturate slightly
    } 
    else if (mode == 7) {
        // Clarity (Contrast / HDR Simulator)
        rgb = (rgb - 0.5) * 1.2 + 0.5;
        rgb = clamp(rgb, 0.0, 1.0);
    }
    else if (mode == 8) {
        // Balanced (S-Curve Contrast / Exposure balance)
        rgb = 0.5 * sin((rgb - 0.5) * 3.14159265) + 0.5;
    }
    else if (mode == 9) {
        // Natural (Saturation Normalization)
        vec3 hsv = rgb2hsv(rgb);
        float naturalSat = 0.45;
        hsv.y = mix(hsv.y, naturalSat, 0.5 * hsv.y);
        rgb = hsv2rgb(hsv);
    }
    else if (mode == 10) {
        // Warm Sun
        rgb.r = min(rgb.r * 1.15, 1.0);
        rgb.g = min(rgb.g * 1.05, 1.0);
        rgb.b = rgb.b * 0.85;
    }
    else if (mode == 11) {
        // Cool Cyan
        rgb.r = rgb.r * 0.85;
        rgb.g = min(rgb.g * 1.05, 1.0);
        rgb.b = min(rgb.b * 1.20, 1.0);
    }
    else if (mode == 12) {
        // Cyberpunk (Pink & Cyan split)
        float luma = dot(rgb, vec3(0.2126, 0.7152, 0.0722));
        vec3 shadowPink = vec3(0.8, 0.0, 0.5) * (1.0 - luma);
        vec3 highlightCyan = vec3(0.0, 0.8, 1.0) * luma;
        rgb = mix(rgb, shadowPink + highlightCyan, 0.45);
    }
    else if (mode == 13) {
        // Dreamy (Soft contrast & lavender tint)
        rgb.r = min(rgb.r * 1.08 + 0.03, 1.0);
        rgb.g = min(rgb.g * 0.98, 1.0);
        rgb.b = min(rgb.b * 1.15 + 0.05, 1.0);
    }
    else if (mode == 14) {
        // Sepia
        float r = dot(rgb, vec3(0.393, 0.769, 0.189));
        float g = dot(rgb, vec3(0.349, 0.686, 0.168));
        float b = dot(rgb, vec3(0.272, 0.534, 0.131));
        rgb = clamp(vec3(r, g, b), 0.0, 1.0);
    }

    gl_FragColor = vec4(rgb, color.a);
}
