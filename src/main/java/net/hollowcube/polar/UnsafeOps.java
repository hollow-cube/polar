package net.hollowcube.polar;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minestom.server.instance.Chunk;
import net.minestom.server.instance.DynamicChunk;
import net.minestom.server.instance.InstanceContainer;
import net.minestom.server.instance.block.Block;
import net.minestom.server.instance.light.Light;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.util.concurrent.atomic.AtomicBoolean;

final class UnsafeOps {
    private static final MethodHandle CACHE_CHUNK_HANDLE;
    private static final MethodHandle NEEDS_HEIGHTMAP_REFRESH_SETTER;
    private static final MethodHandle DYNAMIC_CHUNK_ENTRIES_GETTER;
    private static final MethodHandle DYNAMIC_CHUNK_TICKABLE_MAP_GETTER;

    private static final MethodHandle BLOCK_LIGHT_CONTENT_SETTER;
    private static final MethodHandle BLOCK_LIGHT_CONTENT_PROPAGATION_SETTER;
    private static final MethodHandle BLOCK_LIGHT_IS_VALID_BORDERS_SETTER;
    private static final MethodHandle BLOCK_LIGHT_NEEDS_SEND_GETTER;
    private static final MethodHandle SKY_LIGHT_CONTENT_SETTER;
    private static final MethodHandle SKY_LIGHT_CONTENT_PROPAGATION_SETTER;
    private static final MethodHandle SKY_LIGHT_IS_VALID_BORDERS_SETTER;
    private static final MethodHandle SKY_LIGHT_NEEDS_SEND_GETTER;

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

    static void unsafeUpdateBlockLightArray(@NotNull Light light, byte[] content) {
        try {
            BLOCK_LIGHT_CONTENT_SETTER.invoke(light, content);
            BLOCK_LIGHT_CONTENT_PROPAGATION_SETTER.invoke(light, content);
            BLOCK_LIGHT_IS_VALID_BORDERS_SETTER.invoke(light, true);
            var needsSend = (AtomicBoolean) BLOCK_LIGHT_NEEDS_SEND_GETTER.invoke(light);
            needsSend.set(true);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    static void unsafeUpdateSkyLightArray(@NotNull Light light, byte[] content) {
        try {
            SKY_LIGHT_CONTENT_SETTER.invoke(light, content);
            SKY_LIGHT_CONTENT_PROPAGATION_SETTER.invoke(light, content);
            SKY_LIGHT_IS_VALID_BORDERS_SETTER.invoke(light, true);
            var needsSend = (AtomicBoolean) SKY_LIGHT_NEEDS_SEND_GETTER.invoke(light);
            needsSend.set(true);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
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

        try {
            var blockLight = Class.forName("net.minestom.server.instance.light.BlockLight");
            var lookup = MethodHandles.privateLookupIn(blockLight, MethodHandles.lookup());
            BLOCK_LIGHT_CONTENT_SETTER = lookup.unreflectSetter(blockLight.getDeclaredField("content"));
            BLOCK_LIGHT_CONTENT_PROPAGATION_SETTER = lookup.unreflectSetter(blockLight.getDeclaredField("contentPropagation"));
            BLOCK_LIGHT_IS_VALID_BORDERS_SETTER = lookup.unreflectSetter(blockLight.getDeclaredField("isValidBorders"));
            BLOCK_LIGHT_NEEDS_SEND_GETTER = lookup.unreflectGetter(blockLight.getDeclaredField("needsSend"));
        } catch (NoSuchFieldException | ClassNotFoundException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
        try {
            var skyLight = Class.forName("net.minestom.server.instance.light.SkyLight");
            var lookup = MethodHandles.privateLookupIn(skyLight, MethodHandles.lookup());
            SKY_LIGHT_CONTENT_SETTER = lookup.unreflectSetter(skyLight.getDeclaredField("content"));
            SKY_LIGHT_CONTENT_PROPAGATION_SETTER = lookup.unreflectSetter(skyLight.getDeclaredField("contentPropagation"));
            SKY_LIGHT_IS_VALID_BORDERS_SETTER = lookup.unreflectSetter(skyLight.getDeclaredField("isValidBorders"));
            SKY_LIGHT_NEEDS_SEND_GETTER = lookup.unreflectGetter(skyLight.getDeclaredField("needsSend"));
        } catch (NoSuchFieldException | ClassNotFoundException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }
}
