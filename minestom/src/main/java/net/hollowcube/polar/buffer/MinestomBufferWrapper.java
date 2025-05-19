package net.hollowcube.polar.buffer;

import net.kyori.adventure.nbt.BinaryTag;
import net.minestom.server.network.NetworkBuffer;
import net.minestom.server.utils.nbt.BinaryTagReader;
import org.jetbrains.annotations.Nullable;

import java.io.DataInputStream;
import java.io.InputStream;
import java.util.Arrays;

public class MinestomBufferWrapper implements BufferWrapper {
    private final NetworkBuffer buffer;

    public MinestomBufferWrapper(NetworkBuffer buffer) {
        this.buffer = buffer;
    }

    @Override
    public void writeIndex(int index) {
        buffer.writeIndex(index);
    }

    @Override
    public int readInt() {
        return buffer.read(NetworkBuffer.INT);
    }

    @Override
    public void writeInt(int value) {
        buffer.write(NetworkBuffer.INT, value);
    }

    @Override
    public short readShort() {
        return buffer.read(NetworkBuffer.SHORT);
    }

    @Override
    public void writeShort(short value) {
        buffer.write(NetworkBuffer.SHORT, value);
    }

    @Override
    public int readVarInt() {
        return buffer.read(NetworkBuffer.VAR_INT);
    }

    @Override
    public void writeVarInt(int value) {
        buffer.write(NetworkBuffer.VAR_INT, value);
    }

    @Override
    public byte readByte() {
        return buffer.read(NetworkBuffer.BYTE);
    }

    @Override
    public void writeByte(byte value) {
        buffer.write(NetworkBuffer.BYTE, value);
    }

    @Override
    public boolean readBoolean() {
        return buffer.read(NetworkBuffer.BOOLEAN);
    }

    @Override
    public void writeBoolean(boolean value) {
        buffer.write(NetworkBuffer.BOOLEAN, value);
    }

    @Override
    public byte[] readByteArray() {
        return buffer.read(NetworkBuffer.BYTE_ARRAY);
    }

    @Override
    public void writeByteArray(byte[] value) {
        buffer.write(NetworkBuffer.BYTE_ARRAY, value);
    }

    @Override
    public byte[] readRawBytes() {
        return buffer.read(NetworkBuffer.RAW_BYTES);
    }

    @Override
    public void writeRawBytes(byte[] value) {
        buffer.write(NetworkBuffer.RAW_BYTES, value);
    }

    @Override
    public String[] readStringArray(int maxSize) {
        return buffer.read(NetworkBuffer.STRING.list(maxSize)).toArray(String[]::new);
    }

    @Override
    public void writeStringArray(String[] value) {
        buffer.write(NetworkBuffer.STRING.list(), Arrays.asList(value));
    }

    @Override
    public long[] readLongArray() {
        return buffer.read(NetworkBuffer.LONG_ARRAY);
    }

    @Override
    public void writeLongArray(long[] value) {
        buffer.write(NetworkBuffer.LONG_ARRAY, value);
    }

    @Override
    public void advanceRead(int length) {
        buffer.advanceRead(length);
    }

    @Override
    public @Nullable String readOptString() {
        return buffer.read(NetworkBuffer.STRING.optional());
    }

    @Override
    public void writeOptString(@Nullable String value) {
        buffer.write(NetworkBuffer.STRING.optional(), value);
    }

    @Override
    public BinaryTag readNbt() {
        return buffer.read(NetworkBuffer.NBT);
    }

    /**
     * Minecraft (so Minestom) had a breaking change in NBT reading in 1.20.2. This method replicates the old
     * behavior which we use for any Polar version less than VERSION_MINESTOM_NBT_READ_BREAK.
     *
     * @see NetworkBuffer#NBT
     */
    @Override
    public BinaryTag readLegacyNbt() {
        try {
            var nbtReader = new BinaryTagReader(new DataInputStream(new InputStream() {
                public int read() {
                    return buffer.read(NetworkBuffer.BYTE) & 255;
                }

                public int available() {
                    return (int) buffer.readableBytes();
                }
            }));
            return nbtReader.readNamed().getValue();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }

    @Override
    public void writeOptNbt(@Nullable BinaryTag value) {
        buffer.write(NetworkBuffer.NBT.optional(), value);
    }

    public static final NetworkBuffer.Type<byte[]> LIGHT_DATA = NetworkBuffer.FixedRawBytes(2048);

    @Override
    public byte[] readLightData() {
        return buffer.read(LIGHT_DATA);
    }
}
