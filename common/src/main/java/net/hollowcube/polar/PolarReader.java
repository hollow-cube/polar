package net.hollowcube.polar;

import com.github.luben.zstd.Zstd;
import net.hollowcube.polar.PolarSection.LightContent;
import net.hollowcube.polar.buffer.BufferFactory;
import net.hollowcube.polar.buffer.BufferWrapper;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.nbt.CompoundBinaryTag;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;

public class PolarReader {
    private static final boolean FORCE_LEGACY_NBT = Boolean.getBoolean("polar.debug.force-legacy-nbt");
    static final int MAX_BLOCK_PALETTE_SIZE = 16 * 16 * 16;
    static final int MAX_BIOME_PALETTE_SIZE = 8 * 8 * 8;

    private PolarReader() {
    }

    public static @NotNull PolarWorld read(byte @NotNull [] data, @NotNull BufferFactory bufferFactory) {
        return read(data, bufferFactory, PolarDataConverter.NOOP);
    }

    public static @NotNull PolarWorld read(byte @NotNull [] data, @NotNull BufferFactory bufferFactory, @NotNull PolarDataConverter dataConverter) {
        var buffer = bufferFactory.wrap(data);
        buffer.writeIndex(data.length); // Set write index to end so readableBytes returns remaining bytes

        var magicNumber = buffer.readInt();
        assertThat(magicNumber == PolarWorld.MAGIC_NUMBER, "Invalid magic number");

        short version = buffer.readShort();
        validateVersion(version);

        int dataVersion = version >= PolarWorld.VERSION_DATA_CONVERTER
                ? buffer.readVarInt()
                : dataConverter.defaultDataVersion();

        var compression = PolarWorld.CompressionType.fromId(buffer.readByte());
        assertThat(compression != null, "Invalid compression type");
        var compressedDataLength = buffer.readVarInt();

        // Replace the buffer with a "decompressed" version. This is a no-op if compression is NONE.
        buffer = decompressBuffer(bufferFactory, buffer, compression, compressedDataLength);

        byte minSection = buffer.readByte(), maxSection = buffer.readByte();
        assertThat(minSection < maxSection, "Invalid section range");

        // User (world) data
        byte[] userData = new byte[0];
        if (version > PolarWorld.VERSION_WORLD_USERDATA)
            userData = buffer.readByteArray();

        int chunkCount = buffer.readVarInt();
        var chunks = new ArrayList<PolarChunk>(chunkCount);
        for (int i = 0; i < chunkCount; i++) {
            chunks.add(readChunk(dataConverter, version, dataVersion, buffer, maxSection - minSection + 1));
        }

        return new PolarWorld(version, dataVersion, compression, minSection, maxSection, userData, chunks);
    }

    private static @NotNull PolarChunk readChunk(@NotNull PolarDataConverter dataConverter, short version, int dataVersion, @NotNull BufferWrapper buffer, int sectionCount) {
        var chunkX = buffer.readVarInt();
        var chunkZ = buffer.readVarInt();

        var sections = new PolarSection[sectionCount];
        for (int i = 0; i < sectionCount; i++) {
            sections[i] = readSection(dataConverter, version, dataVersion, buffer);
        }

        int blockEntityCount = buffer.readVarInt();
        var blockEntities = new ArrayList<PolarChunk.BlockEntity>(blockEntityCount);
        for (int i = 0; i < blockEntityCount; i++) {
            blockEntities.add(readBlockEntity(dataConverter, version, dataVersion, buffer));
        }

        var heightmaps = readHeightmapData(buffer, false);

        // Objects
        byte[] userData = new byte[0];
        if (version > PolarWorld.VERSION_USERDATA_OPT_BLOCK_ENT_NBT)
            userData = buffer.readByteArray();

        return new PolarChunk(
                chunkX, chunkZ,
                sections,
                blockEntities,
                heightmaps,
                userData
        );
    }

    private static @NotNull PolarSection readSection(@NotNull PolarDataConverter dataConverter, short version, int dataVersion, @NotNull BufferWrapper buffer) {
        // If section is empty exit immediately
        if (buffer.readBoolean()) return new PolarSection();

        var blockPalette = buffer.readStringArray(MAX_BLOCK_PALETTE_SIZE);
        if (dataVersion < dataConverter.dataVersion()) {
            dataConverter.convertBlockPalette(blockPalette, dataVersion, dataConverter.dataVersion());
        }
        upgradeGrassInPalette(blockPalette, version);
        int[] blockData = null;
        if (blockPalette.length > 1) {
            blockData = new int[PolarSection.BLOCK_PALETTE_SIZE];

            var rawBlockData = buffer.readLongArray();
            var bitsPerEntry = (int) Math.ceil(Math.log(blockPalette.length) / Math.log(2));
            PaletteUtil.unpack(blockData, rawBlockData, bitsPerEntry);
        }

        var biomePalette = buffer.readStringArray(MAX_BIOME_PALETTE_SIZE);
        int[] biomeData = null;
        if (biomePalette.length > 1) {
            biomeData = new int[PolarSection.BIOME_PALETTE_SIZE];

            var rawBiomeData = buffer.readLongArray();
            var bitsPerEntry = (int) Math.ceil(Math.log(biomePalette.length) / Math.log(2));
            PaletteUtil.unpack(biomeData, rawBiomeData, bitsPerEntry);
        }

        LightContent blockLightContent = LightContent.MISSING, skyLightContent = LightContent.MISSING;
        byte[] blockLight = null, skyLight = null;
        if (version > PolarWorld.VERSION_UNIFIED_LIGHT) {
            blockLightContent = version >= PolarWorld.VERSION_IMPROVED_LIGHT
                    ? LightContent.VALUES[buffer.readByte()]
                    : (buffer.readBoolean() ? LightContent.PRESENT : LightContent.MISSING);
            if (blockLightContent == LightContent.PRESENT)
                blockLight = buffer.readLightData();
            skyLightContent = version >= PolarWorld.VERSION_IMPROVED_LIGHT
                    ? LightContent.VALUES[buffer.readByte()]
                    : (buffer.readBoolean() ? LightContent.PRESENT : LightContent.MISSING);
            if (skyLightContent == LightContent.PRESENT)
                skyLight = buffer.readLightData();
        } else if (buffer.readBoolean()) {
            blockLightContent = LightContent.PRESENT;
            blockLight = buffer.readLightData();
            skyLightContent = LightContent.PRESENT;
            skyLight = buffer.readLightData();
        }

        return new PolarSection(
                blockPalette, blockData,
                biomePalette, biomeData,
                blockLightContent, blockLight,
                skyLightContent, skyLight
        );
    }

