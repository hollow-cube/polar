package net.hollowcube.polar;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minestom.server.instance.Chunk;
import net.minestom.server.instance.DynamicChunk;
import net.minestom.server.instance.InstanceContainer;
import net.minestom.server.instance.block.Block;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;

final class UnsafeOps {
    private static final MethodHandle CACHE_CHUNK_HANDLE;
    private static final MethodHandle NEEDS_HEIGHTMAP_REFRESH_SETTER;
    private static final MethodHandle DYNAMIC_CHUNK_ENTRIES_GETTER;
    private static final MethodHandle DYNAMIC_CHUNK_TICKABLE_MAP_GETTER;

    static void unsafeCacheChunk(@NotNull InstanceContainer instance, @NotNull Chunk chunk) {
        try {
            CACHE_CHUNK_HANDLE.invokeExact(instance, chunk);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    static void unsafeSetNeedsCompleteHeightmapRefresh(@NotNull Chunk chunk, boolean value) {
        if (chunk instanceof DynamicChunk dynamicChunk) {
            try {
                NEEDS_HEIGHTMAP_REFRESH_SETTER.invokeExact(dynamicChunk, value);
            } catch (Throwable t) {
                throw new RuntimeException(t);
            }
        }
    }

    static @Nullable Int2ObjectOpenHashMap<Block> unsafeGetEntries(@NotNull Chunk chunk) {
        if (chunk instanceof DynamicChunk dynamicChunk) {
            try {
                return (Int2ObjectOpenHashMap<Block>) DYNAMIC_CHUNK_ENTRIES_GETTER.invokeExact(dynamicChunk);
            } catch (Throwable t) {
                throw new RuntimeException(t);
            }
        } else return null;
    }

    static @Nullable Int2ObjectOpenHashMap<Block> unsafeGetTickableMap(@NotNull Chunk chunk) {
        if (chunk instanceof DynamicChunk dynamicChunk) {
            try {
                return (Int2ObjectOpenHashMap<Block>) DYNAMIC_CHUNK_TICKABLE_MAP_GETTER.invokeExact(dynamicChunk);
            } catch (Throwable t) {
                throw new RuntimeException(t);
            }
        } else return null;
    }

    static {
        try {
            var lookup = MethodHandles.privateLookupIn(InstanceContainer.class, MethodHandles.lookup());
            var method = InstanceContainer.class.getDeclaredMethod("cacheChunk", Chunk.class);
            CACHE_CHUNK_HANDLE = lookup.unreflect(method);
        } catch (IllegalAccessException | NoSuchMethodException e) {
            throw new RuntimeException(e);
        }

        try {
            var lookup = MethodHandles.privateLookupIn(DynamicChunk.class, MethodHandles.lookup());
            NEEDS_HEIGHTMAP_REFRESH_SETTER = lookup.unreflectSetter(DynamicChunk.class
                    .getDeclaredField("needsCompleteHeightmapRefresh"));
            DYNAMIC_CHUNK_ENTRIES_GETTER = lookup.unreflectGetter(DynamicChunk.class
                    .getDeclaredField("entries"));
            DYNAMIC_CHUNK_TICKABLE_MAP_GETTER = lookup.unreflectGetter(DynamicChunk.class
                    .getDeclaredField("tickableMap"));
        } catch (IllegalAccessException | NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
    }
}
