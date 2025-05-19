package net.hollowcube.polar;

import net.minestom.server.MinecraftServer;
import net.minestom.server.instance.InstanceContainer;
import net.minestom.server.world.DimensionType;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TestCustomBiomes {

    static {
        MinecraftServer.init();
    }

    @Test
    void testWriteRead() {
        var world = new PolarWorld();

        var wa = new PolarWorldAccess() {
            @Override
            public int getBiomeId(@NotNull String name) {
                return "test:biome".equals(name) ? 1000 : PolarWorldAccess.super.getBiomeId(name);
            }

            @Override
            public @NotNull String getBiomeName(int id) {
                return id == 1000 ? "test:biome" : PolarWorldAccess.super.getBiomeName(id);
            }
        };
        var loader = new PolarLoader(world).setWorldAccess(wa);
        var instance = new InstanceContainer(UUID.randomUUID(), DimensionType.OVERWORLD, loader);
        var chunk = instance.loadChunk(0, 0).join();
        chunk.getSection(0).biomePalette().fill(1000);

        loader.saveChunk(chunk);

        var newInstance = new InstanceContainer(UUID.randomUUID(), DimensionType.OVERWORLD, loader);
        var newChunk = loader.loadChunk(newInstance, 0, 0);

        assertEquals(1000, newChunk.getSection(0).biomePalette().get(2, 2, 2));
    }

}
