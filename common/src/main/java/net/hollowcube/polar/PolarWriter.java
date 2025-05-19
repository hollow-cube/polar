package net.hollowcube.polar;

import com.github.luben.zstd.Zstd;
import net.hollowcube.polar.buffer.BufferFactory;
import net.hollowcube.polar.buffer.BufferWrapper;
import org.jetbrains.annotations.NotNull;

public class PolarWriter {
    private PolarWriter() {
    }

    public static byte[] write(@NotNull PolarWorld world) {
        return write(world, PolarDataConverter.NOOP);
    }

    public static byte[] write(@NotNull PolarWorld world, @NotNull PolarDataConverter dataConverter) {
        BufferFactory bufferFactory = BufferFactory.INSTANCE;
        // Write the compressed content first
        var contentBytes = bufferFactory.makeArray(content -> {
            content.writeByte(world.minSection());
            content.writeByte(world.maxSection());
            content.writeByteArray(world.userData());

            content.writeVarInt(world.chunks().size());
            for (var chunk : world.chunks()) {
                writeChunk(content, chunk, world.maxSection() - world.minSection() + 1);
            }
        });

        // Create final buffer
        return bufferFactory.makeArray(buffer -> {
            buffer.writeInt(PolarWorld.MAGIC_NUMBER);
            buffer.writeShort(PolarWorld.VERSION_IMPROVED_LIGHT);
            buffer.writeVarInt(dataConverter.dataVersion());
            buffer.writeByte((byte) world.compression().ordinal());
            switch (world.compression()) {
                case NONE -> {
                    buffer.writeVarInt(contentBytes.length);
                    buffer.writeRawBytes(contentBytes);
                }
                case ZSTD -> {
                    buffer.writeVarInt(contentBytes.length);
                    buffer.writeRawBytes(Zstd.compress(contentBytes));
                }
            }
        });
    }

    private static void writeChunk(@NotNull BufferWrapper buffer, @NotNull PolarChunk chunk, int sectionCount) {
        buffer.writeVarInt(chunk.x());
        buffer.writeVarInt(chunk.z());

        assert sectionCount == chunk.sections().length : "section count and chunk section length mismatch";

        for (var section : chunk.sections()) {
            writeSection(buffer, section);
        }

        buffer.writeVarInt(chunk.blockEntities().size());
        for (var blockEntity : chunk.blockEntities()) {
            writeBlockEntity(buffer, blockEntity);
        }

        {
            int heightmapBits = 0;
            for (int i = 0; i < PolarChunk.MAX_HEIGHTMAPS; i++) {
                if (chunk.heightmap(i) != null)
                    heightmapBits |= 1 << i;
            }
            buffer.writeInt(heightmapBits);

            int bitsPerEntry = PaletteUtil.bitsToRepresent(sectionCount * 16);
            for (int i = 0; i < PolarChunk.MAX_HEIGHTMAPS; i++) {
                var heightmap = chunk.heightmap(i);
                if (heightmap == null) continue;
                if (heightmap.length == 0) buffer.writeLongArray(new long[0]);
                else buffer.writeLongArray(PaletteUtil.pack(heightmap, bitsPerEntry));
            }
        }

        buffer.writeByteArray(chunk.userData());
    }

    private static void writeSection(@NotNull BufferWrapper buffer, @NotNull PolarSection section) {
        buffer.writeBoolean(section.isEmpty());
        if (section.isEmpty()) return;

        // Blocks
        var blockPalette = section.blockPalette();
        buffer.writeStringArray(blockPalette);
        if (blockPalette.length > 1) {
            var blockData = section.blockData();
            var bitsPerEntry = (int) Math.ceil(Math.log(blockPalette.length) / Math.log(2));
            if (bitsPerEntry < 1) bitsPerEntry = 1;
            buffer.writeLongArray(PaletteUtil.pack(blockData, bitsPerEntry));
        }

        // Biomes
        var biomePalette = section.biomePalette();
        buffer.writeStringArray(biomePalette);
        if (biomePalette.length > 1) {
            var biomeData = section.biomeData();
            var bitsPerEntry = (int) Math.ceil(Math.log(biomePalette.length) / Math.log(2));
            if (bitsPerEntry < 1) bitsPerEntry = 1;
            buffer.writeLongArray(PaletteUtil.pack(biomeData, bitsPerEntry));
        }

        // Light
        buffer.writeByte((byte) section.blockLightContent().ordinal());
        if (section.blockLightContent() == PolarSection.LightContent.PRESENT)
            buffer.writeRawBytes(section.blockLight());
        buffer.writeByte((byte) section.skyLightContent().ordinal());
        if (section.skyLightContent() == PolarSection.LightContent.PRESENT)
            buffer.writeRawBytes(section.skyLight());
    }

    private static void writeBlockEntity(@NotNull BufferWrapper buffer, @NotNull PolarChunk.BlockEntity blockEntity) {
        var index = Utils.chunkBlockIndex(blockEntity.x(), blockEntity.y(), blockEntity.z());
        buffer.writeInt(index);
        buffer.writeOptString(blockEntity.id());
        buffer.writeOptNbt(blockEntity.data());
    }
}
