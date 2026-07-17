package com.nova.blocks;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.Random;

/**
 * NOVA 2048 board: rendering, swipe input, game logic, sliding-tile animation,
 * haptic feedback, one-level undo and continue-after-win. Drawn entirely with
 * Canvas; no XML layouts or resources.
 */
public class GameView extends View {

    private static final int N = 4;
    private static final int TARGET = 2048;
    private static final long SLIDE_MS = 95;
    private static final long POP_MS = 130;

    private static final int C_BG      = 0xFF0A0E1C;
    private static final int C_PANEL   = 0xFF141B36;
    private static final int C_EMPTY   = 0xFF1E2749;
    private static final int C_TEXT_HI = 0xFFF4F7FF;
    private static final int C_TEXT_LO = 0xFF8A93C4;
    private static final int C_ACCENT  = 0xFF19E3C2;
    private static final int C_ACCENT2 = 0xFFB14BFF;

    /** A tile mid-slide: value moving from (fr,fc) to (tr,tc) in grid coords. */
    private static final class Anim {
        final int value;
        final float fr, fc, tr, tc;
        Anim(int v, float fr, float fc, float tr, float tc) {
            this.value = v; this.fr = fr; this.fc = fc; this.tr = tr; this.tc = tc;
        }
    }

    private final int[][] board = new int[N][N];
    private final long[][] pop = new long[N][N];      // pop-animation timestamps
    private final int[][] prevBoard = new int[N][N];  // one-level undo snapshot

    private final ArrayList<Anim> anims = new ArrayList<Anim>();
    private long slideStart;
    private boolean sliding;

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private final Random rnd = new Random();
    private final SharedPreferences prefs;

    private int score, best, prevScore;
    private boolean canUndo, showWin, over;

    private float boardLeft, boardTop, boardSize, cell, gap, radius;
    private final RectF newBtn = new RectF();
    private final RectF undoBtn = new RectF();
    private float downX, downY;

    public GameView(Context ctx) {
        super(ctx);
        prefs = ctx.getSharedPreferences("nova2048", Context.MODE_PRIVATE);
        best = prefs.getInt("best", 0);
        text.setTextAlign(Paint.Align.CENTER);
        text.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        newGame();
    }

    private void newGame() {
        for (int y = 0; y < N; y++)
            for (int x = 0; x < N; x++) { board[y][x] = 0; pop[y][x] = 0; }
        score = 0;
        showWin = false;
        over = false;
        canUndo = false;
        sliding = false;
        anims.clear();
        spawn();
        spawn();
        invalidate();
    }

    private void spawn() {
        ArrayList<int[]> empty = new ArrayList<int[]>();
        for (int y = 0; y < N; y++)
            for (int x = 0; x < N; x++)
                if (board[y][x] == 0) empty.add(new int[]{x, y});
        if (empty.isEmpty()) return;
        int[] c = empty.get(rnd.nextInt(empty.size()));
        board[c[1]][c[0]] = rnd.nextInt(10) == 0 ? 4 : 2;
        pop[c[1]][c[0]] = SystemClock.uptimeMillis();
    }

    // maps a line coordinate (i-th line, j-th slot toward the target edge) to (row,col)
    private int rc(int dir, int i, int j, boolean row) {
        switch (dir) {
            case 0: return row ? i : j;             // left
            case 1: return row ? i : N - 1 - j;     // right
            case 2: return row ? j : i;             // up
            default: return row ? N - 1 - j : i;    // down
        }
    }

