package net.hollowcube.polar.buffer;

import io.netty.buffer.ByteBuf;
import net.kyori.adventure.nbt.BinaryTag;
import org.jetbrains.annotations.Nullable;

public class NettyBufferWrapper implements BufferWrapper {
    private final ByteBuf buffer;

    public NettyBufferWrapper(ByteBuf buffer) {
        this.buffer = buffer;
    }

    @Override
    public void writeIndex(int index) {
        buffer.writerIndex(index);
    }

    @Override
    public void advanceRead(int length) {
        buffer.readBytes(length);
    }

    @Override
    public boolean readBoolean() {
        return buffer.readBoolean();
    }

    @Override
    public void writeBoolean(boolean value) {
        buffer.writeBoolean(value);
    }

    @Override
    public byte readByte() {
        return buffer.readByte();
    }

    @Override
    public void writeByte(byte value) {
        buffer.writeByte(value);
    }

    @Override
    public short readShort() {
        return buffer.readShort();
    }

    @Override
    public void writeShort(short value) {
        buffer.writeShort(value);
    }

    @Override
    public int readInt() {
        return buffer.readInt();
    }

    @Override
    public void writeInt(int value) {
        buffer.writeInt(value);
    }

    @Override
    public int readVarInt() {
        return ByteBufUtils.readVarInt(buffer);
    }

    @Override
    public void writeVarInt(int value) {
        ByteBufUtils.writeVarInt(buffer, value);
    }

    @Override
    public byte[] readByteArray() {
        int len = readVarInt();
        var byteArray = new byte[len];
        buffer.readBytes(byteArray);
        return byteArray;
    }

    @Override
    public void writeByteArray(byte[] value) {
        writeVarInt(value.length);
        buffer.writeBytes(value);
    }

    @Override
    public byte[] readRawBytes() {
        int len = buffer.readableBytes();
        var byteArray = new byte[len];
        buffer.readBytes(byteArray);
        return byteArray;
    }

    @Override
    public void writeRawBytes(byte[] value) {
        buffer.writeBytes(value);
    }

    @Override
    public String[] readStringArray(int maxSize) {
        int len = readVarInt();
        var array = new String[len];
        for (int i = 0; i < len; i++) {
            array[i] = ByteBufUtils.readString(buffer, maxSize);
        }
        return array;
    }

    @Override
    public void writeStringArray(String[] value) {
        writeVarInt(value.length);
        for (String s : value) {
            ByteBufUtils.writeString(buffer, s);
        }
    }

    @Override
    public long[] readLongArray() {
        int len = readVarInt();
        var array = new long[len];
        for (int i = 0; i < len; i++) {
            array[i] = buffer.readLong();
        }
        return array;
    }

    @Override
    public void writeLongArray(long[] value) {
        writeVarInt(value.length);
        for (long l : value) {
            buffer.writeLong(l);
        }
    }

    @Override
    public @Nullable String readOptString() {
        boolean hasValue = readBoolean();
        if (!hasValue) return null;
        return ByteBufUtils.readString(buffer, Short.MAX_VALUE);
    }

    @Override
    public void writeOptString(@Nullable String value) {
        boolean hasValue = value != null;
        writeBoolean(hasValue);
        if (hasValue) {
            ByteBufUtils.writeString(buffer, value);
        }
    }

    @Override
    public BinaryTag readNbt() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public BinaryTag readLegacyNbt() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public void writeOptNbt(@Nullable BinaryTag value) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public byte[] readLightData() {
        byte[] bytes = new byte[2048];
        buffer.readBytes(bytes);
        return bytes;
    }
}
