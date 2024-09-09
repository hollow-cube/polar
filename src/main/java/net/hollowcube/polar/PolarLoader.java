package net.hollowcube.polar;

import it.unimi.dsi.fastutil.shorts.Short2ObjectMap;
import it.unimi.dsi.fastutil.shorts.Short2ObjectOpenHashMap;
import net.hollowcube.polar.PolarSection.LightContent;
import net.minestom.server.MinecraftServer;
import net.minestom.server.command.builder.arguments.minecraft.ArgumentBlockState;
import net.minestom.server.command.builder.exception.ArgumentSyntaxException;
import net.minestom.server.exception.ExceptionManager;
import net.minestom.server.instance.Chunk;
import net.minestom.server.instance.IChunkLoader;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.Section;
import net.minestom.server.instance.block.Block;
import net.minestom.server.instance.block.BlockManager;
import net.minestom.server.instance.light.LightCompute;
import net.minestom.server.network.NetworkBuffer;
import net.minestom.server.registry.DynamicRegistry;
import net.minestom.server.world.DimensionType;
import net.minestom.server.world.biome.Biome;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static net.minestom.server.instance.Chunk.CHUNK_SECTION_SIZE;

@SuppressWarnings("UnstableApiUsage")
public class PolarLoader implements IChunkLoader {
    static final Logger logger = LoggerFactory.getLogger(PolarLoader.class);
    private static final BlockManager BLOCK_MANAGER = MinecraftServer.getBlockManager();
    private static final DynamicRegistry<Biome> BIOME_REGISTRY = MinecraftServer.getBiomeRegistry();
    private static final ExceptionManager EXCEPTION_HANDLER = MinecraftServer.getExceptionManager();
    private static final int PLAINS_BIOME_ID = BIOME_REGISTRY.getId(Biome.PLAINS);

    private final Map<String, Integer> biomeReadCache = new ConcurrentHashMap<>();
    private final Map<Integer, String> biomeWriteCache = new ConcurrentHashMap<>();

    private final Path savePath;
    private final ReentrantReadWriteLock worldDataLock = new ReentrantReadWriteLock();
    private PolarWorld worldData;

    private PolarWorldAccess worldAccess = PolarWorldAccess.DEFAULT;
    private boolean parallel = false;
    private boolean loadLighting = true;

    public PolarLoader(@NotNull Path path) throws IOException {
        this(path, Files.exists(path) ? PolarReader.read(Files.readAllBytes(path)) : new PolarWorld());
    }

    public PolarLoader(@NotNull Path savePath, @NotNull PolarWorld worldData) {
        this.savePath = savePath;
        this.worldData = worldData;
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

    @Contract("_ -> this")
    public @NotNull PolarLoader setWorldAccess(@NotNull PolarWorldAccess worldAccess) {
        this.worldAccess = worldAccess;
        return this;
    }

    /**
     * Sets the loader to save and load in parallel.
     * <br/><br/>
     * The Polar loader on its own supports parallel load out of the box, but
     * a user implementation of {@link PolarWorldAccess} may not support parallel
     * operations, so care must be taken when enabling this option.
     *
     * @param parallel True to load and save chunks in parallel, false otherwise.
     * @return this
     */
    @Contract("_ -> this")
    public @NotNull PolarLoader setParallel(boolean parallel) {
        this.parallel = parallel;
        return this;
    }

    @Contract("_ -> this")
    public @NotNull PolarLoader setLoadLighting(boolean loadLighting) {
        this.loadLighting = loadLighting;
        return this;
    }

    // Loading


    @Override
    public boolean supportsParallelLoading() {
        return parallel;
    }

    @Override
    public void loadInstance(@NotNull Instance instance) {
        var userData = worldData.userData();
        if (userData.length > 0) {
            worldAccess.loadWorldData(instance, new NetworkBuffer(ByteBuffer.wrap(userData)));
        }
    }

    @Override
    public @NotNull CompletableFuture<@Nullable Chunk> loadChunk(@NotNull Instance instance, int chunkX, int chunkZ) {
        // Only need to lock for this tiny part, chunks are immutable.
        worldDataLock.readLock().lock();
        var chunkData = worldData.chunkAt(chunkX, chunkZ);
        worldDataLock.readLock().unlock();
        if (chunkData == null) return CompletableFuture.completedFuture(null);

        // We are making the assumption here that the chunk height is the same as this world.
        // Polar includes world height metadata in the prelude and assumes all chunks match
        // those values. We check that the dimension settings match in #loadInstance, so
        // here it can be ignored/assumed.

        // Load the chunk
        var chunk = instance.getChunkSupplier().createChunk(instance, chunkX, chunkZ);
        synchronized (chunk) {
            //todo replace with java locks, not synchronized
            //   actually on second thought, do we really even need to lock the chunk? it is a local variable still
            int sectionY = chunk.getMinSection();
            for (var sectionData : chunkData.sections()) {
                if (sectionData.isEmpty()) {
                    sectionY++;
                    continue;
                }

                var section = chunk.getSection(sectionY);
                loadSection(sectionData, section);
                sectionY++;
            }

            for (var blockEntity : chunkData.blockEntities()) {
                loadBlockEntity(blockEntity, chunk);
            }

            worldAccess.loadHeightmaps(chunk, chunkData.heightmaps());

            var userData = chunkData.userData();
            if (userData.length > 0) {
                worldAccess.loadChunkData(chunk, new NetworkBuffer(ByteBuffer.wrap(userData)));
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
                int index = y * CHUNK_SECTION_SIZE * CHUNK_SECTION_SIZE + z * CHUNK_SECTION_SIZE + x;
                return blockPalette[paletteData[index]].stateId();
            });
        }

        // Biomes
        var rawBiomePalette = sectionData.biomePalette();
        var biomePalette = new int[rawBiomePalette.length];
        for (int i = 0; i < rawBiomePalette.length; i++) {
            biomePalette[i] = biomeReadCache.computeIfAbsent(rawBiomePalette[i], name -> {
                var biomeId = BIOME_REGISTRY.getId(worldAccess.getBiome(name));
                if (biomeId == -1) {
                    logger.error("Failed to find biome: {}", name);
                    biomeId = PLAINS_BIOME_ID;
                }
                return biomeId;
            });
        }
        if (biomePalette.length == 1) {
            section.biomePalette().fill(biomePalette[0]);
        } else {
            final var paletteData = sectionData.biomeData();
            section.biomePalette().setAll((x, y, z) -> {
                int index = x / 4 + (z / 4) * 4 + (y / 4) * 16;

                var paletteIndex = paletteData[index];
                if (paletteIndex >= biomePalette.length) {
                    logger.error("Invalid biome palette index. This is probably a corrupted world, " +
                            "but it has been loaded with plains instead. No data has been written.");
                    return PLAINS_BIOME_ID;
                }

                return biomePalette[paletteIndex];
            });
        }

        // Light
        if (loadLighting && sectionData.blockLightContent() != LightContent.MISSING)
            section.setBlockLight(getLightArray(sectionData.blockLightContent(), sectionData.blockLight()));
        if (loadLighting && sectionData.skyLightContent() != LightContent.MISSING)
            section.setSkyLight(getLightArray(sectionData.skyLightContent(), sectionData.skyLight()));
    }

