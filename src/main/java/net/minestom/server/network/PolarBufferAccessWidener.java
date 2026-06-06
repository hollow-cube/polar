package net.minestom.server.network;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;

import static net.minestom.server.network.NetworkBufferImpl.impl;

/**
 * Provides some low level access to the internals of {@link NetworkBuffer}.
 *
 * <p><b>Do not use this class.</b></p>
 */
@ApiStatus.Internal
public final class PolarBufferAccessWidener {
    private static final MethodHandle SEGMENT_GETTER;

    static {
        try {
            final var lookup = MethodHandles.privateLookupIn(NetworkBufferImpl.class, MethodHandles.lookup());
            final var field = NetworkBufferImpl.class.getDeclaredField("segment");
            SEGMENT_GETTER = lookup.unreflectGetter(field);
        } catch (IllegalAccessException | NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
    }

    public static @NotNull NetworkBuffer networkBufferView(@NotNull NetworkBuffer buffer, long start, long len) {
        final MemorySegment slice = getSegment(impl(buffer)).asSlice(start, len);
        final NetworkBufferImpl view = new NetworkBufferImpl(slice, 0, len, null, impl(buffer).registries);
        view.readOnly();
        return view;
    }

    public static long networkBufferAddress(@NotNull NetworkBuffer buffer) {
        return getSegment(impl(buffer)).address();
    }

    private static MemorySegment getSegment(@NotNull NetworkBufferImpl impl) {
        try {
            return (MemorySegment) SEGMENT_GETTER.invokeExact(impl);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }
}
