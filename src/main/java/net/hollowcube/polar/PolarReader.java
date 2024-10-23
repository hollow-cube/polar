package net.hollowcube.polar;

import com.github.luben.zstd.Zstd;
import net.hollowcube.polar.PolarSection.LightContent;
import net.kyori.adventure.nbt.BinaryTag;
import net.kyori.adventure.nbt.CompoundBinaryTag;
import net.minestom.server.coordinate.CoordConversion;
import net.minestom.server.network.NetworkBuffer;
import net.minestom.server.utils.NamespaceID;
import net.minestom.server.utils.nbt.BinaryTagReader;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.io.DataInputStream;
import java.io.InputStream;

import static net.minestom.server.network.NetworkBuffer.*;

@SuppressWarnings("UnstableApiUsage")
public class PolarReader {
    private static final boolean FORCE_LEGACY_NBT = Boolean.getBoolean("polar.debug.force-legacy-nbt");
    private static final int MAX_BLOCK_ENTITIES = Integer.MAX_VALUE;
    private static final int MAX_CHUNKS = Integer.MAX_VALUE;
    private static final int MAX_BLOCK_PALETTE_SIZE = 16 * 16 * 16;
    private static final int MAX_BIOME_PALETTE_SIZE = 8 * 8 * 8;

    private PolarReader() {
    }

    public static @NotNull PolarWorld read(byte @NotNull [] data) {
        return read(data, PolarDataConverter.NOOP);
    }

    public static @NotNull PolarWorld read(byte @NotNull [] data, @NotNull PolarDataConverter dataConverter) {
        var buffer = NetworkBuffer.wrap(data, 0, 0);
        buffer.writeIndex(data.length); // Set write index to end so readableBytes returns remaining bytes

        var magicNumber = buffer.read(INT);
        assertThat(magicNumber == PolarWorld.MAGIC_NUMBER, "Invalid magic number");

        short version = buffer.read(SHORT);
        validateVersion(version);

        int dataVersion = version >= PolarWorld.VERSION_DATA_CONVERTER
                ? buffer.read(VAR_INT)
                : dataConverter.defaultDataVersion();

        var compression = PolarWorld.CompressionType.fromId(buffer.read(BYTE));
        assertThat(compression != null, "Invalid compression type");
        var compressedDataLength = buffer.read(VAR_INT);

        // Replace the buffer with a "decompressed" version. This is a no-op if compression is NONE.
        buffer = decompressBuffer(buffer, compression, compressedDataLength);

        byte minSection = buffer.read(BYTE), maxSection = buffer.read(BYTE);
        assertThat(minSection < maxSection, "Invalid section range");

        // User (world) data
        byte[] userData = new byte[0];
        if (version > PolarWorld.VERSION_WORLD_USERDATA)
            userData = buffer.read(BYTE_ARRAY);

        var chunks =  buffer.read(new NetworkBuffer.Type<PolarChunk>() {
            @Override
            public void write(@NotNull NetworkBuffer buffer, PolarChunk value) {
                throw new UnsupportedOperationException();
            }

            @Override
            public PolarChunk read(@NotNull NetworkBuffer buffer) {
                return readChunk(dataConverter, version, dataVersion, buffer, maxSection - minSection + 1);
            }
        }.list(MAX_CHUNKS));

        return new PolarWorld(version, dataVersion, compression, minSection, maxSection, userData, chunks);
    }

