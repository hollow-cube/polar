package net.hollowcube.polar;


import net.minestom.server.instance.Chunk;
import org.jetbrains.annotations.Nullable;
import org.jglrxavpok.hephaistos.nbt.NBTCompound;

import java.util.List;

/**
 * A Java type representing the latest version of the chunk format.
 */
public record PolarChunk(
        int x,
        int z,
        PolarSection[] sections,
        List<BlockEntity> blockEntities,
        int[][] heightmaps,
        byte[] userData
) {

    public static final int HEIGHTMAP_NONE = 0b0;
    public static final int HEIGHTMAP_MOTION_BLOCKING = 0b1;
    public static final int HEIGHTMAP_MOTION_BLOCKING_NO_LEAVES = 0b10;
    public static final int HEIGHTMAP_OCEAN_FLOOR = 0b100;
    public static final int HEIGHTMAP_OCEAN_FLOOR_WG = 0b1000;
    public static final int HEIGHTMAP_WORLD_SURFACE = 0b10000;
    public static final int HEIGHTMAP_WORLD_SURFACE_WG = 0b100000;
    static final int[] HEIGHTMAPS = new int[]{
            HEIGHTMAP_NONE,
            HEIGHTMAP_MOTION_BLOCKING,
            HEIGHTMAP_MOTION_BLOCKING_NO_LEAVES,
            HEIGHTMAP_OCEAN_FLOOR,
            HEIGHTMAP_OCEAN_FLOOR_WG,
            HEIGHTMAP_WORLD_SURFACE,
            HEIGHTMAP_WORLD_SURFACE_WG,
    };
    static final int HEIGHTMAP_SIZE = Chunk.CHUNK_SIZE_X * Chunk.CHUNK_SIZE_Z;
    static final int MAX_HEIGHTMAPS = 32;

    public int @Nullable [] heightmap(int type) {
        return heightmaps[type];
    }

    public record BlockEntity(
            int x,
            int y,
            int z,
            @Nullable String id,
            @Nullable NBTCompound data
    ) {

    }

}
