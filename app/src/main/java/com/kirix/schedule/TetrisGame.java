package com.kirix.schedule;

import java.util.Random;

final class TetrisGame {

    static final int COLS = 10;
    static final int ROWS = 20;
    static final int BUFFER_ROWS = 2;

    static final int STATE_NOT_STARTED = 0;
    static final int STATE_RUNNING = 1;
    static final int STATE_PAUSED = 2;
    static final int STATE_GAME_OVER = 3;
    static final int NEXT_COUNT = 5;

    private static final int[] LINES_PER_LEVEL = {0, 10, 20, 30, 40, 50, 60, 70, 80, 90, 100};
    private static final long[] LEVEL_SPEEDS = {
            800, 720, 630, 550, 470, 380, 300, 220, 140, 100, 80, 60, 50
    };

    private final int[][] board = new int[ROWS + BUFFER_ROWS][COLS];
    private final Random random = new Random();

    private int state = STATE_NOT_STARTED;
    private int score;
    private int level = 1;
    private int totalLines;

    private int currentType;
    private int currentRotation;
    private int currentRow;
    private int currentCol;

    private final int[] nextQueue = new int[NEXT_COUNT];

    private Listener listener;

    interface Listener {
        void onStateChanged(int state);
        void onBoardChanged();
        void onScoreChanged(int score, int level, int lines);
    }

    void setListener(Listener listener) {
        this.listener = listener;
    }

    int getState() {
        return state;
    }

    int getScore() {
        return score;
    }

    int getLevel() {
        return level;
    }

    int getTotalLines() {
        return totalLines;
    }

    int[][] getBoard() {
        return board;
    }

    int getCurrentType() {
        return currentType;
    }

    int getCurrentRotation() {
        return currentRotation;
    }

    int getCurrentRow() {
        return currentRow;
    }

    int getCurrentCol() {
        return currentCol;
    }

    int getNextType() {
        return nextQueue[0];
    }

    int[] getNextQueue() {
        return nextQueue.clone();
    }

    int getGhostRow() {
        int row = currentRow;
        while (canPlace(currentType, currentRotation, row + 1, currentCol)) {
            row++;
        }
        return row;
    }

    long getDropInterval() {
        int idx = Math.min(Math.max(level - 1, 0), LEVEL_SPEEDS.length - 1);
        return LEVEL_SPEEDS[idx];
    }

    void start() {
        resetBoard();
        score = 0;
        level = 1;
        totalLines = 0;
        for (int i = 0; i < NEXT_COUNT; i++) {
            nextQueue[i] = randomType();
        }
        spawnPiece();
        state = STATE_RUNNING;
        notifyStateChanged();
        notifyScoreChanged();
        notifyBoardChanged();
    }

    void pause() {
        if (state == STATE_RUNNING) {
            state = STATE_PAUSED;
            notifyStateChanged();
        }
    }

    void resume() {
        if (state == STATE_PAUSED) {
            state = STATE_RUNNING;
            notifyStateChanged();
        }
    }

    boolean isRunning() {
        return state == STATE_RUNNING;
    }

    boolean isPaused() {
        return state == STATE_PAUSED;
    }

    void moveLeft() {
        if (state != STATE_RUNNING) return;
        if (canPlace(currentType, currentRotation, currentRow, currentCol - 1)) {
            currentCol--;
            notifyBoardChanged();
        }
    }

    void moveRight() {
        if (state != STATE_RUNNING) return;
        if (canPlace(currentType, currentRotation, currentRow, currentCol + 1)) {
            currentCol++;
            notifyBoardChanged();
        }
    }

    void softDrop() {
        if (state != STATE_RUNNING) return;
        if (canPlace(currentType, currentRotation, currentRow + 1, currentCol)) {
            currentRow++;
            score += 1;
            notifyScoreChanged();
            notifyBoardChanged();
        }
    }

    void hardDrop() {
        if (state != STATE_RUNNING) return;
        int dropDistance = 0;
        while (canPlace(currentType, currentRotation, currentRow + 1, currentCol)) {
            currentRow++;
            dropDistance++;
        }
        score += dropDistance * 2;
        lockPiece();
        notifyScoreChanged();
    }

    void rotate() {
        if (state != STATE_RUNNING) return;
        int newRotation = (currentRotation + 1) & 3;
        // Пробуем поворот как есть
        if (canPlace(currentType, newRotation, currentRow, currentCol)) {
            currentRotation = newRotation;
            notifyBoardChanged();
            return;
        }
        // Wall kick: пробуем сдвиг влево
        if (canPlace(currentType, newRotation, currentRow, currentCol - 1)) {
            currentRotation = newRotation;
            currentCol--;
            notifyBoardChanged();
            return;
        }
        // Wall kick: пробуем сдвиг вправо
        if (canPlace(currentType, newRotation, currentRow, currentCol + 1)) {
            currentRotation = newRotation;
            currentCol++;
            notifyBoardChanged();
            return;
        }
        // Wall kick: сдвиг на 2 влево (для I-фигуры)
        if (canPlace(currentType, newRotation, currentRow, currentCol - 2)) {
            currentRotation = newRotation;
            currentCol -= 2;
            notifyBoardChanged();
            return;
        }
        // Wall kick: сдвиг на 2 вправо
        if (canPlace(currentType, newRotation, currentRow, currentCol + 2)) {
            currentRotation = newRotation;
            currentCol += 2;
            notifyBoardChanged();
        }
    }

