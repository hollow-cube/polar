package net.hollowcube.polar;

final class PaletteUtil {
    private PaletteUtil() {}

    public static int bitsToRepresent(int n) {
        assert n > 0: "n must be greater than 0";
        return Integer.SIZE - Integer.numberOfLeadingZeros(n);
    }

    public static long[] pack(int[] ints, int bitsPerEntry) {
        int intsPerLong = (int) Math.floor(64d / bitsPerEntry);
        long[] longs = new long[(int) Math.ceil(ints.length / (double) intsPerLong)];

        long mask = (1L << bitsPerEntry) - 1L;
        for (int i = 0; i < longs.length; i++) {
            for (int intIndex = 0; intIndex < intsPerLong; intIndex++) {
                int bitIndex = intIndex * bitsPerEntry;
                int intActualIndex = intIndex + i * intsPerLong;
                if (intActualIndex < ints.length) {
                    longs[i] |= (ints[intActualIndex] & mask) << bitIndex;
                }
            }
        }

        return longs;
    }

    public static void unpack(int[] out, long[] in, int bitsPerEntry) {
        assert in.length != 0: "unpack input array is zero";

        var intsPerLong = Math.floor(64d / bitsPerEntry);
        var intsPerLongCeil = (int) Math.ceil(intsPerLong);

        long mask = (1L << bitsPerEntry) - 1L;
        for (int i = 0; i < out.length; i++) {
            int longIndex = i / intsPerLongCeil;
            int subIndex = i % intsPerLongCeil;

            out[i] = (int) ((in[longIndex] >>> (bitsPerEntry * subIndex)) & mask);
        }
    }
}
