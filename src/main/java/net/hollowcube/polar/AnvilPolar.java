package net.hollowcube.polar;

import net.minestom.server.instance.Chunk;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.InstanceContainer;
import net.minestom.server.instance.anvil.AnvilLoader;
import net.minestom.server.world.DimensionType;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class AnvilPolar {

    /**
     * Convert the anvil world at the given path to a Polar world. The world height range (in sections) is assumed
     * to be from -4 to 19 (inclusive), which is the default for recent Minecraft versions.
     * <br />
     * All chunks from all regions in the anvil world will be included in the Polar world.
     *
     * @param path Path to the anvil world (the directory containing the region directory)
     * @return The Polar world representing the given Anvil world
     * @throws IOException If there was an error reading the anvil world
     */
    public static @NotNull PolarWorld anvilToPolar(@NotNull Path path) throws IOException {
        return anvilToPolar(path, -4, 19, ChunkSelector.all());
    }

    /**
     * Convert the anvil world at the given path to a Polar world. The world height range (in sections) is assumed
     * to be from -4 to 19 (inclusive), which is the default for recent Minecraft versions.
     * <br />
     * Only the selected chunks will be included in the resulting Polar world.
     *
     * @param path     Path to the anvil world (the directory containing the region directory)
     * @param selector Chunk selector to use to determine which chunks to include in the Polar world
     * @return The Polar world representing the given Anvil world
     * @throws IOException If there was an error reading the anvil world
     */
    public static @NotNull PolarWorld anvilToPolar(@NotNull Path path, @NotNull ChunkSelector selector) throws IOException {
        return anvilToPolar(path, -4, 19, selector);
    }

    /**
     * Convert the anvil world at the given path to a Polar world. The provided world height range
     * will be used to determine which sections will be included in the Polar world. If a section is missing,
     * an empty polar section will be included in its place.
     * <br />
     * All chunks from all regions in the anvil world will be included in the Polar world.
     *
     * @param path       Path to the anvil world (the directory containing the region directory)
     * @param minSection The minimum section to include in the Polar world
     * @param maxSection The maximum section to include in the Polar world
     * @return The Polar world representing the given Anvil world
     * @throws IOException If there was an error reading the anvil world
     */
    public static @NotNull PolarWorld anvilToPolar(@NotNull Path path, int minSection, int maxSection) throws IOException {
        return anvilToPolar(path, minSection, maxSection, ChunkSelector.all());
    }

    /**
     * Convert the anvil world at the given path to a Polar world. The provided world height range
     * will be used to determine which sections will be included in the Polar world. If a section is missing,
     * an empty polar section will be included in its place.
     * <br />
     * Only the selected chunks will be included in the resulting Polar world.
     *
     * @param path       Path to the anvil world (the directory containing the region directory)
     * @param minSection The minimum section to include in the Polar world
     * @param maxSection The maximum section to include in the Polar world
     * @param selector   Chunk selector to use to determine which chunks to include in the Polar world
     * @return The Polar world representing the given Anvil world
     * @throws IOException If there was an error reading the anvil world
     */
    public static @NotNull PolarWorld anvilToPolar(@NotNull Path path, int minSection, int maxSection, @NotNull ChunkSelector selector) throws IOException {
        final Instance instance = new InstanceContainer(UUID.randomUUID(), DimensionType.OVERWORLD); // Empty instance, never registered
        final AnvilLoader anvilLoader = new AnvilLoader(path);

        var anvilChunks = new ArrayList<Chunk>();
        try (var files = Files.walk(path.resolve("region"), 1)) {
            for (var regionFile : files.toList()) {
                if (!regionFile.getFileName().toString().endsWith(".mca")) continue;

                var nameParts = regionFile.getFileName().toString().split("\\.");
                var regionX = Integer.parseInt(nameParts[1]);
                var regionZ = Integer.parseInt(nameParts[2]);

                anvilChunks.addAll(readAnvilChunks(instance, anvilLoader, regionX, regionZ, minSection, maxSection, selector));
            }
        }

        var world = new PolarWorld(
                PolarWorld.LATEST_VERSION,
                PolarWorld.DEFAULT_COMPRESSION,
                (byte) minSection, (byte) maxSection,
                new byte[0],
                List.of()
        );
        var polarLoader = new PolarLoader(world);
        polarLoader.saveChunks(anvilChunks);

        return world;
    }

    private static @NotNull List<Chunk> readAnvilChunks(@NotNull Instance instance, @NotNull AnvilLoader anvilLoader, int regionX, int regionZ, int minSection, int maxSection, @NotNull ChunkSelector selector) throws IOException {
        var chunks = new ArrayList<Chunk>();
        for (int x = 0; x < 32; x++) {
            for (int z = 0; z < 32; z++) {
                int chunkX = x + (regionX * 32);
                int chunkZ = z + (regionZ * 32);

                if (!selector.test(chunkX, chunkZ)) continue;

                var chunk = anvilLoader.loadChunk(instance, chunkX, chunkZ).join();
                if (chunk == null) continue;

                chunks.add(chunk);
            }
        }
        return chunks;
    }

}
