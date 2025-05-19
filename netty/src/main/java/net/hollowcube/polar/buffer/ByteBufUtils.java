package net.hollowcube.polar.buffer;

import io.netty.buffer.ByteBuf;

import java.nio.charset.StandardCharsets;

public class ByteBufUtils {

    private static final int SEGMENT_BITS = 0x7F;
    private static final int CONTINUE_BIT = 0x80;

    public static int readVarInt(ByteBuf buffer) {
        int value = 0;
        int position = 0;
        int currentByte;

        while (buffer.isReadable()) {
            currentByte = buffer.readByte() & 0xFF;
            value |= (currentByte & SEGMENT_BITS) << position;

            if ((currentByte & CONTINUE_BIT) == 0) {
                break;
            }

            position += 7;
            if (position >= 32) {
                throw new RuntimeException("VarInt is too big");
            }
        }
        return value;
    }

    public static void writeVarInt(ByteBuf buffer, int value) {
        while (true) {
            if ((value & ~SEGMENT_BITS) == 0) {
                buffer.writeByte(value);
                return;
            }
            buffer.writeByte((value & SEGMENT_BITS) | CONTINUE_BIT);
            value >>>= 7;
        }
    }

    public static String readString(ByteBuf buffer, int maxLength) {
        final int maxSize = maxLength * 3;
        final int size = readVarInt(buffer);

        if (size > maxSize) {
            throw new IllegalStateException("The received string was longer than the allowed " + maxSize + " (" + size + " > " + maxSize + ")");
        }
        if (size < 0) {
            throw new IllegalStateException("The received string's length was smaller than 0");
        }

        byte[] bytes = new byte[size];
        buffer.readBytes(bytes);
        String str = new String(bytes, StandardCharsets.UTF_8);

        if (str.length() > maxLength) {
            throw new IllegalStateException("The received string was longer than the allowed (" + str.length() + " > " + maxLength + ")");
        }

        return str;
    }

    public static void writeString(ByteBuf buffer, String text) {
        byte[] utf8Bytes = text.getBytes(StandardCharsets.UTF_8);
        writeVarInt(buffer, utf8Bytes.length);
        buffer.writeBytes(utf8Bytes);
    }
}
