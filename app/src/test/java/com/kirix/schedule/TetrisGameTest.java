package com.kirix.schedule;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

public class TetrisGameTest {

    private TetrisGame game;

    @Before
    public void setUp() {
        game = new TetrisGame();
        game.setRandomSeed(42L);
    }

    @Test
    public void testInitialState() {
        assertEquals(TetrisGame.STATE_NOT_STARTED, game.getState());
        assertEquals(0, game.getScore());
        assertEquals(1, game.getLevel());
        assertEquals(0, game.getTotalLines());
    }

    @Test
    public void testStartSetsRunning() {
        game.start();
        assertEquals(TetrisGame.STATE_RUNNING, game.getState());
    }

    @Test
    public void testBoardIsEmptyAfterStart() {
        game.start();
        int[][] board = game.getBoard();
        for (int r = TetrisGame.BUFFER_ROWS; r < TetrisGame.ROWS + TetrisGame.BUFFER_ROWS; r++) {
            for (int c = 0; c < TetrisGame.COLS; c++) {
                assertEquals(0, board[r][c]);
            }
        }
    }

    @Test
    public void testMoveLeft() {
        game.start();
        int col = game.getCurrentCol();
        game.moveLeft();
        assertEquals(col - 1, game.getCurrentCol());
    }

    @Test
    public void testMoveRight() {
        game.start();
        int col = game.getCurrentCol();
        game.moveRight();
        assertEquals(col + 1, game.getCurrentCol());
    }

    @Test
    public void testMoveLeftAtBoundary() {
        game.start();
        for (int i = 0; i < 20; i++) game.moveLeft();
        int col = game.getCurrentCol();
        game.moveLeft();
        assertEquals(col, game.getCurrentCol());
    }

    @Test
    public void testMoveRightAtBoundary() {
        game.start();
        for (int i = 0; i < 20; i++) game.moveRight();
        int col = game.getCurrentCol();
        game.moveRight();
        assertEquals(col, game.getCurrentCol());
    }

    @Test
    public void testSoftDropIncreasesScore() {
        game.start();
        int score = game.getScore();
        game.softDrop();
        assertTrue(game.getScore() > score);
    }

    @Test
    public void testHardDrop() {
        game.start();
        game.hardDrop();
        // Game should still be running after hard drop (piece locks, new piece spawns)
        assertEquals(TetrisGame.STATE_RUNNING, game.getState());
    }

    @Test
    public void testPauseResume() {
        game.start();
        game.pause();
        assertEquals(TetrisGame.STATE_PAUSED, game.getState());
        game.resume();
        assertEquals(TetrisGame.STATE_RUNNING, game.getState());
    }

    @Test
    public void testNoMovementWhenPaused() {
        game.start();
        game.pause();
        int col = game.getCurrentCol();
        game.moveLeft();
        assertEquals(col, game.getCurrentCol());
        game.moveRight();
        assertEquals(col, game.getCurrentCol());
    }

    @Test
    public void testNoTickWhenPaused() {
        game.start();
        game.pause();
        int row = game.getCurrentRow();
        game.tick();
        assertEquals(row, game.getCurrentRow());
    }

    @Test
    public void testRotate() {
        game.start();
        int rotation = game.getCurrentRotation();
        game.rotate();
        assertNotEquals(rotation, game.getCurrentRotation());
    }

    @Test
    public void testRotateDoesNotGoOutOfBounds() {
        game.start();
        for (int i = 0; i < 10; i++) game.moveLeft();
        game.rotate();
        assertTrue(game.getCurrentCol() >= -2);
        assertTrue(game.getCurrentCol() < TetrisGame.COLS + 2);
    }

    @Test
    public void testHardDropLocksPiece() {
        game.start();
        int[][] boardBefore = copyBoard(game.getBoard());
        int type = game.getCurrentType();
        int rotation = game.getCurrentRotation();
        int row = game.getCurrentRow();
        int col = game.getCurrentCol();
        // Place piece cells on board before to simulate near-lock
        int[][] cells = TetrominoType.cells(type, rotation);
        for (int[] cell : cells) {
            int r = row + cell[0];
            int c = col + cell[1];
            if (r >= 0 && r < TetrisGame.ROWS + TetrisGame.BUFFER_ROWS && c >= 0 && c < TetrisGame.COLS) {
                boardBefore[r][c] = 0;
            }
        }
        game.hardDrop();
        // After hard drop, a new piece should exist
        assertNotEquals(type, game.getCurrentType());
    }

    @Test
    public void testFullLineIsCleared() {
        game.start();
        int[][] board = game.getBoard();
        int totalRows = TetrisGame.ROWS + TetrisGame.BUFFER_ROWS;
        // Fill entire bottom row
        for (int c = 0; c < TetrisGame.COLS; c++) {
            board[totalRows - 1][c] = 1;
        }
        int linesBefore = game.getTotalLines();
        game.hardDrop();
        assertTrue(game.getTotalLines() > linesBefore);
    }

