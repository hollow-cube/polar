package net.hollowcube.polar.buffer;

import org.jetbrains.annotations.NotNull;
import java.util.function.Consumer;

public interface BufferFactory {
    BufferWrapper wrap(byte[] data);
    byte[] makeArray(@NotNull Consumer<@NotNull BufferWrapper> writing);
}
