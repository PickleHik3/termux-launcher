#ifdef GL_FRAGMENT_PRECISION_HIGH
precision highp float;
#else
precision mediump float;
#endif

// ShaderEditor-style gradient: its own void main(), gl_FragColor, and its own
// uniform declarations (resolution, time, touch), compiled as GLSL ES 1.00.

uniform vec2 resolution;
uniform float time;
uniform vec2 touch;

void main(void) {
    vec2 uv = gl_FragCoord.xy / resolution.xy;
    vec3 top = vec3(0.10, 0.16, 0.35);
    vec3 bottom = vec3(0.85, 0.45, 0.30);
    float wave = 0.04 * sin(uv.x * 6.2831 + time * 0.8);
    vec3 col = mix(bottom, top, smoothstep(0.0, 1.0, uv.y + wave));
    float d = length(gl_FragCoord.xy - touch) / resolution.y;
    col += 0.15 * smoothstep(0.2, 0.0, d);
    gl_FragColor = vec4(col, 1.0);
}
