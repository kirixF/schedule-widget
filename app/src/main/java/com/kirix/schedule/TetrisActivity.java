package com.kirix.schedule;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

public final class TetrisActivity extends Activity {

    private static final String PREFS = "tetris_high_score";
    private static final String KEY_HIGH_SCORE = "high_score";
    private static final String KEY_BOARD = "board";
    private static final String KEY_SCORE = "score";
    private static final String KEY_LEVEL = "level";
    private static final String KEY_LINES = "lines";
    private static final String KEY_CUR_TYPE = "cur_type";
    private static final String KEY_CUR_ROT = "cur_rot";
    private static final String KEY_CUR_ROW = "cur_row";
    private static final String KEY_CUR_COL = "cur_col";
    private static final String KEY_NEXT_QUEUE = "next_queue";
    private static final String KEY_STATE = "state";

    private TetrisGame game;
    private TetrisBoardView boardView;
    private TetrisNextQueueView queueView;
    private TextView tvScore;
    private TextView tvHighScore;
    private TextView tvLevel;
    private TextView tvLines;
    private Button btnPause;
    private Button btnRestart;

    private final Handler timerHandler = new Handler(Looper.getMainLooper());
    private final Runnable tickRunnable = new Runnable() {
        @Override
        public void run() {
            if (game != null && game.isRunning()) {
                game.tick();
                timerHandler.postDelayed(this, game.getDropInterval());
            }
        }
    };

