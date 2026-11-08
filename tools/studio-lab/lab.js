// Studio lab: runs the app's own .agsl subject sources on CanvasKit (SkSL runtime effects) and
// drives them from the app's own recipe.json. Nothing here is a copy of the shaders or the words.
'use strict';

const CK_BASE = 'https://cdn.jsdelivr.net/npm/canvaskit-wasm@0.39.1/bin/';
// Published layout first (publish.sh), then the source tree when served from the repo root.
const ASSET_BASES = ['shaders/', '../../app/src/main/assets/studio/'];
const PHONE = { w: 1080, h: 2412 };
const TILE = { w: 180, h: 402 };

const $ = (id) => document.getElementById(id);

// No GPU (software WebGL such as SwiftShader or llvmpipe): render at a fraction of the size and
// start paused, or the heavy shader holds the main thread and clicks never land.
function softwareGl() {
  try {
    const gl = document.createElement('canvas').getContext('webgl');
    if (!gl) return true;
    const ext = gl.getExtension('WEBGL_debug_renderer_info');
    const r = ext ? String(gl.getParameter(ext.UNMASKED_RENDERER_WEBGL)) : '';
    return /swiftshader|llvmpipe|softpipe|software|basic render/i.test(r);
  } catch (e) { return true; }
}
const params = new URLSearchParams(location.search);
const SOFTWARE = params.has('lowres') || (!params.has('fullres') && softwareGl());
const RENDER_SCALE = SOFTWARE ? 0.5 : 1;

const lab = {
  ck: null, recipe: null, effects: {}, effect: null,
  words: {}, seed: 1, dark: true, locks: new Set(), seedLocked: false,
  hour: new Date().getHours() + new Date().getMinutes() / 60,
  paused: params.has('t') || SOFTWARE, time: params.has('t') ? parseFloat(params.get('t')) : 0,
  views: [], ready: null, errors: [],
};
window.lab = lab;

async function fetchFirst(name) {
  for (const base of ASSET_BASES) {
    try {
      const r = await fetch(base + name, { cache: 'no-store' });
      if (r.ok) return r.text();
    } catch (e) { /* try the next base */ }
  }
  throw new Error('Could not load ' + name);
}

function showError(msg) {
  lab.errors.push(msg);
  const el = $('err');
  el.hidden = false;
  el.textContent = lab.errors.join('\n\n');
  console.error(msg);
}

// ---- recipe model (the Java StudioRecipe will mirror this) ----

function subject(id) { return lab.recipe.subjects.find((s) => s.id === (id || lab.words.subject)); }

function slotWords(slot) {
  if (slot === 'subject') return lab.recipe.subjects;
  return lab.recipe[slot].words;
}

function findWord(slot, id) { return slotWords(slot).find((w) => w.id === id); }

function allowed(words) { return subject(words.subject).materials.includes(words.material); }

function fnv1a(str) {
  let h = 0x811c9dc5;
  for (const b of new TextEncoder().encode(str)) { h ^= b; h = Math.imul(h, 0x01000193); }
  return h | 0;
}

// The seed hash documented in recipe.json (seedHash): one unit value per seeded entry.
function seedUnit(seed, id) {
  let h = Math.imul(seed | 0, 0x9E3779B1) ^ Math.imul(fnv1a(id), 0x85EBCA77);
  h ^= h >>> 16; h = Math.imul(h, 0x7FEB352D);
  h ^= h >>> 15; h = Math.imul(h, 0x846CA68B);
  h ^= h >>> 16;
  return (h >>> 0) / 4294967296;
}

function seededValues(subj, seed, out) {
  for (const e of [...lab.recipe.commonSeeded, ...subj.seeded]) {
    const u = seedUnit(seed, e.id);
    if (e.range) out[e.uniform] = e.range[0] + u * (e.range[1] - e.range[0]);
    else if (e.int) out[e.uniform] = Math.floor(e.int[0] + u * (e.int[1] - e.int[0] + 1));
    else if (e.choices) out[e.uniform] = e.choices[Math.floor(u * e.choices.length)];
    else if (e.presets) Object.assign(out, e.presets[Math.floor(u * e.presets.length)]);
  }
  return out;
}

