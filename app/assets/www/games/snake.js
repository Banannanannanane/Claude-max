/* 🐍 Serpent — mange les pommes, traverse les murs, évite ta queue */
registerGame({
  id: 'snake',
  name: 'Serpent',
  emoji: '🐍',
  desc: 'Mange les pommes',
  bestLabel: 'Record',

  start(host, api) {
    const cv = createCanvas(host);
    const ctx = cv.ctx;
    let running = true, raf = 0;
    let snake, dir, nextDir, food, score, over, started, acc, stepMs, last;

    const COLS = 17;

    function cellSize() { return Math.floor(Math.min(cv.W() / COLS, cv.H() / 24)); }
    function rows() { return Math.floor((cv.H() - 10) / cellSize()); }

    function reset() {
      const R = rows();
      snake = [
        { x: 8, y: Math.floor(R / 2) },
        { x: 7, y: Math.floor(R / 2) },
        { x: 6, y: Math.floor(R / 2) }
      ];
      dir = { x: 1, y: 0 };
      nextDir = dir;
      score = 0; over = false; started = false;
      stepMs = 160; acc = 0; last = 0;
      placeFood();
      api.setScore('0');
    }

    function placeFood() {
      const R = rows();
      do {
        food = { x: Math.floor(Math.random() * COLS), y: Math.floor(Math.random() * R) };
      } while (snake.some(s => s.x === food.x && s.y === food.y));
    }

    function turn(d) {
      Sfx.unlock();
      const m = { up: { x: 0, y: -1 }, down: { x: 0, y: 1 }, left: { x: -1, y: 0 }, right: { x: 1, y: 0 } };
      const nd = m[d];
      if (!nd) return;
      started = true;
      // pas de demi-tour
      if (nd.x === -dir.x && nd.y === -dir.y) return;
      nextDir = nd;
      Sfx.tap(); vib(8);
    }

    function step() {
      dir = nextDir;
      const R = rows();
      const head = {
        x: (snake[0].x + dir.x + COLS) % COLS,
        y: (snake[0].y + dir.y + R) % R
      };
      if (snake.some(s => s.x === head.x && s.y === head.y)) {
        over = true;
        Sfx.over(); vib(200);
        const nb = setBest('snake', score);
        api.setBestLabel('Record : ' + getBest('snake'));
        showEnd(host, {
          title: 'Ouch, ta queue ! 🐍',
          sub: 'Score : ' + score,
          newBest: nb,
          onRetry: reset
        });
        return;
      }
      snake.unshift(head);
      if (head.x === food.x && head.y === food.y) {
        score++;
        api.setScore(String(score));
        Sfx.score(); vib(20);
        stepMs = Math.max(70, stepMs - 3);
        placeFood();
      } else {
        snake.pop();
      }
    }

    function tick(ts) {
      if (!running) return;
      if (!last) last = ts;
      const dt = ts - last;
      last = ts;

      if (started && !over) {
        acc += dt;
        while (acc >= stepMs) { acc -= stepMs; step(); if (over) break; }
      }

      const W = cv.W(), H = cv.H();
      const cs = cellSize();
      const R = rows();
      const ox = (W - COLS * cs) / 2, oy = (H - R * cs) / 2;

      ctx.clearRect(0, 0, W, H);
      ctx.fillStyle = 'rgba(255,255,255,.05)';
      ctx.fillRect(ox, oy, COLS * cs, R * cs);

      // pomme
      ctx.fillStyle = '#ff5d6c';
      ctx.beginPath();
      ctx.arc(ox + food.x * cs + cs / 2, oy + food.y * cs + cs / 2, cs * .38, 0, 7);
      ctx.fill();
      ctx.fillStyle = '#5be37c';
      ctx.fillRect(ox + food.x * cs + cs / 2 - 1, oy + food.y * cs + cs * .08, 2, cs * .18);

      // serpent
      snake.forEach((s, i) => {
        const t = i / snake.length;
        ctx.fillStyle = i === 0 ? '#00d9c0' : 'hsl(' + (168 - t * 40) + ', 70%, ' + (48 - t * 15) + '%)';
        const p = cs * .06;
        ctx.fillRect(ox + s.x * cs + p, oy + s.y * cs + p, cs - 2 * p, cs - 2 * p);
      });
      // yeux
      const h = snake[0];
      ctx.fillStyle = '#0f1220';
      const ex = ox + h.x * cs + cs / 2, ey = oy + h.y * cs + cs / 2;
      ctx.beginPath();
      ctx.arc(ex + dir.x * cs * .15 - dir.y * cs * .18, ey + dir.y * cs * .15 - dir.x * cs * .18, cs * .09, 0, 7);
      ctx.arc(ex + dir.x * cs * .15 + dir.y * cs * .18, ey + dir.y * cs * .15 + dir.x * cs * .18, cs * .09, 0, 7);
      ctx.fill();

      if (!started) {
        ctx.fillStyle = 'rgba(255,255,255,.7)';
        ctx.font = 'bold 16px system-ui';
        ctx.textAlign = 'center';
        ctx.fillText('Glisse pour diriger le serpent', W / 2, oy + R * cs * .25);
        ctx.font = '13px system-ui';
        ctx.fillText('(les murs se traversent)', W / 2, oy + R * cs * .25 + 24);
      }

      raf = requestAnimationFrame(tick);
    }

    const offSwipe = onSwipe(host, d => { if (d !== 'tap') turn(d); });
    const onKey = e => {
      const map = { ArrowLeft: 'left', ArrowRight: 'right', ArrowUp: 'up', ArrowDown: 'down' };
      if (map[e.key]) { e.preventDefault(); turn(map[e.key]); }
    };
    window.addEventListener('keydown', onKey);

    reset();
    raf = requestAnimationFrame(tick);

    return {
      stop() {
        running = false;
        cancelAnimationFrame(raf);
        offSwipe();
        window.removeEventListener('keydown', onKey);
        cv.destroy();
      }
    };
  }
});
