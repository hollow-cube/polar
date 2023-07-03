package net.hollowcube.polar;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;

import static org.junit.jupiter.api.Assertions.*;

class TestReaderBackwardsCompatibility {

    @Test
    void testVersion1() {
        runTest(1);
    }

    @Test
    void testVersion2() {
        runTest(2);
    }

    @Test
    void testVersion3() {
        runTest(3);
    }

    private static void runTest(int version) {
        var is = TestReaderBackwardsCompatibility.class.getResourceAsStream("/backward/" + version + ".polar");
        assertNotNull(is);

        var worldData = assertDoesNotThrow(is::readAllBytes);
        var world = assertDoesNotThrow(() -> PolarReader.read(worldData));
        assertNotNull(world);

        assertEquals(32 * 32, world.chunks().size());

        var chunk = world.chunkAt(5, 5);
        assertNotNull(chunk);
        assertEquals(0, chunk.blockEntities().size());

        var section = chunk.sections()[7];
        var expectedPalette = new String[]{"granite", "stone", "diorite", "gravel", "coal_ore", "copper_ore", "iron_ore", "dirt"};
        assertArrayEquals(expectedPalette, section.blockPalette());
    }

}
