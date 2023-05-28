package net.hollowcube.polar;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Representation of the latest version of the section format.
 * <p>
 * Marked as internal because of the use of mutable arrays. These arrays must _not_ be mutated.
 * This class should be considered immutable.
 */
@ApiStatus.Internal
public class PolarSection {
    public static final int BLOCK_PALETTE_SIZE = 4096;
    public static final int BIOME_PALETTE_SIZE = 64;

    private final boolean empty;

    private final String @NotNull [] blockPalette;
    private final int @Nullable [] blockData;

    private final String @NotNull [] biomePalette;
    private final int @Nullable [] biomeData;

    // Both light arrays are present/missing together. you cannot have one without the other.
    private final byte @Nullable [] blockLight;
    private final byte @Nullable [] skyLight;

    public PolarSection() {
        this.empty = true;

        this.blockPalette = new String[]{"minecraft:air"};
        this.blockData = null;
        this.biomePalette = new String[]{"minecraft:plains"};
        this.biomeData = null;

        this.blockLight = null;
        this.skyLight = null;
    }

    public PolarSection(
            String @NotNull [] blockPalette, int @Nullable [] blockData,
            String @NotNull [] biomePalette, int @Nullable [] biomeData,
            byte @Nullable [] blockLight, byte @Nullable [] skyLight
    ) {
        this.empty = false;

        this.blockPalette = blockPalette;
        this.blockData = blockData;
        this.biomePalette = biomePalette;
        this.biomeData = biomeData;

        this.blockLight = blockLight;
        this.skyLight = skyLight;
    }

    public boolean isEmpty() {
        return empty;
    }

    public @NotNull String @NotNull [] blockPalette() {
        return blockPalette;
    }

    /**
     * Returns the uncompressed palette data. Each int corresponds to an index in the palette.
     * Always has a length of 4096.
     */
    public int[] blockData() {
        assert blockData != null : "must check length of blockPalette() before using blockData()";
        return blockData;
    }

    public @NotNull String @NotNull [] biomePalette() {
        return biomePalette;
    }

    /**
     * Returns the uncompressed palette data. Each int corresponds to an index in the palette.
     * Always has a length of 256.
     */
    public int[] biomeData() {
        assert biomeData != null : "must check length of biomePalette() before using biomeData()";
        return biomeData;
    }

    public boolean hasLightData() {
        return blockLight != null && skyLight != null;
    }

    public byte[] blockLight() {
        assert blockLight != null : "must check hasLightData() before calling blockLight()";
        return blockLight;
    }

    public byte[] skyLight() {
        assert skyLight != null : "must check hasLightData() before calling skyLight()";
        return skyLight;
    }
}
