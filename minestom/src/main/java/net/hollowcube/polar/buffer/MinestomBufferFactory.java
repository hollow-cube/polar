package net.hollowcube.polar.buffer;

import net.minestom.server.network.NetworkBuffer;
import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;

public class MinestomBufferFactory implements BufferFactory {
    public static final MinestomBufferFactory INSTANCE = new MinestomBufferFactory();

    private MinestomBufferFactory() {
    }

    @Override
    public BufferWrapper wrap(byte[] data) {
        NetworkBuffer buffer = NetworkBuffer.wrap(data, 0, 0);
        return new MinestomBufferWrapper(buffer);
    }

    @Override
    public byte[] makeArray(@NotNull Consumer<@NotNull BufferWrapper> writing) {
        return NetworkBuffer.makeArray((buffer) -> {
            writing.accept(new MinestomBufferWrapper(buffer));
        });
    }
}
