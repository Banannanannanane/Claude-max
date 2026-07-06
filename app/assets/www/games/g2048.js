/* 🔢 2048 — glisse pour fusionner les tuiles */
registerGame({
  id: 'g2048',
  name: '2048',
  emoji: '🔢',
  desc: 'Fusionne les tuiles',
  bestLabel: 'Record',

  start(host, api) {
    const cv = createCanvas(host);
    const ctx = cv.ctx;
    const N = 4;
    let grid, score, over, offSwipe, popAnim;

    const COLORS = {
      2: '#eee4da', 4: '#ede0c8', 8: '#f2b179', 16: '#f59563',
      32: '#f67c5f', 64: '#f65e3b', 128: '#edcf72', 256: '#edcc61',
      512: '#edc850', 1024: '#edc53f', 2048: '#edc22e'
    };

    function reset() {
      grid = Array.from({ length: N }, () => Array(N).fill(0));
      score = 0; over = false; popAnim = {};
      addTile(); addTile();
      api.setScore('0');
      draw();
    }

    function addTile() {
      const empty = [];
      for (let r = 0; r < N; r++) for (let c = 0; c < N; c++)
        if (grid[r][c] === 0) empty.push([r, c]);
      if (empty.length === 0) return;
      const [r, c] = empty[Math.floor(Math.random() * empty.length)];
      grid[r][c] = Math.random() < .9 ? 2 : 4;
      popAnim[r + ',' + c] = 1;
    }

    // fait glisser une ligne vers la gauche, renvoie [nouvelle ligne, points, bougé]
    function slide(row) {
      const vals = row.filter(v => v !== 0);
      let pts = 0;
      const out = [];
      for (let i = 0; i < vals.length; i++) {
        if (i + 1 < vals.length && vals[i] === vals[i + 1]) {
          out.push(vals[i] * 2);
          pts += vals[i] * 2;
          i++;
        } else out.push(vals[i]);
      }
      while (out.length < N) out.push(0);
      const moved = out.some((v, i) => v !== row[i]);
      return [out, pts, moved];
    }

    function move(dirName) {
      if (over) return;
      Sfx.unlock();
      let moved = false, pts = 0;

      const get = (r, c) => {
        if (dirName === 'left') return grid[r][c];
        if (dirName === 'right') return grid[r][N - 1 - c];
        if (dirName === 'up') return grid[c][r];
        return grid[N - 1 - c][r]; // down
      };
      const set = (r, c, v) => {
        if (dirName === 'left') grid[r][c] = v;
        else if (dirName === 'right') grid[r][N - 1 - c] = v;
        else if (dirName === 'up') grid[c][r] = v;
        else grid[N - 1 - c][r] = v;
      };

      for (let r = 0; r < N; r++) {
        const row = [];
        for (let c = 0; c < N; c++) row.push(get(r, c));
        const [out, p, m] = slide(row);
        if (m) moved = true;
        pts += p;
        for (let c = 0; c < N; c++) set(r, c, out[c]);
      }

      if (!moved) return;
      score += pts;
      api.setScore(String(score));
      if (pts > 0) { Sfx.pop(); vib(15); } else { Sfx.tap(); vib(8); }
      addTile();
      draw();
      if (isStuck()) {
        over = true;
        Sfx.over(); vib(200);
        const nb = setBest('g2048', score);
        api.setBestLabel('Record : ' + getBest('g2048'));
        showEnd(host, {
          title: 'Plus de coups !',
          sub: 'Score : ' + score,
          newBest: nb,
          onRetry: reset
        });
      }
    }

    function isStuck() {
      for (let r = 0; r < N; r++) for (let c = 0; c < N; c++) {
        if (grid[r][c] === 0) return false;
        if (c + 1 < N && grid[r][c] === grid[r][c + 1]) return false;
        if (r + 1 < N && grid[r][c] === grid[r + 1][c]) return false;
      }
      return true;
    }

    function draw() {
      const W = cv.W(), H = cv.H();
      ctx.clearRect(0, 0, W, H);
      const size = Math.min(W - 24, H - 24, 420);
      const x0 = (W - size) / 2, y0 = (H - size) / 2;
      const gap = 10, cell = (size - gap * (N + 1)) / N;

      ctx.fillStyle = 'rgba(255,255,255,.09)';
      roundRect(x0, y0, size, size, 14); ctx.fill();

      for (let r = 0; r < N; r++) for (let c = 0; c < N; c++) {
        const x = x0 + gap + c * (cell + gap);
        const y = y0 + gap + r * (cell + gap);
        const v = grid[r][c];
        ctx.fillStyle = v === 0 ? 'rgba(255,255,255,.06)' : (COLORS[v] || '#3c3a32');
        const k = r + ',' + c;
        let s = cell;
        if (popAnim[k] > 0) {
          s = cell * (1.12 - popAnim[k] * .12);
          popAnim[k] -= .2;
          if (popAnim[k] <= 0) delete popAnim[k];
          requestAnimationFrame(draw);
        }
        const off = (cell - s) / 2;
        roundRect(x + off, y + off, s, s, 8); ctx.fill();
        if (v !== 0) {
          ctx.fillStyle = v <= 4 ? '#776e65' : '#fff';
          ctx.font = 'bold ' + (v < 128 ? cell * .45 : v < 1024 ? cell * .38 : cell * .3) + 'px system-ui';
          ctx.textAlign = 'center';
          ctx.textBaseline = 'middle';
          ctx.fillText(v, x + cell / 2, y + cell / 2 + 2);
        }
      }

      ctx.fillStyle = 'rgba(255,255,255,.45)';
      ctx.font = '13px system-ui';
      ctx.textAlign = 'center';
      ctx.textBaseline = 'alphabetic';
      ctx.fillText('Glisse dans une direction pour jouer', W / 2, y0 + size + 26);
    }

    function roundRect(x, y, w, h, r) {
      ctx.beginPath();
      ctx.moveTo(x + r, y);
      ctx.arcTo(x + w, y, x + w, y + h, r);
      ctx.arcTo(x + w, y + h, x, y + h, r);
      ctx.arcTo(x, y + h, x, y, r);
      ctx.arcTo(x, y, x + w, y, r);
      ctx.closePath();
    }

    offSwipe = onSwipe(host, dir => { if (dir !== 'tap') move(dir); });
    const onKey = e => {
      const map = { ArrowLeft: 'left', ArrowRight: 'right', ArrowUp: 'up', ArrowDown: 'down' };
      if (map[e.key]) { e.preventDefault(); move(map[e.key]); }
    };
    window.addEventListener('keydown', onKey);
    window.addEventListener('resize', draw);

    reset();

    return {
      stop() {
        offSwipe();
        window.removeEventListener('keydown', onKey);
        window.removeEventListener('resize', draw);
        cv.destroy();
      }
    };
  }
});
