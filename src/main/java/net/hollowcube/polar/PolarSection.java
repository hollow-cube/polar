package net.hollowcube.polar;

public class PolarSection {
    public static final int BLOCK_PALETTE_SIZE = 4096;
    public static final int BIOME_PALETTE_SIZE = 64;

    public enum LightContent {
        MISSING,
        EMPTY,
        FULL,
        PRESENT,
        ;

        public static final LightContent[] VALUES = values();
    }

    private final boolean empty;

    private final String[] blockPalette;
    private final int[] blockData;

    private final String[] biomePalette;
    private final int[] biomeData;

    private final LightContent blockLightContent;
    private final byte[] blockLight;
    private final LightContent skyLightContent;
    private final byte[] skyLight;

    public PolarSection() {
        this.empty = true;

        this.blockPalette = new String[] {"minecraft:air"};
        this.blockData = null;
        this.biomePalette = new String[] {"minecraft:plains"};
        this.biomeData = null;

        this.blockLightContent = LightContent.MISSING;
        this.blockLight = null;
        this.skyLightContent = LightContent.MISSING;
        this.skyLight = null;
    }

    public PolarSection(
        String[] blockPalette,
        int[] blockData,
        String[] biomePalette,
        int[] biomeData,
        LightContent blockLightContent,
        byte[] blockLight,
        LightContent skyLightContent,
        byte[] skyLight
    ) {
        this.empty = false;

        this.blockPalette = blockPalette;
        this.blockData = blockData;
        this.biomePalette = biomePalette;
        this.biomeData = biomeData;

        this.blockLightContent = blockLightContent;
        this.blockLight = blockLight;
        this.skyLightContent = skyLightContent;
        this.skyLight = skyLight;
    }

    public boolean isEmpty() {
        return empty;
    }

    public String[] blockPalette() {
        return blockPalette;
    }

    public int[] blockData() {
        assert blockData != null : "must check length of blockPalette() before using blockData()";
        return blockData;
    }

    public String[] biomePalette() {
        return biomePalette;
    }

    public int[] biomeData() {
        assert biomeData != null : "must check length of biomePalette() before using biomeData()";
        return biomeData;
    }

    public LightContent blockLightContent() {
        return blockLightContent;
    }

    public byte[] blockLight() {
        return blockLight;
    }

    public LightContent skyLightContent() {
        return skyLightContent;
    }

    public byte[] skyLight() {
        return skyLight;
    }
}
