package com.kirix.schedule;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

final class TetrisBoardView extends View {

    private final Paint cellPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint darkPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint lightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ghostPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint centerBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final RectF bgRect = new RectF();
    private String labelTetris = "Tetris";
    private String labelGameOver = "Game Over";
    private String labelPaused = "Paused";

    private TetrisGame game;
    private float cellSize;
    private int boardPixelWidth;
    private int boardPixelHeight;
    private float offsetX;
    private float offsetY;

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
        bgPaint.setColor(0xFF0A1020);
        gridPaint.setColor(0xFF16203A);
        gridPaint.setStyle(Paint.Style.STROKE);
        gridPaint.setStrokeWidth(1f);
        borderPaint.setColor(0xFF232F4D);
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(3f);
        ghostPaint.setStyle(Paint.Style.STROKE);
        ghostPaint.setStrokeWidth(2f);
        textPaint.setColor(0xFF8899AA);
        textPaint.setTextAlign(Paint.Align.CENTER);
        centerBgPaint.setColor(0xE6000B18);
        lightPaint.setColor(0x59FFFFFF);
        darkPaint.setColor(0x59000000);
    }

    void setGame(TetrisGame game) {
        this.game = game;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int widthSize = MeasureSpec.getSize(widthMeasureSpec);
        int heightSize = MeasureSpec.getSize(heightMeasureSpec);
        float boardRatio = (float) TetrisGame.COLS / TetrisGame.ROWS;
        float availRatio = heightSize == 0 ? boardRatio : (float) widthSize / heightSize;
        if (availRatio > boardRatio) {
            boardPixelHeight = heightSize;
            boardPixelWidth = (int) (heightSize * boardRatio);
        } else {
            boardPixelWidth = widthSize;
            boardPixelHeight = (int) (widthSize / boardRatio);
        }
        cellSize = (float) boardPixelWidth / TetrisGame.COLS;
        boardPixelWidth = (int) (cellSize * TetrisGame.COLS);
        boardPixelHeight = (int) (cellSize * TetrisGame.ROWS);
        offsetX = (widthSize - boardPixelWidth) / 2f;
        offsetY = (heightSize - boardPixelHeight) / 2f;
        setMeasuredDimension(widthSize, heightSize);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (game == null) return;

        // Panel background with rounded corners.
        bgRect.set(offsetX, offsetY,
                offsetX + boardPixelWidth, offsetY + boardPixelHeight);
        canvas.drawRoundRect(bgRect, 16f, 16f, bgPaint);

        canvas.save();
        canvas.translate(offsetX, offsetY);
        // Clip content to rounded panel so blocks do not spill over corners.
        canvas.save();
        canvas.clipRect(0, 0, boardPixelWidth, boardPixelHeight);

        drawEmptyGrid(canvas);
        drawBoard(canvas);
        drawGhost(canvas);
        drawCurrentPiece(canvas);

        canvas.restore();

        canvas.drawRoundRect(new RectF(1.5f, 1.5f,
                boardPixelWidth - 1.5f, boardPixelHeight - 1.5f),
                16f, 16f, borderPaint);

        if (game.getState() == TetrisGame.STATE_NOT_STARTED) {
            drawCenterText(canvas, labelTetris);
        } else if (game.getState() == TetrisGame.STATE_GAME_OVER) {
            drawCenterText(canvas, labelGameOver);
        } else if (game.getState() == TetrisGame.STATE_PAUSED) {
            drawCenterText(canvas, labelPaused);
        }

        canvas.restore();
    }

    private void drawEmptyGrid(Canvas canvas) {
        for (int r = 0; r < TetrisGame.ROWS; r++) {
            for (int c = 0; c < TetrisGame.COLS; c++) {
                float left = c * cellSize;
                float top = r * cellSize;
                canvas.drawRect(left, top, left + cellSize, top + cellSize, gridPaint);
            }
        }
    }

    private void drawBoard(Canvas canvas) {
        int[][] board = game.getBoard();
        for (int r = TetrisGame.BUFFER_ROWS; r < TetrisGame.ROWS + TetrisGame.BUFFER_ROWS; r++) {
            for (int c = 0; c < TetrisGame.COLS; c++) {
                if (board[r][c] != 0) {
                    int row = r - TetrisGame.BUFFER_ROWS;
                    drawBlock(canvas, c * cellSize, row * cellSize,
                            TetrominoType.colorForType(board[r][c] - 1),
                            TetrominoType.darkForType(board[r][c] - 1));
                }
            }
        }
    }

    private void drawGhost(Canvas canvas) {
        if (game.getState() != TetrisGame.STATE_RUNNING) return;
        int ghostRow = game.getGhostRow();
        if (ghostRow == game.getCurrentRow()) return;
        int[][] cells = TetrominoType.cells(game.getCurrentType(), game.getCurrentRotation());
        ghostPaint.setColor(TetrominoType.colorForType(game.getCurrentType()));
        ghostPaint.setAlpha(90);
        for (int[] cell : cells) {
            int r = ghostRow + cell[0] - TetrisGame.BUFFER_ROWS;
            int c = game.getCurrentCol() + cell[1];
            if (r < 0) continue;
            float left = c * cellSize;
            float top = r * cellSize;
            rect.set(left + 1.5f, top + 1.5f, left + cellSize - 1.5f, top + cellSize - 1.5f);
            canvas.drawRoundRect(rect, 4f, 4f, ghostPaint);
        }
        ghostPaint.setAlpha(255);
    }

    private void drawCurrentPiece(Canvas canvas) {
        if (game.getState() != TetrisGame.STATE_RUNNING) return;
        int[][] cells = TetrominoType.cells(game.getCurrentType(), game.getCurrentRotation());
        int color = TetrominoType.colorForType(game.getCurrentType());
        int dark = TetrominoType.darkForType(game.getCurrentType());
        for (int[] cell : cells) {
            int r = game.getCurrentRow() + cell[0] - TetrisGame.BUFFER_ROWS;
            int c = game.getCurrentCol() + cell[1];
            if (r < 0) continue;
            drawBlock(canvas, c * cellSize, r * cellSize, color, dark);
        }
    }

    private void drawBlock(Canvas canvas, float left, float top, int color, int dark) {
        float gap = Math.max(1f, cellSize * 0.06f);
        float radius = Math.max(3f, cellSize * 0.16f);
        // Base.
        cellPaint.setColor(color);
        cellPaint.setStyle(Paint.Style.FILL);
        rect.set(left + gap, top + gap, left + cellSize - gap, top + cellSize - gap);
        canvas.drawRoundRect(rect, radius, radius, cellPaint);
        // Bottom shade.
        darkPaint.setColor(0x55000000);
        RectF shade = new RectF(rect.left, rect.top + rect.height() * 0.55f,
                rect.right, rect.bottom);
        canvas.drawRoundRect(shade, radius, radius, darkPaint);
        // Dark edge.
        darkPaint.setColor(dark);
        darkPaint.setStyle(Paint.Style.STROKE);
        darkPaint.setStrokeWidth(Math.max(1f, cellSize * 0.05f));
        canvas.drawRoundRect(rect, radius, radius, darkPaint);
        darkPaint.setStyle(Paint.Style.FILL);
        // Top gloss.
        lightPaint.setColor(0x66FFFFFF);
        float glossH = rect.height() * 0.34f;
        RectF gloss = new RectF(rect.left + radius * 0.5f, rect.top + radius * 0.4f,
                rect.right - radius * 0.5f, rect.top + glossH);
        canvas.drawRoundRect(gloss, radius * 0.7f, radius * 0.7f, lightPaint);
    }

    private void drawCenterText(Canvas canvas, String text) {
        float cx = boardPixelWidth / 2f;
        float cy = boardPixelHeight / 2f;
        float pad = cellSize;
        textPaint.setTextSize(Math.max(20f, cellSize * 1.1f));
        float tw = textPaint.measureText(text);
        RectF pill = new RectF(cx - tw / 2f - pad, cy - 34, cx + tw / 2f + pad, cy + 34);
        canvas.drawRoundRect(pill, 18f, 18f, centerBgPaint);
        textPaint.setColor(0xFFFFFFFF);
        canvas.drawText(text, cx, cy + textPaint.getTextSize() * 0.35f, textPaint);
    }
}
