/* 🏗️ Stack — empile les blocs le plus haut possible, tape au bon moment */
registerGame({
  id: 'stack',
  name: 'Stack',
  emoji: '🏗️',
  desc: 'Empile les blocs',
  bestLabel: 'Record',

  start(host, api) {
    const cv = createCanvas(host);
    const ctx = cv.ctx;
    const BLOCK_H = 26;
    let running = true, raf = 0;
    let blocks, moving, dir, speed, score, cameraY, over, flash, hueBase;

    function reset() {
      const W = cv.W();
      hueBase = Math.random() * 360;
      blocks = [{ x: W * .15, w: W * .7, y: 0 }]; // y en "étages"
      score = 0; cameraY = 0; over = false; flash = 0;
      speed = Math.max(2.6, W / 160);
      dir = 1;
      moving = { x: -W * .7, w: W * .7, y: 1 };
      api.setScore('0');
    }

    function hue(i) { return 'hsl(' + ((hueBase + i * 9) % 360) + ', 65%, 58%)'; }

    function place() {
      if (over) return;
      Sfx.unlock();
      const top = blocks[blocks.length - 1];
      const left = Math.max(moving.x, top.x);
      const right = Math.min(moving.x + moving.w, top.x + top.w);
      const overlap = right - left;

      if (overlap <= 4) {
        over = true;
        Sfx.over(); vib(150);
        const nb = setBest('stack', score);
        api.setBestLabel('Record : ' + getBest('stack'));
        showEnd(host, {
          title: 'La tour est tombée !',
          sub: 'Hauteur : ' + score + ' étages',
          newBest: nb,
          onRetry: reset
        });
        return;
      }

      const perfect = overlap >= moving.w - 7;
      if (perfect) {
        blocks.push({ x: top.x, w: top.w, y: moving.y });
        flash = 1;
        Sfx.score(); vib([15, 25, 15]);
      } else {
        blocks.push({ x: left, w: overlap, y: moving.y });
        Sfx.pop(); vib(15);
      }

      score++;
      api.setScore(String(score));
      speed = Math.min(speed + .12, cv.W() / 55);
      dir = Math.random() < .5 ? 1 : -1;
      const nw = blocks[blocks.length - 1].w;
      moving = {
        x: dir === 1 ? -nw : cv.W(),
        w: nw,
        y: blocks.length
      };
    }

    function tick() {
      if (!running) return;
      const W = cv.W(), H = cv.H();

      if (!over) {
        moving.x += speed * dir;
        if (moving.x + moving.w > W && dir === 1) { moving.x = W - moving.w; dir = -1; }
        if (moving.x < 0 && dir === -1) { moving.x = 0; dir = 1; }
      }

      // caméra : garde le haut de la tour visible
      const targetCam = Math.max(0, (blocks.length + 1) * BLOCK_H - H * .55);
      cameraY += (targetCam - cameraY) * .1;

      ctx.clearRect(0, 0, W, H);
      const yPix = lvl => H - 40 - (lvl + 1) * BLOCK_H + cameraY;

      // sol
      ctx.fillStyle = 'rgba(255,255,255,.08)';
      ctx.fillRect(0, H - 40 + cameraY, W, 40);

      blocks.forEach((b, i) => {
        const y = yPix(b.y);
        if (y > -BLOCK_H && y < H) {
          ctx.fillStyle = hue(i);
          ctx.fillRect(b.x, y, b.w, BLOCK_H - 2);
        }
      });

      if (!over) {
        ctx.fillStyle = hue(blocks.length);
        ctx.fillRect(moving.x, yPix(moving.y), moving.w, BLOCK_H - 2);
      }

      if (flash > 0) {
        ctx.fillStyle = 'rgba(255,255,255,' + (flash * .35) + ')';
        ctx.fillRect(0, 0, W, H);
        flash -= .07;
      }

      if (score === 0 && !over) {
        ctx.fillStyle = 'rgba(255,255,255,.6)';
        ctx.font = '16px system-ui';
        ctx.textAlign = 'center';
        ctx.fillText('Touche l’écran pour poser le bloc', W / 2, H * .28);
      }

      raf = requestAnimationFrame(tick);
    }

    const onTap = e => { e.preventDefault(); place(); };
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
