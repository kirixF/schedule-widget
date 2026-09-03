package com.kirix.schedule;

final class TetrominoType {

    static final int I = 0;
    static final int O = 1;
    static final int T = 2;
    static final int S = 3;
    static final int Z = 4;
    static final int J = 5;
    static final int L = 6;
    static final int COUNT = 7;

    private static final int[][][][] SHAPES = {
            {
                    {{0, 0}, {0, 1}, {0, 2}, {0, 3}},
                    {{0, 0}, {1, 0}, {2, 0}, {3, 0}},
                    {{0, 0}, {0, 1}, {0, 2}, {0, 3}},
                    {{0, 0}, {1, 0}, {2, 0}, {3, 0}}
            },
            {
                    {{0, 0}, {0, 1}, {1, 0}, {1, 1}},
                    {{0, 0}, {0, 1}, {1, 0}, {1, 1}},
                    {{0, 0}, {0, 1}, {1, 0}, {1, 1}},
                    {{0, 0}, {0, 1}, {1, 0}, {1, 1}}
            },
            {
                    {{0, 0}, {0, 1}, {0, 2}, {1, 1}},
                    {{0, 0}, {1, 0}, {2, 0}, {1, 1}},
                    {{1, 0}, {1, 1}, {1, 2}, {0, 1}},
                    {{0, 0}, {1, 0}, {2, 0}, {1, -1}}
            },
            {
                    {{0, 1}, {0, 2}, {1, 0}, {1, 1}},
                    {{0, 0}, {1, 0}, {1, 1}, {2, 1}},
                    {{0, 1}, {0, 2}, {1, 0}, {1, 1}},
                    {{0, 0}, {1, 0}, {1, 1}, {2, 1}}
            },
            {
                    {{0, 0}, {0, 1}, {1, 1}, {1, 2}},
                    {{0, 1}, {1, 0}, {1, 1}, {2, 0}},
                    {{0, 0}, {0, 1}, {1, 1}, {1, 2}},
                    {{0, 1}, {1, 0}, {1, 1}, {2, 0}}
            },
            {
                    {{0, 0}, {1, 0}, {1, 1}, {1, 2}},
                    {{0, 0}, {0, 1}, {1, 0}, {2, 0}},
                    {{0, 0}, {0, 1}, {0, 2}, {1, 2}},
                    {{0, 0}, {1, 0}, {2, 0}, {2, -1}}
            },
            {
                    {{0, 2}, {1, 0}, {1, 1}, {1, 2}},
                    {{0, 0}, {1, 0}, {2, 0}, {2, 1}},
                    {{0, 0}, {0, 1}, {0, 2}, {1, 0}},
                    {{0, 0}, {0, 1}, {1, 1}, {2, 1}}
            }
    };

    private TetrominoType() {
    }

    static int[][] cells(int type, int rotation) {
        return SHAPES[type][rotation & 3];
    }

    static int colorForType(int type) {
        switch (type) {
            case I: return 0xFF00BCD4;
            case O: return 0xFFFFEB3B;
            case T: return 0xFF9C27B0;
            case S: return 0xFF4CAF50;
            case Z: return 0xFFF44336;
            case J: return 0xFF2196F3;
            case L: return 0xFFFF9800;
            default: return 0xFF808080;
        }
    }
}