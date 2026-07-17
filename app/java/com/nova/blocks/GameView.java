package com.nova.blocks;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.Random;

/**
 * NOVA 2048 board. Handles rendering, swipe input, game logic and a small
 * spawn/merge pop animation. No external resources are used.
 */
public class GameView extends View {

    private static final int N = 4;              // grid size
    private static final int TARGET = 2048;      // win value
    private static final long ANIM_MS = 130;     // pop animation duration

    // --- palette ------------------------------------------------------------
    private static final int C_BG        = 0xFF0A0E1C;
    private static final int C_PANEL     = 0xFF141B36;
    private static final int C_EMPTY     = 0xFF1E2749;
    private static final int C_TEXT_HI   = 0xFFF4F7FF;
    private static final int C_TEXT_LO   = 0xFF8A93C4;
    private static final int C_ACCENT    = 0xFF19E3C2;
    private static final int C_ACCENT2   = 0xFFB14BFF;

    private final int[][] board = new int[N][N];
    private final long[][] appear = new long[N][N]; // spawn/merge timestamps

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();

    private final Random rnd = new Random();
    private final SharedPreferences prefs;

    private int score = 0;
    private int best = 0;
    private boolean won = false;
    private boolean over = false;

    // layout (computed in onSizeChanged)
    private float boardLeft, boardTop, boardSize, cell, gap, radius;
    private final RectF newGameBtn = new RectF();