    void tick() {
        if (state != STATE_RUNNING) return;
        if (canPlace(currentType, currentRotation, currentRow + 1, currentCol)) {
            currentRow++;
            notifyBoardChanged();
        } else {
            lockPiece();
        }
    }

    private void lockPiece() {
        int[][] cells = TetrominoType.cells(currentType, currentRotation);
        for (int[] cell : cells) {
            int r = currentRow + cell[0];
            int c = currentCol + cell[1];
            if (r >= 0 && r < ROWS + BUFFER_ROWS && c >= 0 && c < COLS) {
                board[r][c] = currentType + 1;
            }
        }
        int cleared = clearLines();
        if (cleared > 0) {
            addScoreForLines(cleared);
        }
        spawnPiece();
        if (!canPlace(currentType, currentRotation, currentRow, currentCol)) {
            state = STATE_GAME_OVER;
            notifyStateChanged();
        }
        notifyScoreChanged();
        notifyBoardChanged();
    }

    private int clearLines() {
        int cleared = 0;
        for (int r = ROWS + BUFFER_ROWS - 1; r >= 0; r--) {
            boolean full = true;
            for (int c = 0; c < COLS; c++) {
                if (board[r][c] == 0) {
                    full = false;
                    break;
                }
            }
            if (full) {
                cleared++;
                // Сдвигаем всё вниз
                for (int shift = r; shift > 0; shift--) {
                    System.arraycopy(board[shift - 1], 0, board[shift], 0, COLS);
                }
                for (int c = 0; c < COLS; c++) {
                    board[0][c] = 0;
                }
                r++; // Проверяем ту же строку заново
            }
        }
        return cleared;
    }

    private void addScoreForLines(int lines) {
        int points;
        switch (lines) {
            case 1: points = 100; break;
            case 2: points = 300; break;
            case 3: points = 500; break;
            case 4: points = 800; break;
            default: points = lines * 200; break;
        }
        score += points * level;
        totalLines += lines;
        updateLevel();
    }

    private void updateLevel() {
        int newLevel = 1;
        for (int i = LINES_PER_LEVEL.length - 1; i >= 0; i--) {
            if (totalLines >= LINES_PER_LEVEL[i]) {
                newLevel = i + 1;
                break;
            }
        }
        if (newLevel > level) {
            level = Math.min(newLevel, LEVEL_SPEEDS.length);
        }
    }

    private void spawnPiece() {
        currentType = nextQueue[0];
        System.arraycopy(nextQueue, 1, nextQueue, 0, NEXT_COUNT - 1);
        nextQueue[NEXT_COUNT - 1] = randomType();
        currentRotation = 0;
        // Центрируем фигуру, начиная сверху видимой области (с учётом буфера)
        int[][] cells = TetrominoType.cells(currentType, currentRotation);
        int minCol = Integer.MAX_VALUE;
        int maxCol = Integer.MIN_VALUE;
        for (int[] cell : cells) {
            if (cell[1] < minCol) minCol = cell[1];
            if (cell[1] > maxCol) maxCol = cell[1];
        }
        int pieceWidth = maxCol - minCol + 1;
        currentCol = (COLS - pieceWidth) / 2 - minCol;
        currentRow = 0;
    }

    private boolean canPlace(int type, int rotation, int row, int col) {
        int[][] cells = TetrominoType.cells(type, rotation);
        for (int[] cell : cells) {
            int r = row + cell[0];
            int c = col + cell[1];
            if (c < 0 || c >= COLS || r >= ROWS + BUFFER_ROWS) return false;
            if (r < 0) continue;
            if (board[r][c] != 0) return false;
        }
        return true;
    }

    private int randomType() {
        return random.nextInt(TetrominoType.COUNT);
    }

    private void resetBoard() {
        for (int r = 0; r < board.length; r++) {
            for (int c = 0; c < COLS; c++) {
                board[r][c] = 0;
            }
        }
    }

    void setBoardForTest(int[][] testBoard, int testType, int testRotation, int testRow, int testCol) {
        for (int r = 0; r < board.length; r++) {
            System.arraycopy(testBoard[r], 0, board[r], 0, COLS);
        }
        currentType = testType;
        currentRotation = testRotation;
        currentRow = testRow;
        currentCol = testCol;
    }

    void setRandomSeed(long seed) {
        random.setSeed(seed);
    }

    void restoreState(int[] flatBoard, int score, int level, int lines,
                      int curType, int curRotation, int curRow, int curCol,
                      int[] savedQueue, int state) {
        int i = 0;
        for (int r = 0; r < board.length && i < flatBoard.length; r++) {
            for (int col = 0; col < COLS && i < flatBoard.length; col++) {
                board[r][col] = flatBoard[i++];
            }
        }
        this.score = score;
        this.level = level;
        this.totalLines = lines;
        this.currentType = curType;
        this.currentRotation = curRotation;
        this.currentRow = curRow;
        this.currentCol = curCol;
        int n = Math.min(savedQueue == null ? 0 : savedQueue.length, NEXT_COUNT);
        for (int k = 0; k < n; k++) {
            this.nextQueue[k] = savedQueue[k];
        }
        for (int k = n; k < NEXT_COUNT; k++) {
            this.nextQueue[i] = randomType();
        }
        this.state = state;
        notifyStateChanged();
        notifyScoreChanged();
        notifyBoardChanged();
    }

    private void notifyStateChanged() {
        if (listener != null) listener.onStateChanged(state);
    }

    private void notifyBoardChanged() {
        if (listener != null) listener.onBoardChanged();
    }

    private void notifyScoreChanged() {
        if (listener != null) listener.onScoreChanged(score, level, totalLines);
    }
}