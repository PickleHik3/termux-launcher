// Uses iTime, iResolution, tlPageOffset, tlColor0, tlColor1, tlColor2.
// The look's own colours, barely moving. For people who want calm.

float hash21(vec2 p) {
    p = fract(p * vec2(127.1, 311.7));
    p += dot(p, p + 34.23);
    return fract(p.x * p.y);
}

float vnoise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    float a = hash21(i);
    float b = hash21(i + vec2(1.0, 0.0));
    float c = hash21(i + vec2(0.0, 1.0));
    float d = hash21(i + vec2(1.0, 1.0));
    return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
}

void mainImage(out vec4 fragColor, in vec2 fragCoord) {
    vec2 uv = fragCoord / iResolution.xy;
    uv.x += 0.1 * tlPageOffset;
    vec2 p = vec2(uv.x * iResolution.x / iResolution.y, uv.y) * 1.6;
    float t = iTime * 0.015;
    float n = 0.65 * vnoise(p + vec2(t, -t * 0.7)) + 0.35 * vnoise(p * 2.1 - vec2(t * 1.3, t));
    vec3 col = mix(tlColor1, tlColor0, smoothstep(0.3, 0.9, n) * 0.45);
    col = mix(col, tlColor2, smoothstep(0.6, 1.0, vnoise(p * 0.7 + 7.0 + t)) * 0.12);
    fragColor = vec4(col, 1.0);
}