    private byte[] getLightArray(@NotNull LightContent content, byte @Nullable [] data) {
        return switch (content) {
            case MISSING -> null;
            case EMPTY -> LightCompute.EMPTY_CONTENT;
            case FULL -> LightCompute.CONTENT_FULLY_LIT;
            case PRESENT -> data;
        };
    }

    private void loadBlockEntity(@NotNull PolarChunk.BlockEntity blockEntity, @NotNull Chunk chunk) {
        // Fetch the block type, we can ignore Handler/NBT since we are about to replace it
        var block = chunk.getBlock(blockEntity.x(), blockEntity.y(), blockEntity.z(), Block.Getter.Condition.TYPE);

        if (blockEntity.id() != null)
            block = block.withHandler(BLOCK_MANAGER.getHandlerOrDummy(blockEntity.id()));
        if (blockEntity.data() != null)
            block = block.withNbt(blockEntity.data());
        if (worldAccess != null)
            chunk.setBlock(blockEntity.x(), blockEntity.y(), blockEntity.z(), block);
    }

    // Unloading/saving


    @Override
    public boolean supportsParallelSaving() {
        return parallel;
    }

    @Override
    public @NotNull CompletableFuture<Void> saveInstance(@NotNull Instance instance) {
        worldData.userData(NetworkBuffer.makeArray(b -> worldAccess.saveWorldData(instance, b)));
        DimensionType dimensionType = MinecraftServer.getDimensionTypeRegistry().get(instance.getDimensionType());

        byte minSection = (byte) (dimensionType.minY() / CHUNK_SECTION_SIZE);

        byte maxSection = (byte) (dimensionType.maxY() / CHUNK_SECTION_SIZE - 1);

        if (minSection == this.worldData.minSection() && this.worldData.maxSection() == maxSection)
            return saveChunks(instance.getChunks());

        worldDataLock.writeLock().lock();

        worldData = PolarWorldUtil.updateWorldHeight(worldData, minSection, maxSection);

        worldDataLock.writeLock().unlock();

        return saveChunks(instance.getChunks());
    }

    @Override
    public void unloadChunk(Chunk chunk) {
        updateChunkData(new Short2ObjectOpenHashMap<>(), chunk);
    }

