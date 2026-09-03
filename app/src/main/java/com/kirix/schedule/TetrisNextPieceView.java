package com.kirix.schedule;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

final class TetrisNextPieceView extends View {

    private final Paint cellPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();

    private int pieceType = -1;
    private float cellSize;
    private float offsetX;
    private float offsetY;

    public TetrisNextPieceView(Context context) {
        super(context);
        init();
    }

    public TetrisNextPieceView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public TetrisNextPieceView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        bgPaint.setColor(0xFF0D1117);
    }

    void setPieceType(int type) {
        this.pieceType = type;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int w = getWidth();
        int h = getHeight();
        canvas.drawRect(0, 0, w, h, bgPaint);

        if (pieceType < 0 || pieceType >= TetrominoType.COUNT) return;

        int[][] cells = TetrominoType.cells(pieceType, 0);
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
        cellSize = Math.min((float) w / (pieceW + 1), (float) h / (pieceH + 1));
        offsetX = (w - pieceW * cellSize) / 2f;
        offsetY = (h - pieceH * cellSize) / 2f;

        int color = TetrominoType.colorForType(pieceType);
        cellPaint.setColor(color);
        float gap = 1f;
        for (int[] cell : cells) {
            float left = offsetX + (cell[1] - minC) * cellSize;
            float top = offsetY + (cell[0] - minR) * cellSize;
            rect.set(left + gap, top + gap, left + cellSize - gap, top + cellSize - gap);
            canvas.drawRoundRect(rect, 2f, 2f, cellPaint);
        }
    }
}