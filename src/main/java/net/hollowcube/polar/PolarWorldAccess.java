package net.hollowcube.polar;

import net.minestom.server.MinecraftServer;
import net.minestom.server.instance.Chunk;
import net.minestom.server.instance.Instance;
import net.minestom.server.network.NetworkBuffer;
import net.minestom.server.registry.DynamicRegistry;
import net.minestom.server.world.biome.Biome;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * Provides access to user world data for a {@link PolarLoader} to get and set user
 * specific world data such as objects, as well as provides some relevant callbacks.
 * <br/><br/>
 * Usage if world access is completely optional, dependent features will not add
 * overhead to the format if unused.
 */
@SuppressWarnings("UnstableApiUsage")
public interface PolarWorldAccess {
    PolarWorldAccess DEFAULT = new PolarWorldAccess() {
    };

    /**
     * Called when an instance is created from this chunk loader.
     * <br/><br/>
     * Can be used to initialize the world based on saved user data in the world.
     *
     * @param instance The Minestom instance being created
     * @param userData The saved user data, or null if none is present.
     */
    default void loadWorldData(@NotNull Instance instance, @Nullable NetworkBuffer userData) {
    }

    /**
     * Called when an instance is being saved.
     * <br/><br/>
     * Can be used to save user data in the world by writing it to the buffer.
     *
     * @param instance The Minestom instance being saved
     * @param userData A buffer to write user data to save
     */
    default void saveWorldData(@NotNull Instance instance, @NotNull NetworkBuffer userData) {
    }

    /**
     * Called when a chunk is created, just before it is added to the world.
     * <br/><br/>
     * Can be used to initialize the chunk based on saved user data in the world.
     *
     * @param chunk    The Minestom chunk being created
     * @param userData The saved user data, or null if none is present
     */
    default void loadChunkData(@NotNull Chunk chunk, @Nullable NetworkBuffer userData) {
    }

    /**
     * Called when a chunk is being saved.
     * <br/><br/>
     * Can be used to save user data in the chunk by writing it to the buffer.
     *
     * @param chunk    The Minestom chunk being saved
     * @param userData A buffer to write user data to save
     */
    default void saveChunkData(@NotNull Chunk chunk, @NotNull NetworkBuffer userData) {
    }

    @ApiStatus.Experimental
    default void loadHeightmaps(@NotNull Chunk chunk, int[][] heightmaps) {
    }

    @ApiStatus.Experimental
    default void saveHeightmaps(@NotNull Chunk chunk, int[][] heightmaps) {
    }

    /**
     * Called when a chunk is being loaded by a {@link PolarLoader} to convert biome ids back to instances.
     * <br/><br/>
     * It is valid to change the behavior as long as a biome is returned in all cases (i.e. have a default).
     * <br/><br/>
     * Biomes are cached by the loader per loader instance, so there will only be a single call per loader, even over many chunks.
     *
     * @param name The namespace ID of the biome, eg minecraft:plains
     * @return The biome instance
     */
    default @NotNull DynamicRegistry.Key<Biome> getBiome(@NotNull String name) {
        var biomeRegistry = MinecraftServer.getBiomeRegistry();
        var key = DynamicRegistry.Key.<Biome>of(name);
        var biome = biomeRegistry.get(key);
        if (biome == null) {
            PolarLoader.logger.error("Failed to find biome: {}", name);
            return Biome.PLAINS;
        }
        return key;
    }

    default @NotNull String getBiomeName(int id) {
        var biomeRegistry = MinecraftServer.getBiomeRegistry();
        var biome = biomeRegistry.getKey(id);
        if (biome == null) {
            PolarLoader.logger.error("Failed to find biome: {}", id);
            return Objects.requireNonNull(biomeRegistry.get(Biome.PLAINS)).name();
        }
        return biome.name();
    }

}
