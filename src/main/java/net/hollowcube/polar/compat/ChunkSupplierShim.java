package net.hollowcube.polar.compat;

import net.minestom.server.instance.Chunk;
import net.minestom.server.instance.DynamicChunk;
import net.minestom.server.instance.Instance;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;

/**
 * A shim for {@link net.minestom.server.utils.chunk.ChunkSupplier} to allow for
 * compatibility with main Minestom which does not have the lighting PR (which
 * adds {@link net.minestom.server.utils.chunk.ChunkSupplier}.
 */
@ApiStatus.Internal
@FunctionalInterface
public interface ChunkSupplierShim {

    static @NotNull ChunkSupplierShim select() {
        try {
            // If this function is present we have the lighting branch and should use that chunk supplier
            Instance.class.getDeclaredMethod("getChunkSupplier");
            return (instance, cx, cz) -> instance.getChunkSupplier().createChunk(instance, cx, cz);
        } catch (NoSuchMethodException e) {
            // Otherwise we should use the default chunk supplier
            return DynamicChunk::new;
        }
    }

    @NotNull Chunk createChunk(@NotNull Instance instance, int chunkX, int chunkZ);
}
