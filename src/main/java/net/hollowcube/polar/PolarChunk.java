package net.hollowcube.polar;


import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jglrxavpok.hephaistos.nbt.NBTCompound;

import java.util.List;

/**
 * A Java type representing the latest version of the chunk format.
 */
public class PolarChunk {

    public static final int HEIGHTMAP_NONE = 0x0;
    public static final int HEIGHTMAP_MOTION_BLOCKING = 0x1;
    public static final int HEIGHTMAP_MOTION_BLOCKING_NO_LEAVES = 0x2;
    public static final int HEIGHTMAP_OCEAN_FLOOR = 0x4;
    public static final int HEIGHTMAP_OCEAN_FLOOR_WG = 0x8;
    public static final int HEIGHTMAP_WORLD_SURFACE = 0x10;
    public static final int HEIGHTMAP_WORLD_SURFACE_WG = 0x20;
    static final int[] HEIGHTMAPS = new int[]{
            HEIGHTMAP_NONE,
            HEIGHTMAP_MOTION_BLOCKING,
            HEIGHTMAP_MOTION_BLOCKING_NO_LEAVES,
            HEIGHTMAP_OCEAN_FLOOR,
            HEIGHTMAP_OCEAN_FLOOR_WG,
            HEIGHTMAP_WORLD_SURFACE,
            HEIGHTMAP_WORLD_SURFACE_WG,
    };

    private final int x;
    private final int z;

    private final PolarSection[] sections;
    private final List<BlockEntity> blockEntities;

    private final byte[][] heightmaps;

    //todo entities


    public PolarChunk(int x, int z, PolarSection[] sections, List<BlockEntity> blockEntities, byte[][] heightmaps) {
        this.x = x;
        this.z = z;
        this.sections = sections;
        this.blockEntities = blockEntities;
        this.heightmaps = heightmaps;
    }

    public int x() {
        return x;
    }

    public int z() {
        return z;
    }

    public @NotNull List<PolarSection> sections() {
        return List.of(sections);
    }

    public @NotNull List<BlockEntity> blockEntities() {
        return blockEntities;
    }

    public byte @Nullable [] heightmap(int type) {
        return heightmaps[type];
    }

    public record BlockEntity(
            int x,
            int y,
            int z,
            @NotNull String id,
            @NotNull NBTCompound data
    ) {

    }

}
