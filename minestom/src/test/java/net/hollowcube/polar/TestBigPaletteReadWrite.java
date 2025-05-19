package net.hollowcube.polar;

import net.minestom.server.MinecraftServer;
import net.minestom.server.instance.InstanceContainer;
import net.minestom.server.instance.block.Block;
import net.minestom.server.world.DimensionType;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.channels.Channels;
import java.util.Random;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestBigPaletteReadWrite {

    static {
        MinecraftServer.init();
    }

    @Test
    void testPackUnpackDirect() {
        var ints = new int[4096];
        for (int i = 0; i < 510; i++) {
            ints[i] = i;
        }
        var bitsPerEntry = 9;

        var longs = PaletteUtil.pack(ints, bitsPerEntry);
        var out = new int[ints.length];
        PaletteUtil.unpack(out, longs, bitsPerEntry);

        assertArrayEquals(ints, out);
    }

    @Test
    void testStreamLoader() {
        var instance = new InstanceContainer(UUID.randomUUID(), DimensionType.OVERWORLD);

        var random = new Random(22);
        int blockCount = 256;

        for (int y = 0; y < 16; y++) {
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    var block = Block.fromStateId(random.nextInt(blockCount));
                    instance.setBlock(x, y, z, block);
                }
            }
        }

        var loader = new PolarLoader(new PolarWorld());
        instance.setChunkLoader(loader);
        instance.saveChunksToStorage().join();
        var worldBytes = PolarWriter.write(loader.world());

        var loadInstance = new InstanceContainer(UUID.randomUUID(), DimensionType.OVERWORLD);
        PolarLoader.streamLoad(loadInstance, Channels.newChannel(new ByteArrayInputStream(worldBytes)),
                worldBytes.length, null, null, false).join();

        random = new Random(22);
        for (int y = 0; y < 16; y++) {
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    var block = Block.fromStateId(random.nextInt(blockCount));
                    assertEquals(block, loadInstance.getBlock(x, y, z));
                }
            }
        }
    }

    @Test
    void testLegacyLoader() {
        var instance = new InstanceContainer(UUID.randomUUID(), DimensionType.OVERWORLD);

        var random = new Random(22);
        int blockCount = 256;

        for (int y = 0; y < 16; y++) {
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    var block = Block.fromStateId(random.nextInt(blockCount));
                    instance.setBlock(x, y, z, block);
                }
            }
        }

        var loader = new PolarLoader(new PolarWorld());
        instance.setChunkLoader(loader);
        instance.saveChunksToStorage().join();
        var worldBytes = PolarWriter.write(loader.world());

        var loadInstance = new InstanceContainer(UUID.randomUUID(), DimensionType.OVERWORLD);
        loadInstance.setChunkLoader(new PolarLoader(MinestomPolarReader.read(worldBytes)));
        loadInstance.loadChunk(0, 0).join();

        random = new Random(22);
        for (int y = 0; y < 16; y++) {
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    var block = Block.fromStateId(random.nextInt(blockCount));
                    assertEquals(block, loadInstance.getBlock(x, y, z));
                }
            }
        }
    }
}
