package net.hollowcube.polar;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jglrxavpok.hephaistos.mca.AnvilException;
import org.jglrxavpok.hephaistos.mca.RegionFile;
import org.jglrxavpok.hephaistos.mca.readers.ChunkReader;
import org.jglrxavpok.hephaistos.mca.readers.ChunkSectionReader;
import org.jglrxavpok.hephaistos.nbt.NBTCompound;
import org.jglrxavpok.hephaistos.nbt.NBTString;
import org.jglrxavpok.hephaistos.nbt.mutable.MutableNBTCompound;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class AnvilPolar {
    private static final Logger logger = LoggerFactory.getLogger(AnvilPolar.class);

    private static final boolean FILE_RW_MODE = Boolean.getBoolean("polar.anvil_rw_mode");
    public static final String FILE_RW_MODE_ERROR = """
            Hephaistos anvil reader attempted to do normalization and write the result back to disk during read.
            
            Polar prevents this behavior by default to avoid modifying the input worlds. Updating this world to a
            recent version should fix this issue, otherwise you can pass the system property
            `-Dpolar.anvil_rw_mode=true` to allow this write to occur.
            """;

    /**
     * Convert the anvil world at the given path to a Polar world. The world height range (in sections) is assumed
     * to be from -4 to 19 (inclusive), which is the default for recent Minecraft versions.
     * <br />
     * All chunks from all regions in the anvil world will be included in the Polar world.
     *
     * @param path Path to the anvil world (the directory containing the region directory)
     * @return The Polar world representing the given Anvil world
     * @throws IOException If there was an error reading the anvil world
     */
    public static @NotNull PolarWorld anvilToPolar(@NotNull Path path) throws IOException {
        return anvilToPolar(path, -4, 19, ChunkSelector.all());
    }

    /**
     * Convert the anvil world at the given path to a Polar world. The world height range (in sections) is assumed
     * to be from -4 to 19 (inclusive), which is the default for recent Minecraft versions.
     * <br />
     * Only the selected chunks will be included in the resulting Polar world.
     *
     * @param path Path to the anvil world (the directory containing the region directory)
     * @param selector Chunk selector to use to determine which chunks to include in the Polar world
     * @return The Polar world representing the given Anvil world
     * @throws IOException If there was an error reading the anvil world
     */
    public static @NotNull PolarWorld anvilToPolar(@NotNull Path path, @NotNull ChunkSelector selector) throws IOException {
        return anvilToPolar(path, -4, 19, selector);
    }

    /**
     * Convert the anvil world at the given path to a Polar world. The provided world height range
     * will be used to determine which sections will be included in the Polar world. If a section is missing,
     * an empty polar section will be included in its place.
     * <br />
     * All chunks from all regions in the anvil world will be included in the Polar world.
     *
     * @param path Path to the anvil world (the directory containing the region directory)
     * @param minSection The minimum section to include in the Polar world
     * @param maxSection The maximum section to include in the Polar world
     * @return The Polar world representing the given Anvil world
     * @throws IOException If there was an error reading the anvil world
     */
    public static @NotNull PolarWorld anvilToPolar(@NotNull Path path, int minSection, int maxSection) throws IOException {
        return anvilToPolar(path, minSection, maxSection, ChunkSelector.all());
    }

    /**
     * Convert the anvil world at the given path to a Polar world. The provided world height range
     * will be used to determine which sections will be included in the Polar world. If a section is missing,
     * an empty polar section will be included in its place.
     * <br />
     * Only the selected chunks will be included in the resulting Polar world.
     *
     * @param path Path to the anvil world (the directory containing the region directory)
     * @param minSection The minimum section to include in the Polar world
     * @param maxSection The maximum section to include in the Polar world
     * @param selector Chunk selector to use to determine which chunks to include in the Polar world
     * @return The Polar world representing the given Anvil world
     * @throws IOException If there was an error reading the anvil world
     */
    public static @NotNull PolarWorld anvilToPolar(@NotNull Path path, int minSection, int maxSection, @NotNull ChunkSelector selector) throws IOException {
        var chunks = new ArrayList<PolarChunk>();
        try (var files = Files.walk(path.resolve("region"), 1)) {
            for (var regionFile : files.toList()) {
                if (!regionFile.getFileName().toString().endsWith(".mca")) continue;

                var nameParts = regionFile.getFileName().toString().split("\\.");
                var regionX = Integer.parseInt(nameParts[1]);
                var regionZ = Integer.parseInt(nameParts[2]);

                try (var region = new RegionFile(new RandomAccessFile(regionFile.toFile(), FILE_RW_MODE ? "rw" : "r"), regionX, regionZ)) {
                    chunks.addAll(readAnvilChunks(region, minSection, maxSection, selector));
                } catch (IOException e) {
                    if (e.getMessage().equals("Bad file descriptor"))
                        throw new IOException(FILE_RW_MODE_ERROR, e);

                    throw e;
                }
            }
        } catch (AnvilException e) {
            throw new IOException(e);
        }

        return new PolarWorld(
                PolarWorld.LATEST_VERSION,
                PolarWorld.DEFAULT_COMPRESSION,
                (byte) minSection, (byte) maxSection,
                new byte[0],
                chunks
        );
    }

    private static @NotNull List<PolarChunk> readAnvilChunks(@NotNull RegionFile regionFile, int minSection, int maxSection, @NotNull ChunkSelector selector) throws AnvilException, IOException {
        var chunks = new ArrayList<PolarChunk>();
        for (int x = 0; x < 32; x++) {
            for (int z = 0; z < 32; z++) {
                int chunkX = x + (regionFile.getRegionX() * 32);
                int chunkZ = z + (regionFile.getRegionZ() * 32);

                if (!selector.test(chunkX, chunkZ)) continue;

                var chunkData = regionFile.getChunkData(chunkX, chunkZ);
                if (chunkData == null) continue;

                var chunkReader = new ChunkReader(chunkData);

                var sections = new PolarSection[maxSection - minSection + 1];
                for (var sectionData : chunkReader.getSections()) {
                    var sectionReader = new ChunkSectionReader(chunkReader.getMinecraftVersion(), sectionData);

                    if (sectionReader.getY() < minSection) {
                        logger.warn("Skipping section below min: {} (min={})", sectionReader.getY(), minSection);
                        continue;
                    }
                    if (sectionReader.getY() > maxSection) {
                        logger.warn("Skipping section above max: {} (max={})", sectionReader.getY(), maxSection);
                        continue;
                    }

                    // Blocks
                    String[] blockPalette;
                    int[] blockData = null;
                    var blockInfo = sectionReader.getBlockPalette();
                    if (blockInfo == null) {
                        // No blocks present, replace with a full air chunk
                        logger.warn("Chunk section {}, {}, {} has no block palette",
                                chunkReader.getChunkX(), sectionReader.getY(), chunkReader.getChunkZ());

                        blockPalette = new String[]{"minecraft:air"};
                    } else if (blockInfo.getSize() == 1) {
                        // Single block palette, no block data.
                        blockPalette = new String[]{readBlock(blockInfo.get(0))};
                    } else {
                        blockData = new int[PolarSection.BLOCK_PALETTE_SIZE];
                        var rawBlockData = sectionReader.getCompactedBlockStates().copyArray();
                        var bitsPerEntry = rawBlockData.length * 64 / PolarSection.BLOCK_PALETTE_SIZE;
                        PaletteUtil.unpack(blockData, rawBlockData, bitsPerEntry);

//                        blockData = sectionReader.getUncompressedBlockStateIDs();
                        blockPalette = new String[blockInfo.getSize()];
                        for (int i = 0; i < blockPalette.length; i++) {
                            blockPalette[i] = readBlock(blockInfo.get(i));
                        }
                    }

                    // Biomes
                    String[] biomePalette;
                    int[] biomeData = null;
                    var biomeInfo = sectionReader.getBiomeInformation();
                    if (!biomeInfo.hasBiomeInformation()) {
                        // No biomes are a warning + replaced with plains only. This happens for older worlds/unmigrated chunks
                        logger.warn("Chunk section {}, {}, {} has no biome information",
                                chunkReader.getChunkX(), sectionReader.getY(), chunkReader.getChunkZ());

                        biomePalette = new String[]{"minecraft:plains"};
                    } else if (biomeInfo.isFilledWithSingleBiome()) {
                        // Single biome case, handled as null data and a single entry palette
                        biomePalette = new String[]{biomeInfo.getBaseBiome()};
                    } else {
                        // Full palette case, convert from 64 strings provided by anvil to a normal palette (split data + palette)
                        var palette = new ArrayList<String>();
                        biomeData = new int[PolarSection.BIOME_PALETTE_SIZE];
                        for (int i = 0; i < biomeData.length; i++) {
                            var biome = biomeInfo.getBiomes()[i];
                            var paletteId = palette.indexOf(biome);
                            if (paletteId == -1) {
                                palette.add(biome);
                                paletteId = palette.size() - 1;
                            }

                            biomeData[i] = paletteId;
                        }
                        biomePalette = palette.toArray(new String[0]);
                    }

                    // Lighting data, if present
                    byte[] blockLight = null;
                    if (sectionReader.getBlockLight() != null) {
                        blockLight = sectionReader.getBlockLight().copyArray();
                    }
                    byte[] skyLight = null;
                    if (sectionReader.getSkyLight() != null) {
                        skyLight = sectionReader.getSkyLight().copyArray();
                    }

                    sections[sectionReader.getY() - minSection] = new PolarSection(
                            blockPalette, blockData,
                            biomePalette, biomeData,
                            blockLight, skyLight
                    );
                }
                // Fill in the remaining sections with empty sections
                for (int i = 0; i < sections.length; i++) {
                    if (sections[i] != null) continue;
                    sections[i] = new PolarSection();
                }

                var blockEntities = new ArrayList<PolarChunk.BlockEntity>();
                for (var blockEntityCompound : chunkReader.getBlockEntities()) {
                    var blockEntity = convertBlockEntity(blockEntityCompound);
                    if (blockEntity != null) blockEntities.add(blockEntity);
                }

                var heightmaps = new byte[PolarChunk.HEIGHTMAP_BYTE_SIZE][PolarChunk.HEIGHTMAPS.length];
                chunkData.getCompound("Heightmaps");
                //todo: heightmaps
//                MOTION_BLOCKING MOTION_BLOCKING_NO_LEAVES
//                OCEAN_FLOOR OCEAN_FLOOR_WG
//                WORLD_SURFACE WORLD_SURFACE_WG

                var userData = new byte[0];

                chunks.add(new PolarChunk(
                        chunkReader.getChunkX(),
                        chunkReader.getChunkZ(),
                        sections,
                        blockEntities,
                        heightmaps,
                        userData
                ));
            }
        }
        return chunks;
    }

    private static @Nullable PolarChunk.BlockEntity convertBlockEntity(@NotNull NBTCompound blockEntityCompound) {
        final var x = blockEntityCompound.getInt("x");
        final var y = blockEntityCompound.getInt("y");
        final var z = blockEntityCompound.getInt("z");
        if (x == null || y == null || z == null) {
            logger.warn("Block entity could not be converted due to invalid coordinates");
            return null;
        }

        final String blockEntityId = blockEntityCompound.getString("id");
        if (blockEntityId == null) {
            logger.warn("Block entity could not be converted due to missing id");
            return null;
        }

        // Remove anvil tags
        MutableNBTCompound mutableCopy = blockEntityCompound.toMutableCompound();
        mutableCopy.remove("id");
        mutableCopy.remove("x");
        mutableCopy.remove("y");
        mutableCopy.remove("z");
        mutableCopy.remove("keepPacked");

        return new PolarChunk.BlockEntity(x, y, z, blockEntityId, mutableCopy.toCompound());
    }

    private static @NotNull String readBlock(@NotNull NBTCompound paletteEntry) {
        var blockName = new StringBuilder();
        var namespaceId = Objects.requireNonNull(paletteEntry.getString("Name"))
                .replace("minecraft:", ""); // No need to include minecraft: prefix, it is assumed.
        blockName.append(namespaceId);

        var propertiesNbt = paletteEntry.getCompound("Properties");
        if (propertiesNbt != null && propertiesNbt.getSize() > 0) {
            blockName.append("[");

            for (var property : propertiesNbt) {
                blockName.append(property.getKey())
                        .append("=")
                        .append(((NBTString) property.getValue()).getValue())
                        .append(",");
            }
            blockName.deleteCharAt(blockName.length() - 1);

            blockName.append("]");
        }

        return blockName.toString();
    }

}
