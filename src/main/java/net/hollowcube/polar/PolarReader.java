package net.hollowcube.polar;

import com.github.luben.zstd.Zstd;
import net.minestom.server.network.NetworkBuffer;
import net.minestom.server.utils.NamespaceID;
import net.minestom.server.utils.chunk.ChunkUtils;
import net.minestom.server.utils.validate.Check;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jglrxavpok.hephaistos.nbt.CompressedProcesser;
import org.jglrxavpok.hephaistos.nbt.NBT;
import org.jglrxavpok.hephaistos.nbt.NBTCompound;
import org.jglrxavpok.hephaistos.nbt.NBTReader;

import java.io.InputStream;
import java.nio.ByteBuffer;

import static net.minestom.server.network.NetworkBuffer.*;

@SuppressWarnings("UnstableApiUsage")
public class PolarReader {
    private PolarReader() {}

    public static @NotNull PolarWorld read(byte @NotNull [] data) {
        var buffer = new NetworkBuffer(ByteBuffer.wrap(data));
        buffer.writeIndex(data.length); // Set write index to end so readableBytes returns remaining bytes

        var magicNumber = buffer.read(INT);
        assertThat(magicNumber == PolarWorld.MAGIC_NUMBER, "Invalid magic number");

        short version = buffer.read(SHORT);
        validateVersion(version);

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

        var chunks = buffer.readCollection(b -> readChunk(version, b, maxSection - minSection + 1));

        return new PolarWorld(version, compression, minSection, maxSection, userData, chunks);
    }

    private static @NotNull PolarChunk readChunk(short version, @NotNull NetworkBuffer buffer, int sectionCount) {
        var chunkX = buffer.read(VAR_INT);
        var chunkZ = buffer.read(VAR_INT);

        var sections = new PolarSection[sectionCount];
        for (int i = 0; i < sectionCount; i++) {
            sections[i] = readSection(version, buffer);
        }

        var blockEntities = buffer.readCollection(b -> readBlockEntity(version, b));

        var heightmaps = new byte[PolarChunk.HEIGHTMAP_BYTE_SIZE][PolarChunk.HEIGHTMAPS.length];
        int heightmapMask = buffer.read(INT);
        for (int i = 0; i < PolarChunk.HEIGHTMAPS.length; i++) {
            if ((heightmapMask & PolarChunk.HEIGHTMAPS[i]) == 0)
                continue;

            heightmaps[i] = buffer.readBytes(32);
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

    private static @NotNull PolarSection readSection(short version, @NotNull NetworkBuffer buffer) {
        // If section is empty exit immediately
        if (buffer.read(BOOLEAN)) return new PolarSection();

        var blockPalette = buffer.readCollection(STRING).toArray(String[]::new);
        if (version <= PolarWorld.VERSION_SHORT_GRASS) {
            for (int i = 0; i < blockPalette.length; i++) {
                String strippedID = blockPalette[i].split("\\[")[0];
                if (NamespaceID.from(strippedID).path().equals("grass"))
                    blockPalette[i] = "short_grass";
            }
        }
        int[] blockData = null;
        if (blockPalette.length > 1) {
            blockData = new int[PolarSection.BLOCK_PALETTE_SIZE];

            var rawBlockData = buffer.read(LONG_ARRAY);
            var bitsPerEntry = rawBlockData.length * 64 / PolarSection.BLOCK_PALETTE_SIZE;
            PaletteUtil.unpack(blockData, rawBlockData, bitsPerEntry);
        }

        var biomePalette = buffer.readCollection(STRING).toArray(String[]::new);
        int[] biomeData = null;
        if (biomePalette.length > 1) {
            biomeData = new int[PolarSection.BIOME_PALETTE_SIZE];

            var rawBiomeData = buffer.read(LONG_ARRAY);
            var bitsPerEntry = rawBiomeData.length * 64 / PolarSection.BIOME_PALETTE_SIZE;
            PaletteUtil.unpack(biomeData, rawBiomeData, bitsPerEntry);
        }

        byte[] blockLight = null, skyLight = null;

        if (version > PolarWorld.VERSION_UNIFIED_LIGHT) {
            if (buffer.read(BOOLEAN))
                blockLight = buffer.readBytes(2048);
            if (buffer.read(BOOLEAN))
                skyLight = buffer.readBytes(2048);
        } else if (buffer.read(BOOLEAN)) {
            blockLight = buffer.readBytes(2048);
            skyLight = buffer.readBytes(2048);
        }

        return new PolarSection(blockPalette, blockData, biomePalette, biomeData, blockLight, skyLight);
    }

    private static @NotNull PolarChunk.BlockEntity readBlockEntity(int version, @NotNull NetworkBuffer buffer) {
        int posIndex = buffer.read(INT);
        var id = buffer.readOptional(STRING);

        NBTCompound nbt = null;
        if (version <= PolarWorld.VERSION_USERDATA_OPT_BLOCK_ENT_NBT || buffer.read(BOOLEAN)) {
            if (version <= PolarWorld.VERSION_MINESTOM_NBT_READ_BREAK) {
                nbt = (NBTCompound) legacyReadNBT(buffer);
            } else {
                nbt = (NBTCompound) buffer.read(NBT);
            }
        }

        return new PolarChunk.BlockEntity(
                ChunkUtils.blockIndexToChunkPositionX(posIndex),
                ChunkUtils.blockIndexToChunkPositionY(posIndex),
                ChunkUtils.blockIndexToChunkPositionZ(posIndex),
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
                var bytes = Zstd.decompress(buffer.readBytes(buffer.readableBytes()), length);
                var newBuffer = new NetworkBuffer(ByteBuffer.wrap(bytes));
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
    private static org.jglrxavpok.hephaistos.nbt.NBT legacyReadNBT(@NotNull NetworkBuffer buffer) {
        try {
            var nbtReader = new NBTReader(new InputStream() {
                @Override
                public int read() {
                    return buffer.read(BYTE) & 0xFF;
                }
                @Override
                public int available() {
                    return buffer.readableBytes();
                }
            }, CompressedProcesser.NONE);

            return nbtReader.read();
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
