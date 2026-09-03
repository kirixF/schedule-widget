package com.kirix.schedule;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

final class TetrisNextQueueView extends View {

    private final Paint cellPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();

    private int[] queue = new int[0];

    public TetrisNextQueueView(Context context) {
        super(context);
    }

    public TetrisNextQueueView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public TetrisNextQueueView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    void setQueue(int[] queue) {
        this.queue = queue == null ? new int[0] : queue.clone();
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int count = Math.min(queue.length, TetrisGame.NEXT_COUNT);
        if (count == 0) return;

        float w = getWidth();
        float slotH = (float) getHeight() / count;
        float gap = 1f;

        for (int s = 0; s < count; s++) {
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
            float cell = Math.min(w / (pieceW + 1), slotH / (pieceH + 1));
            float ox = (w - pieceW * cell) / 2f;
            float oy = s * slotH + (slotH - pieceH * cell) / 2f;
            cellPaint.setColor(TetrominoType.colorForType(type));
            for (int[] cellPos : cells) {
                float left = ox + (cellPos[1] - minC) * cell;
                float top = oy + (cellPos[0] - minR) * cell;
                rect.set(left + gap, top + gap, left + cell - gap, top + cell - gap);
                canvas.drawRoundRect(rect, 2f, 2f, cellPaint);
            }
        }
    }
}