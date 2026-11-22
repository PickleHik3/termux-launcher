// Uses iTime, iResolution.
// Water: caustic ripples and slow waves bending a drifting picture (the built-in mesh gradient).
// Adapted from Paper Design shaders (Apache-2.0) through the AGSL port in AndroidPoet/mirage
// at 9da9b00f678d57ce9989838ac513ffa88a11b038 (Apache-2.0, Copyright 2026 Ranbir Singh).
// Licensed under the Apache License, Version 2.0, http://www.apache.org/licenses/LICENSE-2.0;
// see THIRD_PARTY_NOTICES.md. Changes: Shadertoy uniforms, fixed settings, mesh gradient as the
// image, derivatives from fwidth.

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

vec3 permute(vec3 x) { return mod(((x * 34.0) + 1.0) * x, 289.0); }

float snoise(vec2 v) {
    const vec4 C = vec4(0.211324865405187, 0.366025403784439, -0.577350269189626, 0.024390243902439);
    vec2 i = floor(v + dot(v, C.yy));
    vec2 x0 = v - i + dot(i, C.xx);
    vec2 i1 = (x0.x > x0.y) ? vec2(1.0, 0.0) : vec2(0.0, 1.0);
    vec4 x12 = x0.xyxy + C.xxzz;
    x12.xy -= i1;
    i = mod(i, 289.0);
    vec3 p = permute(permute(i.y + vec3(0.0, i1.y, 1.0)) + i.x + vec3(0.0, i1.x, 1.0));
    vec3 m = max(0.5 - vec3(dot(x0, x0), dot(x12.xy, x12.xy), dot(x12.zw, x12.zw)), 0.0);
    m = m * m;
    m = m * m;
    vec3 x = 2.0 * fract(p * C.www) - 1.0;
    vec3 h = abs(x) - 0.5;
    vec3 a0 = x - floor(x + 0.5);
    m *= 1.79284291400159 - 0.85373472095314 * (a0 * a0 + h * h);
    vec3 g;
    g.x = a0.x * x0.x + h.x * x0.y;
    g.yz = a0.yz * x12.xz + h.yz * x12.yw;
    return 130.0 * dot(m, g);
}

float causticNoise(vec2 uv, float t, float scale) {
    vec2 n = vec2(0.1);
    vec2 N = vec2(0.1);
    mat2 m = mat2(cos(0.5), sin(0.5), -sin(0.5), cos(0.5));
    for (int j = 0; j < 6; j++) {
        uv *= m;
        n *= m;
        vec2 q = uv * scale + float(j) + n + (0.5 + 0.5 * float(j)) * (mod(float(j), 2.0) - 1.0) * t;
        n += sin(q);
        N += cos(q) / scale;
        scale *= 1.1;
    }
    return N.x + N.y + 1.0;
}

float uvFrame(vec2 uv, vec2 aa) {
    vec2 a = 2.0 * aa;
    return smoothstep(0.0, a.x, uv.x) * (1.0 - smoothstep(1.0 - a.x, 1.0, uv.x))
        * smoothstep(0.0, a.y, uv.y) * (1.0 - smoothstep(1.0 - a.y, 1.0, uv.y));
}

void mainImage(out vec4 fragColor, in vec2 fragCoord) {
    vec2 imageUV = vec2(fragCoord.x / iResolution.x, 1.0 - fragCoord.y / iResolution.y);
    vec2 aa = fwidth(imageUV);
    float t = iTime * 0.5;
    vec2 patternUV = (imageUV - 0.5) * vec2(iResolution.x / iResolution.y, 1.0) / 0.1;

    float waves = 0.3;
    float wavesNoise = snoise((0.3 + 0.1 * sin(t)) * 0.1 * patternUV + vec2(0.0, 0.4 * t));
    float caustic = causticNoise(patternUV + waves * vec2(1.0, -1.0) * wavesNoise, 2.0 * t, 1.5);
    caustic += 0.5 * causticNoise(patternUV + 2.0 * waves * vec2(1.0, -1.0) * wavesNoise, 1.5 * t, 2.0);
    caustic *= caustic;

    float bend = 0.1 * waves * wavesNoise;
    vec2 uv = imageUV + vec2(bend, -bend) + 0.1 * 0.02 * caustic;
    float frame = uvFrame(uv, aa);

    vec3 image = meshImage(uv, 0.25 * iTime + 20.75);
    vec3 color = mix(vec3(0.565), image, frame);
    caustic = max(-0.2, caustic);
    color = mix(color, vec3(1.0), 0.05 * 0.07 * caustic);
    color += 0.025 * 0.07 * caustic * (0.5 + 0.5 * wavesNoise);
    fragColor = vec4(clamp(color, 0.0, 1.0), 1.0);
}
