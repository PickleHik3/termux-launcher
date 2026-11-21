// Uses iTime, iResolution, iMouse. Touch and drag to move the bright spot.
// Shadertoy-style mainImage, GLSL ES 3.00; the launcher adds the prelude and main().

void mainImage(out vec4 fragColor, in vec2 fragCoord) {
    vec2 uv = (fragCoord - 0.5 * iResolution.xy) / iResolution.y;
    float t = iTime * 0.6;

    float v = sin(uv.x * 6.0 + t);
    v += sin((uv.y * 6.0 + t) * 0.7);
    v += sin((uv.x * 4.0 + uv.y * 5.0 + t * 1.3) * 0.8);
    vec2 c = uv + 0.6 * vec2(sin(t * 0.5), cos(t * 0.4));
    v += sin(sqrt(dot(c, c) * 40.0 + 1.0) - t);
    v *= 0.5;

    vec3 col = 0.5 + 0.5 * cos(3.14159 * v + vec3(0.0, 2.1, 4.2));

    if (iMouse.z > 0.0) {
        vec2 m = (iMouse.xy - 0.5 * iResolution.xy) / iResolution.y;
        col += 0.35 * (1.0 - smoothstep(0.0, 0.25, length(uv - m)));
    }

    fragColor = vec4(col * 0.85, 1.0);
}