    // swipe tracking
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
            for (int x = 0; x < N; x++) { board[y][x] = 0; appear[y][x] = 0; }
        score = 0;
        won = false;
        over = false;
        spawn();
        spawn();
        invalidate();
    }

    private boolean spawn() {
        ArrayList<int[]> empty = new ArrayList<int[]>();
        for (int y = 0; y < N; y++)
            for (int x = 0; x < N; x++)
                if (board[y][x] == 0) empty.add(new int[]{x, y});
        if (empty.isEmpty()) return false;
        int[] c = empty.get(rnd.nextInt(empty.size()));
        board[c[1]][c[0]] = rnd.nextInt(10) == 0 ? 4 : 2;
        appear[c[1]][c[0]] = SystemClock.uptimeMillis();
        return true;
    }

    // ---- movement ----------------------------------------------------------
    // dir: 0=left 1=right 2=up 3=down
    private void move(int dir) {
        if (over) return;
        boolean moved = false;
        long now = SystemClock.uptimeMillis();

        for (int i = 0; i < N; i++) {
            int[] line = new int[N];
            for (int j = 0; j < N; j++) line[j] = readCell(dir, i, j);
            int[] merged = collapse(line, now, dir, i);
            for (int j = 0; j < N; j++) {
                if (merged[j] != line[j]) moved = true;
                writeCell(dir, i, j, merged[j]);
            }
        }

        if (moved) {
            spawn();
            if (score > best) { best = score; prefs.edit().putInt("best", best).apply(); }
            if (!won && hasValue(TARGET)) won = true;
            if (!canMove()) over = true;
            invalidate();
        }
    }

    // Reads the j-th tile along a line, oriented so collapse always slides
    // toward index 0.
    private int readCell(int dir, int i, int j) {
        switch (dir) {
            case 0: return board[i][j];           // left:  row i, x=j
            case 1: return board[i][N - 1 - j];   // right
            case 2: return board[j][i];           // up:    col i, y=j
            default: return board[N - 1 - j][i];  // down
        }
    }

    private void writeCell(int dir, int i, int j, int v) {
        switch (dir) {
            case 0: board[i][j] = v; break;
            case 1: board[i][N - 1 - j] = v; break;
            case 2: board[j][i] = v; break;
            default: board[N - 1 - j][i] = v; break;
        }
    }

    private void markAppear(int dir, int i, int j, long now) {
        switch (dir) {
            case 0: appear[i][j] = now; break;
            case 1: appear[i][N - 1 - j] = now; break;
            case 2: appear[j][i] = now; break;
            default: appear[N - 1 - j][i] = now; break;
        }
    }

    // Slides non-zero values toward index 0 and merges equal neighbours once.
    private int[] collapse(int[] line, long now, int dir, int i) {
        int[] out = new int[N];
        int pos = 0;
        int last = 0;      // last placed value (candidate to merge into)
        boolean lastMergeable = false;
        for (int j = 0; j < N; j++) {
            int v = line[j];
            if (v == 0) continue;
            if (lastMergeable && last == v) {
                out[pos - 1] = v * 2;
                score += v * 2;
                markAppear(dir, i, pos - 1, now);
                lastMergeable = false;
            } else {
                out[pos] = v;
                last = v;
                lastMergeable = true;
                pos++;
            }
        }
        return out;
    }

    private boolean hasValue(int val) {
        for (int y = 0; y < N; y++)
            for (int x = 0; x < N; x++)
                if (board[y][x] == val) return true;
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

    // ---- input -------------------------------------------------------------
    @Override
    public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = e.getX();
                downY = e.getY();
                return true;
            case MotionEvent.ACTION_UP: {
                float dx = e.getX() - downX;
                float dy = e.getY() - downY;
                float adx = Math.abs(dx), ady = Math.abs(dy);
                if (adx < 40 && ady < 40) {
                    if (newGameBtn.contains(e.getX(), e.getY())) newGame();
                    else if (over || won) newGame();
                    return true;
                }
                if (adx > ady) move(dx > 0 ? 1 : 0);
                else move(dy > 0 ? 3 : 2);
                return true;
            }
        }
        return super.onTouchEvent(e);
    }

    // ---- layout ------------------------------------------------------------
    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        float margin = w * 0.05f;
        boardSize = w - margin * 2f;
        boardLeft = margin;
        boardTop = h * 0.30f;
        gap = boardSize * 0.030f;
        cell = (boardSize - gap * (N + 1)) / N;
        radius = cell * 0.14f;

        float btnW = w * 0.42f, btnH = h * 0.075f;
        newGameBtn.set(w - margin - btnW, h * 0.185f, w - margin, h * 0.185f + btnH);
    }

    // ---- rendering ---------------------------------------------------------
    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth(), h = getHeight();
        canvas.drawColor(C_BG);

        // title
        text.setColor(C_ACCENT);
        text.setTextSize(h * 0.052f);
        text.setTextAlign(Paint.Align.LEFT);
        canvas.drawText("NOVA", boardLeft, h * 0.095f, text);
        text.setColor(C_ACCENT2);
        float novaW = text.measureText("NOVA ");
        canvas.drawText("2048", boardLeft + novaW, h * 0.095f, text);
        text.setTextAlign(Paint.Align.CENTER);

        // score + best chips
        float chipW = (newGameBtn.left - boardLeft - gap) / 2f;
        drawChip(canvas, boardLeft, h * 0.115f, chipW, h * 0.075f, "SCORE", score);
        drawChip(canvas, boardLeft + chipW + gap, h * 0.115f, chipW, h * 0.075f, "BEST", best);

        // new game button
        fill.setColor(C_ACCENT);
        canvas.drawRoundRect(newGameBtn, radius, radius, fill);
        text.setColor(C_BG);
        text.setTextSize(h * 0.024f);
        canvas.drawText("NEW GAME", newGameBtn.centerX(),
                newGameBtn.centerY() + text.getTextSize() * 0.35f, text);

        // board background
        fill.setColor(C_PANEL);
        r.set(boardLeft, boardTop, boardLeft + boardSize, boardTop + boardSize);
        canvas.drawRoundRect(r, radius * 1.4f, radius * 1.4f, fill);

        long now = SystemClock.uptimeMillis();
        boolean animating = false;

        for (int y = 0; y < N; y++) {
            for (int x = 0; x < N; x++) {
                float cx = boardLeft + gap + x * (cell + gap);
                float cy = boardTop + gap + y * (cell + gap);
                fill.setColor(C_EMPTY);
                r.set(cx, cy, cx + cell, cy + cell);
                canvas.drawRoundRect(r, radius, radius, fill);

                int v = board[y][x];
                if (v == 0) continue;

                float scale = 1f;
                long a = appear[y][x];
                if (a != 0) {
                    long dt = now - a;
                    if (dt < ANIM_MS) {
                        float t = dt / (float) ANIM_MS;
                        scale = 0.55f + 0.45f * easeOut(t);
                        animating = true;
                    }
                }

                float inset = cell * (1f - scale) / 2f;
                r.set(cx + inset, cy + inset, cx + cell - inset, cy + cell - inset);
                fill.setColor(tileColor(v));
                canvas.drawRoundRect(r, radius, radius, fill);

                text.setColor(v <= 4 ? C_BG : C_TEXT_HI);
                String s = String.valueOf(v);
                text.setTextSize(cell * (s.length() >= 4 ? 0.30f : s.length() == 3 ? 0.36f : 0.44f));
                canvas.drawText(s, cx + cell / 2f,
                        cy + cell / 2f + text.getTextSize() * 0.35f, text);
            }
        }

        // overlays
        if (over || won) {
            fill.setColor(0xCC0A0E1C);
            canvas.drawRoundRect(
                    new RectF(boardLeft, boardTop, boardLeft + boardSize, boardTop + boardSize),
                    radius * 1.4f, radius * 1.4f, fill);
            text.setColor(over ? C_ACCENT2 : C_ACCENT);
            text.setTextSize(boardSize * 0.13f);
            canvas.drawText(over ? "GAME OVER" : "YOU WIN!",
                    boardLeft + boardSize / 2f, boardTop + boardSize * 0.46f, text);
            text.setColor(C_TEXT_LO);
            text.setTextSize(boardSize * 0.052f);
            canvas.drawText("tap to play again",
                    boardLeft + boardSize / 2f, boardTop + boardSize * 0.58f, text);
        }

        // hint
        text.setColor(C_TEXT_LO);
        text.setTextSize(h * 0.026f);
        canvas.drawText("swipe to move the tiles",
                w / 2f, boardTop + boardSize + h * 0.06f, text);

        if (animating) postInvalidateOnAnimation();
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
