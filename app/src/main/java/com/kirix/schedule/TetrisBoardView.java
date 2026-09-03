package com.kirix.schedule;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

final class TetrisBoardView extends View {

    private static final float GAP = 1f;

    private final Paint cellPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ghostPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint centerBgPaint = new Paint();
    private final RectF rect = new RectF();
    private String labelTetris = "Tetris";
    private String labelGameOver = "Game Over";
    private String labelPaused = "Paused";

    private TetrisGame game;
    private float cellSize;
    private int boardPixelWidth;
    private int boardPixelHeight;
    private int offsetX;
    private int offsetY;

    public TetrisBoardView(Context context) {
        super(context);
        init(context);
    }

    public TetrisBoardView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public TetrisBoardView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        labelTetris = context.getString(R.string.tetris_title);
        labelGameOver = context.getString(R.string.tetris_game_over);
        labelPaused = context.getString(R.string.tetris_paused);
        centerBgPaint.setColor(0xCC000000);
        gridPaint.setColor(0xFF1A2332);
        gridPaint.setStyle(Paint.Style.STROKE);
        gridPaint.setStrokeWidth(0.5f);

        borderPaint.setColor(0xFF2A3A4A);
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(2f);

        ghostPaint.setStyle(Paint.Style.STROKE);
        ghostPaint.setStrokeWidth(2f);

        textPaint.setColor(0xFF8899AA);
        textPaint.setTextAlign(Paint.Align.CENTER);
    }

    void setGame(TetrisGame game) {
        this.game = game;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int widthSize = MeasureSpec.getSize(widthMeasureSpec);
        int heightSize = MeasureSpec.getSize(heightMeasureSpec);

        float boardRatio = (float) TetrisGame.COLS / (TetrisGame.ROWS + TetrisGame.BUFFER_ROWS);
        float availRatio = (float) widthSize / heightSize;

        if (availRatio > boardRatio) {
            boardPixelHeight = heightSize;
            boardPixelWidth = (int) (heightSize * boardRatio);
        } else {
            boardPixelWidth = widthSize;
            boardPixelHeight = (int) (widthSize / boardRatio);
        }

        cellSize = (float) boardPixelWidth / TetrisGame.COLS;
        boardPixelWidth = (int) (cellSize * TetrisGame.COLS);
        boardPixelHeight = (int) (cellSize * (TetrisGame.ROWS + TetrisGame.BUFFER_ROWS));

        offsetX = (widthSize - boardPixelWidth) / 2;
        offsetY = (heightSize - boardPixelHeight) / 2;

        setMeasuredDimension(widthSize, heightSize);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (game == null) return;

        canvas.save();
        canvas.translate(offsetX, offsetY);

        drawBoard(canvas);
        drawGhost(canvas);
        drawCurrentPiece(canvas);
        drawBorder(canvas);

        if (game.getState() == TetrisGame.STATE_NOT_STARTED) {
            drawCenterText(canvas, labelTetris);
        } else if (game.getState() == TetrisGame.STATE_GAME_OVER) {
            drawCenterText(canvas, labelGameOver);
        } else if (game.getState() == TetrisGame.STATE_PAUSED) {
            drawCenterText(canvas, labelPaused);
        }

        canvas.restore();
    }

    private void drawBoard(Canvas canvas) {
        int[][] board = game.getBoard();
        for (int r = TetrisGame.BUFFER_ROWS; r < TetrisGame.ROWS + TetrisGame.BUFFER_ROWS; r++) {
            for (int c = 0; c < TetrisGame.COLS; c++) {
                int row = r - TetrisGame.BUFFER_ROWS;
                float left = c * cellSize;
                float top = row * cellSize;
                if (board[r][c] != 0) {
                    int color = TetrominoType.colorForType(board[r][c] - 1);
                    cellPaint.setColor(color);
                    cellPaint.setStyle(Paint.Style.FILL);
                    rect.set(left + GAP, top + GAP, left + cellSize - GAP, top + cellSize - GAP);
                    canvas.drawRoundRect(rect, 2f, 2f, cellPaint);
                    // Highlight
                    cellPaint.setColor(0x33FFFFFF);
                    rect.set(left + GAP, top + GAP, left + cellSize - GAP, top + cellSize * 0.4f);
                    canvas.drawRoundRect(rect, 2f, 2f, cellPaint);
                } else {
                    cellPaint.setColor(0xFF0D1117);
                    cellPaint.setStyle(Paint.Style.FILL);
                    canvas.drawRect(left, top, left + cellSize, top + cellSize, cellPaint);
                    canvas.drawRect(left, top, left + cellSize, top + cellSize, gridPaint);
                }
            }
        }
    }

    private void drawGhost(Canvas canvas) {
        if (game.getState() != TetrisGame.STATE_RUNNING) return;
        int ghostRow = game.getGhostRow();
        if (ghostRow == game.getCurrentRow()) return;
        int[][] cells = TetrominoType.cells(game.getCurrentType(), game.getCurrentRotation());
        int color = TetrominoType.colorForType(game.getCurrentType());
        ghostPaint.setColor(color);
        ghostPaint.setAlpha(60);
        for (int[] cell : cells) {
            int r = ghostRow + cell[0] - TetrisGame.BUFFER_ROWS;
            int c = game.getCurrentCol() + cell[1];
            if (r < 0) continue;
            float left = c * cellSize;
            float top = r * cellSize;
            rect.set(left + GAP, top + GAP, left + cellSize - GAP, top + cellSize - GAP);
            canvas.drawRoundRect(rect, 2f, 2f, ghostPaint);
        }
        ghostPaint.setAlpha(255);
    }

    private void drawCurrentPiece(Canvas canvas) {
        if (game.getState() != TetrisGame.STATE_RUNNING) return;
        int[][] cells = TetrominoType.cells(game.getCurrentType(), game.getCurrentRotation());
        int color = TetrominoType.colorForType(game.getCurrentType());
        cellPaint.setColor(color);
        cellPaint.setStyle(Paint.Style.FILL);
        for (int[] cell : cells) {
            int r = game.getCurrentRow() + cell[0] - TetrisGame.BUFFER_ROWS;
            int c = game.getCurrentCol() + cell[1];
            if (r < 0) continue;
            float left = c * cellSize;
            float top = r * cellSize;
            rect.set(left + GAP, top + GAP, left + cellSize - GAP, top + cellSize - GAP);
            canvas.drawRoundRect(rect, 2f, 2f, cellPaint);
            // Highlight
            cellPaint.setColor(0x44FFFFFF);
            rect.set(left + GAP, top + GAP, left + cellSize - GAP, top + cellSize * 0.4f);
            canvas.drawRoundRect(rect, 2f, 2f, cellPaint);
            cellPaint.setColor(color);
        }
    }

    private void drawBorder(Canvas canvas) {
        canvas.drawRect(0, 0, boardPixelWidth, boardPixelHeight, borderPaint);
    }

    private void drawCenterText(Canvas canvas, String text) {
        float cx = boardPixelWidth / 2f;
        float cy = boardPixelHeight / 2f;
        canvas.drawRect(0, cy - 40, boardPixelWidth, cy + 40, centerBgPaint);
        textPaint.setTextSize(cellSize * 1.5f);
        textPaint.setColor(0xFFFFFFFF);
        canvas.drawText(text, cx, cy + textPaint.getTextSize() * 0.35f, textPaint);
    }
}