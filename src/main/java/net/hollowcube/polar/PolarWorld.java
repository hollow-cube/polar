package net.hollowcube.polar;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minestom.server.utils.chunk.ChunkUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

/**
 * A Java type representing the latest version of the world format.
 */
@SuppressWarnings("UnstableApiUsage")
public class PolarWorld {
    public static final int MAGIC_NUMBER = 0x506F6C72;
    public static final short LATEST_VERSION = 1;

    public static CompressionType DEFAULT_COMPRESSION = CompressionType.ZSTD;

    // Polar metadata
    private short version;
    private CompressionType compression;

    // World metadata
    private final byte minSection;
    private final byte maxSection;

    // Chunk data
    private final Long2ObjectMap<PolarChunk> chunks = new Long2ObjectOpenHashMap<>();

    public PolarWorld(
            short version,
            @NotNull CompressionType compression,
            byte minSection, byte maxSection,
            @NotNull List<PolarChunk> chunks
    ) {
        this.version = version;
        this.compression = compression;

        this.minSection = minSection;
        this.maxSection = maxSection;

        for (var chunk : chunks) {
            var index = ChunkUtils.getChunkIndex(chunk.x(), chunk.z());
            this.chunks.put(index, chunk);
        }
    }

    public short version() {
        return version;
    }

    public @NotNull CompressionType compression() {
        return compression;
    }

    public byte minSection() {
        return minSection;
    }

    public byte maxSection() {
        return maxSection;
    }

    public @Nullable PolarChunk chunkAt(int x, int z) {
        return chunks.getOrDefault(ChunkUtils.getChunkIndex(x, z), null);
    }
    public void updateChunkAt(int x, int z, @NotNull PolarChunk chunk) {
        chunks.put(ChunkUtils.getChunkIndex(x, z), chunk);
    }

    public @NotNull Collection<PolarChunk> chunks() {
        return chunks.values();
    }

    public enum CompressionType {
        NONE,
        ZSTD;

        private static final CompressionType[] VALUES = values();

        public static @Nullable CompressionType fromId(int id) {
            if (id < 0 || id >= VALUES.length) return null;
            return VALUES[id];
        }
    }
}
