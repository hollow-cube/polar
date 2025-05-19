package net.hollowcube.polar.buffer;

import io.netty.buffer.Unpooled;
import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;

public class NettyBufferFactory implements BufferFactory {
    @Override
    public BufferWrapper wrap(byte[] data) {
        return new NettyBufferWrapper(Unpooled.copiedBuffer(data));
    }

    @Override
    public byte[] makeArray(@NotNull Consumer<@NotNull BufferWrapper> writing) {
        NettyBufferWrapper buffer = new NettyBufferWrapper(Unpooled.buffer());
        writing.accept(buffer);
        return buffer.readRawBytes();
    }
}
