package com.kirix.schedule;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

final class TetrisNextQueueView extends View {

    private static final int SHOWN = 3;

    private final Paint cellPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glossPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint edgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint slotBg = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint slotBorder = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final RectF slot = new RectF();

    private int[] queue = new int[0];

    public TetrisNextQueueView(Context context) {
        super(context);
        init();
    }

    public TetrisNextQueueView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public TetrisNextQueueView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        slotBg.setColor(0xFF0B1224);
        slotBorder.setColor(0xFF1A2440);
        slotBorder.setStyle(Paint.Style.STROKE);
        slotBorder.setStrokeWidth(1.5f);
        glossPaint.setColor(0x66FFFFFF);
        edgePaint.setStyle(Paint.Style.STROKE);
        edgePaint.setStrokeWidth(1f);
    }

    void setQueue(int[] queue) {
        this.queue = queue == null ? new int[0] : queue.clone();
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int count = Math.min(Math.min(queue.length, TetrisGame.NEXT_COUNT), SHOWN);
        if (count == 0) return;

        float w = getWidth();
        float h = getHeight();
        float gapBetween = 8f;
        float slotH = (h - gapBetween * (count - 1)) / count;

        for (int s = 0; s < count; s++) {
            float top = s * (slotH + gapBetween);
            slot.set(0, top, w, top + slotH);
            canvas.drawRoundRect(slot, 10f, 10f, slotBg);
            canvas.drawRoundRect(slot, 10f, 10f, slotBorder);

            int type = queue[s];
            if (type < 0 || type >= TetrominoType.COUNT) continue;
            int[][] cells = TetrominoType.cells(type, 0);
            int minR = Integer.MAX_VALUE, maxR = Integer.MIN_VALUE;
            int minC = Integer.MAX_VALUE, maxC = Integer.MIN_VALUE;
            for (int[] cell : cells) {
                if (cell[0] < minR) minR = cell[0];
                if (cell[0] > maxR) maxR = cell[0];
                if (cell[1] < minC) minC = cell[1];
                if (cell[1] > maxC) maxC = cell[1];
            }
            int pieceW = maxC - minC + 1;
            int pieceH = maxR - minR + 1;
            float cell = Math.min((w - 20f) / (pieceW + 0.6f), (slotH - 16f) / (pieceH + 0.6f));
            if (cell <= 0) continue;
            float ox = (w - pieceW * cell) / 2f;
            float oy = top + (slotH - pieceH * cell) / 2f;
            int color = TetrominoType.colorForType(type);
            int dark = TetrominoType.darkForType(type);
            for (int[] cellPos : cells) {
                float left = ox + (cellPos[1] - minC) * cell;
                float cTop = oy + (cellPos[0] - minR) * cell;
                float g = Math.max(1f, cell * 0.08f);
                rect.set(left + g, cTop + g, left + cell - g, cTop + cell - g);
                float r = Math.max(2f, cell * 0.18f);
                cellPaint.setColor(color);
                cellPaint.setStyle(Paint.Style.FILL);
                canvas.drawRoundRect(rect, r, r, cellPaint);
                edgePaint.setColor(dark);
                canvas.drawRoundRect(rect, r, r, edgePaint);
                RectF gloss = new RectF(rect.left + 1, rect.top + 1,
                        rect.right - 1, rect.top + rect.height() * 0.38f);
                canvas.drawRoundRect(gloss, r * 0.7f, r * 0.7f, glossPaint);
            }
        }
    }
}
