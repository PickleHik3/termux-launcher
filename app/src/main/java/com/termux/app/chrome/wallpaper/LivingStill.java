package com.termux.app.chrome.wallpaper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.chrome.wallpaper.living.LivingRecipe;
import com.termux.app.chrome.wallpaper.living.Manifest;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A living still (project-docs/active/animated-wallpaper/living-stills.md, Part D): the user's own
 * photo with its depth map, three region masks and effect recipe, played as a generated
 * background. The id is {@code living:<hash>}; {@link AnimatedWallpapers#byId(android.content.Context,
 * String)} builds one from the manifest on disk and keeps it out of {@link AnimatedWallpapers#all()}.
 *
 * <p>The program is the same for every still: {@link MomentAgsl#HEAD}, five child shaders (the
 * photo, depth and the three masks, bound by {@link WallpaperUniforms}) and recipe uniforms, whose
 * values {@link #recipeUniforms} derives from {@link LivingRecipe}, so one compiled program serves
 * every photo. Its {@code scene} is the browser prototype's shader ({@code wall-alive/page.html})
 * ported to AGSL: depth drift, bob, sway, three water modes, a looped sky flow, falling water, glow,
 * mist, particles and focus blur.</p>
 *
 * <p>Rest pose: at {@code uEnergy == 0} (and so {@code uPhase == 0}) {@code scene} returns the
 * photo before any effect runs, so the still the system holds and the rest frame are the same
 * picture. Everything that moves is scaled by {@code uEnergy}; {@code uTime} is read once, below
 * that early return. The photo is laid over the frame like a centre-cropped cover.</p>
 *
 * <p>Cost: the five children are sampled by branches on the mask values, so a pixel pays the two
 * depth lookups, the masks it needs and one to three photo taps; about 7 on the common path, 11
 * with a sky or falling water under focus blur. The loop of 120 s is nominal: the noise and drift
 * terms are not periodic, which only matters if the clock wraps (after a day of playing).</p>
 */
public final class LivingStill implements AnimatedWallpaper {

    public static final String ID_PREFIX = "living:";
    static final float PERIOD = 120f;

    /** The shader's {@code uWaterMode} values. */
    static final float WATER_OFF = -1f, WATER_NOISE = 0f, WATER_LAKE = 1f, WATER_POOL = 2f;
    /** The shader's {@code uGlowMode} values. */
    static final float GLOW_OFF = 0f, GLOW_BREATHE = 1f, GLOW_FLICKER = 2f, GLOW_TRAILS = 3f;
    /** The prototype's trail direction, used when the recipe names none: right and slightly up. */
    static final float TRAIL_DIR_X = 0.45f, TRAIL_DIR_Y = -0.89f;

    static final String SCENE =
        "uniform shader uImage;\n" +
        "uniform shader uDepth;\n" +
        "uniform shader uMaskA;\n" +
        "uniform shader uMaskB;\n" +
        "uniform shader uMaskC;\n" +
        "uniform float2 uImageSize;\n" +
        "uniform float2 uDepthSize;\n" +
        "uniform float2 uMapSize;\n" +
        "uniform float2 uCover;\n" +
        "uniform float uFocus;\n" +
        "uniform float uIntensity;\n" +
        "uniform float uDrift;\n" +
        "uniform float2 uSway;\n" +
        "uniform float uWaterMode;\n" +
        "uniform float4 uWater;\n" +
        "uniform float uSkyFlow;\n" +
        "uniform float uSkyStars;\n" +
        "uniform float uPour;\n" +
        "uniform float uGlowMode;\n" +
        "uniform float uGlowGain;\n" +
        "uniform float2 uGlowDir;\n" +
        "uniform float4 uMist;\n" +
        "uniform float uParticles;\n" +
        "uniform float3 uNeed;\n" +
        // Frame pixels to image uv: the photo covers the frame, centred.
        "float2 coverUv(float2 p) {\n" +
        "    return (p - 0.5 * uResolution) / uCover + 0.5;\n" +
        "}\n" +
        "float3 imgAt(float2 uv) { return float3(uImage.eval(uv * uImageSize).rgb); }\n" +
        "float3 maskA(float2 uv) { return float3(uMaskA.eval(uv * uMapSize).rgb); }\n" +
        "float3 maskB(float2 uv) { return float3(uMaskB.eval(uv * uMapSize).rgb); }\n" +
        "float3 maskC(float2 uv) { return float3(uMaskC.eval(uv * uMapSize).rgb); }\n" +
        "float depthAt(float2 uv) { return float(uDepth.eval(uv * uDepthSize).r); }\n" +
        "float luma3(float3 c) { return dot(c, float3(0.299, 0.587, 0.114)); }\n" +
        "float fbm4(float2 p) {\n" +
        "    float s = 0.0;\n" +
        "    float a = 0.5;\n" +
        "    for (int i = 0; i < 4; i++) {\n" +
        "        s += a * vnoise(p);\n" +
        "        p = p * 2.03 + 17.1;\n" +
        "        a *= 0.5;\n" +
        "    }\n" +
        "    return s;\n" +
        "}\n" +
        // Focus blur: a 7 tap disc, radius rad in image widths (no textureLod in AGSL).
        "float3 sampleImg(float2 uv, float rad) {\n" +
        "    float3 c = imgAt(uv);\n" +
        "    if (rad <= 0.0) return c;\n" +
        "    float ry = rad * uCover.x / uCover.y;\n" +
        "    c += imgAt(uv + float2(rad, 0.0));\n" +
        "    c += imgAt(uv + float2(0.5 * rad, 0.866 * ry));\n" +
        "    c += imgAt(uv + float2(-0.5 * rad, 0.866 * ry));\n" +
        "    c += imgAt(uv + float2(-rad, 0.0));\n" +
        "    c += imgAt(uv + float2(-0.5 * rad, -0.866 * ry));\n" +
        "    c += imgAt(uv + float2(0.5 * rad, -0.866 * ry));\n" +
        "    return c / 7.0;\n" +
        "}\n" +
        // The cheaper 3 tap version for the looped regions, which take two phases each.
        "float3 sampleLite(float2 uv, float rad) {\n" +
        "    float3 c = imgAt(uv);\n" +
        "    if (rad <= 0.0) return c;\n" +
        "    float ry = rad * uCover.x / uCover.y;\n" +
        "    c += imgAt(uv + float2(0.9 * rad, 0.5 * ry));\n" +
        "    c += imgAt(uv - float2(0.9 * rad, 0.5 * ry));\n" +
        "    return c / 3.0;\n" +
        "}\n" +
        "float caustic(float2 qv, float t) {\n" +
        "    float2 iv = qv;\n" +
        "    float c = 1.0;\n" +
        "    float inten = 0.005;\n" +
        "    for (int n = 0; n < 4; n++) {\n" +
        "        float tt = t * (1.0 - 3.5 / float(n + 1));\n" +
        "        iv = qv + float2(cos(tt - iv.x) + sin(tt + iv.y), sin(tt - iv.y) + cos(tt + iv.x));\n" +
        "        c += 1.0 / length(float2(qv.x / (sin(iv.x + tt) / inten), qv.y / (cos(iv.y + tt) / inten)));\n" +
        "    }\n" +
        "    c /= 4.0;\n" +
        "    c = 1.17 - pow(max(c, 0.0), 1.4);\n" +
        "    return pow(abs(c), 8.0);\n" +
        "}\n" +
        // Sky: two phases of one flow, crossfaded, so clouds drift without stretching away.
        "float3 skyLoop(float2 s, float t, float I, float rad) {\n" +
        "    float f0 = fract(t / 26.0);\n" +
        "    float f1 = fract(t / 26.0 + 0.5);\n" +
        "    float2 fl = (float2(uSkyFlow, 0.0) + (float2(vnoise(s * 3.0 + t * 0.02), vnoise(s * 3.0 + 9.0)) - 0.5) * float2(0.012, 0.004)) * I;\n" +
        "    float3 a = sampleLite(s - fl * (f0 - 0.5), rad);\n" +
        "    float3 b = sampleLite(s - fl * (f1 - 0.5), rad);\n" +
        "    return mix(a, b, abs(2.0 * f0 - 1.0));\n" +
        "}\n" +
        "const float2 WAVE1 = float2(0.8, 0.6);\n" +
        "const float2 WAVE2 = float2(-0.507, 0.862);\n" +
        "const float2 WAVE3 = float2(0.954, -0.301);\n" +
        "const float2 WAVE4 = float2(-0.2, -0.98);\n" +
        "float3 scene(float2 p) {\n" +
        "    float2 uv0 = coverUv(p);\n" +
        "    float e = clamp(uEnergy, 0.0, 1.0);\n" +
        // Rest pose: the photo itself, before any effect or any use of time.
        "    if (e <= 0.0005) return imgAt(uv0);\n" +
        "    float t = uTime;\n" +
        "    float I = e * uIntensity;\n" +
        "    float asp = uCover.y / uCover.x;\n" +
        "    float2 uv = 0.5 + (uv0 - 0.5) * (1.0 - 0.03 * e);\n" +
        "    float d0 = depthAt(uv);\n" +
        "    float focus = clamp(uFocus, 0.0, 1.0) * e;\n" +
        "    float rad = focus * mix(0.014, 0.006, smoothstep(0.35, 0.9, d0));\n" +
        // Depth drift: a slow camera float; near pixels move against far ones (two-step lookup).
        "    float2 drift = float2(sin(t * 0.23), sin(t * 0.17 + 1.3) * 0.7) * 0.6 * I * 0.013 * uDrift;\n" +
        "    drift.y /= asp;\n" +
        "    float2 s = uv + drift * (d0 - 0.45);\n" +
        "    s = uv + drift * (depthAt(s) - 0.45);\n" +
        "    float3 A = float3(0.0);\n" +
        "    float3 B = float3(0.0);\n" +
        "    float3 C = float3(0.0);\n" +
        "    if (uNeed.x > 0.5) A = maskA(s);\n" +
        "    if (uNeed.y > 0.5) B = maskB(s);\n" +
        "    if (uNeed.z > 0.5) C = maskC(s);\n" +
        // Bob: floating things rock gently.
        "    if (C.r > 0.01) {\n" +
        "        s += C.r * float2(sin(t * 0.9) * 0.6, sin(t * 1.3 + 0.7)) * float2(0.0045, 0.0045 / asp) * I;\n" +
        "    }\n" +
        // Sway: a travelling phase so neighbours never move in lockstep.
        "    if (uSway.y > 0.0 && A.g > 0.01) {\n" +
        "        float phS = t * uSway.x + s.y * 7.0 + vnoise(s * float2(5.0, 3.0)) * 6.0;\n" +
        "        s.x += A.g * (sin(phS) * 0.7 + sin(phS * 1.9 + 1.0) * 0.3) * uSway.y * I;\n" +
        "        s.y += A.g * sin(phS * 1.3) * uSway.y * 0.25 / asp * I;\n" +
        "    }\n" +
        // Water. 0: refract through drifting noise (streams, reflections with anisotropic params).
        // 1: cartoon lake, wave bands that shrink toward the horizon. 2: clear pool, one wave surface.
        "    float2 q0 = s * float2(1.0, asp);\n" +
        "    float persp = 1.0;\n" +
        "    float waveH = 0.0;\n" +
        "    if (uWaterMode > 0.5 && uWaterMode < 1.5) {\n" +
        "        persp = mix(0.3, 1.0, smoothstep(0.58, 0.95, s.y));\n" +
        "        float k = 70.0 / persp;\n" +
        "        float phW = q0.y * k - t * 1.15 + sin(q0.x * 4.0 / persp + t * 0.35) * 1.4;\n" +
        "        float2 off = float2(sin(phW) * 0.8 + sin(phW * 0.53 + 2.0) * 0.2, sin(q0.x * 9.0 / persp - t * 0.7 + q0.y * 30.0) * 0.25);\n" +
        "        s += A.r * off * float2(uWater.z, uWater.w) * persp * I;\n" +
        "    } else if (uWaterMode > 1.5) {\n" +
        "        float2 qq = q0 + (float2(fbm4(q0 * 2.2 + t * 0.04), fbm4(q0 * 2.2 + 7.3 - t * 0.03)) - 0.5) * 0.35;\n" +
        "        float p1 = dot(qq, WAVE1) * 17.0 - t * 0.85;\n" +
        "        float p2 = dot(qq, WAVE2) * 26.0 - t * 1.15;\n" +
        "        float p3 = dot(qq, WAVE3) * 39.0 - t * 1.5;\n" +
        "        float p4 = dot(qq, WAVE4) * 11.0 - t * 0.55;\n" +
        "        float2 waveG = WAVE1 * cos(p1) + WAVE2 * cos(p2) * 0.8 + WAVE3 * cos(p3) * 0.55 + WAVE4 * cos(p4) * 0.9;\n" +
        "        waveH = (sin(p1) + sin(p2) * 0.8 + sin(p3) * 0.55 + sin(p4) * 0.9) / 3.25;\n" +
        "        s += A.r * waveG * float2(uWater.z, uWater.w / asp) * I;\n" +
        "    } else if (uWaterMode > -0.5) {\n" +
        "        float2 wq = s * uWater.xy;\n" +
        "        float2 wn = float2(fbm4(wq + float2(t * 0.12, t * 0.05)), fbm4(wq * 1.1 + float2(-t * 0.09, t * 0.11) + 5.2)) - 0.5;\n" +
        "        s += A.r * wn * float2(uWater.z, uWater.w) * I * 2.0;\n" +
        "    }\n" +
        "    float3 col;\n" +
        "    bool sky = uSkyFlow > 0.0 && A.b > 0.01;\n" +
        "    if (sky && A.b > 0.98) {\n" +
        "        col = skyLoop(s, t, I, rad);\n" +
        "    } else {\n" +
        "        col = sampleImg(s, rad);\n" +
        "        if (sky) col = mix(col, skyLoop(s, t, I, rad), A.b);\n" +
        "    }\n" +
        // Stars twinkle instead of clouds drifting.
        "    if (uSkyStars > 0.5 && A.b > 0.01) {\n" +
        "        float2 cell = floor(s * float2(140.0, 140.0 * asp));\n" +
        "        float hc = hash21(cell);\n" +
        "        float tw = 0.5 + 0.5 * sin(t * (0.8 + hc * 2.0) + hc * 40.0);\n" +
        "        col *= 1.0 + A.b * step(0.65, luma3(col)) * tw * 0.4 * min(I, 1.0);\n" +
        "    }\n" +
        // Falling water: a fast looped flow downward plus bright streaks.
        "    if (uPour > 0.0 && B.r > 0.01) {\n" +
        "        float f0 = fract(t / 1.4);\n" +
        "        float f1 = fract(t / 1.4 + 0.5);\n" +
        "        float2 fl = float2(0.0, 0.03) * I * uPour;\n" +
        "        float3 cf = mix(sampleLite(s - fl * (f0 - 0.5), rad), sampleLite(s - fl * (f1 - 0.5), rad), abs(2.0 * f0 - 1.0));\n" +
        "        float st = vnoise(float2(s.x * 260.0, s.y * 16.0 - t * 7.0));\n" +
        "        cf += pow(st, 5.0) * 0.45 * float3(0.92, 0.98, 1.0) * min(I, 1.0) * uPour;\n" +
        "        col = mix(col, cf, B.r);\n" +
        "    }\n" +
        "    float2 q = s * float2(1.0, asp);\n" +
        "    float wI = min(I, 1.5);\n" +
        "    if (uWaterMode > 0.5 && uWaterMode < 1.5 && A.r > 0.01) {\n" +
        // Light dashes that open and fade like drawn ripple strokes, sliding slowly sideways.
        "        float2 cs = float2(0.26, 0.034) * persp;\n" +
        "        float2 g = (q + float2(t * 0.012, 0.0)) / cs;\n" +
        "        float2 id = floor(g);\n" +
        "        float hc = hash21(id);\n" +
        "        float2 f = fract(g) - 0.5;\n" +
        "        float life = pow(max(0.0, sin(t * 0.5 + hc * 31.0)), 2.0);\n" +
        "        float len = 0.12 + 0.26 * life;\n" +
        "        float dash = smoothstep(len, len - 0.08, abs(f.x)) * smoothstep(0.16, 0.04, abs(f.y + f.x * f.x * 0.6));\n" +
        "        col = mix(col, float3(0.86, 0.97, 1.0), dash * life * step(0.72, hc) * 0.75 * A.r * wI);\n" +
        // Shoreline lapping: a pale band just inside the water that creeps up and back.
        "        float lap = 0.012 + 0.006 * sin(t * 1.1 + q.x * 5.0);\n" +
        "        float shore = A.r * (1.0 - maskA(s - float2(0.0, lap * persp)).r);\n" +
        "        col = mix(col, float3(0.8, 0.95, 0.98), smoothstep(0.15, 0.7, shore) * 0.45 * wI);\n" +
        "    } else if (uWaterMode > 1.5 && A.r > 0.01) {\n" +
        // Highlights follow the passing crests; a faint caustic net drifts on top; glints where crests line up.
        "        float hl = smoothstep(0.45, 0.85, luma3(col));\n" +
        "        col *= 1.0 + A.r * (waveH * 0.5 + 0.15) * (0.25 + hl) * 0.45 * wI;\n" +
        "        float cc = caustic(q * 3.2 * TAU + 10.0, t * 0.22 + 23.0);\n" +
        "        col += A.r * smoothstep(0.2, 1.2, cc) * 0.18 * float3(0.75, 1.0, 1.0) * wI;\n" +
        "        float gl = smoothstep(0.93, 0.99, waveH * 0.5 + 0.5) * step(0.8, hash21(floor(q * 120.0)));\n" +
        "        col += A.r * gl * 0.5 * wI;\n" +
        "    }\n" +
        // Mist: drifting haze, thicker where the mask says and further from the camera.
        "    if (uMist.a > 0.0 && C.g > 0.01) {\n" +
        "        float f = smoothstep(0.35, 0.85, fbm4(q * float2(2.2, 3.0) + float2(t * 0.03, -t * 0.008)));\n" +
        "        float gate = smoothstep(0.0, 0.15, e);\n" +
        "        col = mix(col, uMist.rgb, C.g * f * uMist.a * min(I + 0.2, 1.2) * gate * (1.0 - 0.6 * d0));\n" +
        "    }\n" +
        // Glow: 1 breathe, 2 flicker, 3 pulses running along the trails.
        "    if (uGlowMode > 0.5 && B.b > 0.01) {\n" +
        "        float gI = min(I, 1.5);\n" +
        "        float3 bloom = 0.5 * (imgAt(s + float2(0.010, 0.006)) + imgAt(s - float2(0.010, 0.006)));\n" +
        "        float2 cell = floor(s * float2(3.0, 9.0));\n" +
        "        float hc = hash21(cell);\n" +
        "        float g = 1.0;\n" +
        "        if (uGlowMode < 1.5) {\n" +
        "            g = 0.75 + 0.25 * sin(t * (0.6 + hc) + hc * 6.0);\n" +
        "            col *= mix(1.0, g * 1.1, B.b * gI);\n" +
        "        } else if (uGlowMode < 2.5) {\n" +
        "            g = 0.7 + 0.3 * sin(t * (1.0 + hc * 2.0) + hc * 6.0);\n" +
        "            g *= mix(1.0, 0.25, step(0.975, hash21(cell + floor(t * 11.0))));\n" +
        "            col *= mix(1.0, g * 1.15, B.b * gI);\n" +
        "        } else {\n" +
        "            float r = dot(q, uGlowDir);\n" +
        "            g = pow(0.5 + 0.5 * sin(r * 26.0 - t * 5.0), 10.0) + 0.35 * (0.5 + 0.5 * sin(t * 1.4));\n" +
        "            col += B.b * col * g * 0.9 * gI;\n" +
        "        }\n" +
        "        col += B.b * bloom * uGlowGain * (0.55 + 0.45 * g) * 0.6 * gI;\n" +
        "    }\n" +
        // Particles: 1 glints on water, 2 fireflies, 3 rain, 4 snow, 5 dust, 6 debris along the ground.
        "    if (uParticles > 0.5 && C.b > 0.01) {\n" +
        "        float pa = 0.0;\n" +
        "        float3 pc = float3(1.0);\n" +
        "        float pI = min(I, 1.0);\n" +
        "        if (uParticles < 1.5) {\n" +
        "            float2 g = q * 34.0;\n" +
        "            float2 id = floor(g);\n" +
        "            float hc = hash21(id);\n" +
        "            float2 c = float2(hash21(id + 3.1), hash21(id + 7.7)) * 0.8 + 0.1;\n" +
        "            float tw = pow(max(0.0, sin(t * 1.6 + hc * 40.0)), 24.0);\n" +
        "            pa = smoothstep(0.09, 0.0, length(fract(g) - c)) * tw * step(0.55, hc) * A.r;\n" +
        "            pc = float3(1.0, 1.0, 0.95) * 1.3;\n" +
        "        } else if (uParticles < 2.5) {\n" +
        "            float2 g = q * 7.0;\n" +
        "            float2 id = floor(g);\n" +
        "            float hc = hash21(id);\n" +
        "            float2 c = 0.5 + 0.32 * float2(sin(t * 0.35 + hc * 6.3), cos(t * 0.27 + hc * 9.1));\n" +
        "            float b = pow(0.5 + 0.5 * sin(t * 1.3 + hc * 20.0), 3.0);\n" +
        "            pa = smoothstep(0.07, 0.0, length(fract(g) - c)) * b * step(0.5, hc);\n" +
        "            pa += smoothstep(0.22, 0.0, length(fract(g) - c)) * b * step(0.5, hc) * 0.25;\n" +
        "            pc = float3(0.85, 1.0, 0.45) * 1.4;\n" +
        "        } else if (uParticles < 3.5) {\n" +
        "            float lane = floor(q.x * 150.0 + q.y * 14.0);\n" +
        "            float hl = hash21(float2(lane, 3.0));\n" +
        "            float seg = fract(q.y * 2.2 - t * (1.8 + hl) + hl * 17.0);\n" +
        "            float lx = abs(fract(q.x * 150.0 + q.y * 14.0) - 0.5);\n" +
        "            pa = step(0.86, hl) * smoothstep(0.0, 0.06, seg) * smoothstep(0.3, 0.06, seg) * smoothstep(0.5, 0.1, lx) * 0.12;\n" +
        "            pc = float3(0.8, 0.86, 1.0);\n" +
        "        } else if (uParticles < 4.5) {\n" +
        "            float2 g = (q - float2(sin(t * 0.3) * 0.02, t * 0.05)) * 18.0;\n" +
        "            float2 id = floor(g);\n" +
        "            float hc = hash21(id);\n" +
        "            float2 c = float2(hash21(id + 2.0), hash21(id + 5.0)) * 0.6 + 0.2;\n" +
        "            pa = smoothstep(0.12, 0.0, length(fract(g) - c)) * step(0.55, hc);\n" +
        "            pc = float3(1.1, 1.1, 1.1);\n" +
        "        } else if (uParticles < 5.5) {\n" +
        "            float2 g = (q + float2(t * 0.006, -t * 0.004)) * 30.0;\n" +
        "            float2 id = floor(g);\n" +
        "            float hc = hash21(id);\n" +
        "            float2 c = float2(hash21(id + 2.0), hash21(id + 5.0)) * 0.7 + 0.15;\n" +
        "            pa = smoothstep(0.08, 0.0, length(fract(g) - c)) * step(0.6, hc) * (0.5 + 0.5 * sin(t * 0.8 + hc * 40.0)) * 0.6;\n" +
        "            pc = float3(1.0, 0.97, 0.9);\n" +
        "        } else {\n" +
        "            float2 dir = normalize(float2(1.0, 0.3));\n" +
        "            float2 g = (q - dir * t * 0.09) * 26.0;\n" +
        "            float2 id = floor(g);\n" +
        "            float hc = hash21(id);\n" +
        "            float2 c = float2(hash21(id + 2.0), hash21(id + 5.0)) * 0.7 + 0.15;\n" +
        "            float da = smoothstep(0.08, 0.0, length((fract(g) - c) * float2(1.0, 2.2))) * step(0.86, hc);\n" +
        "            float3 dc = luma3(col) < 0.35 ? float3(0.85, 0.85, 0.85) : float3(0.04, 0.04, 0.04);\n" +
        "            col = mix(col, dc, da * C.b * pI);\n" +
        "        }\n" +
        "        col += pa * pc * C.b * pI;\n" +
        "    }\n" +
        "    col *= 1.0 - 0.16 * focus;\n" +
        "    return col;\n" +
        "}\n";

    /** The living still's own tail: no palette lift and no 0.6 cap, so the photo keeps its full range. */
    static final String TAIL =
        "half4 main(float2 p0) {\n" +
        "    float2 p = momWarp(p0);\n" +
        "    float3 c = scene(p);\n" +
        "    c += momLift(p0) * c * 0.6;\n" +
        "    c *= 1.0 - clamp(uDim, 0.0, 1.0);\n" +
        "    return half4(half3(clamp(c, float3(0.0, 0.0, 0.0), float3(1.0, 1.0, 1.0))), 1.0);\n" +
        "}\n";

    private static final String AGSL = MomentAgsl.HEAD + "const float PERIOD = " + MomentAgsl.lit(PERIOD) + ";\n"
        + SCENE + TAIL;

    private static final int[] OWN = {0xFF2A2A2A, 0xFFB0B0B0, 0xFF808080, 0xFF101010};

    @NonNull private final Manifest mManifest;
    private final long mStamp;

    public LivingStill(@NonNull Manifest manifest) {
        mManifest = manifest;
        mStamp = manifest.recipeFile().lastModified();
    }

    @NonNull public Manifest manifest() { return mManifest; }
    /** When the recipe file was written; a still re-read has a new stamp, so a cached player rebuilds. */
    long stamp() { return mStamp; }

    @Override public String id() { return mManifest.wallpaperId(); }
    @Override public String label() { return "Living still"; }
    @Override public float periodSeconds() { return PERIOD; }
    @Override public int[] ownPalette() { return OWN.clone(); }
    @Override public String agsl() { return AGSL; }

    /** The hash an id like {@code living:0123456789abcdef} names, or null. */
    @Nullable
    static String hashOf(@Nullable String id) {
        return AnimatedWallpapers.isLivingId(id) ? id.substring(ID_PREFIX.length()) : null;
    }

    /**
     * The size, in frame pixels, the photo takes when it covers a {@code frameW} x {@code frameH}
     * frame (the shader's {@code uCover}).
     */
    @NonNull
    static float[] coverSize(float frameW, float frameH, float imageW, float imageH) {
        float scale = Math.max(frameW / Math.max(1f, imageW), frameH / Math.max(1f, imageH));
        return new float[] {imageW * scale, imageH * scale};
    }

    /**
     * The recipe as the shader's uniforms, by name. Each value is the uniform's floats. The source
     * of each: {@code uDrift} drift; {@code uSway} swaySpeed, swayAmp; {@code uWaterMode} and
     * {@code uWater} waterMode and waterParams (freqX, freqY, ampX, ampY); {@code uSkyFlow} skyFlow;
     * {@code uSkyStars} skyStars; {@code uPour} pour; {@code uGlowMode}, {@code uGlowGain} and
     * {@code uGlowDir} glowMode, glowGain and glowTrailAngleDeg (screen degrees, 0 = right, 90 =
     * down; 0 keeps the prototype's direction); {@code uMist} mistColour and mistAmount;
     * {@code uParticles} particles; {@code uIntensity} intensity; {@code uNeed} which of the three
     * masks the recipe reads, so the shader skips the others.
     */
    @NonNull
    Map<String, float[]> recipeUniforms() {
        LivingRecipe r = mManifest.recipe();
        Map<String, float[]> u = new LinkedHashMap<>();
        u.put("uIntensity", new float[] {r.intensity});
        u.put("uDrift", new float[] {r.drift});
        u.put("uSway", new float[] {r.swaySpeed, r.swayAmp});
        float water = waterMode(r.waterMode);
        u.put("uWaterMode", new float[] {water});
        u.put("uWater", new float[] {param(r, "freqX"), param(r, "freqY"), param(r, "ampX"), param(r, "ampY")});
        u.put("uSkyFlow", new float[] {r.skyFlow});
        u.put("uSkyStars", new float[] {r.skyStars ? 1f : 0f});
        u.put("uPour", new float[] {r.pour});
        u.put("uGlowMode", new float[] {glowMode(r.glowMode)});
        u.put("uGlowGain", new float[] {r.glowGain});
        float dx = TRAIL_DIR_X, dy = TRAIL_DIR_Y;
        if (r.glowTrailAngleDeg != 0f) {
            double a = Math.toRadians(r.glowTrailAngleDeg);
            dx = (float) Math.cos(a);
            dy = (float) Math.sin(a);
        } else {
            float n = (float) Math.sqrt(dx * dx + dy * dy);
            dx /= n;
            dy /= n;
        }
        u.put("uGlowDir", new float[] {dx, dy});
        u.put("uMist", new float[] {r.mistColour[0], r.mistColour[1], r.mistColour[2], r.mistAmount});
        u.put("uParticles", new float[] {particleKind(r.particles)});
        boolean needA = r.swayAmp > 0f || water > WATER_OFF || r.skyFlow > 0f || r.skyStars
            || LivingRecipe.PARTICLES_GLINTS.equals(r.particles);
        boolean needB = r.pour > 0f || glowMode(r.glowMode) > GLOW_OFF;
        boolean needC = water > WATER_OFF || r.mistAmount > 0f || particleKind(r.particles) > 0f;
        u.put("uNeed", new float[] {needA ? 1f : 0f, needB ? 1f : 0f, needC ? 1f : 0f});
        return u;
    }

    private static float param(LivingRecipe r, String key) {
        Float v = r.waterParams.get(key);
        return v == null ? 0f : v;
    }

    static float waterMode(String mode) {
        switch (mode) {
            case LivingRecipe.WATER_LAKE: return WATER_LAKE;
            case LivingRecipe.WATER_POOL: return WATER_POOL;
            case LivingRecipe.WATER_NOISE:
            case LivingRecipe.WATER_REFLECTION: return WATER_NOISE;
            default: return WATER_OFF;
        }
    }

    static float glowMode(String mode) {
        switch (mode) {
            case LivingRecipe.GLOW_BREATHE: return GLOW_BREATHE;
            case LivingRecipe.GLOW_FLICKER: return GLOW_FLICKER;
            case LivingRecipe.GLOW_TRAILS: return GLOW_TRAILS;
            default: return GLOW_OFF;
        }
    }

    static float particleKind(String kind) {
        switch (kind) {
            case LivingRecipe.PARTICLES_GLINTS: return 1f;
            case LivingRecipe.PARTICLES_FIREFLIES: return 2f;
            case LivingRecipe.PARTICLES_RAIN: return 3f;
            case LivingRecipe.PARTICLES_SNOW: return 4f;
            case LivingRecipe.PARTICLES_DUST: return 5f;
            case LivingRecipe.PARTICLES_DEBRIS: return 6f;
            default: return 0f;
        }
    }
}