    private float touchStartX;
    private float touchStartY;
    private boolean swiped;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tetris);

        boardView = findViewById(R.id.boardView);
        queueView = findViewById(R.id.nextQueueView);
        tvScore = findViewById(R.id.tvScore);
        tvHighScore = findViewById(R.id.tvHighScore);
        tvLevel = findViewById(R.id.tvLevel);
        tvLines = findViewById(R.id.tvLines);
        btnPause = findViewById(R.id.btnPause);
        btnRestart = findViewById(R.id.btnRestart);

        game = new TetrisGame();
        game.setListener(createListener());
        boardView.setGame(game);

        btnPause.setOnClickListener(v -> togglePause());
        btnRestart.setOnClickListener(v -> restartGame());

        findViewById(R.id.btnLeft).setOnClickListener(v -> game.moveLeft());
        findViewById(R.id.btnRight).setOnClickListener(v -> game.moveRight());
        findViewById(R.id.btnRotate).setOnClickListener(v -> game.rotate());
        findViewById(R.id.btnSoftDrop).setOnClickListener(v -> game.softDrop());
        findViewById(R.id.btnHardDrop).setOnClickListener(v -> game.hardDrop());

        boardView.setOnTouchListener((v, event) -> handleTouch(event));

        tvHighScore.setText(String.valueOf(loadHighScore()));
        if (savedInstanceState != null) {
            restoreGame(savedInstanceState);
        } else {
            game.start();
        }
        updateAll();
        if (game.isPaused()) {
            btnPause.setText(R.string.tetris_continue);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        int rows = TetrisGame.ROWS + TetrisGame.BUFFER_ROWS;
        int[] flat = new int[rows * TetrisGame.COLS];
        int[][] board = game.getBoard();
        int i = 0;
        for (int r = 0; r < rows; r++) {
            for (int col = 0; col < TetrisGame.COLS; col++) {
                flat[i++] = board[r][col];
            }
        }
        outState.putIntArray(KEY_BOARD, flat);
        outState.putInt(KEY_SCORE, game.getScore());
        outState.putInt(KEY_LEVEL, game.getLevel());
        outState.putInt(KEY_LINES, game.getTotalLines());
        outState.putInt(KEY_CUR_TYPE, game.getCurrentType());
        outState.putInt(KEY_CUR_ROT, game.getCurrentRotation());
        outState.putInt(KEY_CUR_ROW, game.getCurrentRow());
        outState.putInt(KEY_CUR_COL, game.getCurrentCol());
        outState.putIntArray(KEY_NEXT_QUEUE, game.getNextQueue());
        outState.putInt(KEY_STATE, game.getState());
    }

    private void restoreGame(Bundle inState) {
        int[] flat = inState.getIntArray(KEY_BOARD);
        if (flat == null) {
            game.start();
            return;
        }
        game.restoreState(flat,
                inState.getInt(KEY_SCORE, 0),
                Math.max(inState.getInt(KEY_LEVEL, 1), 1),
                inState.getInt(KEY_LINES, 0),
                inState.getInt(KEY_CUR_TYPE, 0),
                inState.getInt(KEY_CUR_ROT, 0),
                inState.getInt(KEY_CUR_ROW, 0),
                inState.getInt(KEY_CUR_COL, 0),
                inState.getIntArray(KEY_NEXT_QUEUE),
                inState.getInt(KEY_STATE, TetrisGame.STATE_NOT_STARTED));
    }

    private void togglePause() {
        if (game.isRunning()) {
            game.pause();
            btnPause.setText(R.string.tetris_continue);
        } else if (game.isPaused()) {
            game.resume();
            btnPause.setText(R.string.tetris_pause);
            startTimer();
        }
    }

    private void restartGame() {
        stopTimer();
        game.start();
        btnPause.setText(R.string.tetris_pause);
        updateAll();
    }

    private boolean handleTouch(MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                touchStartX = event.getX();
                touchStartY = event.getY();
                swiped = false;
                return true;
            case MotionEvent.ACTION_UP:
                if (!swiped) {
                    game.rotate();
                }
                return true;
            case MotionEvent.ACTION_MOVE:
                if (swiped) return true;
                float dx = event.getX() - touchStartX;
                float dy = event.getY() - touchStartY;
                float threshold = boardView.getWidth() / (float) TetrisGame.COLS;
                if (Math.abs(dx) > threshold && Math.abs(dx) > Math.abs(dy)) {
                    if (dx > 0) game.moveRight();
                    else game.moveLeft();
                    touchStartX = event.getX();
                    touchStartY = event.getY();
                    swiped = true;
                } else if (dy > threshold * 1.5 && dy > Math.abs(dx)) {
                    game.softDrop();
                    touchStartY = event.getY();
                    swiped = true;
                } else if (dy < -threshold * 1.5 && Math.abs(dy) > Math.abs(dx)) {
                    game.hardDrop();
                    swiped = true;
                }
                return true;
        }
        return false;
    }

    private TetrisGame.Listener createListener() {
        return new TetrisGame.Listener() {
            @Override
            public void onStateChanged(int state) {
                runOnUiThread(() -> {
                    if (state == TetrisGame.STATE_GAME_OVER) {
                        stopTimer();
                        saveHighScore(game.getScore());
                        tvHighScore.setText(String.valueOf(loadHighScore()));
                    }
                    boardView.invalidate();
                });
            }

            @Override
            public void onBoardChanged() {
                boardView.invalidate();
                queueView.setQueue(game.getNextQueue());
            }

            @Override
            public void onScoreChanged(int score, int level, int lines) {
                runOnUiThread(() -> {
                    tvScore.setText(String.valueOf(score));
                    tvLevel.setText(String.valueOf(level));
                    tvLines.setText(String.valueOf(lines));
                });
            }
        };
    }

    private void updateAll() {
        boardView.invalidate();
        queueView.setQueue(game.getNextQueue());
        tvScore.setText(String.valueOf(game.getScore()));
        tvLevel.setText(String.valueOf(game.getLevel()));
        tvLines.setText(String.valueOf(game.getTotalLines()));
        boardView.post(() -> startTimer());
    }

    private void startTimer() {
        stopTimer();
        if (game.isRunning()) {
            timerHandler.postDelayed(tickRunnable, game.getDropInterval());
        }
    }

    private void stopTimer() {
        timerHandler.removeCallbacks(tickRunnable);
    }

    private int loadHighScore() {
        return getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getInt(KEY_HIGH_SCORE, 0);
    }

    private void saveHighScore(int score) {
        int current = loadHighScore();
        if (score > current) {
            getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .edit().putInt(KEY_HIGH_SCORE, score).apply();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (game.isRunning()) {
            startTimer();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopTimer();
        if (game.isRunning()) {
            game.pause();
            btnPause.setText(R.string.tetris_continue);
        }
    }

    @Override
    protected void onDestroy() {
        stopTimer();
        super.onDestroy();
    }
}