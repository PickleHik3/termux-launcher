// Uses iTime, iResolution, iMouse.
// Water light: warped grids summed into bright ripples on deep blue. Touch to brighten.

float grid(vec2 p, float t) {
    p += 0.35 * vec2(sin(p.y * 1.7 + t), cos(p.x * 1.3 - t * 0.8));
    vec2 s = sin(p * 3.0);
    return 1.0 - abs(s.x * s.y);
}

void mainImage(out vec4 fragColor, in vec2 fragCoord) {
    vec2 uv = (fragCoord - 0.5 * iResolution.xy) / iResolution.y;
    float t = iTime * 0.25;
    float v = grid(uv * 2.0, t)
        * grid(uv * 3.1 + vec2(1.7, 0.4), -t * 1.2)
        * grid(uv * 1.3 + vec2(-0.6, 2.2), t * 0.7);
    float c = pow(clamp(v, 0.0, 1.0), 3.0);
    vec3 deep = mix(vec3(0.0, 0.05, 0.16), vec3(0.0, 0.12, 0.28), uv.y + 0.5);
    vec3 col = deep + vec3(0.35, 0.8, 1.0) * c * 0.9;
    if (iMouse.z > 0.0) {
        vec2 m = (iMouse.xy - 0.5 * iResolution.xy) / iResolution.y;
        float near = smoothstep(0.5, 0.0, length(uv - m));
        col += vec3(0.2, 0.5, 0.7) * near * (0.25 + c);
    }
    fragColor = vec4(col, 1.0);
}