    static void upgradeGrassInPalette(String[] blockPalette, int version) {
        if (version <= PolarWorld.VERSION_SHORT_GRASS) {
            for (int i = 0; i < blockPalette.length; i++) {
                if (blockPalette[i].contains("grass")) {
                    String strippedID = blockPalette[i].split("\\[")[0];
                    if (Key.key(strippedID).value().equals("grass")) {
                        blockPalette[i] = "short_grass";
                    }
                }
            }
        }
    }

    static int[][] readHeightmapData(@NotNull BufferWrapper buffer, boolean skip) {
        var heightmaps = !skip ? new int[PolarChunk.MAX_HEIGHTMAPS][] : null;
        int heightmapMask = buffer.readInt();
        for (int i = 0; i < PolarChunk.MAX_HEIGHTMAPS; i++) {
            if ((heightmapMask & (1 << i)) == 0)
                continue;

            if (!skip) {
                var packed = buffer.readLongArray();
                if (packed.length == 0) {
                    heightmaps[i] = new int[0];
                } else {
                    var bitsPerEntry = packed.length * 64 / PolarChunk.HEIGHTMAP_SIZE;
                    heightmaps[i] = new int[PolarChunk.HEIGHTMAP_SIZE];
                    PaletteUtil.unpack(heightmaps[i], packed, bitsPerEntry);
                }
            } else {
                buffer.advanceRead(buffer.readVarInt() * 8); // Skip a long array
            }
        }
        return heightmaps;
    }

    static @NotNull PolarChunk.BlockEntity readBlockEntity(@NotNull PolarDataConverter dataConverter, int version, int dataVersion, @NotNull BufferWrapper buffer) {
        int posIndex = buffer.readInt();
        var id = buffer.readOptString();

        CompoundBinaryTag nbt = CompoundBinaryTag.empty();
        if (version <= PolarWorld.VERSION_USERDATA_OPT_BLOCK_ENT_NBT || buffer.readBoolean()) {
            if (version <= PolarWorld.VERSION_MINESTOM_NBT_READ_BREAK || FORCE_LEGACY_NBT) {
                nbt = (CompoundBinaryTag) buffer.readLegacyNbt();
            } else {
                nbt = (CompoundBinaryTag) buffer.readNbt();
            }
        }

        if (dataVersion < dataConverter.dataVersion()) {
            var converted = dataConverter.convertBlockEntityData(id == null ? "" : id, nbt, dataVersion, dataConverter.dataVersion());
            id = converted.getKey();
            if (id.isEmpty()) id = null;
            nbt = converted.getValue();
            if (nbt.size() == 0) nbt = null;
        }

        return new PolarChunk.BlockEntity(
                Utils.chunkBlockIndexGetX(posIndex),
                Utils.chunkBlockIndexGetY(posIndex),
                Utils.chunkBlockIndexGetZ(posIndex),
                id, nbt
        );
    }

    static void validateVersion(int version) {
        var invalidVersionError = String.format("Unsupported Polar version. Up to %d is supported, found %d.",
                PolarWorld.LATEST_VERSION, version);
        assertThat(version <= PolarWorld.LATEST_VERSION, invalidVersionError);
    }

    private static @NotNull BufferWrapper decompressBuffer(@NotNull BufferFactory bufferFactory, @NotNull BufferWrapper buffer, @NotNull PolarWorld.CompressionType compression, int length) {
        return switch (compression) {
            case NONE -> buffer;
            case ZSTD -> {
                var bytes = Zstd.decompress(buffer.readRawBytes(), length);
                var newBuffer = bufferFactory.wrap(bytes);
                newBuffer.writeIndex(bytes.length);
                yield newBuffer;
            }
        };
    }

    @Contract("false, _ -> fail")
    static void assertThat(boolean condition, @NotNull String message) {
        if (!condition) throw new Error(message);
    }

    public static class Error extends RuntimeException {
        private Error(String message) {
            super(message);
        }
    }

}
