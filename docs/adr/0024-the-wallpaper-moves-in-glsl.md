---
status: accepted
date: 2026-11-09
amends: live-wallpapers-2026-11-05 decisions 2-4 (first-party AGSL scenes, pure procedural, no texture inputs)
---

# The wallpaper moves in GLSL, fed by the user

Spec: [`project-docs/active/shader-video-wallpapers-2026-11-09.md`](../../project-docs/active/shader-video-wallpapers-2026-11-09.md).

On 2026-11-09 the developer replaced the first-party AGSL Live scenes. AGSL is pinned at GLSL
ES 1.0 (no derivatives, bit operations, dynamic loops, preprocessor, cubemaps or feedback), which
kept the scenes short of what they wanted, and nothing from Shadertoy or ShaderEditor pasted in.

1. **The moving wallpaper is the user's.** Two kinds, Video (a file) and Shader (a fragment
   shader), replace the scene catalogue. The launcher ships no scenes of its own.
2. **The renderer is OpenGL ES 3 on the existing back surface.** Its gates, thread, frame
   policy, still-for-Home-and-Lock and the glass's low-res pass keep their contracts; only the
   drawing changes. Glass stays on AGSL inside the window: it samples what lies beneath it and
   gains nothing from GL.
3. **The shader contract is Shadertoy's**, with ShaderEditor's names as aliases and a few
   launcher uniforms. A model or a person writes the most documented shader dialect there is,
   and it compiles unchanged.
4. **It is still the home screen.** A shader is applied only after it compiles and draws
   offscreen; context loss and heavy frames step down and then revert to Photo; a pending marker
   reverts at start if the last apply never ran 3 s.
