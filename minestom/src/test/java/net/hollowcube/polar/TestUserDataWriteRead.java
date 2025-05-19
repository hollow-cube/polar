package net.hollowcube.polar;

import net.minestom.server.MinecraftServer;
import net.minestom.server.instance.InstanceContainer;
import net.minestom.server.network.NetworkBuffer;
import net.minestom.server.world.DimensionType;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TestUserDataWriteRead {

    static {
        MinecraftServer.init();
    }

    @Test
    void testWriteRead() {
        var world = new PolarWorld();

        var emptySections = new PolarSection[24];
        Arrays.fill(emptySections, new PolarSection());

        var heightmaps = new int[PolarChunk.MAX_HEIGHTMAPS][];
        world.updateChunkAt(0, 0, new PolarChunk(0, 0, emptySections, List.of(), heightmaps, new byte[0]));

        var wa = new UpdateTimeWorldAccess();
        var loader = new PolarLoader(world).setWorldAccess(wa);
        var instance = new InstanceContainer(UUID.randomUUID(), DimensionType.OVERWORLD, loader);
        var chunk = loader.loadChunk(instance, 0, 0);

        loader.saveChunk(chunk);

        var newPolarChunk = world.chunkAt(0, 0);
        var savedTime = NetworkBuffer.wrap(newPolarChunk.userData(), 0, newPolarChunk.userData().length).read(NetworkBuffer.LONG);
        assertEquals(wa.saveTime, savedTime);

        loader.loadChunk(instance, 0, 0);
        assertEquals(wa.loadTime, savedTime);
    }

}
