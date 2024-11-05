package net.hollowcube.polar;

import com.github.luben.zstd.Zstd;
import net.minestom.server.coordinate.CoordConversion;
import net.minestom.server.instance.Chunk;
import net.minestom.server.network.NetworkBuffer;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

import static net.minestom.server.network.NetworkBuffer.*;

@SuppressWarnings("UnstableApiUsage")
public class PolarWriter {
    private PolarWriter() {
    }

    public static byte[] write(@NotNull PolarWorld world) {
        return write(world, PolarDataConverter.NOOP);
    }

    public static byte[] write(@NotNull PolarWorld world, @NotNull PolarDataConverter dataConverter) {
        // Write the compressed content first
        var contentBytes = NetworkBuffer.makeArray(content -> {
            content.write(BYTE, world.minSection());
            content.write(BYTE, world.maxSection());
            content.write(BYTE_ARRAY, world.userData());

            content.write(VAR_INT, world.chunks().size());
            for (var chunk : world.chunks()) {
                writeChunk(content, chunk, world.maxSection() - world.minSection() + 1);
            }
        });

        // Create final buffer
        return NetworkBuffer.makeArray(buffer -> {
            buffer.write(INT, PolarWorld.MAGIC_NUMBER);
            buffer.write(SHORT, PolarWorld.VERSION_IMPROVED_LIGHT);
            buffer.write(VAR_INT, dataConverter.dataVersion());
            buffer.write(BYTE, (byte) world.compression().ordinal());
            switch (world.compression()) {
                case NONE -> {
                    buffer.write(VAR_INT, contentBytes.length);
                    buffer.write(RAW_BYTES, contentBytes);
                }
                case ZSTD -> {
                    buffer.write(VAR_INT, contentBytes.length);
                    buffer.write(RAW_BYTES, Zstd.compress(contentBytes));
                }
            }
        });
    }

    private static void writeChunk(@NotNull NetworkBuffer buffer, @NotNull PolarChunk chunk, int sectionCount) {
        buffer.write(VAR_INT, chunk.x());
        buffer.write(VAR_INT, chunk.z());

        assert sectionCount == chunk.sections().length : "section count and chunk section length mismatch";

        for (var section : chunk.sections()) {
            writeSection(buffer, section);
        }

        buffer.write(VAR_INT, chunk.blockEntities().size());
        for (var blockEntity : chunk.blockEntities()) {
            writeBlockEntity(buffer, blockEntity);
        }

        {
            int heightmapBits = 0;
            for (int i = 0; i < PolarChunk.MAX_HEIGHTMAPS; i++) {
                if (chunk.heightmap(i) != null)
                    heightmapBits |= 1 << i;
            }
            buffer.write(INT, heightmapBits);

            int bitsPerEntry = PaletteUtil.bitsToRepresent(sectionCount * Chunk.CHUNK_SECTION_SIZE);
            for (int i = 0; i < PolarChunk.MAX_HEIGHTMAPS; i++) {
                var heightmap = chunk.heightmap(i);
                if (heightmap == null) continue;
                if (heightmap.length == 0) buffer.write(LONG_ARRAY, new long[0]);
                else buffer.write(LONG_ARRAY, PaletteUtil.pack(heightmap, bitsPerEntry));
            }
        }

        buffer.write(BYTE_ARRAY, chunk.userData());
    }

    private static void writeSection(@NotNull NetworkBuffer buffer, @NotNull PolarSection section) {
        buffer.write(BOOLEAN, section.isEmpty());
        if (section.isEmpty()) return;

        // Blocks
        var blockPalette = section.blockPalette();
        buffer.write(STRING.list(), Arrays.asList(blockPalette));
        if (blockPalette.length > 1) {
            var blockData = section.blockData();
            var bitsPerEntry = (int) Math.ceil(Math.log(blockPalette.length) / Math.log(2));
            if (bitsPerEntry < 1) bitsPerEntry = 1;
            buffer.write(LONG_ARRAY, PaletteUtil.pack(blockData, bitsPerEntry));
        }

        // Biomes
        var biomePalette = section.biomePalette();
        buffer.write(STRING.list(), Arrays.asList(biomePalette));
        if (biomePalette.length > 1) {
            var biomeData = section.biomeData();
            var bitsPerEntry = (int) Math.ceil(Math.log(biomePalette.length) / Math.log(2));
            if (bitsPerEntry < 1) bitsPerEntry = 1;
            buffer.write(LONG_ARRAY, PaletteUtil.pack(biomeData, bitsPerEntry));
        }

        // Light
        buffer.write(BYTE, (byte) section.blockLightContent().ordinal());
        if (section.blockLightContent() == PolarSection.LightContent.PRESENT)
            buffer.write(RAW_BYTES, section.blockLight());
        buffer.write(BYTE, (byte) section.skyLightContent().ordinal());
        if (section.skyLightContent() == PolarSection.LightContent.PRESENT)
            buffer.write(RAW_BYTES, section.skyLight());
    }

    private static void writeBlockEntity(@NotNull NetworkBuffer buffer, @NotNull PolarChunk.BlockEntity blockEntity) {
        var index = CoordConversion.chunkBlockIndex(blockEntity.x(), blockEntity.y(), blockEntity.z());
        buffer.write(INT, index);
        buffer.write(STRING.optional(), blockEntity.id());
        buffer.write(NBT.optional(), blockEntity.data());
    }
}
