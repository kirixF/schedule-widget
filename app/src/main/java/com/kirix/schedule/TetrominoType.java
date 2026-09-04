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
            case I: return 0xFF22D3EE;
            case O: return 0xFFFACC15;
            case T: return 0xFFA855F7;
            case S: return 0xFF4ADE80;
            case Z: return 0xFFEF4444;
            case J: return 0xFF3B82F6;
            case L: return 0xFFFB923C;
            default: return 0xFF808080;
        }
    }

    static int darkForType(int type) {
        switch (type) {
            case I: return 0xFF0E7490;
            case O: return 0xFFA16207;
            case T: return 0xFF7E22CE;
            case S: return 0xFF15803D;
            case Z: return 0xFFB91C1C;
            case J: return 0xFF1D4ED8;
            case L: return 0xFFC2410C;
            default: return 0xFF525252;
        }
    }
}