function shuffle() {
  const pick = (arr) => arr[Math.floor(Math.random() * arr.length)];
  for (let attempt = 0; attempt < 200; attempt++) {
    const next = { ...lab.words };
    for (const s of lab.recipe.slots) if (!lab.locks.has(s)) next[s] = pick(slotWords(s)).id;
    if (allowed(next)) { lab.words = next; break; }
  }
  if (!lab.seedLocked) lab.seed = 1 + Math.floor(Math.random() * 99999);
  changed();
}

function hexToRgb(hex) {
  const n = parseInt(hex.slice(1), 16);
  return [((n >> 16) & 255) / 255, ((n >> 8) & 255) / 255, (n & 255) / 255];
}

function uniformValues(words, seed, dark, time, w, h) {
  const r = lab.recipe;
  const out = {};
  out[r.resolutionUniform] = [w, h];
  out[r.timeUniform] = time;
  out[r.seedUniform] = seed;
  out[r.themeUniform] = dark ? 1 : 0;
  out[r.timeOfDayUniform] = lab.hour;
  const pal = findWord('palette', words.palette);
  const pv = dark ? pal.dark : pal.light;
  for (const [key, u] of Object.entries(r.paletteUniforms)) out[u] = hexToRgb(pv[key]);
  Object.assign(out, findWord('material', words.material).uniforms);
  seededValues(subject(words.subject), seed, out);
  return out;
}

// ---- rendering ----

function packUniforms(effect, values, into) {
  const arr = into && into.length === effect.getUniformFloatCount() ? into : new Float32Array(effect.getUniformFloatCount());
  for (let i = 0; i < effect.getUniformCount(); i++) {
    const u = effect.getUniform(i);
    const name = effect.getUniformName(i);
    const size = u.rows * u.columns;
    const v = values[name];
    if (v === undefined) { showError('No value for uniform ' + name); continue; }
    if (size === 1) arr[u.slot] = Array.isArray(v) ? v[0] : v;
    else for (let k = 0; k < size; k++) arr[u.slot + k] = v[k];
  }
  return arr;
}

function makeSurface(canvas) {
  return lab.ck.MakeWebGLCanvasSurface(canvas) || lab.ck.MakeSWCanvasSurface(canvas);
}

function makeView(canvas, cssW, cssH, darkOverride) {
  const dpr = Math.min(window.devicePixelRatio || 1, 2) * RENDER_SCALE;
  canvas.style.width = cssW + 'px';
  canvas.style.height = cssH + 'px';
  canvas.width = Math.round(cssW * dpr);
  canvas.height = Math.round(cssH * dpr);
  const view = { canvas, surface: makeSurface(canvas), paint: new lab.ck.Paint(), darkOverride, uniforms: null };
  lab.views.push(view);
  return view;
}

function drawWith(view, words, seed, dark, time) {
  const effect = lab.effects[words.subject];
  const values = uniformValues(words, seed, dark, time, view.canvas.width, view.canvas.height);
  view.uniforms = packUniforms(effect, values, view.uniforms);
  const shader = effect.makeShader(view.uniforms);
  view.paint.setShader(shader);
  view.surface.getCanvas().drawPaint(view.paint);
  view.surface.flush();
  shader.delete();
}

function drawView(view, time) {
  drawWith(view, lab.words, lab.seed, view.darkOverride ? !lab.dark : lab.dark, time);
}

// Variations tiles are WebGL canvases drawn once per change. A CPU surface
// (ck.MakeSurface) runs the shader in software and froze the page on every click.
const tileViews = [];
function tileView(k) {
  if (tileViews[k]) return tileViews[k];
  const canvas = document.createElement('canvas');
  canvas.width = Math.round(TILE.w * RENDER_SCALE);
  canvas.height = Math.round(TILE.h * RENDER_SCALE);
  canvas.onclick = () => { lab.seed = Number(canvas.dataset.seed); changed(); };
  $('vars').appendChild(canvas);
  return (tileViews[k] = { canvas, surface: makeSurface(canvas), paint: new lab.ck.Paint(), uniforms: null });
}

