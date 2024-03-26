package net.hollowcube.polar;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TestPolarReader {

    @Test
    void testReadInvalidMagic() {
        var e = assertThrows(PolarReader.Error.class, () -> {
            PolarReader.read(new byte[]{0x00, 0x00, 0x00, 0x00, 0x00, 0x00});
        });
        assertEquals("Invalid magic number", e.getMessage());
    }

    @Test
    void testNewerVersionFail() {
        var e = assertThrows(PolarReader.Error.class, () -> {
            PolarReader.read(new byte[]{
                    0x50, 0x6F, 0x6C, 0x72, // magic number
                    0x50, 0x50, // version
            });
        });
        assertEquals("Unsupported Polar version. Up to " + PolarWorld.LATEST_VERSION + " is supported, found 20560.", e.getMessage());
    }

    @Test
    void testHeightmapReadWrite() {
        var world = new PolarWorld();
        var emptySections = new PolarSection[24];
        Arrays.fill(emptySections, new PolarSection());
        var heightmaps = new int[PolarChunk.MAX_HEIGHTMAPS][];
        heightmaps[0] = new int[PolarChunk.HEIGHTMAP_SIZE];
        for (int i = 0; i < PolarChunk.HEIGHTMAP_SIZE; i++) {
            heightmaps[0][i] = i;
        }
        world.updateChunkAt(0, 0, new PolarChunk(0, 0, emptySections, List.of(), heightmaps, new byte[0]));

        var raw = PolarWriter.write(world);
        var newWorld = PolarReader.read(raw);
        var newChunk = newWorld.chunkAt(0, 0);
        var newHeightmap = newChunk.heightmap(0);
        for (int i = 0; i < PolarChunk.HEIGHTMAP_SIZE; i++) {
            assertEquals(i, newHeightmap[i]);
        }

    }

}
