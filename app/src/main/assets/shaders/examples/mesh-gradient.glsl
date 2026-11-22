// Uses iTime, iResolution.
// Mesh gradient: four colour spots drifting on their own paths, bent by a slow swirl.
// Adapted from Paper Design shaders (Apache-2.0) through the AGSL port in AndroidPoet/mirage
// at 9da9b00f678d57ce9989838ac513ffa88a11b038 (Apache-2.0, Copyright 2026 Ranbir Singh).
// Licensed under the Apache License, Version 2.0, http://www.apache.org/licenses/LICENSE-2.0;
// see THIRD_PARTY_NOTICES.md. Changes: Shadertoy uniforms, fixed palette, no grain.

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

void mainImage(out vec4 fragColor, in vec2 fragCoord) {
    vec2 uv = vec2(fragCoord.x / iResolution.x, 1.0 - fragCoord.y / iResolution.y);
    fragColor = vec4(meshImage(uv, 0.25 * iTime + 20.75), 1.0);
}