function variationSeeds() {
  const out = [];
  for (let k = 1; k <= 4; k++) out.push((lab.seed * 7919 + k * 104729) % 99991 + 1);
  return out;
}

let varTimer = 0;
function renderVariations() {
  if (params.has('single')) return;   // ?single: one frame, no tiles (headless shots)
  clearTimeout(varTimer);
  varTimer = setTimeout(() => {
    variationSeeds().forEach((seed, k) => {
      const v = tileView(k);
      v.canvas.title = 'seed ' + seed;
      v.canvas.dataset.seed = seed;
      drawWith(v, lab.words, seed, lab.dark, 6.0);
    });
  }, 120);
}

// ---- sentence UI ----

function wordLabel(slot) { const w = findWord(slot, lab.words[slot]); return w ? w.label : '?'; }

function renderSentence() {
  const el = $('sentence');
  el.textContent = '';
  const parts = lab.recipe.sentence.split(/(\{[A-Za-z]+\})/);
  parts.forEach((part, i) => {
    const m = part.match(/^\{([A-Za-z]+)\}$/);
    if (!m) { el.appendChild(document.createTextNode(part)); return; }
    if (m[1] === 'A') {
      const next = parts.slice(i + 1).find((x) => /^\{[a-z]+\}$/.test(x));
      const nextLabel = next ? wordLabel(next.slice(1, -1)) : '';
      el.appendChild(document.createTextNode(/^[aeiou]/i.test(nextLabel) ? 'An' : 'A'));
      return;
    }
    const slot = m[1];
    const span = document.createElement('span');
    span.className = 'word' + (lab.locks.has(slot) ? ' locked' : '');
    span.textContent = wordLabel(slot);
    let pressTimer = 0;
    let longPressed = false;
    span.addEventListener('pointerdown', () => {
      longPressed = false;
      pressTimer = setTimeout(() => { longPressed = true; toggleLock(slot); }, 450);
    });
    span.addEventListener('pointerup', () => clearTimeout(pressTimer));
    span.addEventListener('pointerleave', () => clearTimeout(pressTimer));
    span.addEventListener('click', (e) => {
      e.stopPropagation();
      if (longPressed) return;
      if (e.shiftKey) { toggleLock(slot); return; }
      openMenu(slot, span);
    });
    el.appendChild(span);
  });
}

function toggleLock(slot) {
  if (lab.locks.has(slot)) lab.locks.delete(slot); else lab.locks.add(slot);
  closeMenu();
  renderSentence();
  writeHash();
}

// Picking a word that breaks the subject-material pair moves the other word to an allowed one,
// unless that word is locked; then the option is disabled.
function applyWord(slot, id) {
  const next = { ...lab.words, [slot]: id };
  if (!allowed(next)) {
    if (slot === 'subject' && !lab.locks.has('material')) next.material = subject(id).materials[0];
    else if (slot === 'material' && !lab.locks.has('subject')) next.subject = lab.recipe.subjects.find((s) => s.materials.includes(id)).id;
    else return false;
  }
  lab.words = next;
  return true;
}

function canApply(slot, id) {
  const next = { ...lab.words, [slot]: id };
  if (allowed(next)) return true;
  return slot === 'subject' ? !lab.locks.has('material') : !lab.locks.has('subject');
}

