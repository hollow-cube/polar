package net.hollowcube.polar.demo;

import net.hollowcube.polar.PolarLoader;
import net.hollowcube.polar.PolarReader;
import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.GameMode;
import net.minestom.server.event.player.PlayerLoginEvent;
import net.minestom.server.event.player.PlayerSpawnEvent;
import net.minestom.server.instance.LightingChunk;

import java.nio.file.Files;
import java.nio.file.Path;

public class DemoServer {
    public static void main(String[] args) throws Exception {
        var server = MinecraftServer.init();

        var instance = MinecraftServer.getInstanceManager().createInstanceContainer();
//        var world = AnvilPolar.anvilToPolar(Path.of("./src/test/resources/testtnt").toRealPath());
        var world = PolarReader.read(Files.readAllBytes(Path.of("./src/test/resources/world.polar")));
        instance.setChunkLoader(new PolarLoader(world));
        instance.setChunkSupplier(LightingChunk::new);

        MinecraftServer.getGlobalEventHandler()
                .addListener(PlayerLoginEvent.class, event -> {
                    event.setSpawningInstance(instance);
                    event.getPlayer().setRespawnPoint(new Pos(0, 100, 0));
                })
                .addListener(PlayerSpawnEvent.class, event -> {
                    event.getPlayer().setGameMode(GameMode.CREATIVE);
                });

        server.start("0.0.0.0", 25565);
    }
}
