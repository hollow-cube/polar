package net.hollowcube.polar;

import net.hollowcube.polar.PolarSection.LightContent;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.InflaterInputStream;

public class PolarReader {
    static final int LIGHT_DATA_LENGTH = 2048;
    static final int MAX_BLOCK_PALETTE_SIZE = 16 * 16 * 16;
    static final int MAX_BIOME_PALETTE_SIZE = 8 * 8 * 8;

    private PolarReader() {}

    public static PolarWorld read(byte[] data) {
        return read(data, PolarDataConverter.NOOP);
    }

    public static PolarWorld read(byte[] data, PolarDataConverter dataConverter) {
        PolarBuffer buffer = PolarBuffer.wrap(data);

        int magicNumber = buffer.readInt();
        assertThat(magicNumber == PolarWorld.MAGIC_NUMBER, "Invalid magic number");

        short version = buffer.readShort();
        validateVersion(version);

        int dataVersion = version >= PolarWorld.VERSION_DATA_CONVERTER
                ? buffer.readVarInt()
                : dataConverter.defaultDataVersion();

        PolarWorld.CompressionType compression =
                PolarWorld.CompressionType.fromId(buffer.readByte());
        assertThat(compression != null, "Invalid compression type");
        int compressedDataLength = buffer.readVarInt();

        buffer = decompressBuffer(buffer, compression, compressedDataLength);

        byte minSection = buffer.readByte();
        byte maxSection = buffer.readByte();
        assertThat(minSection < maxSection, "Invalid section range");

        byte[] userData = new byte[0];
        if (version > PolarWorld.VERSION_WORLD_USERDATA) {
            userData = buffer.readByteArray();
        }

        int chunkCount = buffer.readVarInt();
        List<PolarChunk> chunks = new ArrayList<>(chunkCount);
        for (int i = 0; i < chunkCount; i++) {
            chunks.add(readChunk(dataConverter, version, dataVersion, buffer,
                    maxSection - minSection + 1));
        }

        return new PolarWorld(version, dataVersion, compression, minSection, maxSection,
                userData, chunks);
    }

    private static PolarChunk readChunk(PolarDataConverter dataConverter, short version,
                                        int dataVersion, PolarBuffer buffer, int sectionCount) {
        int chunkX = buffer.readVarInt();
        int chunkZ = buffer.readVarInt();

        PolarSection[] sections = new PolarSection[sectionCount];
        for (int i = 0; i < sectionCount; i++) {
            sections[i] = readSection(dataConverter, version, dataVersion, buffer);
        }

        int blockEntityCount = buffer.readVarInt();
        List<PolarChunk.BlockEntity> blockEntities = new ArrayList<>(blockEntityCount);
        for (int i = 0; i < blockEntityCount; i++) {
            blockEntities.add(readBlockEntity(version, buffer));
        }

        int[][] heightmaps = readHeightmapData(buffer);

        byte[] userData = new byte[0];
        if (version > PolarWorld.VERSION_USERDATA_OPT_BLOCK_ENT_NBT) {
            userData = buffer.readByteArray();
        }

        return new PolarChunk(chunkX, chunkZ, sections, blockEntities, heightmaps, userData);
    }

    private static PolarSection readSection(PolarDataConverter dataConverter, short version,
                                            int dataVersion, PolarBuffer buffer) {
        if (buffer.readBoolean()) {
            return new PolarSection();
        }

        String[] blockPalette = buffer.readStringList(MAX_BLOCK_PALETTE_SIZE)
                .toArray(String[]::new);
        if (dataVersion < dataConverter.dataVersion()) {
            dataConverter.convertBlockPalette(blockPalette, dataVersion,
                    dataConverter.dataVersion());
        }
        upgradeGrassInPalette(blockPalette, version);

        int[] blockData = null;
        if (blockPalette.length > 1) {
            blockData = new int[PolarSection.BLOCK_PALETTE_SIZE];
            long[] rawBlockData = buffer.readLongArray();
            int bitsPerEntry = (int) Math.ceil(Math.log(blockPalette.length) / Math.log(2));
            PaletteUtil.unpack(blockData, rawBlockData, bitsPerEntry);
        }

        String[] biomePalette = buffer.readStringList(MAX_BIOME_PALETTE_SIZE)
                .toArray(String[]::new);
        int[] biomeData = null;
        if (biomePalette.length > 1) {
            biomeData = new int[PolarSection.BIOME_PALETTE_SIZE];
            long[] rawBiomeData = buffer.readLongArray();
            int bitsPerEntry = (int) Math.ceil(Math.log(biomePalette.length) / Math.log(2));
            PaletteUtil.unpack(biomeData, rawBiomeData, bitsPerEntry);
        }

        LightContent blockLightContent = LightContent.MISSING;
        LightContent skyLightContent = LightContent.MISSING;
        byte[] blockLight = null;
        byte[] skyLight = null;
        if (version > PolarWorld.VERSION_UNIFIED_LIGHT) {
            blockLightContent = version >= PolarWorld.VERSION_IMPROVED_LIGHT
                    ? LightContent.VALUES[buffer.readByte()]
                    : (buffer.readBoolean() ? LightContent.PRESENT : LightContent.MISSING);
            if (blockLightContent == LightContent.PRESENT) {
                blockLight = buffer.readRawBytes(LIGHT_DATA_LENGTH);
            }
            skyLightContent = version >= PolarWorld.VERSION_IMPROVED_LIGHT
                    ? LightContent.VALUES[buffer.readByte()]
                    : (buffer.readBoolean() ? LightContent.PRESENT : LightContent.MISSING);
            if (skyLightContent == LightContent.PRESENT) {
                skyLight = buffer.readRawBytes(LIGHT_DATA_LENGTH);
            }
        } else if (buffer.readBoolean()) {
            blockLightContent = LightContent.PRESENT;
            blockLight = buffer.readRawBytes(LIGHT_DATA_LENGTH);
            skyLightContent = LightContent.PRESENT;
            skyLight = buffer.readRawBytes(LIGHT_DATA_LENGTH);
        }

        return new PolarSection(blockPalette, blockData, biomePalette, biomeData,
                blockLightContent, blockLight, skyLightContent, skyLight);
    }

