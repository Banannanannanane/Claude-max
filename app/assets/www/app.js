/* ============================================================
   Mini Jeux — noyau commun (hub, sons, stockage, canvas)
   Sans pub, sans compte, sans internet. Juste pour s'amuser.
   ============================================================ */

/* ---------- Stockage ---------- */
const Store = {
  get(k, d) {
    try {
      const v = localStorage.getItem('mj_' + k);
      return v === null ? d : JSON.parse(v);
    } catch (e) { return d; }
  },
  set(k, v) {
    try { localStorage.setItem('mj_' + k, JSON.stringify(v)); } catch (e) {}
  }
};

function getBest(id) { return Store.get('best_' + id, 0); }
function setBest(id, score) {
  if (score > getBest(id)) { Store.set('best_' + id, score); return true; }
  return false;
}

/* ---------- Vibrations ---------- */
function vib(ms) {
  if (navigator.vibrate) { try { navigator.vibrate(ms); } catch (e) {} }
}

/* ---------- Sons (WebAudio, aucun fichier) ---------- */
const Sfx = (() => {
  let ctx = null;
  let muted = Store.get('muted', false);

  function ac() {
    if (!ctx) {
      const AC = window.AudioContext || window.webkitAudioContext;
      if (AC) ctx = new AC();
    }
    if (ctx && ctx.state === 'suspended') ctx.resume();
    return ctx;
  }

  function tone(freq, dur, type, vol, slideTo) {
    if (muted) return;
    const c = ac();
    if (!c) return;
    const o = c.createOscillator();
    const g = c.createGain();
    o.type = type || 'sine';
    o.frequency.setValueAtTime(freq, c.currentTime);
    if (slideTo) o.frequency.exponentialRampToValueAtTime(slideTo, c.currentTime + dur);
    g.gain.setValueAtTime(vol || .05, c.currentTime);
    g.gain.exponentialRampToValueAtTime(.0001, c.currentTime + dur);
    o.connect(g); g.connect(c.destination);
    o.start(); o.stop(c.currentTime + dur);
  }

  return {
    unlock() { ac(); },
    tap()   { tone(300, .05, 'square', .03); },
    pop()   { tone(520, .07, 'triangle', .06, 780); },
    score() { tone(660, .09, 'triangle', .06, 990); },
    win()   { [523, 659, 784, 1047].forEach((f, i) => setTimeout(() => tone(f, .14, 'triangle', .07), i * 90)); },
    over()  { tone(300, .3, 'sawtooth', .05, 90); },
    toggle() { muted = !muted; Store.set('muted', muted); return muted; },
    get muted() { return muted; }
  };
})();

/* ---------- Registre des jeux & navigation ---------- */
const Games = [];
function registerGame(g) { Games.push(g); }

let current = null;      // { game, instance }
const app = document.getElementById('app');

function showHub() {
  if (current) {
    try { current.instance && current.instance.stop && current.instance.stop(); } catch (e) {}
    current = null;
  }
  app.innerHTML = '';

  const hub = document.createElement('div');
  hub.className = 'hub';
  hub.innerHTML =
    '<h1>Mini Jeux 🎮</h1>' +
    '<div class="tagline">Sans pub &bull; Sans internet &bull; Sans prise de tête</div>' +
    '<div class="cards"></div>' +
    '<div class="hubfoot">Tous les scores restent sur ton téléphone.<br>' +
    '<button class="sndbtn" id="sndbtn"></button></div>';

  const cards = hub.querySelector('.cards');
  Games.forEach(g => {
    const c = document.createElement('div');
    c.className = 'card';
    const b = getBest(g.id);
    c.innerHTML =
      '<div class="emoji">' + g.emoji + '</div>' +
      '<div class="name">' + g.name + '</div>' +
      '<div class="desc">' + g.desc + '</div>' +
      '<div class="best">' + (b > 0 ? g.bestLabel + ' : ' + b : '&nbsp;') + '</div>';
    c.addEventListener('click', () => { Sfx.unlock(); Sfx.tap(); vib(15); startGame(g); });
    cards.appendChild(c);
  });

  const snd = hub.querySelector('#sndbtn');
  const lbl = () => snd.textContent = Sfx.muted ? '🔇 Son coupé' : '🔊 Son activé';
  lbl();
  snd.addEventListener('click', () => { Sfx.toggle(); lbl(); Sfx.tap(); });

  app.appendChild(hub);
}

