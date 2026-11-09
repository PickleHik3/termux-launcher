// Studio kit lab worker: composes a wallpaper from a kit's parts with a seed, then renders kit.agsl.
// compose() is the part the app ports: seed + words -> pieces, placements, palette (uniforms).
importScripts('https://cdn.jsdelivr.net/npm/canvaskit-wasm@0.40.0/bin/canvaskit.js');
const V = self.location.search;
let ck, paint, effect, surface, sw = 0, sh = 0, words, kitDef, pieces, gl = null, slots = {}, uniformCount = 0;
const imgs = {};
const BASE = 'studio/kits/wilds/';

const ready = (async () => {
  ck = await CanvasKitInit({ locateFile: f => 'https://cdn.jsdelivr.net/npm/canvaskit-wasm@0.40.0/bin/' + f });
  paint = new ck.Paint();
  const [src, w, k, p] = await Promise.all([
    fetch('studio/kit.agsl' + V).then(r => r.text()),
    fetch('studio/scene-words.json' + V).then(r => r.json()),
    fetch(BASE + 'kit.json' + V).then(r => r.json()),
    fetch(BASE + 'pieces.json' + V).then(r => r.json()),
  ]);
  words = w; kitDef = k; pieces = p;
  let err = ''; effect = ck.RuntimeEffect.Make(src, e => err += e);
  if (!effect) throw Error(err || 'shader failed to compile');
  let n = 0;
  for (let i = 0; i < effect.getUniformCount(); i++) {
    const u = effect.getUniform(i); slots[effect.getUniformName(i)] = [u.slot, u.columns * u.rows]; n = Math.max(n, u.slot + u.columns * u.rows);
  }
  uniformCount = n;
  return { motions: words.motions, atmospheres: Object.fromEntries(Object.entries(kitDef.atmospheres).map(([a, b]) => [a, { label: b.label }])) };
})();

async function shaderOf(file) {
  if (!imgs[file]) {
    const img = ck.MakeImageFromEncoded(new Uint8Array(await (await fetch(BASE + file + V)).arrayBuffer()));
    imgs[file] = img.makeShaderOptions(ck.TileMode.Clamp, ck.TileMode.Clamp, ck.FilterMode.Linear, ck.MipmapMode.None);
  }
  return imgs[file];
}

// ---- composer ----
function rng(seed) { let s = (seed >>> 0) || 1; return () => { s ^= s << 13; s >>>= 0; s ^= s >>> 17; s ^= s << 5; s >>>= 0; return s / 4294967296; }; }
const hex = h => [1, 3, 5].map(i => parseInt(h.slice(i, i + 2), 16) / 255);
const mix = (a, b, t) => a.map((x, i) => x + (b[i] - x) * t);
const mul = (a, b) => a.map((x, i) => x * b[i]);
const luma = c => c[0] * 0.299 + c[1] * 0.587 + c[2] * 0.114;
const clamp1 = c => c.map(x => Math.min(1, Math.max(0, x)));

function tones(baseHex, A, depth, hiAmt = 0.55) {
  let b = hex(baseHex);
  b = mul(mix([luma(b), luma(b), luma(b)], b, A.sat), A.mul);
  const sky = hex(A.skyHor), hi = hex(A.hi);
  const lit = clamp1(mul(b, A.light.map(x => x * 1.3)));
  const t = [mul(b, A.shadow), b, lit, mix(lit, hi, hiAmt)];
  return t.map(c => clamp1(mix(c, sky, A.haze * depth)));
}

