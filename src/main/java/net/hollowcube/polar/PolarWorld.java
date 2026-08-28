package net.hollowcube.polar;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class PolarWorld {
    public static final int MAGIC_NUMBER = 0x506F6C72;
    public static final short LATEST_VERSION = 7;

    static final short VERSION_UNIFIED_LIGHT = 1;
    static final short VERSION_USERDATA_OPT_BLOCK_ENT_NBT = 2;
    static final short VERSION_MINESTOM_NBT_READ_BREAK = 3;
    static final short VERSION_WORLD_USERDATA = 4;
    static final short VERSION_SHORT_GRASS = 5;
    static final short VERSION_DATA_CONVERTER = 6;
    static final short VERSION_IMPROVED_LIGHT = 7;

    public static CompressionType DEFAULT_COMPRESSION = CompressionType.DEFLATE;

    public static int DATA_VERSION = 0;

    private final short version;
    private final int dataVersion;
    private CompressionType compression;

    private byte minSection;
    private byte maxSection;
    private byte [] userData;

    private final Map<Long, PolarChunk> chunks = new LinkedHashMap<>();

    public PolarWorld() {
        this(LATEST_VERSION, DATA_VERSION, DEFAULT_COMPRESSION,
                (byte) -4, (byte) 19, new byte[0], List.of());
    }

    public static PolarWorld forHeight(int minY, int height) {
        return new PolarWorld(LATEST_VERSION, DATA_VERSION, DEFAULT_COMPRESSION,
                (byte) (minY / 16), (byte) ((minY + height) / 16 - 1), new byte[0], List.of());
    }

    public PolarWorld(
        short version,
        int dataVersion,
        CompressionType compression,
        byte minSection,
        byte maxSection,
        byte [] userData,
        List<PolarChunk> chunks
    ) {
        this.version = version;
        this.dataVersion = dataVersion;
        this.compression = compression;

        this.minSection = minSection;
        this.maxSection = maxSection;
        this.userData = userData;

        for (var chunk : chunks) {
            this.chunks.put(chunkIndex(chunk.x(), chunk.z()), chunk);
        }
    }

    public short version() {
        return version;
    }

    public int dataVersion() {
        return dataVersion;
    }

    public CompressionType compression() {
        return compression;
    }

    public void setCompression(CompressionType compression) {
        this.compression = compression;
    }

    public byte minSection() {
        return minSection;
    }

    public byte maxSection() {
        return maxSection;
    }

    public void setSectionCount(byte minSection, byte maxSection) {
        for (Long key : List.copyOf(chunks.keySet())) {
            chunks.put(key, WorldHeightUtil.updateChunkHeight(chunks.get(key), minSection, maxSection));
        }

        this.minSection = minSection;
        this.maxSection = maxSection;
    }

    public byte [] userData() {
        return userData;
    }

    public void userData(byte [] userData) {
        this.userData = userData;
    }

    public PolarChunk chunkAt(int x, int z) {
        return chunks.get(chunkIndex(x, z));
    }

    public void updateChunkAt(int x, int z, PolarChunk chunk) {
        chunks.put(chunkIndex(x, z), chunk);
    }

    static long chunkIndex(int x, int z) {
        return (((long) x) << 32) | (z & 0xFFFFFFFFL);
    }

    public Collection<PolarChunk> chunks() {
        return chunks.values();
    }

    public enum CompressionType {
        NONE,
        ZSTD,
        DEFLATE,
        ;

        private static final CompressionType[] VALUES = values();

        public static CompressionType fromId(int id) {
            if (id < 0 || id >= VALUES.length) return null;
            return VALUES[id];
        }
    }
}
