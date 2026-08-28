package net.hollowcube.polar;

final class PolarCoord {
    private PolarCoord() {}

    static int blockIndex(int x, int y, int z) {
        return (x & 0xF) | ((y & 0xFF) << 8) | ((z & 0xF) << 4);
    }

    static int blockIndexGetX(int index) {
        return index & 0xF;
    }

    static int blockIndexGetY(int index) {
        return (index >> 8) & 0xFF;
    }

    static int blockIndexGetZ(int index) {
        return (index >> 4) & 0xF;
    }
}
