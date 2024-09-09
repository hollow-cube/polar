package net.hollowcube.polar;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;

public class PolarWorldUtil {
    public static @NotNull PolarWorld updateWorldHeight(@NotNull PolarWorld world, byte minSection, byte maxSection) {
        assert minSection <= maxSection : "minSection cannot be less than maxSection";

        ArrayList<PolarChunk> chunks = new ArrayList<>(world.chunks().size());

        for (PolarChunk chunk : world.chunks()) {
            PolarSection[] sections = new PolarSection[maxSection - minSection + 1];

            for (int i = minSection; i <= maxSection; i++) {
                sections[i] = i - minSection < chunk.sections().length ? chunk.sections()[i - minSection] : new PolarSection();
            }

            chunks.add(
                    new PolarChunk(
                            chunk.x(),
                            chunk.z(),
                            sections,
                            chunk.blockEntities(),
                            chunk.heightmaps(),
                            chunk.userData()
                    )
            );
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
}
