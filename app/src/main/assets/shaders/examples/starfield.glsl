// Uses iTime, iResolution, tlDark.
// Drifting stars in three depth layers over a faint nebula. Lighter sky when tlDark is 0.

float hash21(vec2 p) {
    p = fract(p * vec2(233.34, 851.73));
    p += dot(p, p + 23.45);
    return fract(p.x * p.y);
}

float stars(vec2 uv, float scale, float speed, float size) {
    vec2 g = uv * scale - vec2(1.0, -1.0) * (iTime * speed);
    vec2 cell = floor(g);
    vec2 f = fract(g) - 0.5;
    float h = hash21(cell);
    vec2 off = vec2(hash21(cell + 3.1), hash21(cell + 7.7)) - 0.5;
    float d = length(f - off * 0.6);
    float tw = 0.7 + 0.3 * sin(iTime * (1.0 + 2.0 * h) + h * 30.0);
    return step(0.82, h) * smoothstep(size, 0.0, d) * tw;
}

void mainImage(out vec4 fragColor, in vec2 fragCoord) {
    vec2 uv = fragCoord / iResolution.xy;
    vec2 q = vec2(uv.x * iResolution.x / iResolution.y, uv.y);
    float dark = clamp(tlDark, 0.0, 1.0);
    vec3 skyTop = mix(vec3(0.55, 0.65, 0.85), vec3(0.02, 0.03, 0.08), dark);
    vec3 skyBot = mix(vec3(0.75, 0.7, 0.85), vec3(0.08, 0.04, 0.14), dark);
    vec3 col = mix(skyBot, skyTop, uv.y);
    float neb = 0.5 + 0.5 * sin(q.x * 2.5 + sin(q.y * 3.0 + iTime * 0.03) * 1.5);
    col += vec3(0.25, 0.1, 0.35) * neb * smoothstep(0.2, 1.0, uv.y) * 0.25 * dark;
    float s = stars(q, 14.0, 0.02, 0.25) * 0.5
        + stars(q + 5.0, 24.0, 0.04, 0.2) * 0.75
        + stars(q + 11.0, 40.0, 0.07, 0.16);
    vec3 starCol = mix(vec3(0.25, 0.3, 0.5), vec3(1.0, 0.97, 0.9), dark);
    col = mix(col, starCol, clamp(s, 0.0, 1.0));
    fragColor = vec4(col, 1.0);
}
