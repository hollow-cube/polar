package net.hollowcube.polar;

import java.util.Map;

public interface PolarDataConverter {
    PolarDataConverter NOOP = new PolarDataConverter() {};

    default int defaultDataVersion() {
        return PolarWorld.DATA_VERSION;
    }

    default int dataVersion() {
        return PolarWorld.DATA_VERSION;
    }

    default void convertBlockPalette(String[] palette, int fromVersion, int toVersion) {}

    default Map.Entry<String, byte[]> convertBlockEntityData(
            String id, byte[] data, int fromVersion, int toVersion) {
        return Map.entry(id, data);
    }
}
