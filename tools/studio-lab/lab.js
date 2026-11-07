// Studio lab: runs the app's own .agsl family sources on CanvasKit (SkSL runtime effects) and
// drives them from the app's own recipe.json. Nothing here is a copy of the shader or the words.
'use strict';

const CK_BASE = 'https://cdn.jsdelivr.net/npm/canvaskit-wasm@0.39.1/bin/';
// Published layout first (publish.sh), then the source tree when served from the repo root.
const ASSET_BASES = ['shaders/', '../../app/src/main/assets/studio/'];
const PHONE = { w: 1080, h: 2412 };
const TILE = { w: 180, h: 402 };

const $ = (id) => document.getElementById(id);
const params = new URLSearchParams(location.search);

const lab = {
  ck: null, recipe: null, family: null, familyId: 'bloom', effect: null,
  words: {}, seed: 1, dark: true, locks: new Set(), seedLocked: false,
  paused: params.has('t'), time: params.has('t') ? parseFloat(params.get('t')) : 0,
  views: [], ready: null, errors: [],
};
window.lab = lab;

async function fetchFirst(name) {
  for (const base of ASSET_BASES) {
    try {
      const r = await fetch(base + name, { cache: 'no-store' });
      if (r.ok) return { text: await r.text(), base };
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

function slotDef(slot) {
  return lab.family.ownSlots[slot] || lab.recipe.slots[slot];
}

function slotWords(slot) {
  const words = slotDef(slot).words;
  if (slot === 'material') return words.filter((w) => lab.family.materials.includes(w.id));
  return words;
}

function findWord(slot, id) {
  return slotWords(slot).find((w) => w.id === id);
}

function denied(words, slot, id) {
  for (const rule of lab.family.exclude || []) {
    const deny = rule.deny[slot];
    if (!deny || !deny.includes(id)) continue;
    const hit = Object.entries(rule.when).every(([s, v]) => words[s] === v);
    if (hit) return true;
  }
  return false;
}

function allowed(words) {
  return lab.family.slots.every((s) => !denied(words, s, words[s]));
}

function shuffle() {
  const pick = (arr) => arr[Math.floor(Math.random() * arr.length)];
  for (let attempt = 0; attempt < 200; attempt++) {
    const next = { ...lab.words };
    for (const s of lab.family.slots) {
      if (!lab.locks.has(s)) next[s] = pick(slotWords(s)).id;
    }
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
  for (const s of lab.family.slots) {
    const word = findWord(s, words[s]);
    if (!word) continue;
    if (s === 'palette') {
      const pal = dark ? word.dark : word.light;
      for (const [key, u] of Object.entries(r.paletteUniforms)) out[u] = hexToRgb(pal[key]);
    } else {
      Object.assign(out, word.uniforms);
    }
  }
  return out;
}

// ---- rendering ----

function packUniforms(effect, values, into) {
  const arr = into || new Float32Array(effect.getUniformFloatCount());
  for (let i = 0; i < effect.getUniformCount(); i++) {
    const u = effect.getUniform(i);
    const name = effect.getUniformName(i);
    const size = u.rows * u.columns;
    const v = values[name];
    if (v === undefined) { if (!packUniforms.warned) { packUniforms.warned = 1; showError('No value for uniform ' + name); } continue; }
    if (size === 1) arr[u.slot] = Array.isArray(v) ? v[0] : v;
    else for (let k = 0; k < size; k++) arr[u.slot + k] = v[k];
  }
  return arr;
}

function makeView(canvas, cssW, cssH, darkOverride) {
  const dpr = Math.min(window.devicePixelRatio || 1, 2);
  canvas.style.width = cssW + 'px';
  canvas.style.height = cssH + 'px';
  canvas.width = Math.round(cssW * dpr);
  canvas.height = Math.round(cssH * dpr);
  const ck = lab.ck;
  let surface = ck.MakeWebGLCanvasSurface(canvas);
  if (!surface) surface = ck.MakeSWCanvasSurface(canvas);
  const view = { canvas, surface, paint: new ck.Paint(), darkOverride, uniforms: null };
  lab.views.push(view);
  return view;
}

function drawView(view, time) {
  const { surface, paint } = view;
  const c = surface.getCanvas();
  const w = view.canvas.width;
  const h = view.canvas.height;
  const dark = view.darkOverride ? !lab.dark : lab.dark;
  const values = uniformValues(lab.words, lab.seed, dark, time, w, h);
  view.uniforms = packUniforms(lab.effect, values, view.uniforms);
  const shader = lab.effect.makeShader(view.uniforms);
  paint.setShader(shader);
  c.drawPaint(paint);
  surface.flush();
  shader.delete();
}

function renderStill(words, seed, dark, time, w, h) {
  const ck = lab.ck;
  const surface = ck.MakeSurface(w, h);
  const paint = new ck.Paint();
  const shader = lab.effect.makeShader(packUniforms(lab.effect, uniformValues(words, seed, dark, time, w, h)));
  paint.setShader(shader);
  surface.getCanvas().drawPaint(paint);
  const img = surface.makeImageSnapshot();
  const bytes = img.encodeToBytes();
  img.delete(); shader.delete(); paint.delete(); surface.delete();
  return URL.createObjectURL(new Blob([bytes], { type: 'image/png' }));
}

let varTimer = 0;
function renderVariations() {
  if (params.has('single')) return;   // ?single: one frame, no tiles (headless shots)
  clearTimeout(varTimer);
  varTimer = setTimeout(() => {
    const box = $('vars');
    for (const img of box.querySelectorAll('img')) URL.revokeObjectURL(img.src);
    box.textContent = '';
    for (let k = 1; k <= 4; k++) {
      const seed = (lab.seed * 7919 + k * 104729) % 99991 + 1;
      const img = document.createElement('img');
      img.src = renderStill(lab.words, seed, lab.dark, 6.0, TILE.w, TILE.h);
      img.title = 'seed ' + seed;
      img.dataset.seed = seed;
      img.onclick = () => { lab.seed = seed; changed(); };
      box.appendChild(img);
    }
  }, 120);
}

// ---- sentence UI ----

function renderSentence() {
  const el = $('sentence');
  el.textContent = '';
  const parts = lab.family.sentence.split(/(\{[a-z]+\})/);
  for (const part of parts) {
    const m = part.match(/^\{([a-z]+)\}$/);
    if (!m) { el.appendChild(document.createTextNode(part)); continue; }
    const slot = m[1];
    const word = findWord(slot, lab.words[slot]);
    const span = document.createElement('span');
    span.className = 'word' + (lab.locks.has(slot) ? ' locked' : '');
    span.textContent = word ? word.label : '?';
    span.dataset.slot = slot;
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
  }
}

function toggleLock(slot) {
  if (lab.locks.has(slot)) lab.locks.delete(slot); else lab.locks.add(slot);
  closeMenu();
  renderSentence();
  writeHash();
}

function openMenu(slot, anchor) {
  const menu = $('menu');
  menu.textContent = '';
  for (const w of slotWords(slot)) {
    const b = document.createElement('button');
    if (w.id === lab.words[slot]) b.className = 'on';
    const trial = { ...lab.words, [slot]: w.id };
    b.disabled = !allowed(trial);
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
    b.onclick = (e) => { e.stopPropagation(); lab.words[slot] = w.id; closeMenu(); changed(); };
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
  const ws = lab.family.slots.map((s) => lab.words[s]).join('.');
  return `${lab.familyId}:${ws}:${lab.seed}:${lab.dark ? 'd' : 'l'}`;
}

function writeHash() {
  const locks = [...lab.locks].join(',') + (lab.seedLocked ? (lab.locks.size ? ',' : '') + 'seed' : '');
  const s = recipeString() + (locks ? ':' + locks : '');
  history.replaceState(null, '', '#' + s);
  $('recipe').textContent = recipeString();
}

function readHash() {
  const h = decodeURIComponent(location.hash.slice(1));
  if (!h) return;
  const [fam, ws, seed, theme, locks] = h.split(':');
  if (fam !== lab.familyId || !ws) return;
  const ids = ws.split('.');
  lab.family.slots.forEach((s, i) => { if (ids[i] && findWord(s, ids[i])) lab.words[s] = ids[i]; });
  if (seed) lab.seed = parseInt(seed, 10) || 1;
  if (theme) lab.dark = theme !== 'l';
  if (locks) for (const l of locks.split(',')) { if (l === 'seed') lab.seedLocked = true; else if (l) lab.locks.add(l); }
}

function changed() {
  renderSentence();
  $('seed').textContent = lab.seed;
  $('seedBox').classList.toggle('locked', lab.seedLocked);
  $('theme').textContent = lab.dark ? 'Light' : 'Dark';
  document.body.classList.toggle('light', !lab.dark);
  writeHash();
  renderVariations();
  if (lab.paused) drawAll();
}

function drawAll() {
  for (const v of lab.views) drawView(v, lab.time);
}

// Test hook: lab.set({words:{...}, seed, dark, time}) then await lab.frame().
lab.set = function (o) {
  if (o.words) Object.assign(lab.words, o.words);
  if (o.seed !== undefined) lab.seed = o.seed;
  if (o.dark !== undefined) lab.dark = o.dark;
  if (o.time !== undefined) { lab.time = o.time; lab.paused = true; }
  changed();
};
lab.frame = () => new Promise((res) => requestAnimationFrame(() => { drawAll(); requestAnimationFrame(res); }));

// ---- boot ----

async function boot() {
  const ck = await CanvasKitInit({ locateFile: (f) => CK_BASE + f });
  lab.ck = ck;
  const rj = await fetchFirst('recipe.json');
  lab.recipe = JSON.parse(rj.text);
  lab.family = lab.recipe.families[lab.familyId];
  $('familyName').textContent = lab.family.label;
  const src = await fetchFirst(lab.family.shader);
  lab.effect = ck.RuntimeEffect.Make(src.text, (err) => showError('Shader compile error:\n' + err));
  if (!lab.effect) throw new Error('The shader did not compile.');

  lab.words = { ...lab.family.default };
  lab.seed = lab.family.default.seed;
  delete lab.words.seed;
  readHash();

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
    requestAnimationFrame(loop);
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
  requestAnimationFrame(loop);
}

lab.ready = boot().catch((e) => { showError(String(e && e.stack || e)); throw e; });