    // dir: 0=left 1=right 2=up 3=down
    private void move(int dir) {
        if (sliding || over) return;

        for (int y = 0; y < N; y++) System.arraycopy(board[y], 0, prevBoard[y], 0, N);
        prevScore = score;

        anims.clear();
        boolean moved = false, merged = false;

        for (int i = 0; i < N; i++) {
            // read the line toward the target edge (j=0)
            int[] val = new int[N];
            for (int j = 0; j < N; j++) val[j] = board[rc(dir, i, j, true)][rc(dir, i, j, false)];

            int[] result = new int[N];
            int pos = 0;
            int prevVal = 0;
            boolean mergeable = false;
            for (int j = 0; j < N; j++) {
                int v = val[j];
                if (v == 0) continue;
                float fr = rc(dir, i, j, true), fc = rc(dir, i, j, false);
                if (mergeable && prevVal == v) {
                    result[pos - 1] = v * 2;
                    score += v * 2;
                    merged = true;
                    float tr = rc(dir, i, pos - 1, true), tc = rc(dir, i, pos - 1, false);
                    anims.add(new Anim(v, fr, fc, tr, tc));
                    mergeable = false;
                } else {
                    result[pos] = v;
                    float tr = rc(dir, i, pos, true), tc = rc(dir, i, pos, false);
                    anims.add(new Anim(v, fr, fc, tr, tc));
                    if (j != pos) moved = true;
                    prevVal = v;
                    mergeable = true;
                    pos++;
                }
            }
            if (merged) moved = true;
            for (int j = 0; j < N; j++)
                board[rc(dir, i, j, true)][rc(dir, i, j, false)] = result[j];
        }

        if (!moved) { anims.clear(); return; }  // keep prior undo state

        // clear pops; merged/spawn pops are set when the slide finishes
        for (int y = 0; y < N; y++)
            for (int x = 0; x < N; x++) pop[y][x] = 0;

        canUndo = true;
        if (merged) performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY,
                HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING);
        sliding = true;
        slideStart = SystemClock.uptimeMillis();
        invalidate();
    }

    private void finishSlide() {
        sliding = false;
        anims.clear();
        long now = SystemClock.uptimeMillis();
        // pop every tile that is now at a value >= 4 sitting where a merge landed?
        // Simplest: pop the freshly spawned tile only; merges already felt via haptics.
        spawn();
        if (score > best) { best = score; prefs.edit().putInt("best", best).apply(); }
        if (!showWin && has(TARGET)) showWin = true;
        if (!canMove()) over = true;
        invalidate();
    }

    private boolean has(int v) {
        for (int y = 0; y < N; y++)
            for (int x = 0; x < N; x++) if (board[y][x] == v) return true;
        return false;
    }

    private boolean canMove() {
        for (int y = 0; y < N; y++)
            for (int x = 0; x < N; x++) {
                if (board[y][x] == 0) return true;
                if (x + 1 < N && board[y][x] == board[y][x + 1]) return true;
                if (y + 1 < N && board[y][x] == board[y + 1][x]) return true;
            }
        return false;
    }

    private void undo() {
        if (!canUndo || sliding) return;
        for (int y = 0; y < N; y++) System.arraycopy(prevBoard[y], 0, board[y], 0, N);
        score = prevScore;
        canUndo = false;
        over = false;
        for (int y = 0; y < N; y++)
            for (int x = 0; x < N; x++) pop[y][x] = 0;
        invalidate();
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = e.getX(); downY = e.getY();
                return true;
            case MotionEvent.ACTION_UP: {
                float dx = e.getX() - downX, dy = e.getY() - downY;
                float adx = Math.abs(dx), ady = Math.abs(dy);
                if (adx < 40 && ady < 40) {
                    if (newBtn.contains(e.getX(), e.getY())) newGame();
                    else if (undoBtn.contains(e.getX(), e.getY())) undo();
                    else if (showWin) showWin = false;   // continue after winning
                    else if (over) newGame();
                    return true;
                }
                if (adx > ady) move(dx > 0 ? 1 : 0);
                else move(dy > 0 ? 3 : 2);
                return true;
            }
        }
        return super.onTouchEvent(e);
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        float margin = w * 0.05f;
        boardSize = w - margin * 2f;
        boardLeft = margin;
        boardTop = h * 0.32f;
        gap = boardSize * 0.030f;
        cell = (boardSize - gap * (N + 1)) / N;
        radius = cell * 0.14f;

        float btnH = h * 0.070f;
        float btnW = (w - margin * 2f - gap) / 2f;
        float by = h * 0.205f;
        undoBtn.set(boardLeft, by, boardLeft + btnW, by + btnH);
        newBtn.set(boardLeft + btnW + gap, by, w - margin, by + btnH);
    }

    private float px(float col) { return boardLeft + gap + col * (cell + gap); }
    private float py(float row) { return boardTop + gap + row * (cell + gap); }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth(), h = getHeight();
        canvas.drawColor(C_BG);

        // title
        text.setTextAlign(Paint.Align.LEFT);
        text.setTextSize(h * 0.050f);
        text.setColor(C_ACCENT);
        canvas.drawText("NOVA", boardLeft, h * 0.090f, text);
        text.setColor(C_ACCENT2);
        canvas.drawText("2048", boardLeft + text.measureText("NOVA "), h * 0.090f, text);
        text.setTextAlign(Paint.Align.CENTER);

        // score + best chips
        float chipW = (boardSize - gap) / 2f;
        drawChip(canvas, boardLeft, h * 0.110f, chipW, h * 0.075f, "SCORE", score);
        drawChip(canvas, boardLeft + chipW + gap, h * 0.110f, chipW, h * 0.075f, "BEST", best);

        drawButton(canvas, undoBtn, "UNDO", canUndo ? C_ACCENT2 : C_EMPTY,
                canUndo ? C_BG : C_TEXT_LO);
        drawButton(canvas, newBtn, "NEW GAME", C_ACCENT, C_BG);

        // board background + empty cells
        fill.setColor(C_PANEL);
        r.set(boardLeft, boardTop, boardLeft + boardSize, boardTop + boardSize);
        canvas.drawRoundRect(r, radius * 1.4f, radius * 1.4f, fill);
        for (int y = 0; y < N; y++)
            for (int x = 0; x < N; x++) {
                fill.setColor(C_EMPTY);
                r.set(px(x), py(y), px(x) + cell, py(y) + cell);
                canvas.drawRoundRect(r, radius, radius, fill);
            }

        long now = SystemClock.uptimeMillis();

        if (sliding) {
            float t = (now - slideStart) / (float) SLIDE_MS;
            if (t >= 1f) {
                finishSlide();
            } else {
                float e = easeOut(t);
                for (int k = 0; k < anims.size(); k++) {
                    Anim a = anims.get(k);
                    float col = a.fc + (a.tc - a.fc) * e;
                    float row = a.fr + (a.tr - a.fr) * e;
                    drawTile(canvas, px(col), py(row), 1f, a.value);
                }
                postInvalidateOnAnimation();
            }
        }

        if (!sliding) {
            for (int y = 0; y < N; y++)
                for (int x = 0; x < N; x++) {
                    int v = board[y][x];
                    if (v == 0) continue;
                    float scale = 1f;
                    long a = pop[y][x];
                    if (a != 0) {
                        long dt = now - a;
                        if (dt < POP_MS) {
                            scale = 0.5f + 0.5f * easeOut(dt / (float) POP_MS);
                            postInvalidateOnAnimation();
                        }
                    }
                    drawTile(canvas, px(x), py(y), scale, v);
                }
        }

        if (showWin || over) {
            fill.setColor(0xCC0A0E1C);
            r.set(boardLeft, boardTop, boardLeft + boardSize, boardTop + boardSize);
            canvas.drawRoundRect(r, radius * 1.4f, radius * 1.4f, fill);
            text.setColor(over ? C_ACCENT2 : C_ACCENT);
            text.setTextSize(boardSize * 0.12f);
            canvas.drawText(over ? "GAME OVER" : "2048!",
                    boardLeft + boardSize / 2f, boardTop + boardSize * 0.46f, text);
            text.setColor(C_TEXT_LO);
            text.setTextSize(boardSize * 0.050f);
            canvas.drawText(over ? "tap to play again" : "tap to keep going",
                    boardLeft + boardSize / 2f, boardTop + boardSize * 0.58f, text);
        }

        text.setColor(C_TEXT_LO);
        text.setTextSize(h * 0.026f);
        canvas.drawText("swipe to move the tiles",
                w / 2f, boardTop + boardSize + h * 0.055f, text);
    }

    private void drawTile(Canvas c, float x, float y, float scale, int v) {
        float inset = cell * (1f - scale) / 2f;
        r.set(x + inset, y + inset, x + cell - inset, y + cell - inset);
        fill.setColor(tileColor(v));
        c.drawRoundRect(r, radius, radius, fill);
        text.setColor(v <= 4 ? C_BG : C_TEXT_HI);
        String s = String.valueOf(v);
        text.setTextSize(cell * (s.length() >= 4 ? 0.30f : s.length() == 3 ? 0.36f : 0.44f) * scale);
        c.drawText(s, x + cell / 2f, y + cell / 2f + text.getTextSize() * 0.35f, text);
    }

    private void drawChip(Canvas c, float x, float y, float w, float h, String label, int val) {
        fill.setColor(C_PANEL);
        r.set(x, y, x + w, y + h);
        c.drawRoundRect(r, radius, radius, fill);
        text.setColor(C_TEXT_LO);
        text.setTextSize(h * 0.28f);
        c.drawText(label, x + w / 2f, y + h * 0.38f, text);
        text.setColor(C_TEXT_HI);
        text.setTextSize(h * 0.42f);
        c.drawText(String.valueOf(val), x + w / 2f, y + h * 0.86f, text);
    }

    private void drawButton(Canvas c, RectF b, String label, int bg, int fg) {
        fill.setColor(bg);
        c.drawRoundRect(b, radius, radius, fill);
        text.setColor(fg);
        text.setTextSize(b.height() * 0.34f);
        c.drawText(label, b.centerX(), b.centerY() + text.getTextSize() * 0.35f, text);
    }

    private static float easeOut(float t) { return 1f - (1f - t) * (1f - t); }

    private int tileColor(int v) {
        switch (v) {
            case 2:    return 0xFF2E63FF;
            case 4:    return 0xFF19E3C2;
            case 8:    return 0xFF16B67A;
            case 16:   return 0xFFA6D93B;
            case 32:   return 0xFFF2C13D;
            case 64:   return 0xFFFF8A3D;
            case 128:  return 0xFFFF5C7A;
            case 256:  return 0xFFFF3DAE;
            case 512:  return 0xFFB14BFF;
            case 1024: return 0xFF7C5CFF;
            case 2048: return 0xFF35F0E0;
            default:   return 0xFFEDEFFF;
        }
    }
}
