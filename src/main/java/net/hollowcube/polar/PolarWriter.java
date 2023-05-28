package net.hollowcube.polar;

import com.github.luben.zstd.Zstd;
import net.minestom.server.network.NetworkBuffer;
import net.minestom.server.utils.chunk.ChunkUtils;
import org.jetbrains.annotations.NotNull;

import java.nio.ByteBuffer;

import static net.minestom.server.network.NetworkBuffer.*;

@SuppressWarnings("UnstableApiUsage")
public class PolarWriter {

    public static byte[] write(@NotNull PolarWorld world) {
        // Write the compressed content first
        var content = new NetworkBuffer(ByteBuffer.allocate(1024));
        content.write(BYTE, world.minSection());
        content.write(BYTE, world.maxSection());
        content.writeCollection(world.chunks(), PolarWriter::writeChunk);

        // Create final buffer
        return NetworkBuffer.makeArray(buffer -> {
            buffer.write(INT, PolarWorld.MAGIC_NUMBER);
            buffer.write(BYTE, PolarWorld.VERSION_MAJOR);
            buffer.write(BYTE, PolarWorld.VERSION_MINOR);
            buffer.write(BYTE, (byte) world.compression().ordinal());
            switch (world.compression()) {
                case NONE -> {
                    buffer.write(VAR_INT, content.readableBytes());
                    buffer.write(RAW_BYTES, content.readBytes(content.readableBytes()));
                }
                case ZSTD -> {
                    buffer.write(VAR_INT, content.readableBytes());
                    buffer.write(RAW_BYTES, Zstd.compress(content.readBytes(content.readableBytes())));
                }
            }
        });
    }

    private static void writeChunk(@NotNull NetworkBuffer buffer, @NotNull PolarChunk chunk) {
        buffer.write(VAR_INT, chunk.x());
        buffer.write(VAR_INT, chunk.z());

        for (var section : chunk.sections()) {
            writeSection(buffer, section);
        }
        buffer.writeCollection(chunk.blockEntities(), PolarWriter::writeBlockEntity);

        //todo heightmaps
        buffer.write(INT, PolarChunk.HEIGHTMAP_NONE);
    }

    private static void writeSection(@NotNull NetworkBuffer buffer, @NotNull PolarSection section) {
        buffer.write(BOOLEAN, section.isEmpty());
        if (section.isEmpty()) return;

        // Blocks
        var blockPalette = section.blockPalette();
        buffer.writeCollection(STRING, blockPalette);
        if (blockPalette.length > 1) {
            var blockData = section.blockData();
            var bitsPerEntry = (int) Math.ceil(Math.log(blockPalette.length) / Math.log(2));
            if (bitsPerEntry < 1) bitsPerEntry = 1;
            buffer.write(LONG_ARRAY, pack(blockData, bitsPerEntry));
        }

        // Biomes
        var biomePalette = section.biomePalette();
        buffer.writeCollection(STRING, biomePalette);
        if (biomePalette.length > 1) {
            var biomeData = section.biomeData();
            var bitsPerEntry = (int) Math.ceil(Math.log(biomePalette.length) / Math.log(2));
            if (bitsPerEntry < 1) bitsPerEntry = 1;
            buffer.write(LONG_ARRAY, pack(biomeData, bitsPerEntry));
        }

        // Light
        buffer.write(BOOLEAN, section.hasLightData());
        if (section.hasLightData()) {
            buffer.write(RAW_BYTES, section.blockLight());
            buffer.write(RAW_BYTES, section.skyLight());
        }
    }

    private static void writeBlockEntity(@NotNull NetworkBuffer buffer, @NotNull PolarChunk.BlockEntity blockEntity) {
        var index = ChunkUtils.getBlockIndex(blockEntity.x(), blockEntity.y(), blockEntity.z());
        buffer.write(INT, index);
        buffer.write(STRING, blockEntity.id());
        buffer.write(NBT, blockEntity.data());
    }

    private static long[] pack(int[] ints, int bitsPerEntry) {
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
}
