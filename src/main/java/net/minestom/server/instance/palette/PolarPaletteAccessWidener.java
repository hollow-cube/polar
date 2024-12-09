package net.minestom.server.instance.palette;

import org.jetbrains.annotations.NotNull;

public final class PolarPaletteAccessWidener {

    public static void directReplaceInnerPaletteBlock(
            @NotNull Palette outer, byte bitsPerEntry,
            int count, int[] palette, long[] values) {
        directReplaceInnerPalette(outer, 16, 8, bitsPerEntry, count, palette, values);
    }

    public static void directReplaceInnerPaletteBiome(
            @NotNull Palette outer, byte bitsPerEntry,
            int count, int[] palette, long[] values) {
        directReplaceInnerPalette(outer, 4, 3, bitsPerEntry, count, palette, values);
    }

    public static void directReplaceInnerPalette(
            @NotNull Palette outer,
            int dimension, int maxBitsPerEntry, byte bitsPerEntry,
            int count, int[] palette, long[] values) {
        if (!(outer instanceof AdaptivePalette adaptive))
            throw new IllegalArgumentException("Cannot replace inner palette of non-adaptive palette");
        var inner = new PaletteIndirect(dimension, maxBitsPerEntry, bitsPerEntry, count, palette, values);
        inner.values = values; // Minestom accidentally overwrites the passed values. That is wrong.
        adaptive.palette = inner;
    }

}