    private static @NotNull PolarChunk readChunk(@NotNull PolarDataConverter dataConverter, short version, int dataVersion, @NotNull NetworkBuffer buffer, int sectionCount) {
        var chunkX = buffer.read(VAR_INT);
        var chunkZ = buffer.read(VAR_INT);

        var sections = new PolarSection[sectionCount];
        for (int i = 0; i < sectionCount; i++) {
            sections[i] = readSection(dataConverter, version, dataVersion, buffer);
        }

        var blockEntities = buffer.read(new NetworkBuffer.Type<PolarChunk.BlockEntity>() {
            @Override
            public void write(@NotNull NetworkBuffer buffer, PolarChunk.BlockEntity value) {
                throw new UnsupportedOperationException();
            }

            @Override
            public PolarChunk.BlockEntity read(@NotNull NetworkBuffer buffer) {
                return readBlockEntity(dataConverter, version, dataVersion, buffer);
            }
        }.list(MAX_BLOCK_ENTITIES));

        var heightmaps = new int[PolarChunk.MAX_HEIGHTMAPS][];
        int heightmapMask = buffer.read(INT);
        for (int i = 0; i < PolarChunk.MAX_HEIGHTMAPS; i++) {
            if ((heightmapMask & (1 << i)) == 0)
                continue;

            var packed = buffer.read(LONG_ARRAY);
            if (packed.length == 0) {
                heightmaps[i] = new int[0];
            } else {
                var bitsPerEntry = packed.length * 64 / PolarChunk.HEIGHTMAP_SIZE;
                heightmaps[i] = new int[PolarChunk.HEIGHTMAP_SIZE];
                PaletteUtil.unpack(heightmaps[i], packed, bitsPerEntry);
            }
        }

        // Objects
        byte[] userData = new byte[0];
        if (version > PolarWorld.VERSION_USERDATA_OPT_BLOCK_ENT_NBT)
            userData = buffer.read(BYTE_ARRAY);

        return new PolarChunk(
                chunkX, chunkZ,
                sections,
                blockEntities,
                heightmaps,
                userData
        );
    }

    private static @NotNull PolarSection readSection(@NotNull PolarDataConverter dataConverter, short version, int dataVersion, @NotNull NetworkBuffer buffer) {
        // If section is empty exit immediately
        if (buffer.read(BOOLEAN)) return new PolarSection();

        var blockPalette = buffer.read(STRING.list(MAX_BLOCK_PALETTE_SIZE)).toArray(String[]::new);
        if (dataVersion < dataConverter.dataVersion()) {
            dataConverter.convertBlockPalette(blockPalette, dataVersion, dataConverter.dataVersion());
        }
        if (version <= PolarWorld.VERSION_SHORT_GRASS) {
            for (int i = 0; i < blockPalette.length; i++) {
                if (blockPalette[i].contains("grass")) {
                    String strippedID = blockPalette[i].split("\\[")[0];
                    if (NamespaceID.from(strippedID).path().equals("grass")) {
                        blockPalette[i] = "short_grass";
                    }
                }
            }
        }
        int[] blockData = null;
        if (blockPalette.length > 1) {
            blockData = new int[PolarSection.BLOCK_PALETTE_SIZE];

            var rawBlockData = buffer.read(LONG_ARRAY);
            var bitsPerEntry = (int) Math.ceil(Math.log(blockPalette.length) / Math.log(2));
            PaletteUtil.unpack(blockData, rawBlockData, bitsPerEntry);
        }

        var biomePalette = buffer.read(STRING.list(MAX_BIOME_PALETTE_SIZE)).toArray(String[]::new);
        int[] biomeData = null;
        if (biomePalette.length > 1) {
            biomeData = new int[PolarSection.BIOME_PALETTE_SIZE];

            var rawBiomeData = buffer.read(LONG_ARRAY);
            var bitsPerEntry = (int) Math.ceil(Math.log(biomePalette.length) / Math.log(2));
            PaletteUtil.unpack(biomeData, rawBiomeData, bitsPerEntry);
        }

        LightContent blockLightContent = LightContent.MISSING, skyLightContent = LightContent.MISSING;
        byte[] blockLight = null, skyLight = null;
        if (version > PolarWorld.VERSION_UNIFIED_LIGHT) {
            blockLightContent = version >= PolarWorld.VERSION_IMPROVED_LIGHT
                    ? LightContent.VALUES[buffer.read(BYTE)]
                    : (buffer.read(BOOLEAN) ? LightContent.PRESENT : LightContent.MISSING);
            if (blockLightContent == LightContent.PRESENT)
                blockLight = buffer.read(FixedRawBytes(2048));
            skyLightContent = version >= PolarWorld.VERSION_IMPROVED_LIGHT
                    ? LightContent.VALUES[buffer.read(BYTE)]
                    : (buffer.read(BOOLEAN) ? LightContent.PRESENT : LightContent.MISSING);
            if (skyLightContent == LightContent.PRESENT)
                skyLight = buffer.read(FixedRawBytes(2048));
        } else if (buffer.read(BOOLEAN)) {
            blockLightContent = LightContent.PRESENT;
            blockLight = buffer.read(FixedRawBytes(2048));
            skyLightContent = LightContent.PRESENT;
            skyLight = buffer.read(FixedRawBytes(2048));
        }

        return new PolarSection(
                blockPalette, blockData,
                biomePalette, biomeData,
                blockLightContent, blockLight,
                skyLightContent, skyLight
        );
    }

