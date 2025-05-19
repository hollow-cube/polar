package net.hollowcube.polar;

public class Utils {
    public static int chunkBlockIndexGetX(int index) {
        return index & 0xF; // bits 0-3
    }

    public static int chunkBlockIndexGetY(int index) {
        int y = (index & 0x07FFFFF0) >>> 4;
        if ((index & 0x08000000) != 0) y = -y; // Sign bit set, invert sign
        return y; // 4-28 bits
    }

    public static int chunkBlockIndexGetZ(int index) {
        return (index >>> 28) & 0xF; // bits 28-31
    }

    public static long chunkIndex(int chunkX, int chunkZ) {
        return (((long) chunkX) << 32) | (chunkZ & 0xffffffffL);
    }

    public static int globalToSectionRelative(int xyz) {
        return xyz & 0xF;
    }

    public static int chunkBlockIndex(int x, int y, int z) {
        // Mask x and z to ensure only the lower 4 bits are used.
        x = globalToSectionRelative(x);
        z = globalToSectionRelative(z);

        // Bits layout:
        // bits 0-3: x (4 bits)
        // bits 4-26: absolute value of y (23 bits)
        // bit 27: sign bit of y
        // bits 28-31: z (4 bits)
        return (z << 28)                          // Z component (shifted to the upper 4 bits)
                | (y & 0x80000000) >>> 4          // Y sign bit if y was negative
                | (Math.abs(y) & 0x007FFFFF) << 4 // Y component (23 bits for Y, sign encoded in the 24th)
                | (x);                            // X component (4 bits for X)
    }
}
