// Uses iTime, iResolution.
// Fluted glass: ribbed vertical glass over a drifting picture (the built-in mesh gradient).
// Adapted from Paper Design shaders (Apache-2.0) through the AGSL port in AndroidPoet/mirage
// at 9da9b00f678d57ce9989838ac513ffa88a11b038 (Apache-2.0, Copyright 2026 Ranbir Singh).
// Licensed under the Apache License, Version 2.0, http://www.apache.org/licenses/LICENSE-2.0;
// see THIRD_PARTY_NOTICES.md. Changes: Shadertoy uniforms, fixed settings (straight lines, prism
// distortion, no blur or grain), mesh gradient as the image, derivatives from fwidth.

vec3 meshImage(vec2 uv, float t) {
    uv = 0.5 + (uv - 0.5) * vec2(iResolution.x / iResolution.y, 1.0) / max(iResolution.x / iResolution.y, 1.0);
    float radius = smoothstep(0.0, 1.0, length(uv - 0.5));
    float center = 1.0 - radius;
    for (float i = 1.0; i <= 2.0; i++) {
        uv.x += 0.8 * center / i * sin(t + i * 0.4 * smoothstep(0.0, 1.0, uv.y)) * cos(0.2 * t + i * 2.4 * smoothstep(0.0, 1.0, uv.y));
        uv.y += 0.8 * center / i * cos(t + i * 2.0 * smoothstep(0.0, 1.0, uv.x));
    }
    float a = 0.3 * radius;
    vec2 q = mat2(cos(-a), sin(-a), -sin(-a), cos(-a)) * (uv - 0.5) + 0.5;
    vec3 colors[4] = vec3[4](vec3(0.878, 0.918, 1.0), vec3(0.141, 0.114, 0.604),
        vec3(0.969, 0.314, 0.573), vec3(0.624, 0.314, 0.827));
    vec3 sum = vec3(0.0);
    float total = 0.0;
    for (int i = 0; i < 4; i++) {
        float k = float(i) * 0.37;
        vec2 pos = 0.5 + 0.5 * vec2(sin(t * (0.6 + fract(float(i) / 3.0) * 0.9) + k),
            cos(t * (0.8 + fract(float(i + 1) / 4.0)) + k * 1.5));
        float w = 1.0 / (pow(length(q - pos), 3.5) + 1e-3);
        sum += colors[i] * w;
        total += w;
    }
    return sum / max(1e-4, total);
}

float smoothFract(float x, float w) {
    float f = fract(x);
    return mix(f, 1.0 - f, smoothstep(-w, w, abs(f - 0.5) - 0.5));
}

float uvFrame(vec2 uv, float softness, vec2 fw) {
    vec2 a = 2.0 * fw;
    return smoothstep(0.0, a.x + softness, uv.x) * (1.0 - smoothstep(1.0 - softness - a.x, 1.0, uv.x))
        * smoothstep(0.0, a.y + softness, uv.y) * (1.0 - smoothstep(1.0 - softness - a.y, 1.0, uv.y));
}

void mainImage(out vec4 fragColor, in vec2 fragCoord) {
    float t = 0.25 * iTime + 20.75;
    vec2 imageUV = vec2(fragCoord.x / iResolution.x, 1.0 - fragCoord.y / iResolution.y);
    vec2 uvFw = fwidth(imageUV);
    float patternSize = 102.5;
    float px = (imageUV.x - 0.5) * patternSize;
    float fw = fwidth(px);

    float x = smoothFract(px, fw);
    float xn = fract(px) + 0.0001;
    float hw = 2.0 * max(0.001, fw);
    float highlights = clamp(0.1 * (1.0 - smoothstep(0.0, hw, xn) * smoothstep(1.0, 1.0 - hw, xn)), 0.0, 1.0);
    float shadows = pow(x, 1.3);

    float distortion = 0.5 - pow(1.5 * x, 3.0);
    float frameFade = pow(1.5 * x, 3.0);
    float aa = max(0.2, fw) + 0.1;
    float fadeX = smoothstep(0.0, aa, xn) * smoothstep(1.0, 1.0 - aa, xn);
    distortion = mix(0.5, distortion, fadeX) * 1.5;
    frameFade *= 0.5;
    shadows = min(shadows, 1.0) * 0.0625;

    vec2 uv = vec2((floor(px) + fract(px) + distortion) / patternSize + 0.5, imageUV.y);
    float frame = uvFrame(uv, 0.01 + 0.015 * frameFade, uvFw);

    vec3 color = vec3(highlights);
    float opacity = highlights;
    shadows = mix(shadows, 0.0, highlights);
    color = mix(color, vec3(0.0), 0.5 * shadows);
    opacity = clamp(opacity + shadows, 0.0, 1.0);
    color += meshImage(uv, t) * (1.0 - opacity) * frame;
    opacity += (1.0 - opacity) * frame;
    color += meshImage(imageUV, t) * (1.0 - opacity);
    fragColor = vec4(clamp(color, 0.0, 1.0), 1.0);
}
