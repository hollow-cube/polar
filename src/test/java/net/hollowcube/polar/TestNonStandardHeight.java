package net.hollowcube.polar;

import net.minestom.server.MinecraftServer;
import net.minestom.server.instance.InstanceContainer;
import net.minestom.server.instance.block.Block;
import net.minestom.server.registry.DynamicRegistry;
import net.minestom.server.world.DimensionType;
import org.junit.jupiter.api.Test;

import java.util.UUID;

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

        PolarWorld world = new PolarWorld(dimensionType);

        container.setChunkLoader(new PolarLoader(world));

        container.setBlock(0, 0, 0, Block.STONE);

        container.saveChunksToStorage(); // writes chunks to PolarWorld

        byte[] bytes = PolarWriter.write(world);

        PolarReader.read(bytes);
    }
}