    @Test
    public void testMultipleLinesCleared() {
        game.start();
        int[][] board = game.getBoard();
        int totalRows = TetrisGame.ROWS + TetrisGame.BUFFER_ROWS;
        // Fill two bottom rows
        for (int c = 0; c < TetrisGame.COLS; c++) {
            board[totalRows - 1][c] = 1;
            board[totalRows - 2][c] = 1;
        }
        int scoreBefore = game.getScore();
        game.hardDrop();
        assertTrue(game.getScore() > scoreBefore);
    }

    @Test
    public void testLevelIncreases() {
        game.start();
        int levelBefore = game.getLevel();
        int[][] board = game.getBoard();
        int totalRows = TetrisGame.ROWS + TetrisGame.BUFFER_ROWS;
        for (int i = 0; i < 12; i++) {
            // Re-fill bottom row each iteration
            for (int c = 0; c < TetrisGame.COLS; c++) {
                board[totalRows - 1][c] = 1;
            }
            game.hardDrop();
        }
        assertTrue(game.getLevel() > levelBefore);
    }

    @Test
    public void testRestart() {
        game.start();
        game.softDrop();
        game.softDrop();
        // Fill board to cause game over
        int[][] board = game.getBoard();
        int totalRows = TetrisGame.ROWS + TetrisGame.BUFFER_ROWS;
        for (int r = TetrisGame.BUFFER_ROWS; r < totalRows; r++) {
            for (int c = 0; c < TetrisGame.COLS; c++) {
                board[r][c] = 1;
            }
        }
        // Hard drop to trigger game over
        game.hardDrop();
        // Now start a new game
        game.start();
        assertEquals(0, game.getScore());
        assertEquals(1, game.getLevel());
        assertEquals(0, game.getTotalLines());
        assertEquals(TetrisGame.STATE_RUNNING, game.getState());
    }

    @Test
    public void testGhostRow() {
        game.start();
        int ghostRow = game.getGhostRow();
        assertTrue(ghostRow >= game.getCurrentRow());
        assertTrue(ghostRow < TetrisGame.ROWS + TetrisGame.BUFFER_ROWS);
    }

    @Test
    public void testDropInterval() {
        game.start();
        long interval = game.getDropInterval();
        assertTrue(interval > 0);
        assertTrue(interval <= 800);
    }

    @Test
    public void testHardDropScoreBonus() {
        game.start();
        int scoreBefore = game.getScore();
        game.hardDrop();
        assertTrue(game.getScore() > scoreBefore);
    }

    @Test
    public void testTetrominoTypeHasSevenTypes() {
        assertEquals(7, TetrominoType.COUNT);
    }

    @Test
    public void testTetrominoCellsNotEmpty() {
        for (int type = 0; type < TetrominoType.COUNT; type++) {
            for (int rot = 0; rot < 4; rot++) {
                int[][] cells = TetrominoType.cells(type, rot);
                assertNotNull(cells);
                assertEquals(4, cells.length);
            }
        }
    }

    @Test
    public void testTetrominoColor() {
        for (int type = 0; type < TetrominoType.COUNT; type++) {
            int color = TetrominoType.colorForType(type);
            assertTrue(color != 0);
        }
    }

    @Test
    public void testNextQueueHasFivePieces() {
        game.start();
        int[] q = game.getNextQueue();
        assertEquals(TetrisGame.NEXT_COUNT, q.length);
        for (int t : q) {
            assertTrue(t >= 0 && t < TetrominoType.COUNT);
        }
    }

    @Test
    public void testQueueAdvancesAfterLock() {
        game.start();
        int[] before = game.getNextQueue();
        game.hardDrop();
        assertEquals(before[0], game.getCurrentType());
        assertEquals(before[1], game.getNextQueue()[0]);
    }

    @Test
    public void testRestoreStateKeepsQueue() {
        game.start();
        int[] q = game.getNextQueue();
        int rows = TetrisGame.ROWS + TetrisGame.BUFFER_ROWS;
        int[][] b = game.getBoard();
        int[] flat = new int[rows * TetrisGame.COLS];
        int i = 0;
        for (int r = 0; r < rows; r++) {
            for (int col = 0; col < TetrisGame.COLS; col++) {
                flat[i++] = b[r][col];
            }
        }
        TetrisGame restored = new TetrisGame();
        restored.restoreState(flat, 123, 2, 11, game.getCurrentType(), game.getCurrentRotation(),
                game.getCurrentRow(), game.getCurrentCol(), q, TetrisGame.STATE_PAUSED);
        assertArrayEquals(q, restored.getNextQueue());
        assertEquals(123, restored.getScore());
        assertEquals(2, restored.getLevel());
        assertEquals(TetrisGame.STATE_PAUSED, restored.getState());
    }

    private int[][] copyBoard(int[][] source) {
        int[][] copy = new int[source.length][];
        for (int i = 0; i < source.length; i++) {
            copy[i] = source[i].clone();
        }
        return copy;
    }
}