function openMenu(slot, anchor) {
  const menu = $('menu');
  menu.textContent = '';
  for (const w of slotWords(slot)) {
    const b = document.createElement('button');
    if (w.id === lab.words[slot]) b.className = 'on';
    b.disabled = !canApply(slot, w.id);
    if (slot === 'palette') {
      const sw = document.createElement('span');
      sw.className = 'sw';
      const pal = lab.dark ? w.dark : w.light;
      for (const key of ['bgTop', 'inner', 'outer', 'core', 'rim']) {
        const c = document.createElement('span');
        c.style.background = pal[key];
        sw.appendChild(c);
      }
      b.appendChild(sw);
    }
    b.appendChild(document.createTextNode(w.label));
    b.onclick = (e) => { e.stopPropagation(); applyWord(slot, w.id); closeMenu(); changed(); };
    menu.appendChild(b);
  }
  const lockBtn = document.createElement('button');
  lockBtn.textContent = lab.locks.has(slot) ? 'Unlock this word' : 'Lock this word';
  lockBtn.style.color = 'var(--lock)';
  lockBtn.onclick = (e) => { e.stopPropagation(); toggleLock(slot); };
  menu.appendChild(lockBtn);
  menu.hidden = false;
  const r = anchor.getBoundingClientRect();
  const mh = menu.offsetHeight;
  const top = r.bottom + 6 + mh > innerHeight ? Math.max(8, r.top - 6 - mh) : r.bottom + 6;
  menu.style.top = top + 'px';
  menu.style.left = Math.min(r.left, innerWidth - menu.offsetWidth - 8) + 'px';
}

function closeMenu() { $('menu').hidden = true; }

// ---- state ----

function recipeString() {
  return `${lab.words.subject}.${lab.words.material}.${lab.words.palette}:${lab.seed}:${lab.dark ? 'd' : 'l'}`;
}

function writeHash() {
  const locks = [...lab.locks, ...(lab.seedLocked ? ['seed'] : [])].join(',');
  history.replaceState(null, '', '#' + recipeString() + (locks ? ':' + locks : ''));
  const vals = seededValues(subject(), lab.seed, {});
  const shown = Object.entries(vals).map(([k, v]) => `${k.slice(1)}=${Array.isArray(v) ? v.map((x) => +x.toFixed(2)).join('/') : +(+v).toFixed(2)}`);
  $('recipe').textContent = recipeString() + '  ·  ' + shown.join('  ');
}

function readHash() {
  const h = decodeURIComponent(location.hash.slice(1));
  if (!h) return;
  const [ws, seed, theme, locks] = h.split(':');
  const [subj, mat, pal] = (ws || '').split('.');
  if (subj && subject(subj)) lab.words.subject = subj;
  if (mat && findWord('material', mat)) lab.words.material = mat;
  if (pal && findWord('palette', pal)) lab.words.palette = pal;
  if (!allowed(lab.words)) lab.words.material = subject().materials[0];
  if (seed) lab.seed = parseInt(seed, 10) || 1;
  if (theme) lab.dark = theme !== 'l';
  if (locks) for (const l of locks.split(',')) { if (l === 'seed') lab.seedLocked = true; else if (l) lab.locks.add(l); }
}

function fmtHour(h) {
  const hh = Math.floor(h) % 24;
  const mm = Math.floor((h - Math.floor(h)) * 60);
  return String(hh).padStart(2, '0') + ':' + String(mm).padStart(2, '0');
}

function changed() {
  renderSentence();
  $('seed').textContent = lab.seed;
  $('seedBox').classList.toggle('locked', lab.seedLocked);
  $('theme').textContent = lab.dark ? 'Light' : 'Dark';
  document.body.classList.toggle('light', !lab.dark);
  $('hourRow').hidden = !subject().seeded.length || lab.words.subject !== 'horizon';
  $('hourVal').textContent = fmtHour(lab.hour);
  writeHash();
  renderVariations();
  if (lab.paused) drawAll();
}

function drawAll() {
  for (const v of lab.views) drawView(v, lab.time);
}

