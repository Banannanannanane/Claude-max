/* 🔪 Lance-Couteaux — plante tous les couteaux sans toucher les autres */
registerGame({
  id: 'knife',
  name: 'Couteaux',
  emoji: '🔪',
  desc: 'Vise la cible',
  bestLabel: 'Record',

  start(host, api) {
    const cv = createCanvas(host);
    const ctx = cv.ctx;
    let running = true, raf = 0;
    const MIN_ANGLE = .22; // écart minimal entre couteaux (radians)

    let level, angle, rotSpeed, stuck, toThrow, throwing, throwY, score, over, wobble;

    function newLevel(keepScore) {
      if (!keepScore) { score = 0; api.setScore('0'); level = 1; }
      angle = 0;
      stuck = [];
      toThrow = Math.min(5 + level, 10);
      throwing = false;
      over = false;
      wobble = 0;
      const base = 1.4 + level * .18;
      rotSpeed = (Math.random() < .5 ? 1 : -1) * base * (Math.PI / 180) * 60 / 60;
      rotSpeed = (Math.random() < .5 ? 1 : -1) * (0.025 + level * 0.003);
    }

    function throwKnife() {
      Sfx.unlock();
      if (over || throwing || toThrow <= 0) return;
      throwing = true;
      throwY = cv.H() - 90;
      Sfx.tap(); vib(10);
    }

    function targetY() { return cv.H() * .32; }
    function targetR() { return Math.min(cv.W() * .28, 105); }

    function land() {
      // angle du point d'impact (bas du disque) dans le référentiel du disque
      const hit = (-Math.PI / 2 - angle) % (2 * Math.PI);
      for (const k of stuck) {
        let d = Math.abs(hit - k);
        d = Math.min(d % (2 * Math.PI), 2 * Math.PI - (d % (2 * Math.PI)));
        if (d < MIN_ANGLE) {
          // collision avec un couteau déjà planté
          over = true;
          Sfx.over(); vib(200);
          const nb = setBest('knife', score);
          api.setBestLabel('Record : ' + getBest('knife'));
          showEnd(host, {
            title: 'Clang ! ⚔️',
            sub: 'Couteaux plantés : ' + score + '\nNiveau ' + level,
            newBest: nb,
            onRetry: () => newLevel(false)
          });
          return;
        }
      }
      stuck.push(hit);
      score++; toThrow--;
      api.setScore(String(score));
      Sfx.pop(); vib(25);
      wobble = 1;
      if (toThrow === 0) {
        level++;
        Sfx.win(); vib([40, 40, 80]);
        setTimeout(() => { if (running && !over) newLevel(true); }, 500);
      }
    }

    function drawKnife(x, y, rot) {
      ctx.save();
      ctx.translate(x, y);
      ctx.rotate(rot);
      // lame
      ctx.fillStyle = '#dfe6f5';
      ctx.beginPath();
      ctx.moveTo(-4, 0); ctx.lineTo(4, 0); ctx.lineTo(4, -26);
      ctx.lineTo(0, -38); ctx.lineTo(-4, -26);
      ctx.closePath(); ctx.fill();
      // manche
      ctx.fillStyle = '#8a5a2b';
      ctx.fillRect(-5, 0, 10, 26);
      ctx.fillStyle = '#6f4520';
      ctx.fillRect(-5, 8, 10, 4);
      ctx.fillRect(-5, 17, 10, 4);
      ctx.restore();
    }

    function tick() {
      if (!running) return;
      const W = cv.W(), H = cv.H();
      const ty = targetY(), tr = targetR();

      if (!over) {
        angle += rotSpeed;
        if (wobble > 0) wobble -= .08;
      }

      if (throwing) {
        throwY -= 26;
        if (throwY <= ty + tr + 20) {
          throwing = false;
          land();
        }
      }

      ctx.clearRect(0, 0, W, H);

      // cible (rondin)
      ctx.save();
      ctx.translate(W / 2, ty + (wobble > 0 ? Math.sin(wobble * 30) * 2 : 0));
      ctx.rotate(angle);
      ctx.fillStyle = '#a86f3e';
      ctx.beginPath(); ctx.arc(0, 0, tr, 0, 7); ctx.fill();
      ctx.strokeStyle = '#8a5a2b'; ctx.lineWidth = 6;
      ctx.beginPath(); ctx.arc(0, 0, tr - 12, 0, 7); ctx.stroke();
      ctx.strokeStyle = '#7a4c22'; ctx.lineWidth = 3;
      ctx.beginPath(); ctx.arc(0, 0, tr - 34, 0, 7); ctx.stroke();
      ctx.fillStyle = '#5d3a1a';
      ctx.beginPath(); ctx.arc(0, 0, 9, 0, 7); ctx.fill();
      // couteaux plantés (pointe vers le centre, manche vers l'extérieur)
      stuck.forEach(k => {
        ctx.save();
        ctx.rotate(k + Math.PI / 2);
        ctx.translate(0, tr + 20);
        drawKnife(0, 0, Math.PI);
        ctx.restore();
      });
      ctx.restore();

      // couteau en vol / prêt
      if (throwing) drawKnife(W / 2, throwY, 0);
      else if (!over && toThrow > 0) drawKnife(W / 2, H - 90, 0);

      // munitions restantes
      for (let i = 0; i < toThrow; i++) {
        ctx.fillStyle = 'rgba(255,255,255,.75)';
        ctx.fillRect(16 + i * 12, H - 30, 6, 18);
      }

      // niveau
      ctx.fillStyle = 'rgba(255,255,255,.5)';
      ctx.font = '14px system-ui';
      ctx.textAlign = 'center';
      ctx.fillText('Niveau ' + level, W / 2, 24);
      if (score === 0 && stuck.length === 0 && !throwing) {
        ctx.fillText('Touche l’écran pour lancer', W / 2, H - 130);
      }

      raf = requestAnimationFrame(tick);
    }

    const onTap = e => { e.preventDefault(); throwKnife(); };
    cv.el.addEventListener('touchstart', onTap, { passive: false });
    cv.el.addEventListener('mousedown', onTap);

    newLevel(false);
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
