package net.hollowcube.polar;

import net.minestom.server.instance.Chunk;
import net.minestom.server.network.NetworkBuffer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Provides access to user world data for a {@link PolarLoader} to get and set user
 * specific world data such as objects, as well as provides some relevant callbacks.
 * <br/><br/>
 * Usage if world access is completely optional, dependent features will not add
 * overhead to the format if unused.
 */
@SuppressWarnings("UnstableApiUsage")
public interface PolarWorldAccess {

    /**
     * Called when a chunk is created, just before it is added to the world.
     * <br/><br/>
     * Can be used to initialize the chunk based on saved user data in the world.
     *
     * @param chunk The Minestom chunk being created
     * @param userData The saved user data, or null if none is present
     */
    default void loadChunkData(@NotNull Chunk chunk, @Nullable NetworkBuffer userData) {}

    /**
     * Called when a chunk is being saved.
     * <br/><br/>
     * Can be used to save user data in the chunk by writing it to the buffer.
     *
     * @param chunk The Minestom chunk being saved
     * @param userData A buffer to write user data to save
     */
    default void saveChunkData(@NotNull Chunk chunk, @NotNull NetworkBuffer userData) {}

}
