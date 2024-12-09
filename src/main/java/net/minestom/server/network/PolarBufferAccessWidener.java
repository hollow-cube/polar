package net.minestom.server.network;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

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
    private static final MethodHandle ADDRESS_GETTER;
    private static final MethodHandle ADDRESS_SETTER;

    public static @NotNull NetworkBuffer networkBufferView(@NotNull NetworkBuffer buffer, long start, long length) {
        // We create the buffer with -1 to indicate its a 'dummy' buffer. We do not want to cleaner attached to
        // this buffer to free the backing data. We will later update the address.
        var viewBuffer = new NetworkBufferImpl(-1, length, 0, 0, null, impl(buffer).registries);
        setAddress(impl(viewBuffer), getAddress(impl(buffer)) + start);
        viewBuffer.readOnly();
        return viewBuffer;
    }

    public static long networkBufferAddress(@NotNull NetworkBuffer buffer) {
        return getAddress(impl(buffer));
    }

    private static long getAddress(@NotNull NetworkBufferImpl impl) {
        try {
            return (long) ADDRESS_GETTER.invokeExact(impl);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    private static void setAddress(@NotNull NetworkBufferImpl impl, long address) {
        try {
            ADDRESS_SETTER.invokeExact(impl, address);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    static {
        try {
            final var lookup = MethodHandles.privateLookupIn(NetworkBufferImpl.class, MethodHandles.lookup());
            final var field = NetworkBufferImpl.class.getDeclaredField("address");
            ADDRESS_GETTER = lookup.unreflectGetter(field);
            ADDRESS_SETTER = lookup.unreflectSetter(field);
        } catch (IllegalAccessException | NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
    }
}
