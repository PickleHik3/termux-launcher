// Software renderer for the Studio scene lab. Renders are requested one at a time, never self-scheduled.
importScripts('https://cdn.jsdelivr.net/npm/canvaskit-wasm@0.40.0/bin/canvaskit.js');
const V = self.location.search;            // cache stamp passed through from the page
let kit, paint, effect, surface, sw = 0, sh = 0, scene, words, artShader, regShader, gl = null;
const ready = (async () => {
  kit = await CanvasKitInit({ locateFile: f => 'https://cdn.jsdelivr.net/npm/canvaskit-wasm@0.40.0/bin/' + f });
  paint = new kit.Paint();
  const [src, w, sc] = await Promise.all([
    fetch('studio/scene.agsl' + V).then(r => r.text()),
    fetch('studio/scene-words.json' + V).then(r => r.json()),
    fetch('studio/scenes/coastal-meadow/scene.json' + V).then(r => r.json()),
  ]);
  words = w; scene = sc;
  let err = ''; effect = kit.RuntimeEffect.Make(src, e => err += e);
  if (!effect) throw Error(err || 'shader failed to compile');
  const load = async f => kit.MakeImageFromEncoded(new Uint8Array(await (await fetch('studio/scenes/coastal-meadow/' + f + V)).arrayBuffer()));
  const [art, reg] = await Promise.all([load(sc.art), load(sc.regions)]);
  const C = kit.TileMode.Clamp;
  artShader = art.makeShaderOptions(C, C, kit.FilterMode.Linear, kit.MipmapMode.None);
  regShader = reg.makeShaderOptions(C, C, kit.FilterMode.Linear, kit.MipmapMode.None);
  return { scene: sc, words: w };
})();
self.onmessage = async ({ data }) => {
  try {
    const info = await ready;
    if (data.hello) {
      // Draw the live preview with WebGL when there is any (a GPU or SwiftShader); stills stay software.
      if (data.glCanvas && !data.forceSoftware) gl = kit.MakeWebGLCanvasSurface(data.glCanvas);
      self.postMessage({ hello: true, gl: !!gl, ...info }); return;
    }
    const { id, atm, motion, time, width, height } = data;
    const a = words.atmospheres[atm], m = words.motions[motion];
    const u = [width, height, scene.size[0], scene.size[1], time, scene.horizon, scene.cloudTop, a.atm, m.sway, m.water, m.cloud, m.gust];
    if (data.live && gl) {
      const t0 = performance.now(); const shader = effect.makeShaderWithChildren(u, [artShader, regShader]);
      paint.setShader(shader); gl.getCanvas().drawRect(kit.LTRBRect(0, 0, width, height), paint); gl.flush();
      paint.setShader(null); shader.delete(); self.postMessage({ id, gl: true, ms: performance.now() - t0 }); return;
    }
    if (!surface || sw !== width || sh !== height) { surface && surface.delete(); surface = kit.MakeSurface(width, height); sw = width; sh = height; }
    const t0 = performance.now();
    const shader = effect.makeShaderWithChildren(u, [artShader, regShader]);
    paint.setShader(shader);
    surface.getCanvas().drawRect(kit.LTRBRect(0, 0, width, height), paint); surface.flush();
    const img = surface.makeImageSnapshot();
    const px = img.readPixels(0, 0, { width, height, colorType: kit.ColorType.RGBA_8888, alphaType: kit.AlphaType.Unpremul, colorSpace: kit.ColorSpace.SRGB });
    const copy = new Uint8ClampedArray(px); img.delete(); paint.setShader(null); shader.delete();
    self.postMessage({ id, width, height, pixels: copy, ms: performance.now() - t0 }, [copy.buffer]);
  } catch (e) { self.postMessage({ id: data.id, error: String(e.message || e) }); }
};