// Test hook: lab.set({words:{...}, seed, dark, time, hour}) then await lab.frame().
lab.set = function (o) {
  if (o.words) Object.assign(lab.words, o.words);
  if (o.seed !== undefined) lab.seed = o.seed;
  if (o.dark !== undefined) lab.dark = o.dark;
  if (o.hour !== undefined) { lab.hour = o.hour; $('hour').value = o.hour; }
  if (o.time !== undefined) { lab.time = o.time; lab.paused = true; }
  changed();
};
lab.frame = () => new Promise((res) => requestAnimationFrame(() => { drawAll(); requestAnimationFrame(res); }));
lab.variationSeeds = variationSeeds;

// ---- boot ----

async function boot() {
  const ck = await CanvasKitInit({ locateFile: (f) => CK_BASE + f });
  lab.ck = ck;
  lab.recipe = JSON.parse(await fetchFirst('recipe.json'));
  // Each subject's shader is compiled once; the Subject word switches between them.
  for (const s of lab.recipe.subjects) {
    const src = await fetchFirst(s.shader);
    const effect = ck.RuntimeEffect.Make(src, (err) => showError(s.shader + ' did not compile:\n' + err));
    if (effect) lab.effects[s.id] = effect;
  }
  const d = lab.recipe.default;
  lab.words = { material: d.material, subject: d.subject, palette: d.palette };
  lab.seed = d.seed;
  readHash();
  if (!lab.effects[lab.words.subject]) throw new Error('The ' + lab.words.subject + ' shader did not compile.');
  lab.effect = lab.effects[lab.words.subject];

  // Main frame at pong's aspect, sized to the window; the small frame shows the other theme.
  const mainH = Math.max(480, Math.min(innerHeight - 84, 900));
  const mainW = Math.round(mainH * PHONE.w / PHONE.h);
  makeView($('view'), mainW, mainH, false);
  if (!params.has('single')) {
    const small = document.createElement('div');
    small.className = 'phone small';
    const sc = document.createElement('canvas');
    small.appendChild(sc);
    $('stage').appendChild(small);
    const sh = Math.round(mainH * .42);
    makeView(sc, Math.round(sh * PHONE.w / PHONE.h), sh, true);
  }
  $('res').textContent = `${lab.views[0].canvas.width}×${lab.views[0].canvas.height} px`;

  $('shuffle').onclick = shuffle;
  $('theme').onclick = () => { lab.dark = !lab.dark; changed(); };
  $('chromeBtn').onclick = () => { $('chrome').hidden = !$('chrome').hidden; };
  $('pause').onclick = () => { lab.paused = !lab.paused; $('pause').textContent = lab.paused ? 'Play' : 'Pause'; };
  $('seedBox').onclick = () => { lab.seedLocked = !lab.seedLocked; changed(); };
  $('hour').value = lab.hour;
  $('hour').oninput = () => { lab.hour = parseFloat($('hour').value); changed(); };
  document.addEventListener('click', closeMenu);
  document.addEventListener('keydown', (e) => {
    if (e.code === 'Space' && e.target === document.body) { e.preventDefault(); shuffle(); }
    if (e.key === 'Escape') closeMenu();
  });
  $('pause').textContent = lab.paused ? 'Play' : 'Pause';
  changed();

  let last = performance.now();
  let frames = 0;
  let fpsAt = last;
  const loop = (now) => {
    if (SOFTWARE) setTimeout(() => loop(performance.now()), 100); else requestAnimationFrame(loop);
    const dt = Math.min(now - last, 100) / 1000;
    last = now;
    if (lab.paused) { fpsAt = now; frames = 0; return; }   // paused: draw only on change
    lab.time += dt;
    drawAll();
    frames++;
    if (now - fpsAt > 500) {
      $('fps').textContent = Math.round(frames * 1000 / (now - fpsAt));
      frames = 0;
      fpsAt = now;
    }
  };
  if (SOFTWARE) {
    $('res').textContent += ' · no GPU: low resolution, paused';
    setTimeout(() => loop(performance.now()), 100);
  } else requestAnimationFrame(loop);
}

lab.ready = boot().catch((e) => { showError(String(e && e.stack || e)); throw e; });
