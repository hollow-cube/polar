package net.hollowcube.polar;

import net.kyori.adventure.nbt.CompoundBinaryTag;
import net.minestom.server.MinecraftServer;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * Allows for upgrading world data from one game version to another.
 */
public interface PolarDataConverter {
    @NotNull
    PolarDataConverter NOOP = new PolarDataConverter() {
    };

    /**
     * Returns the data version to use on worlds lower than {@link PolarWorld#VERSION_DATA_CONVERTER} which
     * do not store a data version. Defaults to the current Minestom data version.
     */
    default int defaultDataVersion() {
        return MinecraftServer.DATA_VERSION;
    }

    /**
     * Returns the current data version of the world.
     */
    default int dataVersion() {
        return MinecraftServer.DATA_VERSION;
    }

    /**
     * <p>Converts the block palette from one version to another. Implementations are expected to modify
     * the palette array in place.</p>
     *
     * @param palette     An array of block namespaces, eg "minecraft:stone_stairs[facing=north]"
     * @param fromVersion The data version of the palette
     * @param toVersion   The data version to convert the palette to
     */
    default void convertBlockPalette(@NotNull String[] palette, int fromVersion, int toVersion) {

    }

    default @NotNull Map.Entry<String, CompoundBinaryTag> convertBlockEntityData(
            @NotNull String id, @NotNull CompoundBinaryTag data,
            int fromVersion, int toVersion
    ) {
        return Map.entry(id, data);
    }

}
