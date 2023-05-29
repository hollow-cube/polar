package net.hollowcube.polar;

import net.minestom.server.MinecraftServer;
import net.minestom.server.command.builder.arguments.minecraft.ArgumentBlockState;
import net.minestom.server.command.builder.exception.ArgumentSyntaxException;
import net.minestom.server.exception.ExceptionManager;
import net.minestom.server.instance.*;
import net.minestom.server.instance.block.Block;
import net.minestom.server.instance.block.BlockHandler;
import net.minestom.server.instance.block.BlockManager;
import net.minestom.server.utils.NamespaceID;
import net.minestom.server.world.biomes.Biome;
import net.minestom.server.world.biomes.BiomeManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jglrxavpok.hephaistos.nbt.NBTCompound;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ForkJoinPool;

@SuppressWarnings("UnstableApiUsage")
public class PolarLoader implements IChunkLoader {
    private static final BlockManager BLOCK_MANAGER = MinecraftServer.getBlockManager();
    private static final BiomeManager BIOME_MANAGER = MinecraftServer.getBiomeManager();
    private static final ExceptionManager EXCEPTION_HANDLER = MinecraftServer.getExceptionManager();
    private static final Logger logger = LoggerFactory.getLogger(PolarLoader.class);

    private final Path savePath;
    private final PolarWorld worldData;

    public PolarLoader(@NotNull Path path) throws IOException {
        this.worldData = PolarReader.read(Files.readAllBytes(path));
        this.savePath = path;
    }

    public PolarLoader(@NotNull InputStream inputStream) throws IOException {
        try (inputStream) {
            this.worldData = PolarReader.read(inputStream.readAllBytes());
            this.savePath = null;
        }
    }

    public PolarLoader(@NotNull PolarWorld world) {
        this.worldData = world;
        this.savePath = null;
    }

    public @NotNull PolarWorld world() {
        return worldData;
    }

    // Loading

    @Override
    public void loadInstance(@NotNull Instance instance) {
        //todo validate that the chunk is loadable in this world
    }

    @Override
    public @NotNull CompletableFuture<@Nullable Chunk> loadChunk(@NotNull Instance instance, int chunkX, int chunkZ) {
        var chunkData = worldData.chunkAt(chunkX, chunkZ);
        if (chunkData == null) return CompletableFuture.completedFuture(null);

        // We are making the assumption here that the chunk height is the same as this world.
        // Polar includes world height metadata in the prelude and assumes all chunks match
        // those values. We check that the dimension settings match in #loadInstance, so
        // here it can be ignored/assumed.

        // Load the chunk
        var chunk = instance.getChunkSupplier().createChunk(instance, chunkX, chunkZ);
        synchronized (chunk) {
            //todo replace with java locks, not Asynchronized
            int sectionY = chunk.getMinSection();
            for (var sectionData : chunkData.sections()) {
                if (sectionData.isEmpty()) continue;

                var section = chunk.getSection(sectionY);
                loadSection(sectionData, section);
                sectionY++;
            }

            for (var blockEntity : chunkData.blockEntities()) {
                loadBlockEntity(blockEntity, chunk);
            }
        }

        return CompletableFuture.completedFuture(chunk);
    }

    private void loadSection(@NotNull PolarSection sectionData, @NotNull Section section) {
        // assumed that section is _not_ empty

        // Blocks
        var rawBlockPalette = sectionData.blockPalette();
        var blockPalette = new Block[rawBlockPalette.length];
        for (int i = 0; i < rawBlockPalette.length; i++) {
            try {
                //noinspection deprecation
                blockPalette[i] = ArgumentBlockState.staticParse(rawBlockPalette[i]);
            } catch (ArgumentSyntaxException e) {
                logger.error("Failed to parse block state: {} ({})", rawBlockPalette[i], e.getMessage());
                blockPalette[i] = Block.AIR;
            }
        }
        if (blockPalette.length == 1) {
            section.blockPalette().fill(blockPalette[0].stateId());
        } else {
            final var paletteData = sectionData.blockData();
            section.blockPalette().setAll((x, y, z) -> {
                int index = y * Chunk.CHUNK_SECTION_SIZE * Chunk.CHUNK_SECTION_SIZE + z * Chunk.CHUNK_SECTION_SIZE + x;
                return blockPalette[paletteData[index]].stateId();
            });
        }

        // Biomes
        var rawBiomePalette = sectionData.biomePalette();
        var biomePalette = new Biome[rawBiomePalette.length];
        for (int i = 0; i < rawBiomePalette.length; i++) {
            var biome = BIOME_MANAGER.getByName(NamespaceID.from(rawBiomePalette[i]));
            if (biome == null) {
                logger.error("Failed to find biome: {}", rawBiomePalette[i]);
                biome = Biome.PLAINS;
            }
            biomePalette[i] = biome;
        }
        if (biomePalette.length == 1) {
            section.biomePalette().fill(biomePalette[0].id());
        } else {
            final var paletteData = sectionData.biomeData();
            section.biomePalette().setAll((x, y, z) -> {
                int index = x / 4 + (z / 4) * 4 + (y / 4) * 16;
                return biomePalette[paletteData[index]].id();
            });
        }

        // Light
        if (sectionData.hasLightData()) {
            section.setBlockLight(sectionData.blockLight());
            section.setSkyLight(sectionData.skyLight());
        }
    }