function compose(seed, atmKey, motionKey, width, height) {
  const r = rng(seed * 2654435761 + 12345), R = (a, b) => a + (b - a) * r(), pick = a => a[Math.floor(r() * a.length)];
  const H = 1536, vw = H * width / height, A = kitDef.atmospheres[atmKey], M = words.motions[motionKey];
  const landKey = pick(Object.keys(kitDef.lands)), land = kitDef.lands[landKey];
  const u = {}, names = [];
  const strip = (kind, skyY, sMin, sMax) => {
    const name = pick(Object.keys(kitDef.strips[kind])), pc = pieces[name];
    const s = Math.max(R(sMin, sMax), vw / 1024 + 0.02);
    const x = R(vw - 1024 * s, 0);
    u[`u${kind[0].toUpperCase() + kind.slice(1)}P`] = [x, skyY - pc.top * s, s, r() < 0.5 ? 1 : 0];
    names.push(kitDef.strips[kind][name]);
    return { file: name + '.webp', top: skyY, peak: skyY - (pc.top - pc.peak) * s };
  };
  // Lay out from the front: the foreground near the bottom, then the lake (or not), the mid band
  // just behind it, and the far range peeking over the mid band.
  const foreY = H * R(0.74, 0.86);
  const lake = r() < 0.6 ? foreY - H * R(0.10, 0.2) : 0;
  const midY = lake ? lake - R(70, 150) : foreY - H * R(0.09, 0.16);
  const mid = strip('mid', midY, 1.0, 1.5);
  const farY = Math.min(mid.peak - 30, midY - H * R(0.07, 0.16));
  const far = strip('far', farY, 0.95, 1.5);
  const fore = strip('fore', foreY, 1.0, 1.5);
  names.unshift(names.splice(1, 1)[0]);   // read far first
  if (lake) names.splice(2, 0, 'lake');
  u.uMidTop = [midY]; u.uForeTop = [foreY]; u.uLakeY = [lake];
  [u.uFarA, u.uFarB, u.uFarC, u.uFarD] = tones(land.far, A, 1.0, 0.85);
  [u.uMidA, u.uMidB, u.uMidC, u.uMidD] = tones(land.mid, A, 0.45);
  [u.uForeA, u.uForeB, u.uForeC, u.uForeD] = tones(land.fore, A, 0.0);
  [u.uTreeA, u.uTreeB, u.uTreeC, u.uTreeD] = tones(land.tree, A, 0.0);
  [u.uFaunaA, u.uFaunaB, u.uFaunaC, u.uFaunaD] = tones(land.fauna, A, 0.15);
  // sky
  u.uSkyTop = hex(A.skyTop); u.uSkyHor = hex(A.skyHor); u.uHorY = [far.peak + (farY - far.peak) * 0.5 + 60]; u.uStars = [A.stars];
  const top = Math.max(far.peak - 60, H * 0.15);
  if (A.cel === 'none') u.uCel = [0, 0, 0, 0];
  else if (A.cel === 'moon') u.uCel = [R(0.15, 0.85) * vw, R(H * 0.08, top * 0.75), R(30, 44), 2];
  else if (A.cel === 'low-sun') u.uCel = [R(0.15, 0.85) * vw, R(top - 140, top + 30), R(48, 72), 1];
  else u.uCel = [R(0.15, 0.85) * vw, R(H * 0.07, top * 0.6), R(40, 56), 1];
  u.uCelC = hex(A.celC); u.uCloudLo = hex(A.cloudLo); u.uCloudHi = hex(A.cloudHi);
  const nc = Math.floor(R(A.clouds[0], A.clouds[1] + 0.999));
  for (let i = 0; i < 5; i++) u['uC' + i] = i < nc ? [R(0, vw + 400), R(H * 0.06, Math.max(H * 0.1, far.peak - 30)), R(0.45, 1.15), Math.floor(r() * 6)] : [0, 0, 0, 0];
  // framing tree
  if (r() < 0.45) {
    // a framing tree stands at the edge, mostly off screen, so it frames rather than covers
    const cell = Math.floor(r() * 2), s = H * R(0.55, 0.78) / 1024, left = r() < 0.5;
    const x = left ? -R(0.55, 0.72) * 512 * s : vw - 512 * s * (1 - R(0.55, 0.72));
    u.uTreeP = [x, H - 1024 * s, s, cell + 2 * (left ? 0 : 1)];
    names.push(kitDef.trees[cell] + ' tree');
  } else u.uTreeP = [0, 0, 0, 0];
  // animals
  const options = kitDef.fauna.filter(f => f.where !== 'lake' && f.where !== 'shore' || lake);
  const nf = Math.floor(R(0, 2.6));
  const used = new Set();
  for (let i = 0; i < 3; i++) {
    let F = [0, 0, 0, 0], Mo = [0, 0, 0, 0];
    if (i < nf) {
      const f = pick(options.filter(o => !used.has(o.cell)));
      if (f) {
        used.add(f.cell); names.push(f.name);
        let x = R(0.15, 0.85) * vw, y, s = f.scale;
        if (f.where === 'sky') y = R(H * 0.12, Math.max(H * 0.14, far.peak - 80));
        else if (f.where === 'lake') { y = R(lake + 25, Math.max(lake + 30, foreY - 10)); s *= 0.7 + 0.6 * (y - lake) / Math.max(1, foreY - lake); }
        else if (f.where === 'shore') y = lake + R(4, 30);
        else if (lake || r() < 0.5) { y = foreY + R(8, 40); }          // in the foreground grass
        else { y = R(midY + 50, foreY - 10); s *= 0.65; }               // out on the mid ground
        F = [x, y, s, f.cell];
        Mo = [f.mode, (f.speed || 0) * (r() < 0.5 ? -1 : 1), f.mode === 2 ? R(90, 160) : f.mode === 3 ? R(30, 80) : 0, R(0, 6.28)];
      }
    }
    u['uF' + i] = F; u['uF' + i + 'm'] = Mo;
  }
  u.uWaterC = hex(A.water); u.uGlintC = hex(A.glint);
  u.uMist = [A.mist]; u.uMistC = hex(A.mistC);
  u.uSway = [M.sway]; u.uWater = [M.water]; u.uDrift = [M.cloud]; u.uLife = [M.life];
  const files = [far.file, mid.file, fore.file, 'trees.webp', 'clouds.webp', 'fauna.webp'];
  return { u, files, label: `${landKey} · ${names.join(' · ')}` };
}