    @Override
    public @NotNull CompletableFuture<Void> saveChunks(@NotNull Collection<Chunk> chunks) {
        var blockCache = new Short2ObjectOpenHashMap<String>();

        // Update state of each chunk locally
        chunks.forEach(c -> updateChunkData(blockCache, c));

        // Write the file to disk
        if (savePath != null) {
            return CompletableFuture.runAsync(() -> {
                try {
                    Files.write(savePath, PolarWriter.write(worldData),
                            StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                } catch (IOException e) {
                    EXCEPTION_HANDLER.handleException(new RuntimeException("Failed to save world", e));
                }
            }, ForkJoinPool.commonPool());
        }

        return CompletableFuture.completedFuture(null);
    }

    private void updateChunkData(@NotNull Short2ObjectMap<String> blockCache, @NotNull Chunk chunk) {
        var dimension = chunk.getInstance().getCachedDimensionType();

        var blockEntities = new ArrayList<PolarChunk.BlockEntity>();
        var sections = new PolarSection[dimension.height() / CHUNK_SECTION_SIZE];
        assert sections.length == chunk.getSections().size() : "World height mismatch";

        var heightmaps = new int[PolarChunk.MAX_HEIGHTMAPS][];

        var userData = new byte[0];

        synchronized (chunk) {
            for (int i = 0; i < sections.length; i++) {
                int sectionY = i + chunk.getMinSection();
                var section = chunk.getSection(sectionY);
                //todo check if section is empty and skip

                var blockPalette = new ArrayList<String>();
                int[] blockData = null;
                if (section.blockPalette().count() == 0) {
                    // Short circuit empty palette
                    blockPalette.add("air");
                } else {
                    var localBlockData = new int[PolarSection.BLOCK_PALETTE_SIZE];

                    section.blockPalette().getAll((x, sectionLocalY, z, blockStateId) -> {
                        final int blockIndex = x + sectionLocalY * 16 * 16 + z * 16;

                        // Section palette
                        var namespace = blockCache.computeIfAbsent((short) blockStateId, unused -> blockToString(Block.fromStateId(blockStateId)));
                        int paletteId = blockPalette.indexOf(namespace);
                        if (paletteId == -1) {
                            paletteId = blockPalette.size();
                            blockPalette.add(namespace);
                        }
                        localBlockData[blockIndex] = paletteId;
                    });

                    blockData = localBlockData;

                    // Block entities
                    for (int sectionLocalY = 0; sectionLocalY < CHUNK_SECTION_SIZE; sectionLocalY++) {
                        for (int z = 0; z < Chunk.CHUNK_SIZE_Z; z++) {
                            for (int x = 0; x < Chunk.CHUNK_SIZE_X; x++) {
                                int y = sectionLocalY + sectionY * CHUNK_SECTION_SIZE;
                                var block = chunk.getBlock(x, y, z, Block.Getter.Condition.CACHED);
                                if (block == null) continue;

                                var handlerId = block.handler() == null ? null : block.handler().getNamespaceId().asString();
                                if (handlerId != null || block.hasNbt()) {
                                    blockEntities.add(new PolarChunk.BlockEntity(
                                            x, y, z, handlerId, block.nbt()
                                    ));
                                }
                            }
                        }
                    }
                }

                var biomePalette = new ArrayList<String>();
                var biomeData = new int[PolarSection.BIOME_PALETTE_SIZE];

                section.biomePalette().getAll((x, y, z, id) -> {
                    var biomeId = biomeWriteCache.computeIfAbsent(id, worldAccess::getBiomeName);

                    var paletteId = biomePalette.indexOf(biomeId);
                    if (paletteId == -1) {
                        paletteId = biomePalette.size();
                        biomePalette.add(biomeId);
                    }

                    biomeData[x + z * 4 + y * 4 * 4] = paletteId;
                });

                byte[] blockLight = section.blockLight().array();
                byte[] skyLight = section.skyLight().array();
                
                sections[i] = new PolarSection(
                        blockPalette.toArray(new String[0]), blockData,
                        biomePalette.toArray(new String[0]), biomeData,
                        getLightContent(blockLight), blockLight,
                        getLightContent(skyLight), skyLight
                );
            }

            worldAccess.saveHeightmaps(chunk, heightmaps);

            userData = NetworkBuffer.makeArray(b -> worldAccess.saveChunkData(chunk, b));
        }

        worldDataLock.writeLock().lock();
        worldData.updateChunkAt(
                chunk.getChunkX(),
                chunk.getChunkZ(),
                new PolarChunk(
                        chunk.getChunkX(),
                        chunk.getChunkZ(),
                        sections,
                        blockEntities,
                        heightmaps,
                        userData
                )
        );
        worldDataLock.writeLock().unlock();
    }

    private @NotNull LightContent getLightContent(byte @Nullable [] data) {
        if (data == null) return LightContent.MISSING;
        if (data.length == 0 || Arrays.equals(data, LightCompute.EMPTY_CONTENT)) return LightContent.EMPTY;
        if (Arrays.equals(data, LightCompute.CONTENT_FULLY_LIT)) return LightContent.FULL;
        return LightContent.PRESENT;
    }

    @Override
    public @NotNull CompletableFuture<Void> saveChunk(@NotNull Chunk chunk) {
        return saveChunks(List.of(chunk));
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