    private static @NotNull PolarChunk.BlockEntity readBlockEntity(@NotNull PolarDataConverter dataConverter, int version, int dataVersion, @NotNull NetworkBuffer buffer) {
        int posIndex = buffer.read(INT);
        var id = buffer.read(STRING.optional());

        CompoundBinaryTag nbt = CompoundBinaryTag.empty();
        if (version <= PolarWorld.VERSION_USERDATA_OPT_BLOCK_ENT_NBT || buffer.read(BOOLEAN)) {
            if (version <= PolarWorld.VERSION_MINESTOM_NBT_READ_BREAK || FORCE_LEGACY_NBT) {
                nbt = (CompoundBinaryTag) legacyReadNBT(buffer);
            } else {
                nbt = (CompoundBinaryTag) buffer.read(NBT);
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
                CoordConversion.chunkBlockIndexGetX(posIndex),
                CoordConversion.chunkBlockIndexGetY(posIndex),
                CoordConversion.chunkBlockIndexGetZ(posIndex),
                id, nbt
        );
    }

    private static void validateVersion(int version) {
        var invalidVersionError = String.format("Unsupported Polar version. Up to %d is supported, found %d.",
                PolarWorld.LATEST_VERSION, version);
        assertThat(version <= PolarWorld.LATEST_VERSION, invalidVersionError);
    }

    private static @NotNull NetworkBuffer decompressBuffer(@NotNull NetworkBuffer buffer, @NotNull PolarWorld.CompressionType compression, int length) {
        return switch (compression) {
            case NONE -> buffer;
            case ZSTD -> {
                var bytes = Zstd.decompress(buffer.read(FixedRawBytes((int) buffer.readableBytes())), length);
                var newBuffer = NetworkBuffer.wrap(bytes, 0, 0);
                newBuffer.writeIndex(bytes.length);
                yield newBuffer;
            }
        };
    }

    /**
     * Minecraft (so Minestom) had a breaking change in NBT reading in 1.20.2. This method replicates the old
     * behavior which we use for any Polar version less than {@link PolarWorld#VERSION_MINESTOM_NBT_READ_BREAK}.
     *
     * @see NetworkBuffer#NBT
     */
    private static BinaryTag legacyReadNBT(@NotNull NetworkBuffer buffer) {
        try {
            var nbtReader = new BinaryTagReader(new DataInputStream(new InputStream() {
                public int read() {
                    return buffer.read(NetworkBuffer.BYTE) & 255;
                }

                public int available() {
                    return (int) buffer.readableBytes();
                }
            }));
            return nbtReader.readNamed().getValue();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Contract("false, _ -> fail")
    private static void assertThat(boolean condition, @NotNull String message) {
        if (!condition) throw new Error(message);
    }

    public static class Error extends RuntimeException {
        private Error(String message) {
            super(message);
        }
    }

}
