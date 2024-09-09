package net.hollowcube.polar;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.stream.Collectors;

public class WorldHeightUtil {
    public static @NotNull PolarWorld updateWorldHeight(@NotNull PolarWorld world, byte minSection, byte maxSection) {
        assert minSection <= maxSection : "minSection cannot be less than maxSection";

        ArrayList<PolarChunk> chunks = new ArrayList<>(world.chunks().size());

        for (PolarChunk chunk : world.chunks()) {
            chunks.add(updateChunkHeight(chunk, minSection, maxSection));
        }

        return new PolarWorld(
                world.version(),
                world.dataVersion(),
                world.compression(),
                minSection,
                maxSection,
                world.userData(),
                chunks
        );
    }

    public static @NotNull PolarChunk updateChunkHeight(@NotNull PolarChunk chunk, byte minSection, byte maxSection) {
        PolarSection[] sections = new PolarSection[maxSection - minSection + 1];

        for (int i = minSection; i <= maxSection; i++) {
            sections[i] = i - minSection < chunk.sections().length ? chunk.sections()[i - minSection] : new PolarSection();
        }

        return new PolarChunk(
                chunk.x(),
                chunk.z(),
                sections,
                chunk.blockEntities(),
                chunk.heightmaps(),
                chunk.userData()
        );
    }
}
