package net.hollowcube.polar;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

public class PolarWriter {
    private PolarWriter() {}

    public static byte[] write(PolarWorld world) {
        return write(world, PolarDataConverter.NOOP);
    }

    public static byte[] write(PolarWorld world, PolarDataConverter dataConverter) {
        PolarBuffer content = new PolarBuffer();
        content.writeByte(world.minSection());
        content.writeByte(world.maxSection());
        content.writeByteArray(world.userData());

        content.writeVarInt(world.chunks().size());
        for (PolarChunk chunk : world.chunks()) {
            writeChunk(content, chunk, world.maxSection() - world.minSection() + 1);
        }
        byte[] contentBytes = content.toByteArray();

        PolarBuffer buffer = new PolarBuffer(contentBytes.length + 32);
        buffer.writeInt(PolarWorld.MAGIC_NUMBER);
        buffer.writeShort(PolarWorld.VERSION_IMPROVED_LIGHT);
        buffer.writeVarInt(dataConverter.dataVersion());
        buffer.writeByte((byte) world.compression().ordinal());
        buffer.writeVarInt(contentBytes.length);
        switch (world.compression()) {
            case NONE -> buffer.writeRawBytes(contentBytes);
            case DEFLATE -> buffer.writeRawBytes(deflate(contentBytes));
            case ZSTD -> throw new PolarReader.Error(
                    "this build cannot write zstd polar files, use deflate or none");
        }
        return buffer.toByteArray();
    }

    private static void writeChunk(PolarBuffer buffer, PolarChunk chunk, int sectionCount) {
        buffer.writeVarInt(chunk.x());
        buffer.writeVarInt(chunk.z());

        if (sectionCount != chunk.sections().length) {
            throw new PolarReader.Error("chunk has " + chunk.sections().length
                    + " sections but the world wants " + sectionCount);
        }

        for (PolarSection section : chunk.sections()) {
            writeSection(buffer, section);
        }

        buffer.writeVarInt(chunk.blockEntities().size());
        for (PolarChunk.BlockEntity blockEntity : chunk.blockEntities()) {
            writeBlockEntity(buffer, blockEntity);
        }

        int heightmapBits = 0;
        for (int i = 0; i < PolarChunk.MAX_HEIGHTMAPS; i++) {
            if (chunk.heightmap(i) != null) {
                heightmapBits |= 1 << i;
            }
        }
        buffer.writeInt(heightmapBits);

        int bitsPerEntry = PaletteUtil.bitsToRepresent(sectionCount * 16);
        for (int i = 0; i < PolarChunk.MAX_HEIGHTMAPS; i++) {
            int[] heightmap = chunk.heightmap(i);
            if (heightmap == null) {
                continue;
            }
            buffer.writeLongArray(heightmap.length == 0
                    ? new long[0]
                    : PaletteUtil.pack(heightmap, bitsPerEntry));
        }

        buffer.writeByteArray(chunk.userData());
    }

    private static void writeSection(PolarBuffer buffer, PolarSection section) {
        buffer.writeBoolean(section.isEmpty());
        if (section.isEmpty()) {
            return;
        }

        String[] blockPalette = section.blockPalette();
        buffer.writeStringList(Arrays.asList(blockPalette));
        if (blockPalette.length > 1) {
            int bitsPerEntry = Math.max(1,
                    (int) Math.ceil(Math.log(blockPalette.length) / Math.log(2)));
            buffer.writeLongArray(PaletteUtil.pack(section.blockData(), bitsPerEntry));
        }

        String[] biomePalette = section.biomePalette();
        buffer.writeStringList(Arrays.asList(biomePalette));
        if (biomePalette.length > 1) {
            int bitsPerEntry = Math.max(1,
                    (int) Math.ceil(Math.log(biomePalette.length) / Math.log(2)));
            buffer.writeLongArray(PaletteUtil.pack(section.biomeData(), bitsPerEntry));
        }

        buffer.writeByte((byte) section.blockLightContent().ordinal());
        if (section.blockLightContent() == PolarSection.LightContent.PRESENT) {
            buffer.writeRawBytes(section.blockLight());
        }
        buffer.writeByte((byte) section.skyLightContent().ordinal());
        if (section.skyLightContent() == PolarSection.LightContent.PRESENT) {
            buffer.writeRawBytes(section.skyLight());
        }
    }

    private static void writeBlockEntity(PolarBuffer buffer, PolarChunk.BlockEntity blockEntity) {
        buffer.writeInt(PolarCoord.blockIndex(blockEntity.x(), blockEntity.y(), blockEntity.z()));
        buffer.writeOptionalString(blockEntity.id());
        buffer.writeOptionalNbt(blockEntity.data());
    }

    private static byte[] deflate(byte[] raw) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(Math.max(16, raw.length / 4));
        Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION);
        try (DeflaterOutputStream stream = new DeflaterOutputStream(out, deflater)) {
            stream.write(raw);
        } catch (IOException e) {
            throw new PolarReader.Error("could not compress polar data: " + e.getMessage());
        } finally {
            deflater.end();
        }
        return out.toByteArray();
    }
}