function pack(u, width, height, time) {
  u.uOut = [width, height]; u.uTime = [time];
  const arr = new Float32Array(uniformCount);
  for (const [k, [slot, n]] of Object.entries(slots)) {
    const v = u[k]; if (!v) throw Error('missing uniform ' + k);
    if (v.length !== n) throw Error(`uniform ${k} wants ${n} floats, got ${v.length}`);
    arr.set(v, slot);
  }
  return arr;
}

const cache = new Map();   // seed|atm|motion|w|h -> composition
self.onmessage = async ({ data }) => {
  try {
    const info = await ready;
    if (data.hello) {
      if (data.glCanvas && !data.forceSoftware) gl = ck.MakeWebGLCanvasSurface(data.glCanvas);
      self.postMessage({ hello: true, gl: !!gl, ...info }); return;
    }
    const { id, seed, atm, motion, time, width, height } = data;
    const key = [seed, atm, motion, width, height].join('|');
    if (!cache.has(key)) { if (cache.size > 64) cache.clear(); cache.set(key, compose(seed, atm, motion, width, height)); }
    const c = cache.get(key);
    const children = await Promise.all(c.files.map(shaderOf));
    const shader = effect.makeShaderWithChildren(pack(c.u, width, height, time), children);
    paint.setShader(shader);
    const t0 = performance.now();
    if (data.live && gl) {
      gl.getCanvas().drawRect(ck.LTRBRect(0, 0, width, height), paint); gl.flush();
      paint.setShader(null); shader.delete(); self.postMessage({ id, gl: true, label: c.label, ms: performance.now() - t0 }); return;
    }
    if (!surface || sw !== width || sh !== height) { surface && surface.delete(); surface = ck.MakeSurface(width, height); sw = width; sh = height; }
    surface.getCanvas().drawRect(ck.LTRBRect(0, 0, width, height), paint); surface.flush();
    const img = surface.makeImageSnapshot();
    const px = img.readPixels(0, 0, { width, height, colorType: ck.ColorType.RGBA_8888, alphaType: ck.AlphaType.Unpremul, colorSpace: ck.ColorSpace.SRGB });
    const copy = new Uint8ClampedArray(px); img.delete(); paint.setShader(null); shader.delete();
    self.postMessage({ id, width, height, pixels: copy, label: c.label, ms: performance.now() - t0 }, [copy.buffer]);
  } catch (e) { self.postMessage({ id: data.id, error: String(e.message || e) }); }
};
