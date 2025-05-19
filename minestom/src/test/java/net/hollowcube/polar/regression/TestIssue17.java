package net.hollowcube.polar.regression;

import net.hollowcube.polar.PolarLoader;
import net.hollowcube.polar.PolarWorld;
import net.minestom.server.MinecraftServer;
import net.minestom.server.instance.InstanceContainer;
import net.minestom.server.instance.block.Block;
import net.minestom.server.world.DimensionType;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

// https://github.com/hollow-cube/polar/issues/17
class TestIssue17 {

    static {
        MinecraftServer.init();
    }

    @Test
    void testIssue17() {
        var world = new PolarWorld();
        var loader = new PolarLoader(world);

        var instance = new InstanceContainer(UUID.randomUUID(), DimensionType.OVERWORLD, loader);
        instance.loadChunk(0, 0).join();
        instance.setBlock(1, 40, 1, Block.STONE);

        // This historically failed the Chunk lock for access assertion.
        // "java.lang.AssertionError: Chunk must be locked before access"
        assertDoesNotThrow(() -> instance.saveChunksToStorage().join());
    }
}
