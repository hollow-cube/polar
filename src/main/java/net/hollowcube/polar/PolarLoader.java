package net.hollowcube.polar;

import net.minestom.server.MinecraftServer;
import net.minestom.server.command.builder.arguments.minecraft.ArgumentBlockState;
import net.minestom.server.command.builder.exception.ArgumentSyntaxException;
import net.minestom.server.instance.Chunk;
import net.minestom.server.instance.IChunkLoader;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.Section;
import net.minestom.server.instance.block.Block;
import net.minestom.server.instance.block.BlockManager;
import net.minestom.server.utils.NamespaceID;
import net.minestom.server.world.biomes.Biome;
import net.minestom.server.world.biomes.BiomeManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;

@SuppressWarnings("UnstableApiUsage")
public class PolarLoader implements IChunkLoader {
    private static final BlockManager BLOCK_MANAGER = MinecraftServer.getBlockManager();
    private static final BiomeManager BIOME_MANAGER = MinecraftServer.getBiomeManager();
    private static final Logger logger = LoggerFactory.getLogger(PolarLoader.class);

    private final PolarWorld worldData;

    public PolarLoader(@NotNull Path path) throws IOException {
        this(Files.newInputStream(path));
    }

    public PolarLoader(@NotNull InputStream inputStream) throws IOException {
        try (inputStream) {
            this.worldData = PolarReader.read(inputStream.readAllBytes());
        }
    }

    public PolarLoader(@NotNull PolarWorld world) {
        this.worldData = world;
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
            //todo replace with java locks, not synchronized
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
    public void unloadChunk(Chunk chunk) {
        //todo write the chunk data to internal repr
        throw new UnsupportedOperationException("Polar does not support unloading chunks");
    }

    @Override
    public @NotNull CompletableFuture<Void> saveChunks(@NotNull Collection<Chunk> chunks) {
        //todo write each chunk to memory then save them all
        throw new UnsupportedOperationException("Polar does not support unloading chunks");
    }

    @Override
    public @NotNull CompletableFuture<Void> saveInstance(@NotNull Instance instance) {
        //todo also update instance metadata
        return saveChunks(instance.getChunks());
    }

    @Override
    public @NotNull CompletableFuture<Void> saveChunk(@NotNull Chunk chunk) {
        throw new UnsupportedOperationException("Polar does not support saving individual chunks, see #saveChunks(Collection<Chunk>) or #saveInstance(Instance)");
    }
}