function startGame(g) {
  app.innerHTML = '';
  const scr = document.createElement('div');
  scr.className = 'game-screen';
  scr.innerHTML =
    '<div class="topbar">' +
    '  <button class="backbtn">←</button>' +
    '  <div class="gtitle">' + g.emoji + ' ' + g.name + '</div>' +
    '  <div><div class="score" id="score-lab"></div>' +
    '  <div class="bestlab" id="best-lab"></div></div>' +
    '</div>' +
    '<div id="game-host"></div>';
  app.appendChild(scr);

  scr.querySelector('.backbtn').addEventListener('click', () => { Sfx.tap(); showHub(); });

  const host = scr.querySelector('#game-host');
  const api = {
    setScore(v) { scr.querySelector('#score-lab').textContent = v; },
    setBestLabel(v) { scr.querySelector('#best-lab').textContent = v; },
    goHub: showHub
  };
  api.setBestLabel(getBest(g.id) > 0 ? g.bestLabel + ' : ' + getBest(g.id) : '');

  current = { game: g, instance: g.start(host, api) };
}

/* Bouton retour Android (appelé par MainActivity) */
function handleBack() {
  if (current) { showHub(); }
  else { location.href = 'app://exit'; }
}

/* ---------- Canvas plein-écran avec gestion du DPR ---------- */
function createCanvas(host) {
  const c = document.createElement('canvas');
  host.appendChild(c);
  const ctx = c.getContext('2d');
  let w = 0, h = 0;

  function resize() {
    const r = host.getBoundingClientRect();
    const dpr = Math.min(2, window.devicePixelRatio || 1);
    w = r.width; h = r.height;
    c.width = Math.round(w * dpr);
    c.height = Math.round(h * dpr);
    c.style.width = w + 'px';
    c.style.height = h + 'px';
    ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
  }
  resize();
  window.addEventListener('resize', resize);

  return {
    el: c, ctx,
    W: () => w, H: () => h,
    destroy() { window.removeEventListener('resize', resize); }
  };
}

/* ---------- Overlay de fin de partie ---------- */
function showEnd(host, opts) {
  // opts: {title, sub, newBest, retryLabel, onRetry, onMenu}
  const ov = document.createElement('div');
  ov.className = 'overlay';
  ov.innerHTML =
    '<div class="panel">' +
    '  <div class="ptitle">' + opts.title + '</div>' +
    (opts.sub ? '<div class="psub">' + opts.sub + '</div>' : '') +
    (opts.newBest ? '<div class="newbest">🏆 Nouveau record !</div>' : '') +
    '  <button class="retry">' + (opts.retryLabel || 'Rejouer') + '</button>' +
    '  <button class="alt menu">Menu</button>' +
    '</div>';
  ov.querySelector('.retry').addEventListener('click', () => {
    Sfx.tap(); vib(15); ov.remove(); opts.onRetry && opts.onRetry();
  });
  ov.querySelector('.menu').addEventListener('click', () => { Sfx.tap(); showHub(); });
  host.appendChild(ov);
  return ov;
}

/* ---------- Détection de swipe ---------- */
function onSwipe(el, cb) {
  let sx = 0, sy = 0, active = false;
  const down = e => {
    const t = e.touches ? e.touches[0] : e;
    sx = t.clientX; sy = t.clientY; active = true;
  };
  const up = e => {
    if (!active) return;
    active = false;
    const t = e.changedTouches ? e.changedTouches[0] : e;
    const dx = t.clientX - sx, dy = t.clientY - sy;
    if (Math.abs(dx) < 24 && Math.abs(dy) < 24) { cb('tap'); return; }
    if (Math.abs(dx) > Math.abs(dy)) cb(dx > 0 ? 'right' : 'left');
    else cb(dy > 0 ? 'down' : 'up');
  };
  el.addEventListener('touchstart', down, { passive: true });
  el.addEventListener('touchend', up, { passive: true });
  el.addEventListener('mousedown', down);
  el.addEventListener('mouseup', up);
  return () => {
    el.removeEventListener('touchstart', down);
    el.removeEventListener('touchend', up);
    el.removeEventListener('mousedown', down);
    el.removeEventListener('mouseup', up);
  };
}