    static void upgradeGrassInPalette(String[] blockPalette, int version) {
        if (version > PolarWorld.VERSION_SHORT_GRASS) {
            return;
        }
        for (int i = 0; i < blockPalette.length; i++) {
            if (!blockPalette[i].contains("grass")) {
                continue;
            }
            String strippedId = blockPalette[i].split("\\[")[0];
            int colon = strippedId.indexOf(':');
            String value = colon < 0 ? strippedId : strippedId.substring(colon + 1);
            if (value.equals("grass")) {
                blockPalette[i] = "short_grass";
            }
        }
    }

    static int[][] readHeightmapData(PolarBuffer buffer) {
        int[][] heightmaps = new int[PolarChunk.MAX_HEIGHTMAPS][];
        int heightmapMask = buffer.readInt();
        for (int i = 0; i < PolarChunk.MAX_HEIGHTMAPS; i++) {
            if ((heightmapMask & (1 << i)) == 0) {
                continue;
            }
            long[] packed = buffer.readLongArray();
            if (packed.length == 0) {
                heightmaps[i] = new int[0];
            } else {
                int bitsPerEntry = packed.length * 64 / PolarChunk.HEIGHTMAP_SIZE;
                heightmaps[i] = new int[PolarChunk.HEIGHTMAP_SIZE];
                PaletteUtil.unpack(heightmaps[i], packed, bitsPerEntry);
            }
        }
        return heightmaps;
    }

    static PolarChunk.BlockEntity readBlockEntity(int version, PolarBuffer buffer) {
        int posIndex = buffer.readInt();
        String id = buffer.readOptionalString();

        byte[] nbt = null;
        if (version <= PolarWorld.VERSION_USERDATA_OPT_BLOCK_ENT_NBT || buffer.readBoolean()) {
            nbt = buffer.readNbt();
        }

        return new PolarChunk.BlockEntity(
                PolarCoord.blockIndexGetX(posIndex),
                PolarCoord.blockIndexGetY(posIndex),
                PolarCoord.blockIndexGetZ(posIndex),
                id,
                nbt);
    }

    static void validateVersion(int version) {
        String invalidVersionError = String.format(
                "Unsupported Polar version. Up to %d is supported, found %d.",
                PolarWorld.LATEST_VERSION, version);
        assertThat(version <= PolarWorld.LATEST_VERSION, invalidVersionError);
    }

    private static PolarBuffer decompressBuffer(PolarBuffer buffer,
                                                PolarWorld.CompressionType compression,
                                                int length) {
        return switch (compression) {
            case NONE -> buffer;
            case DEFLATE -> PolarBuffer.wrap(inflate(buffer.readRemaining(), length));
            case ZSTD -> throw new Error(
                    "this build cannot read zstd polar files, re-save the world with deflate");
        };
    }

    private static byte[] inflate(byte[] compressed, int length) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(Math.max(16, length));
        byte[] chunk = new byte[8192];
        try (InflaterInputStream in =
                     new InflaterInputStream(new ByteArrayInputStream(compressed))) {
            int read;
            while ((read = in.read(chunk)) > 0) {
                out.write(chunk, 0, read);
            }
        } catch (IOException e) {
            throw new Error("could not decompress polar data: " + e.getMessage());
        }
        return out.toByteArray();
    }

    static void assertThat(boolean condition, String message) {
        if (!condition) {
            throw new Error(message);
        }
    }

    public static class Error extends RuntimeException {
        Error(String message) {
            super(message);
        }
    }
}
