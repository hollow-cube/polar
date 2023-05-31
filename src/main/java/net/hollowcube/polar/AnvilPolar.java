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

    public static @NotNull PolarWorld anvilToPolar(@NotNull Path path) throws IOException {
        return anvilToPolar(path, ChunkSelector.all());
    }

    public static @NotNull PolarWorld anvilToPolar(@NotNull Path path, @NotNull ChunkSelector selector) throws IOException {
        int minSection = Integer.MAX_VALUE, maxSection = Integer.MIN_VALUE;

        var chunks = new ArrayList<PolarChunk>();
        try (var files = Files.walk(path.resolve("region"), 1)) {
            for (var regionFile : files.toList()) {
                if (!regionFile.getFileName().toString().endsWith(".mca")) continue;

                var nameParts = regionFile.getFileName().toString().split("\\.");
                var regionX = Integer.parseInt(nameParts[1]);
                var regionZ = Integer.parseInt(nameParts[2]);

                try (var region = new RegionFile(new RandomAccessFile(regionFile.toFile(), "r"), regionX, regionZ)) {
                    var chunkSet = readAnvilChunks(region, selector);
                    if (chunkSet.chunks.isEmpty()) continue;

                    if (minSection == Integer.MAX_VALUE) {
                        minSection = chunkSet.minSection();
                        maxSection = chunkSet.maxSection();
                    } else {
                        if (minSection != chunkSet.minSection() || maxSection != chunkSet.maxSection()) {
                            throw new IllegalStateException("Inconsistent world height 2 " + minSection + " " + maxSection + " " + chunkSet.minSection() + " " + chunkSet.maxSection());
                        }
                    }

                    chunks.addAll(chunkSet.chunks());
                }
            }
        } catch (AnvilException e) {
            throw new IOException(e);
        }

        return new PolarWorld(
                PolarWorld.VERSION_MAJOR,
                PolarWorld.VERSION_MINOR,
                PolarWorld.DEFAULT_COMPRESSION,
                (byte) minSection, (byte) maxSection,
                chunks
        );
    }

    private static @NotNull ChunkSet readAnvilChunks(@NotNull RegionFile regionFile, @NotNull ChunkSelector selector) throws AnvilException, IOException {
        int minSection = Integer.MAX_VALUE, maxSection = Integer.MIN_VALUE;

        var chunks = new ArrayList<PolarChunk>();
        for (int x = 0; x < 32; x++) {
            for (int z = 0; z < 32; z++) {
                int chunkX = x + (regionFile.getRegionX() * 32);
                int chunkZ = z + (regionFile.getRegionZ() * 32);

                if (!selector.test(chunkX, chunkZ)) continue;

                var chunkData = regionFile.getChunkData(chunkX, chunkZ);
                if (chunkData == null) continue;

                var chunkReader = new ChunkReader(chunkData);

                //todo check over section min/max everywhere
                var yRange = chunkReader.getYRange();
                if (minSection == Integer.MAX_VALUE) {
                    minSection = yRange.getStart() >> 4;
                    maxSection = yRange.getEndInclusive() >> 4;
                } else {
                    if (minSection != yRange.getStart() >> 4 || maxSection != yRange.getEndInclusive() >> 4) {
                        throw new IllegalStateException("Inconsistent world height");
                    }
                }

                var sections = new PolarSection[maxSection - minSection + 1];
                for (var sectionData : chunkReader.getSections()) {
                    var sectionReader = new ChunkSectionReader(chunkReader.getMinecraftVersion(), sectionData);

                    if (sectionReader.getY() < minSection) {
                        if (!sectionReader.isSectionEmpty()) {
                            logger.error("Non-empty section below minimum... something is wrong with this world :| (min={}, section={})", minSection, sectionReader.getY());
                            throw new IllegalStateException("Non-empty section below minimum");
                        }

                        logger.warn("Skipping section below min: {} (min={})", sectionReader.getY(), minSection);
                        continue;
                    }

                    // Blocks
                    String[] blockPalette;
                    int[] blockData = null;
                    var blockInfo = sectionReader.getBlockPalette();
                    if (blockInfo == null) {
                        logger.warn("Chunk section {}, {}, {} has no block palette",
                                chunkReader.getChunkX(), sectionReader.getY(), chunkReader.getChunkZ());

                        blockPalette = new String[]{"minecraft:air"};
                    } else {
//                        System.out.println("sec " + chunkReader.getChunkX() + " " + sectionReader.getY() + " " + chunkReader.getChunkZ());
                        blockData = sectionReader.getUncompressedBlockStateIDs();
                        blockPalette = new String[blockInfo.getSize()];
                        for (int i = 0; i < blockPalette.length; i++) {
                            var paletteEntry = blockInfo.get(i);
                            var blockName = new StringBuilder(Objects.requireNonNull(paletteEntry.getString("Name")));

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

                            blockPalette[i] = blockName.toString();
                        }
                    }

                    // Biomes
                    String[] biomePalette;
                    int[] biomeData = null;
                    var biomeInfo = sectionReader.getBiomeInformation();
                    if (!biomeInfo.hasBiomeInformation()) {
                        logger.warn("Chunk section {}, {}, {} has no biome information",
                                chunkReader.getChunkX(), sectionReader.getY(), chunkReader.getChunkZ());

                        biomePalette = new String[]{"minecraft:plains"};
                    } else if (biomeInfo.isFilledWithSingleBiome()) {
                        biomePalette = new String[]{biomeInfo.getBaseBiome()};
                    } else {
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


                    byte[] blockLight = null, skyLight = null;
                    if (sectionReader.getBlockLight() != null && sectionReader.getSkyLight() != null) {
                        blockLight = sectionReader.getBlockLight().copyArray();
                        skyLight = sectionReader.getSkyLight().copyArray();
                    }

                    sections[sectionReader.getY() - minSection] = new PolarSection(
                            blockPalette, blockData,
                            biomePalette, biomeData,
                            blockLight, skyLight
                    );
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

                chunks.add(new PolarChunk(
                        chunkReader.getChunkX(),
                        chunkReader.getChunkZ(),
                        sections,
                        blockEntities,
                        heightmaps
                ));
            }
        }
        return new ChunkSet(chunks, minSection, maxSection);
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

    private record ChunkSet(List<PolarChunk> chunks, int minSection, int maxSection) {
    }

}