    private void loadBlockEntity(@NotNull PolarChunk.BlockEntity blockEntity, @NotNull Chunk chunk) {
        // Fetch the block type, we can ignore Handler/NBT since we are about to replace it
        var block = chunk.getBlock(blockEntity.x(), blockEntity.y(), blockEntity.z(), Block.Getter.Condition.TYPE);

        block = block.withHandler(BLOCK_MANAGER.getHandlerOrDummy(blockEntity.id()));
        block = block.withNbt(blockEntity.data());

        chunk.setBlock(blockEntity.x(), blockEntity.y(), blockEntity.z(), block);
    }

    // Unloading/saving

    @Override
    public @NotNull CompletableFuture<Void> saveInstance(@NotNull Instance instance) {
        return saveChunks(instance.getChunks());
    }

    @Override
    public void unloadChunk(Chunk chunk) {
        updateChunkData(chunk);
    }

    @Override
    public @NotNull CompletableFuture<Void> saveChunks(@NotNull Collection<Chunk> chunks) {
        // Update state of each chunk locally
        chunks.forEach(this::updateChunkData);

        // Write the file to disk
        if (savePath != null) {
            return CompletableFuture.runAsync(() -> {
                try {
                    Files.write(savePath, PolarWriter.write(worldData));
                } catch (IOException e) {
                    EXCEPTION_HANDLER.handleException(new RuntimeException("Failed to save world", e));
                }
            }, ForkJoinPool.commonPool());
        }

        return CompletableFuture.completedFuture(null);
    }

    private void updateChunkData(@NotNull Chunk chunk) {
        var dimension = chunk.getInstance().getDimensionType();

        var blockEntities = new ArrayList<PolarChunk.BlockEntity>();
        var sections = new PolarSection[dimension.getHeight() / Chunk.CHUNK_SECTION_SIZE];
        assert sections.length == chunk.getSections().size(): "World height mismatch";

        for (int i = 0; i < sections.length; i++) {
            int sectionY = i + chunk.getMinSection();
            var section = chunk.getSection(sectionY);
            //todo check if section is empty and skip

            var blockPalette = new ArrayList<String>();
            var blockData = new int[PolarSection.BLOCK_PALETTE_SIZE];

            for (int sectionLocalY = 0; sectionLocalY < Chunk.CHUNK_SECTION_SIZE; sectionLocalY++) {
                for (int z = 0; z < Chunk.CHUNK_SIZE_Z; z++) {
                    for (int x = 0; x < Chunk.CHUNK_SIZE_X; x++) {
                        int y = sectionLocalY + sectionY * Chunk.CHUNK_SECTION_SIZE;
                        var block = chunk.getBlock(x, y, z);

                        final int blockIndex = x + sectionLocalY * 16 * 16 + z * 16;

                        // Section palette
                        var namespace = blockToString(block);
                        int paletteId = blockPalette.indexOf(namespace);
                        if (paletteId == -1) {
                            paletteId = blockPalette.size();
                            blockPalette.add(namespace);
                        }
                        blockData[blockIndex] = paletteId;

                        // Block entities
                        var handlerId = block.handler() == null ? null : block.handler().getNamespaceId().asString();
                        if (handlerId != null || block.hasNbt()) {
                            blockEntities.add(new PolarChunk.BlockEntity(
                                    x, y, z, handlerId,
                                    block.hasNbt() ? Objects.requireNonNull(block.nbt()) : new NBTCompound()
                            ));
                        }
                    }
                }
            }

            var biomePalette = new ArrayList<String>();
            var biomeData = new int[PolarSection.BIOME_PALETTE_SIZE];

            section.biomePalette().getAll((x, y, z, id) -> {
                var biomeId = BIOME_MANAGER.getById(id).name().asString();

                var paletteId = biomePalette.indexOf(biomeId);
                if (paletteId == -1) {
                    paletteId = biomePalette.size();
                    biomePalette.add(biomeId);
                }

                biomeData[x + z * 4 + y * 4 * 4] = paletteId;
            });

            byte[] blockLight = section.blockLight().array();
            byte[] skyLight = section.skyLight().array();
            if (blockLight.length != 2048 || skyLight.length != 2048) {
                blockLight = null;
                skyLight = null;
            }

            sections[i] = new PolarSection(
                    blockPalette.toArray(new String[0]), blockData,
                    biomePalette.toArray(new String[0]), biomeData,
                    blockLight, skyLight
            );
        }

        var heightmaps = new byte[32][PolarChunk.HEIGHTMAPS.length];
        //todo

        worldData.updateChunkAt(
                chunk.getChunkX(),
                chunk.getChunkZ(),
                new PolarChunk(
                        chunk.getChunkX(),
                        chunk.getChunkZ(),
                        sections,
                        blockEntities,
                        heightmaps
                )
        );
    }

    @Override
    public @NotNull CompletableFuture<Void> saveChunk(@NotNull Chunk chunk) {
        throw new UnsupportedOperationException("Polar does not support saving individual chunks, see #saveChunks(Collection<Chunk>) or #saveInstance(Instance)");
    }

    private @NotNull String blockToString(@NotNull Block block) {
        var builder = new StringBuilder(block.name());
        if (block.properties().isEmpty()) return builder.toString();

        builder.append('[');
        for (var entry : block.properties().entrySet()) {
            builder.append(entry.getKey())
                    .append('=')
                    .append(entry.getValue())
                    .append(',');
        }
        builder.deleteCharAt(builder.length() - 1);
        builder.append(']');

        return builder.toString();
    }
}
