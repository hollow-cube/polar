package net.hollowcube.polar;

final class PolarNbt {
    private static final int END = 0;
    private static final int BYTE = 1;
    private static final int SHORT = 2;
    private static final int INT = 3;
    private static final int LONG = 4;
    private static final int FLOAT = 5;
    private static final int DOUBLE = 6;
    private static final int BYTE_ARRAY = 7;
    private static final int STRING = 8;
    private static final int LIST = 9;
    private static final int COMPOUND = 10;
    private static final int INT_ARRAY = 11;
    private static final int LONG_ARRAY = 12;

    private PolarNbt() {}

    static void skipTag(PolarBuffer buffer) {
        int type = buffer.readByte() & 0xFF;
        if (type == END) {
            return;
        }
        skipPayload(buffer, type);
    }

    private static void skipPayload(PolarBuffer buffer, int type) {
        switch (type) {
            case BYTE -> buffer.skip(1);
            case SHORT -> buffer.skip(2);
            case INT, FLOAT -> buffer.skip(4);
            case LONG, DOUBLE -> buffer.skip(8);
            case BYTE_ARRAY -> buffer.skip(buffer.readInt());
            case STRING -> buffer.skip(buffer.readShort() & 0xFFFF);
            case LIST -> {
                int entryType = buffer.readByte() & 0xFF;
                int length = buffer.readInt();
                for (int i = 0; i < length; i++) {
                    if (entryType != END) {
                        skipPayload(buffer, entryType);
                    }
                }
            }
            case COMPOUND -> {
                while (true) {
                    int entryType = buffer.readByte() & 0xFF;
                    if (entryType == END) {
                        return;
                    }
                    buffer.skip(buffer.readShort() & 0xFFFF);
                    skipPayload(buffer, entryType);
                }
            }
            case INT_ARRAY -> buffer.skip(buffer.readInt() * 4);
            case LONG_ARRAY -> buffer.skip(buffer.readInt() * 8);
            default -> throw new PolarReader.Error("unknown nbt tag type " + type);
        }
    }
}
