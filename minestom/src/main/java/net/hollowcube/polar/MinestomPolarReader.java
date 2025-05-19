package net.hollowcube.polar;

import net.hollowcube.polar.buffer.MinestomBufferFactory;
import org.jetbrains.annotations.NotNull;

public class MinestomPolarReader {
    public static @NotNull PolarWorld read(byte @NotNull [] data) {
        return read(data, PolarDataConverter.NOOP);
    }

    public static @NotNull PolarWorld read(byte @NotNull [] data, @NotNull PolarDataConverter dataConverter) {
        return PolarReader.read(data, MinestomBufferFactory.INSTANCE, dataConverter);
    }
}
