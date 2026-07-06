/* 🐤 Petit Oiseau — tape pour voler, évite les tuyaux */
registerGame({
  id: 'flappy',
  name: 'Petit Oiseau',
  emoji: '🐤',
  desc: 'Évite les tuyaux',
  bestLabel: 'Record',

  start(host, api) {
    const cv = createCanvas(host);
    const ctx = cv.ctx;
    let running = true, raf = 0;
    let bird, pipes, score, over, started, t;

    const GRAV = .45, JUMP = -8.2, PIPE_W = 64;

    function reset() {
      bird = { y: cv.H() * .45, vy: 0, r: 15 };
      pipes = [];
      score = 0; over = false; started = false; t = 0;
      api.setScore('0');
    }

    function gap() { return Math.max(150, cv.H() * .30 - score * 2); }

    function spawnPipe() {
      const g = gap();
      const margin = 60;
      const cy = margin + g / 2 + Math.random() * (cv.H() - 2 * margin - g);
      pipes.push({ x: cv.W() + PIPE_W, cy, g, passed: false });
    }

    function flap() {
      Sfx.unlock();
      if (over) return;
      started = true;
      bird.vy = JUMP;
      Sfx.tap(); vib(10);
    }

    function die() {
      if (over) return;
      over = true;
      Sfx.over(); vib(180);
      const nb = setBest('flappy', score);
      api.setBestLabel('Record : ' + getBest('flappy'));
      showEnd(host, {
        title: 'Aïe ! 💥',
        sub: 'Score : ' + score,
        newBest: nb,
        onRetry: reset
      });
    }

    function tick() {
      if (!running) return;
      const W = cv.W(), H = cv.H();
      t++;

      if (started && !over) {
        bird.vy += GRAV;
        bird.y += bird.vy;

        if (t % 95 === 0 || pipes.length === 0) spawnPipe();
        const sp = Math.min(3.2 + score * .05, 6);
        pipes.forEach(p => p.x -= sp);
        pipes = pipes.filter(p => p.x > -PIPE_W - 10);

        const bx = W * .3;
        pipes.forEach(p => {
          if (!p.passed && p.x + PIPE_W < bx - bird.r) {
            p.passed = true; score++;
            api.setScore(String(score));
            Sfx.score(); vib(12);
          }
          // collision
          if (bx + bird.r > p.x && bx - bird.r < p.x + PIPE_W) {
            if (bird.y - bird.r < p.cy - p.g / 2 || bird.y + bird.r > p.cy + p.g / 2) die();
          }
        });

        if (bird.y + bird.r > H - 20 || bird.y - bird.r < 0) die();
      } else if (!started) {
        bird.y = H * .45 + Math.sin(t * .08) * 8;
      }

      // ---- dessin ----
      ctx.clearRect(0, 0, W, H);

      // tuyaux
      pipes.forEach(p => {
        ctx.fillStyle = '#35d06d';
        const topH = p.cy - p.g / 2, botY = p.cy + p.g / 2;
        ctx.fillRect(p.x, 0, PIPE_W, topH);
        ctx.fillRect(p.x, botY, PIPE_W, H - botY);
        ctx.fillStyle = '#2aa957';
        ctx.fillRect(p.x - 4, topH - 22, PIPE_W + 8, 22);
        ctx.fillRect(p.x - 4, botY, PIPE_W + 8, 22);
      });

      // sol
      ctx.fillStyle = '#c8a24a';
      ctx.fillRect(0, H - 20, W, 20);

      // oiseau
      const bx = W * .3;
      ctx.save();
      ctx.translate(bx, bird.y);
      ctx.rotate(Math.max(-.5, Math.min(.9, bird.vy * .06)));
      ctx.fillStyle = '#ffd34d';
      ctx.beginPath(); ctx.arc(0, 0, bird.r, 0, 7); ctx.fill();
      ctx.fillStyle = '#ff9c40'; // bec
      ctx.beginPath();
      ctx.moveTo(bird.r - 3, -3); ctx.lineTo(bird.r + 9, 2); ctx.lineTo(bird.r - 3, 6);
      ctx.fill();
      ctx.fillStyle = '#fff'; // aile
      ctx.beginPath();
      ctx.ellipse(-4, 3, 8, 5 + Math.sin(t * .5) * 2, -.4, 0, 7);
      ctx.fill();
      ctx.fillStyle = '#222'; // œil
      ctx.beginPath(); ctx.arc(5, -5, 2.6, 0, 7); ctx.fill();
      ctx.restore();

      if (!started) {
        ctx.fillStyle = 'rgba(255,255,255,.75)';
        ctx.font = 'bold 18px system-ui';
        ctx.textAlign = 'center';
        ctx.fillText('Touche l’écran pour voler', W / 2, H * .3);
      }

      raf = requestAnimationFrame(tick);
    }

    const onTap = e => { e.preventDefault(); flap(); };
    cv.el.addEventListener('touchstart', onTap, { passive: false });
    cv.el.addEventListener('mousedown', onTap);

    reset();
    tick();

    return {
      stop() {
        running = false;
        cancelAnimationFrame(raf);
        cv.destroy();
      }
    };
  }
});
