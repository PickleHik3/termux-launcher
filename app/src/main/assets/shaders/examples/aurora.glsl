// Uses iTime, iResolution, tlColor0 (the look's accent).
// Slow curtains of light over a near-black sky, with twinkling stars.

float hash21(vec2 p) {
    p = fract(p * vec2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}

void mainImage(out vec4 fragColor, in vec2 fragCoord) {
    vec2 uv = fragCoord / iResolution.xy;
    float asp = iResolution.x / iResolution.y;
    float t = iTime * 0.12;
    vec3 col = vec3(0.01, 0.015, 0.03) + vec3(0.0, 0.01, 0.02) * uv.y;
    vec3 cool = vec3(0.1, 0.9, 0.7);
    for (int i = 0; i < 4; i++) {
        float fi = float(i);
        float y = 0.55 + 0.08 * fi
            + 0.07 * sin(uv.x * (2.0 + fi) + t * (1.0 + 0.3 * fi) + fi * 1.7)
            + 0.03 * sin(uv.x * 7.0 - t * 2.0 + fi);
        float d = uv.y - y;
        float band = exp(-d * d * 90.0) * (0.5 + 0.5 * sin(uv.x * 3.0 + t * 1.5 + fi * 2.3));
        vec3 tint = mix(tlColor0, cool, 0.35 + 0.2 * fi);
        col += tint * band * 0.3;
    }
    vec2 g = vec2(uv.x * asp, uv.y) * 40.0;
    vec2 cell = floor(g);
    vec2 f = fract(g) - 0.5;
    float h = hash21(cell);
    float star = step(0.93, h) * (1.0 - smoothstep(0.0, 0.12, length(f)));
    star *= 0.5 + 0.5 * sin(iTime * (1.0 + 3.0 * h) + h * 40.0);
    col += vec3(0.8, 0.9, 1.0) * star * 0.7;
    fragColor = vec4(col, 1.0);
}
