package net.hollowcube.polar;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class PolarBuffer {
    private byte[] data;
    private int readIndex;
    private int writeIndex;

    public PolarBuffer() {
        this(1024);
    }

    public PolarBuffer(int capacity) {
        this.data = new byte[Math.max(16, capacity)];
    }

    public static PolarBuffer wrap(byte[] bytes) {
        PolarBuffer buffer = new PolarBuffer(Math.max(16, bytes.length));
        System.arraycopy(bytes, 0, buffer.data, 0, bytes.length);
        buffer.writeIndex = bytes.length;
        return buffer;
    }

    public byte[] toByteArray() {
        return Arrays.copyOf(data, writeIndex);
    }

    public int readableBytes() {
        return writeIndex - readIndex;
    }

    public void skip(int count) {
        require(count);
        readIndex += count;
    }

    private void require(int count) {
        if (readIndex + count > writeIndex) {
            throw new PolarReader.Error("polar data ended early, wanted " + count
                    + " more bytes but only " + readableBytes() + " left");
        }
    }

    private void ensure(int count) {
        if (writeIndex + count <= data.length) {
            return;
        }
        int size = data.length;
        while (size < writeIndex + count) {
            size *= 2;
        }
        data = Arrays.copyOf(data, size);
    }

    public byte readByte() {
        require(1);
        return data[readIndex++];
    }

    public boolean readBoolean() {
        return readByte() != 0;
    }

    public short readShort() {
        return (short) ((readByte() & 0xFF) << 8 | (readByte() & 0xFF));
    }

    public int readInt() {
        return (readByte() & 0xFF) << 24 | (readByte() & 0xFF) << 16
                | (readByte() & 0xFF) << 8 | (readByte() & 0xFF);
    }

    public long readLong() {
        long value = 0;
        for (int i = 0; i < 8; i++) {
            value = (value << 8) | (readByte() & 0xFF);
        }
        return value;
    }

    public int readVarInt() {
        int result = 0;
        int shift = 0;
        while (true) {
            byte b = readByte();
            result |= (b & 0x7F) << shift;
            if ((b & 0x80) == 0) {
                return result;
            }
            shift += 7;
            if (shift >= 32) {
                throw new PolarReader.Error("varint too long");
            }
        }
    }

    public String readString() {
        int length = readVarInt();
        require(length);
        String value = new String(data, readIndex, length, StandardCharsets.UTF_8);
        readIndex += length;
        return value;
    }

    public String readOptionalString() {
        return readBoolean() ? readString() : null;
    }

    public byte[] readByteArray() {
        int length = readVarInt();
        return readRawBytes(length);
    }

    public byte[] readRawBytes(int length) {
        require(length);
        byte[] out = Arrays.copyOfRange(data, readIndex, readIndex + length);
        readIndex += length;
        return out;
    }

    public byte[] readRemaining() {
        return readRawBytes(readableBytes());
    }

    public long[] readLongArray() {
        int length = readVarInt();
        long[] out = new long[length];
        for (int i = 0; i < length; i++) {
            out[i] = readLong();
        }
        return out;
    }

    public List<String> readStringList(int max) {
        int length = readVarInt();
        if (length < 0 || length > max) {
            throw new PolarReader.Error("string list of " + length + " is out of range, max " + max);
        }
        List<String> out = new ArrayList<>(length);
        for (int i = 0; i < length; i++) {
            out.add(readString());
        }
        return out;
    }

    public byte[] readNbt() {
        int start = readIndex;
        PolarNbt.skipTag(this);
        return Arrays.copyOfRange(data, start, readIndex);
    }

    public byte[] readOptionalNbt() {
        return readBoolean() ? readNbt() : null;
    }

    public void writeByte(byte value) {
        ensure(1);
        data[writeIndex++] = value;
    }

    public void writeBoolean(boolean value) {
        writeByte((byte) (value ? 1 : 0));
    }

    public void writeShort(short value) {
        writeByte((byte) (value >>> 8));
        writeByte((byte) value);
    }

    public void writeInt(int value) {
        writeByte((byte) (value >>> 24));
        writeByte((byte) (value >>> 16));
        writeByte((byte) (value >>> 8));
        writeByte((byte) value);
    }

    public void writeLong(long value) {
        for (int i = 7; i >= 0; i--) {
            writeByte((byte) (value >>> (i * 8)));
        }
    }

    public void writeVarInt(int value) {
        int remaining = value;
        while ((remaining & ~0x7F) != 0) {
            writeByte((byte) ((remaining & 0x7F) | 0x80));
            remaining >>>= 7;
        }
        writeByte((byte) remaining);
    }

    public void writeString(String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        writeVarInt(bytes.length);
        writeRawBytes(bytes);
    }

    public void writeOptionalString(String value) {
        writeBoolean(value != null);
        if (value != null) {
            writeString(value);
        }
    }

    public void writeByteArray(byte[] value) {
        writeVarInt(value.length);
        writeRawBytes(value);
    }

    public void writeRawBytes(byte[] value) {
        ensure(value.length);
        System.arraycopy(value, 0, data, writeIndex, value.length);
        writeIndex += value.length;
    }

    public void writeLongArray(long[] value) {
        writeVarInt(value.length);
        for (long entry : value) {
            writeLong(entry);
        }
    }

    public void writeStringList(List<String> value) {
        writeVarInt(value.size());
        for (String entry : value) {
            writeString(entry);
        }
    }

    public void writeOptionalNbt(byte[] raw) {
        writeBoolean(raw != null);
        if (raw != null) {
            writeRawBytes(raw);
        }
    }
}
