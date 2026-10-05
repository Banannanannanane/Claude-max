/* Trouve le Foin — trouver du foin dans une botte d'aiguilles, puis fondre les aiguilles. */
'use strict';
(function () {

// ============================================================ utilitaires
const $ = (s, r = document) => r.querySelector(s);
const $$ = (s, r = document) => Array.from(r.querySelectorAll(s));
const clamp = (v, a, b) => (v < a ? a : v > b ? b : v);
const rand = (a, b) => a + Math.random() * (b - a);
const r3 = v => Math.round(v * 1000) / 1000;
const SUF = ['', 'k', 'M', 'Md', 'Bn', 'Bd', 'Tn', 'Td', 'Qa'];
function fmt(n, dec) {
  if (!isFinite(n)) return '∞';
  const neg = n < 0;
  n = Math.abs(n);
  let s;
  if (n < 1000) s = dec && n % 1 !== 0 && n < 100 ? n.toFixed(dec) : Math.floor(n).toString();
  else {
    let i = 0;
    while (n >= 1000 && i < SUF.length - 1) { n /= 1000; i++; }
    s = (n < 10 ? n.toFixed(2) : n < 100 ? n.toFixed(1) : Math.floor(n).toString()) + SUF[i];
  }
  return (neg ? '-' : '') + s.replace('.', ',');
}
const eur = n => fmt(n, 2) + ' €';
const pct = n => Math.round(n * 100) + ' %';

// ============================================================ données
const METALS = {
  cuivre:    { name: 'Cuivre',    color: '#d27a43', hi: '#f6c19c', melt: 1085, price: 12,  unlock: 1, w: 50 },
  fer:       { name: 'Fer',       color: '#8d939c', hi: '#e2e6ec', melt: 1538, price: 22,  unlock: 1, w: 42 },
  argent:    { name: 'Argent',    color: '#c9ced6', hi: '#ffffff', melt: 962,  price: 34,  unlock: 2, w: 24 },
  or:        { name: 'Or',        color: '#e3b33a', hi: '#fff1b0', melt: 1064, price: 105, unlock: 4, w: 12 },
  titane:    { name: 'Titane',    color: '#7d93ad', hi: '#d0def0', melt: 1668, price: 230, unlock: 6, w: 8 },
  tungstene: { name: 'Tungstène', color: '#5a616d', hi: '#b3bccb', melt: 3422, price: 680, unlock: 9, w: 5 },
};
const METAL_KEYS = Object.keys(METALS);

const ALLOYS = {
  acier:     { name: 'Acier',                color: '#a6b6c6', price: 48,   ing: { fer: 1 }, coal: 2 },
  electrum:  { name: 'Électrum',             color: '#efd77e', price: 190,  ing: { or: 1, argent: 1 }, coal: 0 },
  carbure:   { name: 'Carbure de tungstène', color: '#3f454e', price: 1090, ing: { tungstene: 1 }, coal: 3 },
};
const ALLOY_KEYS = Object.keys(ALLOYS);

const FUELS = {
  foin:    { name: 'Foin',        icon: '🌾', max: 1150, burn: 12 },
  charbon: { name: 'Charbon',     icon: '⚫', max: 1850, burn: 25 },
  elec:    { name: 'Électricité', icon: '⚡', max: 3800 },
};
const ELEC_COST = 2.5;       // € / s
const COAL_PRICE = 4;
const HAY_PRICE = 6;

const TIERS = [
  null,
  { name: 'Four à foin',     icon: '🔥', max: 1200, speed: 1,   fuels: ['foin', 'charbon'],         cost: 0 },
  { name: 'Haut fourneau',   icon: '🏭', max: 1750, speed: 1.6, fuels: ['foin', 'charbon'],         cost: 400 },
  { name: 'Four à arc',      icon: '⚡', max: 3700, speed: 2.5, fuels: ['foin', 'charbon', 'elec'], cost: 6000 },
  { name: 'Four à plasma',   icon: '🌌', max: 5000, speed: 4,   fuels: ['foin', 'charbon', 'elec'], cost: 90000 },
];

const MARKET_KEYS = [...METAL_KEYS, ...ALLOY_KEYS, 'foin'];

const UPG = [
  { id: 'aimant', cat: 'Récolte', icon: '🧲', name: 'Aimant à main', base: 15, k: 1.55, max: 20,
    desc: 'Chaque toucher attire aussi des aiguilles voisines.', eff: l => `+${l} aiguille${l > 1 ? 's' : ''} par toucher` },
  { id: 'gants', cat: 'Récolte', icon: '🧤', name: 'Gants renforcés', base: 30, k: 3, max: 4,
    desc: 'Divise par deux le risque de te piquer les doigts.', eff: l => `Risque de piqûre : ${(9 * 0.5 ** l).toFixed(1).replace('.', ',')} %` },
  { id: 'lunettes', cat: 'Récolte', icon: '🥽', name: 'Lunettes à foin', base: 120, k: 1, max: 1,
    desc: 'Le foin enfoui brille à travers les aiguilles.', req: () => S.stats.hay >= 3 },
  { id: 'electro', cat: 'Automatisation', icon: '⚙️', name: 'Électro-aimant', base: 25, k: 1.17, max: 300,
    desc: 'Retire les aiguilles de la botte tout seul (le foin, lui, n’est pas magnétique).', eff: l => `${fmt(magRate(l), 1)} aiguilles/s` },
  { id: 'chevre', cat: 'Automatisation', icon: '🐐', name: 'Chèvre renifleuse', base: 80, k: 1.4, max: 40,
    desc: 'Flaire et récupère les brins de foin, même tout au fond.', req: () => S.stats.hay >= 1,
    eff: l => `1 brin toutes les ${fmt(1 / goatRate(l), 1)} s` },
  { id: 'convoyeur', cat: 'Automatisation', icon: '🛤️', name: 'Convoyeur', base: 150, k: 1.6, max: 12,
    desc: 'Charge automatiquement les aiguilles dans les trémies des fours.', req: () => S.stats.ingots >= 1,
    eff: l => `${loadRate(l)} aiguilles/s par four` },
  { id: 'souffleur', cat: 'Automatisation', icon: '💨', name: 'Chargeur de combustible', base: 250, k: 1, max: 1,
    desc: 'Remet du foin/charbon dans les fours et allume l’électricité quand il faut.', req: () => S.stats.ingots >= 2 },
  { id: 'coulee', cat: 'Automatisation', icon: '🫗', name: 'Coulée continue', base: 200, k: 1, max: 1,
    desc: 'Coule automatiquement le métal fondu dans les moules à lingots.', req: () => S.stats.ingots >= 2 },
  { id: 'selecteur', cat: 'Automatisation', icon: '🧠', name: 'Sélecteur intelligent', base: 1200, k: 1, max: 1,
    desc: 'Chaque four choisit seul le métal le plus rentable et le bon combustible.', req: () => S.furnaces.length >= 2 || S.botte.level >= 4 },
  { id: 'charbonnier', cat: 'Automatisation', icon: '🚚', name: 'Contrat de charbon', base: 1500, k: 1, max: 1,
    desc: 'Rachète 20 charbons dès que le stock passe sous 15.', req: () => S.stats.coalBought >= 20 },
  { id: 'courtier', cat: 'Commerce', icon: '🤵', name: 'Courtier automatique', base: 900, k: 1, max: 1,
    desc: 'Vend lingots et alliages dès que le cours dépasse ton seuil (réglable au marché).', req: () => S.stats.sold >= 5 },
  { id: 'negoce', cat: 'Commerce', icon: '🤝', name: 'Négociation', base: 300, k: 1.6, max: 30,
    desc: '+6 % sur tous les prix de vente.', req: () => S.stats.sold >= 1, eff: l => `+${6 * l} % sur les ventes` },
  { id: 'creuset', cat: 'Fonderie', icon: '🥣', name: 'Creuset compact', base: 150, k: 2.4, max: 5,
    desc: 'Il faut une aiguille de moins pour couler un lingot.', req: () => S.stats.ingots >= 1, eff: l => `${10 - l} aiguilles par lingot` },
  { id: 'isolation', cat: 'Fonderie', icon: '🧱', name: 'Isolation réfractaire', base: 100, k: 1.8, max: 8,
    desc: 'Les fours montent en température plus vite.', req: () => S.stats.ingots >= 1, eff: l => `Inertie thermique ÷${(1 + 0.35 * l).toFixed(2).replace('.', ',')}` },
  { id: 'vitesse', cat: 'Fonderie', icon: '🔆', name: 'Brûleurs turbo', base: 300, k: 1.7, max: 20,
    desc: '+25 % de vitesse de fusion.', req: () => S.stats.ingots >= 3, eff: l => `+${25 * l} % de vitesse` },
  { id: 'capacite', cat: 'Fonderie', icon: '📦', name: 'Grand creuset', base: 200, k: 1.7, max: 12,
    desc: '+2 lingots en fusion et +25 aiguilles par trémie.', req: () => S.stats.ingots >= 3,
    eff: l => `${moltenCap(l)} lingots · ${hopperCap(l)} aiguilles` },
  { id: 'four', cat: 'Fonderie', icon: '🏗️', name: 'Nouveau four', base: 500, k: 7, max: 3,
    desc: 'Ajoute un four à ta fonderie (4 au maximum).', req: () => S.stats.ingots >= 5, onBuy: () => S.furnaces.push(newFurnace()) },
  { id: 'forgeauto', cat: 'Fonderie', icon: '⚒️', name: 'Forge automatique', base: 5000, k: 1, max: 1,
    desc: 'Fabrique automatiquement les alliages que tu as activés.', req: () => S.stats.alloys >= 1 },
];
const UPG_BY = Object.fromEntries(UPG.map(u => [u.id, u]));
const UPG_CATS = ['Récolte', 'Automatisation', 'Fonderie', 'Commerce'];

const QUESTS = [
  { t: 'Trouve ton premier brin de foin', g: () => [S.stats.hay, 1], r: 10 },
  { t: 'Retire 40 aiguilles de la botte', g: () => [S.stats.needles, 40], r: 10 },
  { t: 'Coule ton premier lingot (Fonderie)', g: () => [S.stats.ingots, 1], r: 25 },
  { t: 'Vends un lingot au Marché', g: () => [S.stats.sold, 1], r: 25 },
  { t: 'Achète un Électro-aimant (Atelier)', g: () => [lvl('electro'), 1], r: 15 },
  { t: 'Termine ta première botte (trouve tout le foin)', g: () => [S.stats.bottes, 1], r: 50 },
  { t: 'Engage une chèvre renifleuse', g: () => [lvl('chevre'), 1], r: 60 },
  { t: 'Installe un convoyeur', g: () => [lvl('convoyeur'), 1], r: 100 },
  { t: 'Coule un lingot de fer (Haut fourneau + charbon)', g: () => [S.stats.made.fer, 1], r: 250 },
  { t: 'Automatise le combustible et la coulée', g: () => [lvl('souffleur') + lvl('coulee'), 2], r: 300 },
  { t: 'Atteins la botte n°5', g: () => [S.botte.level, 5], r: 500 },
  { t: 'Possède 2 fours', g: () => [S.furnaces.length, 2], r: 800 },
  { t: 'Forge un alliage', g: () => [S.stats.alloys, 1], r: 1000 },
  { t: 'Coule 20 lingots d’or', g: () => [S.stats.made.or, 20], r: 3000 },
  { t: 'Gagne 50 000 € au total', g: () => [S.stats.earnedAll, 50000], r: 5000 },
  { t: 'Coule un lingot de tungstène (Four à arc)', g: () => [S.stats.made.tungstene, 1], r: 20000 },
  { t: 'Vends la ferme (prestige)', g: () => [S.stats.prestiges, 1], r: 0 },
  { t: 'Atteins la botte n°15', g: () => [S.botte.level, 15], r: 250000 },
];

// ============================================================ formules
const lvl = id => S.up[id] || 0;
const upCost = u => Math.round(u.base * Math.pow(u.k, lvl(u.id)));
const magRate = l => l <= 0 ? 0 : 0.6 * l * (1 + 0.012 * l) * (1 + 0.05 * S.stars);
const goatRate = l => 0.035 * l;
const loadRate = l => 3 * l;
const npi = (l = lvl('creuset')) => 10 - l;
const hopperCap = (l = lvl('capacite')) => 50 + 25 * l;
const moltenCap = (l = lvl('capacite')) => 3 + 2 * l;
const botteSpec = L => ({ needles: Math.round(220 * Math.pow(1.33, L - 1)), hay: 5 + L });
const botteReward = L => Math.round(40 * Math.pow(1.55, L - 1));
const sellMult = () => (1 + 0.06 * lvl('negoce')) * (1 + 0.1 * S.stars);
const starsPotential = () => Math.floor(Math.sqrt(S.stats.earnedAll / 40000));
const canPrestige = () => S.botte.level >= 8 && starsPotential() > S.stars;

const wCache = {};
function weights(L) {
  if (wCache[L]) return wCache[L];
  const w = {};
  let tot = 0;
  for (const k of METAL_KEYS) {
    const m = METALS[k];
    let x = 0;
    if (L >= m.unlock) x = (k === 'cuivre' || k === 'fer') ? m.w * Math.max(0.35, 1 - 0.05 * (L - 1)) : m.w * (1 + 0.3 * (L - m.unlock));
    w[k] = x; tot += x;
  }
  for (const k of METAL_KEYS) w[k] /= tot;
  return (wCache[L] = w);
}
function pickMetal(L) {
  const w = weights(L);
  let r = Math.random();
  for (const k of METAL_KEYS) { r -= w[k]; if (r <= 0) return k; }
  return 'fer';
}

// ============================================================ état
let S;
function newFurnace() {
  return { tier: 1, metal: 'cuivre', fuel: 'foin', temp: 20, burn: 0, burnType: 'foin', hopper: 0, prog: 0, molten: 0,
    moltenMetal: 'cuivre', elecOn: false, loadAcc: 0 };
}
function newState(keep) {
  const s = {
    v: 1, money: 0, hay: 0, coal: 0,
    needles: {}, ingots: {}, alloys: {}, seen: { cuivre: true, fer: true },
    up: {}, furnaces: [newFurnace()], botte: null, pile: [], market: {},
    stars: 0, quest: 0, event: null, nextEvent: 90,
    stats: { needles: 0, hay: 0, ingots: 0, sold: 0, earned: 0, earnedAll: 0, bottes: 0, made: {}, alloys: 0,
      time: 0, pricks: 0, prestiges: 0, coalBought: 0, bestBotte: 1 },
    settings: { sound: true, vib: true, sellPct: 105, sellHay: false, forge: {} },
    lastSave: Date.now(),
  };
  METAL_KEYS.forEach(k => { s.needles[k] = 0; s.ingots[k] = 0; s.stats.made[k] = 0; });
  ALLOY_KEYS.forEach(k => { s.alloys[k] = 0; s.settings.forge[k] = true; });
  MARKET_KEYS.forEach(k => { s.market[k] = { m: 1, sat: 1 }; });
  if (keep) Object.assign(s, keep);
  return s;
}
function fillDefaults(def, obj) {
  for (const k in def) {
    if (obj[k] === undefined || obj[k] === null) obj[k] = def[k];
    else if (typeof def[k] === 'object' && !Array.isArray(def[k]) && typeof obj[k] === 'object') fillDefaults(def[k], obj[k]);
  }
  return obj;
}

// ============================================================ sauvegarde
const KEY = 'trouvelefoin_save_v1';
const bridge = window.Android || null;
function storeGet() {
  try { if (bridge && bridge.load) { const v = bridge.load(KEY); if (v) return v; } } catch (e) {}
  try { return localStorage.getItem(KEY); } catch (e) { return null; }
}
function storeSet(v) {
  try { if (bridge && bridge.save) bridge.save(KEY, v); } catch (e) {}
  try { localStorage.setItem(KEY, v); } catch (e) {}
}
function save() {
  S.lastSave = Date.now();
  storeSet(JSON.stringify(S, (k, v) => (k === 'b' ? undefined : v)));
}
function load() {
  const raw = storeGet();
  if (raw) {
    try {
      const o = JSON.parse(raw);
      S = fillDefaults(newState(), o);
      S.furnaces.forEach(f => fillDefaults(newFurnace(), f));
      if (!S.botte || !Array.isArray(S.pile)) newBotte(1);
      return true;
    } catch (e) { console.warn('save invalide', e); }
  }
  S = newState();
  newBotte(1);
  return false;
}
window.saveGame = save;

// ============================================================ son & vibration
let AC = null, lastNeedleSfx = 0;
function audio() {
  if (!AC) { try { AC = new (window.AudioContext || window.webkitAudioContext)(); } catch (e) { AC = null; } }
  if (AC && AC.state === 'suspended') AC.resume();
  return AC;
}
function tone(f, d, type = 'sine', vol = 0.12, slide = 0, delay = 0) {
  if (!AC || !S.settings.sound || offline) return;
  const t = AC.currentTime + delay;
  const o = AC.createOscillator(), g = AC.createGain();
  o.type = type;
  o.frequency.setValueAtTime(f, t);
  if (slide) o.frequency.exponentialRampToValueAtTime(Math.max(30, f + slide), t + d);
  g.gain.setValueAtTime(0.0001, t);
  g.gain.exponentialRampToValueAtTime(vol, t + 0.008);
  g.gain.exponentialRampToValueAtTime(0.0001, t + d);
  o.connect(g); g.connect(AC.destination);
  o.start(t); o.stop(t + d + 0.03);
}
const SFX = {
  needle() { const n = performance.now(); if (n - lastNeedleSfx < 45) return; lastNeedleSfx = n; tone(2300 + Math.random() * 900, 0.05, 'triangle', 0.05); },
  hay() { tone(660, 0.14, 'sine', 0.14); tone(990, 0.22, 'sine', 0.11, 0, 0.08); },
  prick() { tone(220, 0.28, 'sawtooth', 0.09, -120); },
  cash() { tone(1320, 0.06, 'square', 0.035); tone(1760, 0.12, 'square', 0.035, 0, 0.05); },
  buy() { tone(440, 0.09, 'triangle', 0.1, 260); },
  cast() { tone(320, 0.35, 'sine', 0.09, -160); tone(900, 0.08, 'triangle', 0.04, 0, 0.3); },
  win() { [523, 659, 784, 1046].forEach((f, i) => tone(f, 0.25, 'triangle', 0.1, 0, i * 0.09)); },
  click() { tone(900, 0.03, 'triangle', 0.04); },
};
function sfx(n) { try { SFX[n](); } catch (e) {} }
function vib(ms) {
  if (!S.settings.vib || offline) return;
  try { if (bridge && bridge.vibrate) { bridge.vibrate(ms); return; } } catch (e) {}
  try { navigator.vibrate && navigator.vibrate(ms); } catch (e) {}
}

// ============================================================ toasts / modal
function toast(msg, cls) {
  if (offline) return;
  const box = $('#toasts');
  const el = document.createElement('div');
  el.className = 'toast' + (cls ? ' ' + cls : '');
  el.textContent = msg;
  box.appendChild(el);
  while (box.children.length > 3) box.firstChild.remove();
  setTimeout(() => { el.classList.add('out'); setTimeout(() => el.remove(), 400); }, 2600);
}
let modalCbs = [];
function modal(title, html, btns) {
  $('#modal-title').textContent = title;
  $('#modal-body').innerHTML = html;
  modalCbs = btns || [{ label: 'OK' }];
  $('#modal-btns').innerHTML = modalCbs.map((b, i) => `<button class="btn ${b.cls || 'gold'}" data-mb="${i}">${b.label}</button>`).join('');
  $('#modal').hidden = false;
}
function closeModal() { $('#modal').hidden = true; }

// ============================================================ gains
function gainMoney(x) { S.money += x; S.stats.earned += x; S.stats.earnedAll += x; }
function gainNeedle(m, n) {
  S.needles[m] += n; S.stats.needles += n;
  if (!S.seen[m]) { S.seen[m] = true; toast(`✨ Nouveau métal : aiguilles en ${METALS[m].name.toLowerCase()} !`, 'gold'); }
}
function gainHay(n) { S.hay += n; S.stats.hay += n; }

// ============================================================ botte (la pile d'aiguilles)
const PILE_TARGET = 300;
function newBotte(L) {
  const sp = botteSpec(L);
  S.botte = { level: L, needlesLeft: sp.needles, hayLeft: sp.hay, needlesTotal: sp.needles, hayTotal: sp.hay, done: false };
  S.stats.bestBotte = Math.max(S.stats.bestBotte || 1, L);
  S.pile = [];
  refill(true);
  dirty = true;
}
function makeObj(isHay, fade) {
  const o = { h: isHay ? 1 : 0, x: r3(rand(0.05, 0.95)), y: r3(rand(0.05, 0.95)), a: r3(rand(0, Math.PI)),
    l: r3(isHay ? rand(0.2, 0.3) : rand(0.16, 0.25)), e: Math.random() < 0.5 ? 1 : -1 };
  if (isHay) o.c = r3(rand(-0.22, 0.22)); else o.m = pickMetal(S.botte.level);
  if (fade) o.b = performance.now();
  return o;
}
function visCounts() { let n = 0, h = 0; for (const o of S.pile) o.h ? h++ : n++; return [n, h]; }
function refill(initial) {
  const [vn, vh] = visCounts();
  let hn = S.botte.needlesLeft - vn, hh = S.botte.hayLeft - vh;
  while (S.pile.length < PILE_TARGET && hn + hh > 0) {
    const hay = Math.random() * (hn + hh) < hh;
    if (initial) S.pile.splice(Math.floor(Math.random() * (S.pile.length + 1)), 0, makeObj(hay, false));
    else S.pile.unshift(makeObj(hay, !offline));
    hay ? hh-- : hn--;
  }
}
function removeObj(o, fly, who) {
  const i = S.pile.indexOf(o);
  if (i < 0) return;
  S.pile.splice(i, 1);
  if (o.h) { S.botte.hayLeft--; gainHay(1); } else { S.botte.needlesLeft--; gainNeedle(o.m, 1); }
  if (fly && !offline) addFly(o, who);
  refill(false);
  dirty = true;
  checkBotte();
}
// extraction "abstraite" (au fond de la botte, ou hors-ligne)
function takeHiddenNeedles(n) {
  const [vn] = visCounts();
  const hidden = Math.max(0, S.botte.needlesLeft - vn);
  let k = Math.min(n, hidden);
  if (k > 0) {
    const w = weights(S.botte.level);
    let given = 0;
    for (const m of METAL_KEYS) {
      if (!w[m]) continue;
      let q = Math.floor(k * w[m]);
      if (Math.random() < k * w[m] - q) q++;
      q = Math.min(q, k - given);
      if (q > 0) { gainNeedle(m, q); given += q; }
    }
    if (given < k) { gainNeedle(pickMetal(S.botte.level), k - given); }
    S.botte.needlesLeft -= k;
  }
  let rest = n - k;
  while (rest > 0) {
    const o = topNeedle();
    if (!o) break;
    removeObj(o, false);
    rest--;
  }
  checkBotte();
}
function topNeedle() { for (let i = S.pile.length - 1; i >= 0; i--) if (!S.pile[i].h) return S.pile[i]; return null; }
function topHay() { for (let i = S.pile.length - 1; i >= 0; i--) if (S.pile[i].h) return S.pile[i]; return null; }
function checkBotte() {
  const b = S.botte;
  if (!b.done && b.hayLeft <= 0) {
    b.done = true;
    const r = botteReward(b.level);
    gainMoney(r);
    S.stats.bottes++;
    sfx('win'); vib(60);
    toast(`🎉 Botte n°${b.level} : tout le foin est trouvé ! +${eur(r)}`, 'gold');
  }
}
function nextBotte() {
  if (!S.botte.done) return;
  newBotte(S.botte.level + 1);
  toast(`🪡 Nouvelle botte n°${S.botte.level} : ${fmt(S.botte.needlesTotal)} aiguilles, ${S.botte.hayTotal} brins cachés`);
}

// ============================================================ fonderie
function reachMax(f, fuel = f.fuel) { return Math.min(TIERS[f.tier].max, FUELS[fuel].max); }
function fuelStock(fuel) { return fuel === 'foin' ? S.hay : fuel === 'charbon' ? S.coal : Infinity; }
function wantsHeat(f) {
  if (f.molten >= moltenCap()) return false;
  return f.hopper >= npi() || (lvl('convoyeur') > 0 && S.needles[f.metal] + f.hopper >= npi());
}
function addFuel(f, manual) {
  if (f.fuel === 'elec') { f.elecOn = !f.elecOn; return true; }
  const F = FUELS[f.fuel];
  if (fuelStock(f.fuel) < 1) { if (manual) toast(`Plus de ${F.name.toLowerCase()} en stock !`); return false; }
  if (f.burn > 0 && f.burnType !== f.fuel) f.burn = 0;
  if (f.burn > F.burn * 5 - 1) { if (manual) toast('Le foyer est déjà plein'); return false; }
  if (f.fuel === 'foin') S.hay--; else S.coal--;
  f.burnType = f.fuel;
  f.burn += F.burn;
  return true;
}
function loadFurnace(f) {
  const q = Math.min(hopperCap() - f.hopper, S.needles[f.metal]);
  if (q <= 0) return 0;
  S.needles[f.metal] -= q; f.hopper += q;
  return q;
}
function castFurnace(f) {
  if (f.molten <= 0) return 0;
  const q = f.molten, m = f.moltenMetal;
  S.ingots[m] += q; S.stats.ingots += q; S.stats.made[m] += q;
  f.molten = 0;
  return q;
}
function setMetal(f, m) {
  if (f.metal === m) return;
  castFurnace(f);
  S.needles[f.metal] += f.hopper;
  f.hopper = 0; f.prog = 0; f.metal = m;
}
function setFuel(f, k) {
  if (!TIERS[f.tier].fuels.includes(k)) return;
  f.fuel = k;
  if (k !== 'elec') f.elecOn = false;
}
function tierUp(f) {
  const t = TIERS[f.tier + 1];
  if (!t || S.money < t.cost) return false;
  S.money -= t.cost;
  f.tier++;
  return true;
}
function autoSelect(f) {
  const tier = TIERS[f.tier];
  let best = null, bestV = -1;
  for (const m of METAL_KEYS) {
    if (!S.seen[m]) continue;
    const have = S.needles[m] + (f.metal === m ? f.hopper : 0);
    if (have < npi()) continue;
    const ok = tier.fuels.some(fu => reachMax(f, fu) >= METALS[m].melt + 25 && (fu === 'elec' ? S.money > 50 : fuelStock(fu) > 0 || (fu === 'charbon' && lvl('charbonnier'))));
    if (!ok) continue;
    const v = METALS[m].price * S.market[m].m;
    if (v > bestV) { bestV = v; best = m; }
  }
  if (best && best !== f.metal && f.hopper < npi()) setMetal(f, best);
  const melt = METALS[f.metal].melt;
  if (reachMax(f) < melt + 25 || (f.fuel !== 'elec' && fuelStock(f.fuel) < 1 && f.burn <= 0)) {
    for (const fu of ['foin', 'charbon', 'elec']) {
      if (!tier.fuels.includes(fu) || reachMax(f, fu) < melt + 25) continue;
      if (fu === 'foin' && S.hay < 1) continue;
      if (fu === 'charbon' && S.coal < 1 && !lvl('charbonnier')) continue;
      setFuel(f, fu); break;
    }
  }
}
function tickFurnace(f, dt) {
  const tier = TIERS[f.tier];
  // combustible
  if (lvl('souffleur')) {
    const want = wantsHeat(f);
    if (f.fuel === 'elec') f.elecOn = want;
    else if (want && f.burn < 1.5) addFuel(f, false);
  }
  let src = 0;
  if (f.fuel === 'elec' && f.elecOn && tier.fuels.includes('elec')) {
    const c = ELEC_COST * f.tier / 3 * dt;
    if (S.money >= c) { S.money -= c; src = FUELS.elec.max; }
    else f.elecOn = false;
  } else if (f.burn > 0) {
    f.burn = Math.max(0, f.burn - dt);
    src = FUELS[f.burnType].max;
  }
  const target = src ? Math.min(tier.max, src) : 20;
  const tau = 6 / (1 + 0.35 * lvl('isolation'));
  f.temp += (target - f.temp) * (1 - Math.exp(-dt / tau));
  // convoyeur
  const lr = loadRate(lvl('convoyeur'));
  if (lr > 0) {
    f.loadAcc += lr * dt;
    const q = Math.min(Math.floor(f.loadAcc), hopperCap() - f.hopper, S.needles[f.metal]);
    if (q > 0) { S.needles[f.metal] -= q; f.hopper += q; }
    f.loadAcc = Math.min(f.loadAcc - Math.max(q, 0), lr);
  }
  // fusion
  const melt = METALS[f.metal].melt;
  if (f.temp >= melt && f.hopper >= npi() && f.molten < moltenCap()) {
    if (f.molten > 0 && f.moltenMetal !== f.metal) castFurnace(f);
    f.prog += dt * tier.speed * (1 + 0.25 * lvl('vitesse')) / 5;
    while (f.prog >= 1 && f.hopper >= npi() && f.molten < moltenCap()) {
      f.prog -= 1; f.hopper -= npi(); f.molten++; f.moltenMetal = f.metal;
    }
    if (f.hopper < npi() || f.molten >= moltenCap()) f.prog = Math.min(f.prog, 0.999);
  }
  if (lvl('coulee') && f.molten > 0) castFurnace(f);
}
function furnaceStatus(f) {
  const M = METALS[f.metal], melt = M.melt;
  if (f.molten >= moltenCap()) return ['warn', 'Creuset plein : coule les lingots !'];
  if (f.hopper < npi()) {
    if (S.needles[f.metal] + f.hopper < npi()) return ['warn', `Pas assez d’aiguilles en ${M.name.toLowerCase()} (${npi()} par lingot)`];
    return ['', `Trémie vide : charge des aiguilles (${npi()} par lingot)`];
  }
  if (reachMax(f) < melt) return ['warn', `${FUELS[f.fuel].name} + ${TIERS[f.tier].name} : max ${reachMax(f)} °C, il faut ${melt} °C`];
  const heating = (f.fuel === 'elec' && f.elecOn) || (f.fuel !== 'elec' && f.burn > 0);
  if (f.temp < melt) return heating ? ['ok', `Chauffe… ${Math.round(f.temp)} / ${melt} °C`] : ['warn', 'Le four est froid : ajoute du combustible'];
  return ['ok', `Fusion du ${M.name.toLowerCase()} en cours…`];
}

// ============================================================ marché
function basePrice(k) { return METALS[k] ? METALS[k].price : ALLOYS[k] ? ALLOYS[k].price : HAY_PRICE; }
function ratio(k) { const mk = S.market[k]; let r = mk.m * mk.sat; if (S.event && S.event.k === k) r *= S.event.x; return r; }
function price(k) { return basePrice(k) * ratio(k) * sellMult(); }
function stock(k) { return METALS[k] ? S.ingots[k] : ALLOYS[k] ? S.alloys[k] : S.hay; }
function setStock(k, v) { if (METALS[k]) S.ingots[k] = v; else if (ALLOYS[k]) S.alloys[k] = v; else S.hay = v; }
function sell(k, q) {
  q = Math.min(Math.floor(q), stock(k));
  if (q <= 0) return 0;
  const mk = S.market[k];
  const steps = Math.min(q, 300), per = q / steps;
  let total = 0;
  for (let i = 0; i < steps; i++) { total += price(k) * per; mk.sat = Math.max(0.35, mk.sat - 0.005 * per); }
  setStock(k, stock(k) - q);
  gainMoney(total);
  S.stats.sold += q;
  sfx('cash');
  return total;
}
function scrapPrice(m) { return METALS[m].price / 10 * 0.35 * S.market[m].m * sellMult(); }
function sellScrap(m) {
  const q = S.needles[m];
  if (q <= 0) return 0;
  const t = q * scrapPrice(m);
  S.needles[m] = 0; gainMoney(t); sfx('cash');
  return t;
}
function buyCoal(q) {
  const c = q * COAL_PRICE;
  if (S.money < c) return false;
  S.money -= c; S.coal += q; S.stats.coalBought += q;
  return true;
}
const hist = {};
MARKET_KEYS.forEach(k => { hist[k] = []; });
function gauss() { return (Math.random() + Math.random() + Math.random() - 1.5) * 1.15; }
function marketStep() {
  for (const k of MARKET_KEYS) {
    const mk = S.market[k];
    mk.m = clamp(mk.m + gauss() * 0.035 + (1 - mk.m) * 0.03, 0.55, 1.8);
    if (!offline) { const h = hist[k]; h.push(ratio(k)); if (h.length > 50) h.shift(); }
  }
}
function unlockedMarket(k) {
  if (METALS[k]) return S.seen[k];
  if (ALLOYS[k]) return alloyUnlocked(k);
  return true;
}
function alloyUnlocked(k) { return Object.keys(ALLOYS[k].ing).every(m => S.stats.made[m] > 0); }
function canForge(k, q = 1) {
  const A = ALLOYS[k];
  if (S.coal < A.coal * q) return false;
  return Object.entries(A.ing).every(([m, n]) => S.ingots[m] >= n * q);
}
function forge(k, q) {
  const A = ALLOYS[k];
  let n = 0;
  while (n < q && canForge(k)) {
    for (const [m, c] of Object.entries(A.ing)) S.ingots[m] -= c;
    S.coal -= A.coal;
    S.alloys[k]++; n++;
  }
  S.stats.alloys += n;
  return n;
}
function maxForge(k) {
  const A = ALLOYS[k];
  let m = A.coal ? Math.floor(S.coal / A.coal) : Infinity;
  for (const [mm, c] of Object.entries(A.ing)) m = Math.min(m, Math.floor(S.ingots[mm] / c));
  return m;
}

// ============================================================ boucle de jeu
let offline = false, dirty = true;
let magAcc = 0, goatAcc = 0, visBudget = 20;
let accMarket = 0, accSec = 0, accSel = 0;
function tickCore(dt) {
  S.stats.time += dt;
  // électro-aimant
  const mr = magRate(lvl('electro'));
  if (mr > 0 && S.botte.needlesLeft > 0) {
    magAcc += mr * dt;
    let n = Math.floor(magAcc);
    magAcc -= n;
    if (!offline) {
      visBudget = Math.min(visBudget + 18 * dt, 18);
      while (n > 0 && visBudget >= 1) {
        const o = topNeedle();
        if (!o) break;
        removeObj(o, true, 'mag'); n--; visBudget--;
      }
    }
    if (n > 0) takeHiddenNeedles(n);
  }
  // chèvre
  const gr = goatRate(lvl('chevre'));
  if (gr > 0 && S.botte.hayLeft > 0) {
    goatAcc += gr * dt;
    while (goatAcc >= 1 && S.botte.hayLeft > 0) {
      goatAcc -= 1;
      const o = topHay();
      if (o) { removeObj(o, true, 'goat'); if (!offline) floater('🐐 +1 🌾', o.x * W, o.y * H, '#f5dc7a'); }
      else { S.botte.hayLeft--; gainHay(1); checkBotte(); }
    }
  } else goatAcc = Math.min(goatAcc, 1);
  // botte vidée : on passe à la suivante
  if (S.botte.done && S.botte.needlesLeft <= 0 && S.botte.hayLeft <= 0) nextBotte();
  // fours
  for (const f of S.furnaces) tickFurnace(f, dt);
  accSel += dt;
  if (accSel >= 2) { accSel = 0; if (lvl('selecteur')) S.furnaces.forEach(autoSelect); }
  // marché
  for (const k of MARKET_KEYS) { const mk = S.market[k]; mk.sat = Math.min(1, mk.sat + 0.012 * dt); }
  accMarket += dt;
  if (accMarket >= 2) { accMarket -= 2; marketStep(); }
  if (S.event && S.stats.time > S.event.until) S.event = null;
  S.nextEvent -= dt;
  if (S.nextEvent <= 0) {
    S.nextEvent = rand(80, 160);
    const ks = MARKET_KEYS.filter(k => k !== 'foin' && unlockedMarket(k));
    const k = ks[Math.floor(Math.random() * ks.length)];
    if (k) {
      S.event = { k, x: rand(1.7, 2.3), until: S.stats.time + 30 };
      const n = (METALS[k] || ALLOYS[k]).name;
      toast(`📈 Ruée sur ${n.toLowerCase()} ! Prix ×${S.event.x.toFixed(1).replace('.', ',')} pendant 30 s`, 'gold');
    }
  }
  // automatisations à la seconde
  accSec += dt;
  if (accSec >= 1) {
    accSec -= 1;
    if (lvl('charbonnier') && S.coal < 15 && S.money >= 20 * COAL_PRICE * 1.5) buyCoal(20);
    if (lvl('forgeauto')) for (const k of ALLOY_KEYS) if (S.settings.forge[k] && alloyUnlocked(k)) forge(k, 5);
    if (lvl('courtier')) {
      const keys = [...METAL_KEYS, ...ALLOY_KEYS];
      if (S.settings.sellHay) keys.push('foin');
      for (const k of keys) {
        let it = 0;
        while (stock(k) > 0 && ratio(k) * 100 >= S.settings.sellPct && it++ < 40) {
          const before = offline; offline = true;          // pas de son à chaque vente auto
          sell(k, Math.max(1, Math.ceil(stock(k) / 15)));
          offline = before;
        }
      }
    }
    checkQuest();
  }
}
function checkQuest() {
  const q = QUESTS[S.quest];
  if (!q) return;
  const [c, g] = q.g();
  if (c >= g) {
    if (q.r) gainMoney(q.r);
    S.quest++;
    toast(`✅ Objectif atteint : ${q.t}${q.r ? ' (+' + eur(q.r) + ')' : ''}`, 'gold');
    sfx('win');
  }
}
function simulate(sec) {
  const snap = { money: S.money, earned: S.stats.earned, needles: S.stats.needles, hay: S.stats.hay, ingots: S.stats.ingots, bottes: S.stats.bottes };
  offline = true;
  let t = sec;
  while (t > 0) { const d = Math.min(1, t); tickCore(d); t -= d; }
  offline = false;
  dirty = true;
  return {
    money: S.stats.earned - snap.earned, needles: S.stats.needles - snap.needles, hay: S.stats.hay - snap.hay,
    ingots: S.stats.ingots - snap.ingots, bottes: S.stats.bottes - snap.bottes,
  };
}
function showOffline(sec, r) {
  if (r.needles + r.hay + r.ingots + r.money < 1) return;
  const h = Math.floor(sec / 3600), m = Math.floor(sec % 3600 / 60);
  modal('Pendant ton absence…', `<p class="muted">Tes machines ont tourné ${h ? h + ' h ' : ''}${m} min.</p>
    <div class="kv"><span>🪡 Aiguilles extraites</span><b>${fmt(r.needles)}</b></div>
    <div class="kv"><span>🌾 Foin trouvé</span><b>${fmt(r.hay)}</b></div>
    <div class="kv"><span>🧱 Lingots coulés</span><b>${fmt(r.ingots)}</b></div>
    <div class="kv"><span>🪡 Bottes terminées</span><b>${fmt(r.bottes)}</b></div>
    <div class="kv"><span>💰 Argent gagné</span><b>${eur(r.money)}</b></div>`, [{ label: 'Super !' }]);
}

// ============================================================ prestige
function prestige() {
  const stars = starsPotential();
  const keep = {
    stars, quest: S.quest, settings: S.settings,
    stats: Object.assign({}, S.stats, { prestiges: S.stats.prestiges + 1, earned: 0 }),
  };
  S = newState(keep);
  newBotte(1);
  save();
  renderAll(true);
  toast(`⭐ Ferme vendue ! Tu as maintenant ${stars} étoile${stars > 1 ? 's' : ''} de foin.`, 'gold');
}

// ============================================================ rendu de la botte
const cvs = $('#pile');
const ctx = cvs.getContext('2d');
let W = 300, H = 400, U = 300, DPR = 1;
const flies = [], floaters = [];
let stunUntil = 0;
const shadeCache = {};
function hex2rgb(h) { const n = parseInt(h.slice(1), 16); return [n >> 16 & 255, n >> 8 & 255, n & 255]; }
function shade(hex, f) {
  const key = hex + f;
  if (shadeCache[key]) return shadeCache[key];
  const [r, g, b] = hex2rgb(hex), bg = [22, 25, 31];
  return (shadeCache[key] = `rgb(${Math.round(bg[0] + (r - bg[0]) * f)},${Math.round(bg[1] + (g - bg[1]) * f)},${Math.round(bg[2] + (b - bg[2]) * f)})`);
}
function resize() {
  const r = cvs.parentElement.getBoundingClientRect();
  DPR = Math.min(window.devicePixelRatio || 1, 2.5);
  W = Math.max(50, r.width); H = Math.max(50, r.height);
  U = Math.min(W, H * 0.8);
  cvs.width = Math.round(W * DPR); cvs.height = Math.round(H * DPR);
  dirty = true;
}
function line(x1, y1, x2, y2) { ctx.beginPath(); ctx.moveTo(x1, y1); ctx.lineTo(x2, y2); ctx.stroke(); }
function drawNeedle(o, f, alpha) {
  const M = METALS[o.m];
  const cx = o.x * W, cy = o.y * H, hl = o.l * U / 2;
  const ca = Math.cos(o.a), sa = Math.sin(o.a);
  const dx = ca * hl, dy = sa * hl;
  ctx.globalAlpha = alpha;
  ctx.strokeStyle = 'rgba(0,0,0,0.5)'; ctx.lineWidth = 3.4;
  line(cx - dx + 1.4, cy - dy + 2, cx + dx + 1.4, cy + dy + 2);
  ctx.strokeStyle = shade(M.color, f); ctx.lineWidth = 2.5;
  line(cx - dx, cy - dy, cx + dx, cy + dy);
  ctx.strokeStyle = shade(M.hi, f); ctx.lineWidth = 0.9;
  line(cx - dx * 0.92 - sa * 0.6, cy - dy * 0.92 + ca * 0.6, cx + dx * 0.8 - sa * 0.6, cy + dy * 0.8 + ca * 0.6);
  // chas (trou de l'aiguille)
  const ex = cx + dx * 0.86 * o.e, ey = cy + dy * 0.86 * o.e;
  ctx.strokeStyle = 'rgba(10,12,16,0.85)'; ctx.lineWidth = 1.1;
  line(ex - dx * 0.06, ey - dy * 0.06, ex + dx * 0.06, ey + dy * 0.06);
  ctx.globalAlpha = 1;
}
function hayPts(o) {
  const cx = o.x * W, cy = o.y * H, hl = o.l * U / 2;
  const ca = Math.cos(o.a), sa = Math.sin(o.a);
  const bend = o.c * hl * 2;
  return [cx - ca * hl, cy - sa * hl, cx - sa * bend, cy + ca * bend, cx + ca * hl, cy + sa * hl, ca, sa];
}
function curve(p, ox, oy) { ctx.beginPath(); ctx.moveTo(p[0] + ox, p[1] + oy); ctx.quadraticCurveTo(p[2] + ox, p[3] + oy, p[4] + ox, p[5] + oy); ctx.stroke(); }
function drawHay(o, f, alpha) {
  const p = hayPts(o);
  ctx.globalAlpha = alpha;
  ctx.strokeStyle = 'rgba(0,0,0,0.45)'; ctx.lineWidth = 4.4; curve(p, 1.4, 2);
  ctx.strokeStyle = shade('#c99a2e', f); ctx.lineWidth = 3.6; curve(p, 0, 0);
  ctx.strokeStyle = shade('#f7df86', f); ctx.lineWidth = 1.2; curve(p, -p[7] * 0.8, p[6] * 0.8);
  ctx.globalAlpha = 1;
}
function render(now) {
  ctx.setTransform(DPR, 0, 0, DPR, 0, 0);
  const g = ctx.createRadialGradient(W / 2, H * 0.45, 10, W / 2, H / 2, Math.max(W, H) * 0.75);
  g.addColorStop(0, '#30353f'); g.addColorStop(1, '#101216');
  ctx.fillStyle = g; ctx.fillRect(0, 0, W, H);
  ctx.lineCap = 'round';
  const n = S.pile.length;
  let anim = false;
  for (let i = 0; i < n; i++) {
    const o = S.pile[i];
    const f = 0.42 + 0.58 * Math.pow((i + 1) / n, 0.8);
    let a = 1;
    if (o.b) { a = (now - o.b) / 350; if (a >= 1) { a = 1; delete o.b; } else anim = true; }
    o.h ? drawHay(o, f, a) : drawNeedle(o, f, a);
  }
  if (lvl('lunettes')) {
    const pulse = 0.25 + 0.15 * Math.sin(now / 300);
    ctx.globalAlpha = pulse;
    ctx.strokeStyle = '#ffe27a'; ctx.lineWidth = 6;
    for (const o of S.pile) if (o.h) curve(hayPts(o), 0, 0);
    ctx.globalAlpha = 1;
    anim = true;
  }
  if (!n) {
    ctx.fillStyle = '#9aa3b1'; ctx.font = '600 16px system-ui'; ctx.textAlign = 'center';
    ctx.fillText('Botte vide !', W / 2, H / 2);
  }
  // aiguilles qui s'envolent
  for (let i = flies.length - 1; i >= 0; i--) {
    const fl = flies[i];
    const t = (now - fl.t0) / fl.d;
    if (t >= 1) { flies.splice(i, 1); continue; }
    anim = true;
    const e = t * t * (3 - 2 * t);
    const o = fl.o;
    const ox = o.x, oy = o.y, oa = o.a, ol = o.l;
    o.x = ox + (fl.tx - ox) * e; o.y = oy + (fl.ty - oy) * e - Math.sin(t * Math.PI) * 0.08;
    o.a = oa + e * fl.spin; o.l = ol * (1 - 0.6 * e);
    o.h ? drawHay(o, 1, 1 - t * 0.7) : drawNeedle(o, 1, 1 - t * 0.7);
    o.x = ox; o.y = oy; o.a = oa; o.l = ol;
  }
  ctx.textAlign = 'center';
  for (let i = floaters.length - 1; i >= 0; i--) {
    const fl = floaters[i];
    const t = (now - fl.t0) / 900;
    if (t >= 1) { floaters.splice(i, 1); continue; }
    anim = true;
    ctx.globalAlpha = 1 - t;
    ctx.font = '700 17px system-ui';
    ctx.fillStyle = '#000'; ctx.fillText(fl.s, fl.x + 1, fl.y - t * 40 + 1);
    ctx.fillStyle = fl.c; ctx.fillText(fl.s, fl.x, fl.y - t * 40);
  }
  ctx.globalAlpha = 1;
  if (now < stunUntil) {
    anim = true;
    const v = ctx.createRadialGradient(W / 2, H / 2, Math.min(W, H) * 0.3, W / 2, H / 2, Math.max(W, H) * 0.7);
    v.addColorStop(0, 'rgba(255,0,0,0)'); v.addColorStop(1, `rgba(255,40,40,${0.35 * (stunUntil - now) / 900})`);
    ctx.fillStyle = v; ctx.fillRect(0, 0, W, H);
  }
  dirty = anim;
}
function addFly(o, who) {
  if (flies.length > 40) flies.shift();
  const tx = o.h ? 0.12 : 0.5 + rand(-0.3, 0.3);
  const ty = o.h ? -0.05 : 1.08;
  flies.push({ o: Object.assign({}, o, { b: 0 }), t0: performance.now(), d: who === 'mag' ? 600 : 450, tx, ty, spin: rand(-2, 2) });
  dirty = true;
}
function floater(s, x, y, c) { if (floaters.length > 12) floaters.shift(); floaters.push({ s, x, y, c: c || '#fff', t0: performance.now() }); dirty = true; }

// ============================================================ interaction botte
function segDist(px, py, o) {
  const cx = o.x * W, cy = o.y * H, hl = o.l * U / 2;
  const dx = Math.cos(o.a) * hl, dy = Math.sin(o.a) * hl;
  let t = ((px - cx) * dx + (py - cy) * dy) / (hl * hl);
  t = clamp(t, -1, 1);
  let qx = cx + dx * t, qy = cy + dy * t;
  if (o.h) { const bend = o.c * hl * (1 - t * t); qx += -Math.sin(o.a) * bend; qy += Math.cos(o.a) * bend; }
  return Math.hypot(qx - px, qy - py);
}
function grab(px, py) {
  const now = performance.now();
  if (now < stunUntil) return;
  const tol = 13;
  let top = null;
  for (let i = S.pile.length - 1; i >= 0; i--) if (segDist(px, py, S.pile[i]) < tol) { top = S.pile[i]; break; }
  if (!top) return;
  if (top.h) {
    removeObj(top, true, 'hand');
    sfx('hay'); vib(30);
    floater('+1 🌾', px, py - 10, '#f5dc7a');
    return;
  }
  if (Math.random() < 0.09 * Math.pow(0.5, lvl('gants'))) {
    stunUntil = now + 900; S.stats.pricks++;
    sfx('prick'); vib(140);
    floater('Aïe ! 🩸', px, py - 10, '#ff6b6b');
    dirty = true;
    return;
  }
  const extra = lvl('aimant');
  const grabbed = [top];
  if (extra > 0) {
    const rad = 24 + 3 * Math.min(extra, 12);
    for (let i = S.pile.length - 1; i >= 0 && grabbed.length <= extra; i--) {
      const q = S.pile[i];
      if (!q.h && q !== top && segDist(px, py, q) < rad) grabbed.push(q);
    }
  }
  for (const o of grabbed) removeObj(o, true, 'hand');
  sfx('needle'); vib(8);
}
let pointerDown = false, lastGrab = 0;
function canvasPos(e) { const r = cvs.getBoundingClientRect(); return [e.clientX - r.left, e.clientY - r.top]; }
cvs.addEventListener('pointerdown', e => {
  audio();
  pointerDown = true;
  try { cvs.setPointerCapture(e.pointerId); } catch (err) {}
  const [x, y] = canvasPos(e);
  grab(x, y); lastGrab = performance.now();
  e.preventDefault();
});
cvs.addEventListener('pointermove', e => {
  if (!pointerDown) return;
  const n = performance.now();
  if (n - lastGrab < 65) return;
  lastGrab = n;
  const [x, y] = canvasPos(e);
  grab(x, y);
});
['pointerup', 'pointercancel', 'pointerleave'].forEach(t => cvs.addEventListener(t, () => { pointerDown = false; }));

// ============================================================ interface
let tab = 'botte';
const panelSig = {};
function chip(m, cls, attrs) {
  const M = METALS[m];
  return `<button class="chip ${cls || ''}" ${attrs || ''}><span class="dot" style="background:${M.color}"></span>${M.name}</button>`;
}

const PANELS = {
  fonderie: {
    sig: () => JSON.stringify([S.furnaces.map(f => [f.tier, f.metal, f.fuel]), S.seen, lvl('souffleur'), lvl('coulee'), lvl('convoyeur') > 0,
      lvl('selecteur'), lvl('forgeauto'), ALLOY_KEYS.map(alloyUnlocked), S.settings.forge]),
    render() {
      let h = `<h2>🔥 Fonderie</h2><p class="sub">Charge les aiguilles dans la trémie, chauffe le four au-dessus du point de fusion du métal, puis coule les lingots. ${npi()} aiguilles = 1 lingot.</p>`;
      S.furnaces.forEach((f, i) => {
        const t = TIERS[f.tier], nt = TIERS[f.tier + 1], M = METALS[f.metal];
        const markPos = clamp(M.melt / t.max * 100, 0, 100);
        h += `<div class="card furnace" data-fc="${i}">
          <div class="row"><div class="ttl">${t.icon} ${t.name} <span class="muted small">n°${i + 1} · max ${t.max} °C</span></div>
          ${nt ? `<button class="btn sm blue" data-a="tierup" data-i="${i}" data-d="tu${i}">⬆ ${nt.name}<br><small>${eur(nt.cost)}</small></button>` : '<span class="tag">niveau max</span>'}</div>
          <div class="thermo"><div class="fill" data-w="tw${i}"></div><div class="mark" style="left:${markPos}%"></div><span data-b="temp${i}"></span></div>
          <div class="small muted">Métal à fondre${lvl('selecteur') ? '<span class="tag">auto</span>' : ''}</div>
          <div class="chips">${METAL_KEYS.filter(m => S.seen[m]).map(m => chip(m, (m === f.metal ? 'sel' : '') + (reachMax(f, t.fuels[t.fuels.length - 1]) < METALS[m].melt ? ' cold' : ''),
            `data-a="metal" data-i="${i}" data-k="${m}"`).replace('</button>', ` <small class="muted">${METALS[m].melt}°</small></button>`)).join('')}</div>
          <div class="small muted">Combustible${lvl('souffleur') ? '<span class="tag">auto</span>' : ''}</div>
          <div class="chips">${t.fuels.map(k => `<button class="chip ${k === f.fuel ? 'sel' : ''}" data-a="fuel" data-i="${i}" data-k="${k}">${FUELS[k].icon} ${FUELS[k].name} <small class="muted">${Math.min(t.max, FUELS[k].max)}°</small></button>`).join('')}</div>
          <div class="row"><span>🪣 Trémie : <b data-b="hop${i}"></b>${lvl('convoyeur') ? '<span class="tag">convoyeur</span>' : ''}</span>
            <button class="btn sm" data-a="load" data-i="${i}" data-d="ld${i}">Charger</button></div>
          <div class="row"><span>${FUELS[f.fuel].icon} <b data-b="fu${i}"></b></span>
            <button class="btn sm fire" data-a="addfuel" data-i="${i}" data-d="af${i}" data-b="afl${i}"></button></div>
          <div class="bar"><div class="fill fire" data-w="pg${i}"></div></div>
          <div class="row"><span>🫕 En fusion : <b data-b="mo${i}"></b>${lvl('coulee') ? '<span class="tag">coulée auto</span>' : ''}</span>
            <button class="btn sm gold" data-a="cast" data-i="${i}" data-d="ca${i}">Couler</button></div>
          <div class="status" data-b="st${i}" data-c="stc${i}"></div>
        </div>`;
      });
      const al = ALLOY_KEYS.filter(alloyUnlocked);
      if (al.length) {
        h += `<h3>⚒️ Forge d’alliages</h3><p class="sub">Les alliages se vendent plus cher que leurs ingrédients.</p>`;
        for (const k of al) {
          const A = ALLOYS[k];
          const ing = Object.entries(A.ing).map(([m, n]) => `${n} lingot ${METALS[m].name.toLowerCase()}`).concat(A.coal ? [`${A.coal} charbon`] : []).join(' + ');
          h += `<div class="card"><div class="row"><div><div class="ttl"><span class="dot" style="display:inline-block;background:${A.color}"></span> ${A.name}</div>
            <div class="small muted">${ing} · base ${eur(A.price)}</div><div class="small">En stock : <b data-b="as_${k}"></b></div></div>
            <div style="display:flex;gap:6px;flex-direction:column">
            <button class="btn sm gold" data-a="forge1" data-k="${k}" data-d="fd_${k}">Forger 1</button>
            <button class="btn sm" data-a="forgemax" data-k="${k}" data-d="fd_${k}">Max</button></div></div>
            ${lvl('forgeauto') ? `<div class="switch small"><span>Forge automatique</span><button class="btn sm ${S.settings.forge[k] ? 'green' : ''}" data-a="forgetoggle" data-k="${k}">${S.settings.forge[k] ? 'Activée' : 'Désactivée'}</button></div>` : ''}
            </div>`;
        }
      }
      return h;
    },
    binds(b) {
      S.furnaces.forEach((f, i) => {
        const t = TIERS[f.tier], nt = TIERS[f.tier + 1];
        b['temp' + i] = `${Math.round(f.temp)} °C  /  fusion ${METALS[f.metal].melt} °C`;
        b['tw' + i] = clamp(f.temp / t.max * 100, 0, 100) + '%';
        b['hop' + i] = `${fmt(f.hopper)} / ${fmt(hopperCap())} (stock ${fmt(S.needles[f.metal])})`;
        b['ld' + i] = S.needles[f.metal] <= 0 || f.hopper >= hopperCap();
        if (f.fuel === 'elec') {
          b['fu' + i] = f.elecOn ? `Électricité : allumée (${fmt(ELEC_COST * f.tier / 3, 1)} €/s)` : 'Électricité : éteinte';
          b['afl' + i] = f.elecOn ? '⚡ Éteindre' : '⚡ Allumer';
          b['af' + i] = false;
        } else {
          b['fu' + i] = f.burn > 0 ? `${FUELS[f.burnType].name} : ${Math.ceil(f.burn)} s` : 'Foyer éteint';
          b['afl' + i] = `+1 ${FUELS[f.fuel].name.toLowerCase()} (${fmt(fuelStock(f.fuel))})`;
          b['af' + i] = fuelStock(f.fuel) < 1;
        }
        b['pg' + i] = (f.prog * 100) + '%';
        b['mo' + i] = `${f.molten} / ${moltenCap()} lingot${f.molten > 1 ? 's' : ''}`;
        b['ca' + i] = f.molten <= 0;
        if (nt) b['tu' + i] = S.money < nt.cost;
        const [c, s] = furnaceStatus(f);
        b['st' + i] = s; b['stc' + i] = 'status ' + c;
      });
      for (const k of ALLOY_KEYS) { b['as_' + k] = fmt(S.alloys[k]); b['fd_' + k] = !canForge(k); }
    },
  },

  marche: {
    sig: () => JSON.stringify([MARKET_KEYS.map(unlockedMarket), lvl('courtier'), S.settings.sellHay, !!S.event]),
    render() {
      let h = `<h2>📈 Marché</h2><p class="sub">Les cours fluctuent. Vendre beaucoup d’un coup fait baisser le prix, qui remonte ensuite doucement.</p>`;
      h += `<div class="event" data-b="ev" data-hide="noev"></div>`;
      h += `<div class="card">`;
      for (const k of MARKET_KEYS) {
        if (!unlockedMarket(k)) continue;
        const isM = !!METALS[k], isA = !!ALLOYS[k];
        const name = isM ? 'Lingot de ' + METALS[k].name.toLowerCase() : isA ? ALLOYS[k].name : 'Brin de foin';
        const color = isM ? METALS[k].color : isA ? ALLOYS[k].color : '#e8c547';
        h += `<div class="mrow"><span class="dot" style="background:${color}"></span>
          <div class="mname">${name}<small data-b="sk_${k}"></small></div>
          <canvas class="spark" data-k="${k}" width="140" height="56"></canvas>
          <div class="mprice"><b data-b="p_${k}"></b><small data-b="r_${k}" data-c="rc_${k}"></small></div>
          <div class="mbtns"><button class="btn sm" data-a="sell" data-k="${k}" data-q="1" data-d="ds_${k}">Vendre 1</button>
          <button class="btn sm" data-a="sell" data-k="${k}" data-q="10" data-d="ds_${k}">10</button>
          <button class="btn sm gold" data-a="sell" data-k="${k}" data-q="all" data-d="ds_${k}">Tout</button></div></div>`;
      }
      h += `</div>`;
      h += `<h3>⚫ Charbon</h3><div class="card"><div class="row"><div>Prix : <b>${eur(COAL_PRICE)}</b> l’unité<div class="small muted">Monte à ${FUELS.charbon.max} °C (le foin : ${FUELS.foin.max} °C).</div></div></div>
        <div class="row" style="justify-content:flex-end">
        <button class="btn sm" data-a="coal" data-q="1" data-d="dc1">+1</button>
        <button class="btn sm" data-a="coal" data-q="10" data-d="dc10">+10</button>
        <button class="btn sm gold" data-a="coal" data-q="100" data-d="dc100">+100</button></div></div>`;
      h += `<h3>🔩 Ferraille (aiguilles en vrac)</h3><div class="card">`;
      for (const m of METAL_KEYS) {
        if (!S.seen[m]) continue;
        h += `<div class="row"><span><span class="dot" style="display:inline-block;background:${METALS[m].color}"></span> ${METALS[m].name} <small class="muted" data-b="sc_${m}"></small></span>
          <button class="btn sm" data-a="scrap" data-k="${m}" data-d="dsc_${m}">Vendre</button></div>`;
      }
      h += `<p class="small muted" style="margin:8px 0 0">Bien moins rentable que de les fondre en lingots !</p></div>`;
      if (lvl('courtier')) {
        h += `<h3>🤵 Courtier automatique</h3><div class="card">
          <div class="row"><span>Vendre quand le cours ≥</span><b data-b="pct"></b></div>
          <input type="range" min="60" max="180" step="5" value="${S.settings.sellPct}" data-input="sellPct">
          <div class="switch"><span>Vendre aussi le foin</span><button class="btn sm ${S.settings.sellHay ? 'green' : ''}" data-a="sellhay">${S.settings.sellHay ? 'Oui' : 'Non'}</button></div></div>`;
      }
      return h;
    },
    binds(b) {
      b.noev = !S.event;
      if (S.event) {
        const n = (METALS[S.event.k] || ALLOYS[S.event.k]).name;
        b.ev = `📈 Ruée sur ${n.toLowerCase()} : prix ×${S.event.x.toFixed(1).replace('.', ',')} encore ${Math.ceil(S.event.until - S.stats.time)} s !`;
      }
      for (const k of MARKET_KEYS) {
        if (!unlockedMarket(k)) continue;
        const r = ratio(k);
        b['sk_' + k] = `En stock : ${fmt(stock(k))}`;
        b['p_' + k] = eur(price(k));
        b['r_' + k] = (r >= 1 ? '▲ ' : '▼ ') + pct(r);
        b['rc_' + k] = r >= 1 ? 'up' : 'down';
        b['ds_' + k] = stock(k) <= 0;
      }
      b.dc1 = S.money < COAL_PRICE; b.dc10 = S.money < COAL_PRICE * 10; b.dc100 = S.money < COAL_PRICE * 100;
      for (const m of METAL_KEYS) {
        if (!S.seen[m]) continue;
        b['sc_' + m] = `${fmt(S.needles[m])} × ${eur(scrapPrice(m))}`;
        b['dsc_' + m] = S.needles[m] <= 0;
      }
      b.pct = S.settings.sellPct + ' %';
    },
  },

  atelier: {
    sig: () => JSON.stringify([S.up, UPG.map(u => !u.req || u.req())]),
    render() {
      let h = `<h2>🛠️ Atelier</h2><p class="sub">Améliore tes outils et automatise toute la chaîne : extraction, fonderie, vente.</p>`;
      let locked = 0;
      for (const cat of UPG_CATS) {
        const list = UPG.filter(u => u.cat === cat);
        const vis = list.filter(u => !u.req || u.req() || lvl(u.id) > 0);
        locked += list.length - vis.length;
        if (!vis.length) continue;
        h += `<h3>${cat}</h3>`;
        for (const u of vis) {
          const l = lvl(u.id), maxed = l >= u.max;
          h += `<div class="card upg"><div class="uico">${u.icon}</div>
            <div class="uname">${u.name}<small>${u.max > 1 ? `niv. ${l}/${u.max}` : maxed ? '✓ acquis' : ''}</small></div>
            ${maxed ? '<span class="tag">max</span>' : `<button class="btn gold" data-a="buy" data-k="${u.id}" data-d="ub_${u.id}">${eur(upCost(u))}</button>`}
            <div class="udesc">${u.desc}${u.eff && l > 0 ? `<div class="ueff">Actuel : ${u.eff(l)}</div>` : ''}</div></div>`;
        }
      }
      if (locked) h += `<div class="locked">🔒 ${locked} amélioration${locked > 1 ? 's' : ''} à découvrir en progressant…</div>`;
      return h;
    },
    binds(b) { for (const u of UPG) b['ub_' + u.id] = S.money < upCost(u); },
  },

  plus: {
    sig: () => JSON.stringify([S.quest, canPrestige(), S.stars, S.settings.sound, S.settings.vib, S.botte.level >= 8]),
    render() {
      const q = QUESTS[S.quest];
      let h = `<h2>📜 Objectifs & ferme</h2>`;
      h += `<h3>Objectif actuel</h3><div class="card">${q ? `<div class="ttl">${q.t}</div>
        <div class="bar"><div class="fill hay" data-w="qw"></div></div>
        <div class="row small"><span class="muted" data-b="qp"></span><span>${q.r ? 'Récompense : ' + eur(q.r) : ''}</span></div>` : '<div class="ttl">🏆 Tous les objectifs sont accomplis !</div>'}</div>`;
      h += `<h3>⭐ Vendre la ferme (prestige)</h3><div class="card">
        <p class="small muted" style="margin-top:0">Recommence de zéro mais gagne des <b>étoiles de foin</b> : chacune donne +10 % sur les ventes et +5 % à l’électro-aimant. Disponible à partir de la botte n°8.</p>
        <div class="kv"><span>Étoiles actuelles</span><b>${S.stars}</b></div>
        <div class="kv"><span>Étoiles après la vente</span><b data-b="sp"></b></div>
        <div class="row" style="justify-content:flex-end;margin-top:10px"><button class="btn gold" data-a="prestige" ${canPrestige() ? '' : 'disabled'}>Vendre la ferme</button></div></div>`;
      h += `<h3>📊 Statistiques</h3><div class="card" id="stats"></div>`;
      h += `<h3>⚙️ Réglages</h3><div class="card">
        <div class="switch"><span>🔊 Son</span><button class="btn sm ${S.settings.sound ? 'green' : ''}" data-a="toggle" data-k="sound">${S.settings.sound ? 'Activé' : 'Coupé'}</button></div>
        <div class="switch"><span>📳 Vibrations</span><button class="btn sm ${S.settings.vib ? 'green' : ''}" data-a="toggle" data-k="vib">${S.settings.vib ? 'Activées' : 'Coupées'}</button></div>
        <div class="switch"><span>🗑️ Effacer la partie</span><button class="btn sm" data-a="reset">Réinitialiser</button></div></div>`;
      h += `<h3>❓ Comment jouer</h3><div class="card help">
        <p><b>1. La botte.</b> C’est l’inverse du jeu classique : tu fouilles une botte d’<b>aiguilles</b> pour trouver des brins de <b>foin</b> ! Touche ou glisse le doigt pour retirer les aiguilles du dessus. Attention aux piqûres…</p>
        <p><b>2. Le foin.</b> Rare et précieux : c’est un combustible gratuit pour tes fours, ou il se vend au marché. Trouver tout le foin d’une botte rapporte une prime et débloque la botte suivante, plus grande et plus riche en métaux rares.</p>
        <p><b>3. La fonderie.</b> Charge les aiguilles, fais monter le four au-dessus du point de fusion (cuivre 1085 °C, fer 1538 °C, tungstène 3422 °C…), puis coule les lingots. Le foin ne dépasse pas 1150 °C : pour le fer il faut un haut fourneau et du charbon, pour le tungstène un four à arc électrique.</p>
        <p><b>4. Le marché.</b> Vends quand le cours est haut, forge des alliages plus chers, guette les ruées.</p>
        <p><b>5. Automatise tout.</b> Électro-aimant, chèvre renifleuse, convoyeur, chargeur de combustible, coulée continue, sélecteur, courtier, forge automatique : ta fonderie finit par tourner toute seule, même quand le jeu est fermé (jusqu’à 8 h).</p></div>`;
      h += `<p class="small muted" style="text-align:center">Trouve le Foin · v1.0</p>`;
      return h;
    },
    binds(b) {
      const q = QUESTS[S.quest];
      if (q) { const [c, g] = q.g(); b.qw = clamp(c / g * 100, 0, 100) + '%'; b.qp = `${fmt(Math.min(c, g))} / ${fmt(g)}`; }
      b.sp = `${Math.max(S.stars, starsPotential())}`;
      const st = $('#stats');
      if (st) {
        const s = S.stats, tm = Math.floor(s.time);
        st.innerHTML = [
          ['🪡 Aiguilles extraites', fmt(s.needles)], ['🌾 Foin trouvé', fmt(s.hay)], ['🧱 Lingots coulés', fmt(s.ingots)],
          ['⚒️ Alliages forgés', fmt(s.alloys)], ['📦 Objets vendus', fmt(s.sold)], ['🪡 Bottes terminées', fmt(s.bottes)],
          ['🏔️ Meilleure botte', 'n°' + (s.bestBotte || 1)], ['🩸 Piqûres', fmt(s.pricks)], ['💰 Gagné (cette ferme)', eur(s.earned)],
          ['💰 Gagné (au total)', eur(s.earnedAll)], ['⏱️ Temps de jeu', `${Math.floor(tm / 3600)} h ${Math.floor(tm % 3600 / 60)} min`],
        ].map(([k, v]) => `<div class="kv"><span>${k}</span><b>${v}</b></div>`).join('');
      }
    },
  },
};

function globalBinds() {
  const b = S.botte;
  const g = {
    money: eur(S.money), hay: fmt(S.hay), coal: fmt(S.coal), stars: fmt(S.stars), nostars: S.stars <= 0,
    botteName: `Botte n°${b.level}`,
    botteHay: `🌾 ${b.hayTotal - b.hayLeft} / ${b.hayTotal}`,
    botteNeedles: `🪡 ${fmt(b.needlesLeft)} aiguilles restantes`,
    botteProg: (b.needlesTotal ? (1 - b.needlesLeft / b.needlesTotal) * 100 : 0) + '%',
    noNext: !b.done,
    rates: [lvl('electro') ? `🧲 ${fmt(magRate(lvl('electro')), 1)}/s` : '', lvl('chevre') ? `🐐 ${fmt(goatRate(lvl('chevre')) * 60, 1)}/min` : ''].filter(Boolean).join(' · '),
    noUpg: !UPG.some(u => lvl(u.id) < u.max && (!u.req || u.req()) && S.money >= upCost(u)),
    noQuest: !(QUESTS[S.quest] && QUESTS[S.quest].g()[0] >= QUESTS[S.quest].g()[1]) && !canPrestige(),
  };
  let hint = '';
  if (S.stats.needles < 3) hint = '👆 Touche (ou glisse sur) les aiguilles pour les retirer. Les brins de foin sont cachés dessous !';
  else if (S.stats.hay < 1) hint = '🌾 Continue de creuser : retire les aiguilles qui recouvrent les brins jaunes.';
  else if (S.stats.ingots < 1 && S.needles.cuivre >= 10) hint = '🔥 Va à la Fonderie : charge 10 aiguilles de cuivre, mets du foin et fonds ton premier lingot !';
  else if (b.done) hint = '🎉 Tout le foin est trouvé ! Continue de vider la botte ou passe à la suivante.';
  g.hint = hint; g.noHint = !hint;
  for (const m of METAL_KEYS) g['n_' + m] = fmt(S.needles[m]);
  return g;
}
let invSig = '';
function renderInv() {
  const sig = METAL_KEYS.filter(m => S.seen[m]).join();
  if (sig === invSig) return;
  invSig = sig;
  $('#inv').innerHTML = METAL_KEYS.filter(m => S.seen[m]).map(m =>
    `<div class="chip-inv"><span class="dot" style="background:${METALS[m].color}"></span>${METALS[m].name} <b data-g="n_${m}">0</b></div>`).join('');
}
function applyBinds(root, map, attr) {
  for (const el of root.querySelectorAll(`[data-${attr}]`)) {
    const v = map[el.getAttribute(`data-${attr}`)];
    if (v !== undefined && el.textContent !== v) el.textContent = v;
  }
}
function updateUI() {
  renderInv();
  const g = globalBinds();
  const app = $('#app');
  applyBinds(app, g, 'g');
  for (const el of app.querySelectorAll('[data-gh]')) el.hidden = !!g[el.getAttribute('data-gh')];
  for (const el of app.querySelectorAll('[data-gw]')) el.style.width = g[el.getAttribute('data-gw')];
  const P = PANELS[tab];
  if (!P) return;
  const root = $('#tab-' + tab);
  const sig = P.sig();
  if (panelSig[tab] !== sig) {
    const sc = root.scrollTop;
    root.innerHTML = P.render();
    root.scrollTop = sc;
    panelSig[tab] = sig;
  }
  const b = {};
  P.binds(b);
  for (const el of root.querySelectorAll('[data-b]')) {
    const v = b[el.getAttribute('data-b')];
    if (v !== undefined && el.textContent !== v) el.textContent = v;
  }
  for (const el of root.querySelectorAll('[data-d]')) { const v = b[el.getAttribute('data-d')]; if (v !== undefined) el.disabled = !!v; }
  for (const el of root.querySelectorAll('[data-w]')) { const v = b[el.getAttribute('data-w')]; if (v !== undefined) el.style.width = v; }
  for (const el of root.querySelectorAll('[data-c]')) { const v = b[el.getAttribute('data-c')]; if (v !== undefined && el.className !== v) el.className = v; }
  for (const el of root.querySelectorAll('[data-hide]')) el.hidden = !!b[el.getAttribute('data-hide')];
  if (tab === 'marche') drawSparks(root);
}
function drawSparks(root) {
  for (const c of root.querySelectorAll('canvas.spark')) {
    const h = hist[c.dataset.k];
    const x = c.getContext('2d');
    x.clearRect(0, 0, c.width, c.height);
    if (!h || h.length < 2) continue;
    let lo = Math.min(...h, 0.9), hi = Math.max(...h, 1.1);
    const Y = v => c.height - 4 - (v - lo) / (hi - lo) * (c.height - 8);
    x.strokeStyle = 'rgba(255,255,255,.15)'; x.lineWidth = 1; x.setLineDash([3, 3]);
    x.beginPath(); x.moveTo(0, Y(1)); x.lineTo(c.width, Y(1)); x.stroke(); x.setLineDash([]);
    x.strokeStyle = h[h.length - 1] >= 1 ? '#5fd38d' : '#ff6b6b'; x.lineWidth = 3;
    x.beginPath();
    h.forEach((v, i) => { const px = i / (50 - 1) * c.width; i ? x.lineTo(px, Y(v)) : x.moveTo(px, Y(v)); });
    x.stroke();
  }
}
function renderAll(force) {
  if (force) for (const k in panelSig) delete panelSig[k];
  invSig = '';
  updateUI();
  dirty = true;
}
function setTab(t) {
  tab = t;
  $$('.tab').forEach(s => s.classList.toggle('active', s.id === 'tab-' + t));
  $$('#nav button').forEach(b => b.classList.toggle('on', b.dataset.tab === t));
  delete panelSig[t];
  updateUI();
  if (t === 'botte') { resize(); }
}

// ============================================================ actions
const ACTIONS = {
  nextBotte() { nextBotte(); },
  tierup(el) { const f = S.furnaces[+el.dataset.i]; if (tierUp(f)) { sfx('buy'); toast(`${TIERS[f.tier].icon} ${TIERS[f.tier].name} installé !`, 'gold'); } },
  metal(el) { setMetal(S.furnaces[+el.dataset.i], el.dataset.k); },
  fuel(el) { setFuel(S.furnaces[+el.dataset.i], el.dataset.k); },
  load(el) { if (loadFurnace(S.furnaces[+el.dataset.i])) sfx('needle'); },
  addfuel(el) { if (addFuel(S.furnaces[+el.dataset.i], true)) sfx('click'); },
  cast(el) { const q = castFurnace(S.furnaces[+el.dataset.i]); if (q) { sfx('cast'); vib(40); toast(`🧱 +${q} lingot${q > 1 ? 's' : ''} !`); } },
  forge1(el) { if (forge(el.dataset.k, 1)) sfx('cast'); },
  forgemax(el) { const n = forge(el.dataset.k, maxForge(el.dataset.k)); if (n) { sfx('cast'); toast(`⚒️ ${n} × ${ALLOYS[el.dataset.k].name}`); } },
  forgetoggle(el) { S.settings.forge[el.dataset.k] = !S.settings.forge[el.dataset.k]; },
  sell(el) {
    const k = el.dataset.k, q = el.dataset.q === 'all' ? stock(k) : +el.dataset.q;
    const t = sell(k, q);
    if (t) { vib(15); if (q > 1) toast(`💰 +${eur(t)}`); }
  },
  scrap(el) { const t = sellScrap(el.dataset.k); if (t) toast(`🔩 Ferraille vendue : +${eur(t)}`); },
  coal(el) { if (buyCoal(+el.dataset.q)) sfx('click'); },
  sellhay() { S.settings.sellHay = !S.settings.sellHay; },
  buy(el) {
    const u = UPG_BY[el.dataset.k], c = upCost(u);
    if (lvl(u.id) >= u.max || S.money < c) return;
    S.money -= c;
    S.up[u.id] = lvl(u.id) + 1;
    if (u.onBuy) u.onBuy();
    sfx('buy'); vib(20);
    if (lvl(u.id) === 1) toast(`${u.icon} ${u.name} acquis !`);
  },
  toggle(el) { const k = el.dataset.k; S.settings[k] = !S.settings[k]; },
  prestige() {
    if (!canPrestige()) return;
    modal('Vendre la ferme ?', `<p>Tu repars de zéro (argent, stocks, améliorations, fours, botte n°1) mais tu obtiens <b>${starsPotential()} étoile(s) de foin</b> au total.</p><p class="muted">Chaque étoile : +10 % sur les ventes, +5 % à l’électro-aimant. Statistiques et objectifs sont conservés.</p>`,
      [{ label: 'Annuler', cls: '' }, { label: 'Vendre !', cb: prestige }]);
  },
  reset() {
    modal('Tout effacer ?', '<p>Ta partie sera définitivement supprimée.</p>',
      [{ label: 'Annuler', cls: '' }, { label: 'Effacer', cls: 'fire', cb: () => { S = newState(); newBotte(1); save(); renderAll(true); setTab('botte'); } }]);
  },
};
document.addEventListener('click', e => {
  const mb = e.target.closest('[data-mb]');
  if (mb) { const b = modalCbs[+mb.dataset.mb]; closeModal(); if (b && b.cb) b.cb(); return; }
  const nb = e.target.closest('#nav button');
  if (nb) { audio(); setTab(nb.dataset.tab); return; }
  const el = e.target.closest('[data-a]');
  if (!el || el.disabled) return;
  audio();
  const fn = ACTIONS[el.dataset.a];
  if (fn) { fn(el); updateUI(); }
});
document.addEventListener('input', e => {
  const el = e.target.closest('[data-input]');
  if (el) { S.settings[el.dataset.input] = +el.value; updateUI(); }
});
window.handleBack = function () {
  if (!$('#modal').hidden) { closeModal(); return true; }
  if (tab !== 'botte') { setTab('botte'); return true; }
  save();
  return false;
};

// ============================================================ démarrage
let last = performance.now(), uiAcc = 0, saveAcc = 0;
function frame(now) {
  let dt = (now - last) / 1000;
  last = now;
  if (dt > 3) {
    const sec = Math.min(dt, 8 * 3600);
    const r = simulate(sec);
    if (sec > 60) showOffline(sec, r);
    renderAll(false);
  } else if (dt > 0) tickCore(Math.min(dt, 0.25));
  if (tab === 'botte' && (dirty || flies.length || floaters.length)) render(now);
  uiAcc += dt;
  if (uiAcc >= 0.25) { uiAcc = 0; updateUI(); }
  saveAcc += dt;
  if (saveAcc >= 10) { saveAcc = 0; save(); }
  requestAnimationFrame(frame);
}
document.addEventListener('visibilitychange', () => { if (document.hidden) save(); else last = performance.now() - Math.max(0, Date.now() - S.lastSave); });
window.addEventListener('pagehide', save);
window.addEventListener('resize', resize);
if (window.ResizeObserver) new ResizeObserver(resize).observe(cvs.parentElement);

const had = load();
for (let i = 0; i < 50; i++) marketStep();
if (had) {
  const away = (Date.now() - S.lastSave) / 1000;
  if (away > 10) {
    const sec = Math.min(away, 8 * 3600);
    const r = simulate(sec);
    if (sec > 60) setTimeout(() => showOffline(sec, r), 300);
  }
} else {
  setTimeout(() => modal('Trouve le Foin 🌾', `<p>Tout le monde cherche l’aiguille dans la botte de foin…</p>
    <p><b>Ici c’est l’inverse :</b> fouille une botte d’<b>aiguilles</b> pour trouver les rares brins de <b>foin</b> !</p>
    <p>Fonds ensuite les aiguilles en lingots, vends-les au marché et automatise toute ta fonderie.</p>`, [{ label: 'C’est parti !' }]), 200);
}
resize();
renderAll(true);
save();
requestAnimationFrame(frame);
window.__game = { get S() { return S; }, simulate, tickCore, setTab, ACTIONS };
})();
