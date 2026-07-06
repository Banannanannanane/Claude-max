/* 🧪 Tri d'eau — verse les liquides pour trier les couleurs (LE jeu des pubs TikTok) */
registerGame({
  id: 'watersort',
  name: "Tri d'eau",
  emoji: '🧪',
  desc: 'Trie les couleurs',
  bestLabel: 'Niveau',

  start(host, api) {
    const PALETTE = ['#ff5d6c', '#4dabff', '#ffd34d', '#5be37c', '#c17bff',
                     '#ff9c40', '#00d9c0', '#ff6fd8'];
    const CAP = 4;
    let level = Math.max(1, getBest('watersort'));
    let tubes = [];        // chaque tube : tableau de couleurs (bas -> haut)
    let selected = -1;
    let history = [];
    let moves = 0;

    const wrap = document.createElement('div');
    wrap.className = 'ws-wrap';
    wrap.innerHTML =
      '<div class="ws-info" id="ws-info"></div>' +
      '<div class="ws-tubes" id="ws-tubes"></div>' +
      '<div class="ws-actions">' +
      '  <button id="ws-undo">↩️ Annuler</button>' +
      '  <button id="ws-reset">🔄 Recommencer</button>' +
      '</div>';
    host.appendChild(wrap);
    wrap.querySelector('#ws-undo').addEventListener('click', undo);
    wrap.querySelector('#ws-reset').addEventListener('click', () => { Sfx.tap(); buildLevel(); });

    function numColors() { return Math.min(3 + Math.floor((level - 1) / 2), PALETTE.length); }

    function buildLevel() {
      const n = numColors();
      // n couleurs x 4 segments, mélangés dans n tubes pleins + 2 vides
      let pool = [];
      for (let i = 0; i < n; i++) for (let j = 0; j < CAP; j++) pool.push(PALETTE[i]);
      do {
        for (let i = pool.length - 1; i > 0; i--) {
          const j = Math.floor(Math.random() * (i + 1));
          [pool[i], pool[j]] = [pool[j], pool[i]];
        }
      } while (isTriviallySorted(pool, n));
      tubes = [];
      for (let i = 0; i < n; i++) tubes.push(pool.slice(i * CAP, i * CAP + CAP));
      tubes.push([], []);
      selected = -1; history = []; moves = 0;
      render();
    }

    function isTriviallySorted(pool, n) {
      for (let i = 0; i < n; i++) {
        const t = pool.slice(i * CAP, i * CAP + CAP);
        if (t.every(c => c === t[0])) return true;
      }
      return false;
    }

    function tubeDone(t) {
      return t.length === 0 || (t.length === CAP && t.every(c => c === t[0]));
    }

    function won() { return tubes.every(tubeDone); }

    function pour(from, to) {
      const src = tubes[from], dst = tubes[to];
      if (src.length === 0) return false;
      const color = src[src.length - 1];
      if (dst.length >= CAP) return false;
      if (dst.length > 0 && dst[dst.length - 1] !== color) return false;
      // nombre de segments contigus de même couleur en haut de la source
      let run = 0;
      for (let i = src.length - 1; i >= 0 && src[i] === color; i--) run++;
      const amount = Math.min(run, CAP - dst.length);
      history.push(tubes.map(t => t.slice()));
      for (let i = 0; i < amount; i++) dst.push(src.pop());
      moves++;
      return true;
    }

    function undo() {
      if (history.length === 0) return;
      tubes = history.pop();
      moves--; selected = -1;
      Sfx.tap();
      render();
    }

    function onTube(i) {
      Sfx.unlock();
      if (selected === -1) {
        if (tubes[i].length > 0 && !tubeDone(tubes[i])) { selected = i; Sfx.tap(); vib(10); }
      } else if (selected === i) {
        selected = -1;
      } else {
        if (pour(selected, i)) {
          Sfx.pop(); vib(20);
          selected = -1;
          render();
          if (won()) return finish();
        } else {
          // sélectionne l'autre tube à la place
          selected = tubes[i].length > 0 && !tubeDone(tubes[i]) ? i : -1;
          if (selected === i) Sfx.tap();
        }
      }
      render();
    }

    function finish() {
      Sfx.win(); vib([60, 60, 120]);
      level++;
      setBest('watersort', level);
      api.setBestLabel('Niveau ' + level);
      showEnd(host, {
        title: '🎉 Niveau réussi !',
        sub: moves + ' coups',
        retryLabel: 'Niveau suivant',
        onRetry: () => { api.setScore('Niv. ' + level); buildLevel(); }
      });
    }

    function render() {
      api.setScore('Niv. ' + level);
      wrap.querySelector('#ws-info').textContent =
        'Touche un tube puis un autre pour verser';
      const box = wrap.querySelector('#ws-tubes');
      box.innerHTML = '';
      tubes.forEach((t, i) => {
        const el = document.createElement('div');
        el.className = 'tube' + (i === selected ? ' sel' : '');
        t.forEach(color => {
          const s = document.createElement('div');
          s.className = 'seg';
          s.style.background = color;
          el.appendChild(s);
        });
        el.addEventListener('click', () => onTube(i));
        box.appendChild(el);
      });
    }

    buildLevel();
    return { stop() {} };
  }
});
