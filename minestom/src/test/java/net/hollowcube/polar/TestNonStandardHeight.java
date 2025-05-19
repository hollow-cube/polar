package net.hollowcube.polar;

import net.minestom.server.MinecraftServer;
import net.minestom.server.instance.InstanceContainer;
import net.minestom.server.instance.block.Block;
import net.minestom.server.registry.DynamicRegistry;
import net.minestom.server.world.DimensionType;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class TestNonStandardHeight {
    static final DimensionType dimensionType = DimensionType.builder()
            .minY(-2032)
            .height(4064)
            .build();

    static final DynamicRegistry.Key<DimensionType> dimensionTypeKey;

    static {
        MinecraftServer.init();

        dimensionTypeKey = MinecraftServer
                .getDimensionTypeRegistry()
                .register("test:height", dimensionType);
    }

    @Test
    void testNonStandardDimensionHeight() {
        InstanceContainer container = new InstanceContainer(UUID.randomUUID(), dimensionTypeKey);

        PolarWorld first = new PolarWorld();

        PolarLoader loader = new PolarLoader(first);

        container.setChunkLoader(loader);

        container.setBlock(0, 0, 0, Block.STONE);

        assertEquals(loader.world().minSection(), -4);
        assertEquals(loader.world().maxSection(), 19);

        // writes chunks to PolarWorld
        // should change the world height
        container.saveInstance();

        assertEquals(loader.world().minSection(), -127);
        assertEquals(loader.world().maxSection(), 126);

        byte[] bytes = PolarWriter.write(loader.world());

        MinestomPolarReader.read(bytes);
    }
}
