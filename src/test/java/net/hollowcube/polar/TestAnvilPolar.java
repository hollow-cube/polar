package net.hollowcube.polar;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TestAnvilPolar {

    @Test
    void testConvertAnvilWorld() throws Exception {
        var world = AnvilPolar.anvilToPolar(
                Path.of("./src/test/resources/world").toRealPath(),
                ChunkSelector.radius(5)
        );
        assertEquals(-4, world.minSection());

        var result = PolarWriter.write(world);
        System.out.println(result.length);
        Files.write(Path.of("./src/test/resources/world.polar"), result);
    }

}
