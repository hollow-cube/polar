package net.hollowcube.polar.buffer;

import net.kyori.adventure.nbt.BinaryTag;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public interface BufferWrapper {
    void writeIndex(int index);
    void advanceRead(int length);

    boolean readBoolean();
    void writeBoolean(boolean value);
    byte readByte();
    void writeByte(byte value);
    short readShort();
    void writeShort(short value);
    int readInt();
    void writeInt(int value);
    int readVarInt();
    void writeVarInt(int value);
    byte[] readByteArray();
    void writeByteArray(byte[] value);
    byte[] readRawBytes();
    void writeRawBytes(byte[] value);
    String[] readStringArray(int maxSize);
    void writeStringArray(String[] value);
    long[] readLongArray();
    void writeLongArray(long[] value);
    @Nullable String readOptString();
    void writeOptString(@Nullable String value);
    BinaryTag readNbt();
    BinaryTag readLegacyNbt();
    void writeOptNbt(@Nullable BinaryTag value);
    byte[] readLightData();
}
