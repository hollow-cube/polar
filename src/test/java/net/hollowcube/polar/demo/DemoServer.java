package net.hollowcube.polar.demo;

import net.hollowcube.polar.PolarLoader;
import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.GameMode;
import net.minestom.server.event.player.AsyncPlayerConfigurationEvent;
import net.minestom.server.event.player.PlayerChatEvent;
import net.minestom.server.event.player.PlayerSpawnEvent;

import java.nio.file.Path;

public class DemoServer {
    public static void main(String[] args) throws Exception {
        System.setProperty("minestom.chunk-view-distance", "32");

        var server = MinecraftServer.init();

        var instance = MinecraftServer.getInstanceManager().createInstanceContainer();
        instance.setChunkLoader(new PolarLoader(Path.of("./src/test/resources/emclobby.polar")));
//        instance.setChunkSupplier(LightingChunk::new);

        MinecraftServer.getGlobalEventHandler()
                .addListener(AsyncPlayerConfigurationEvent.class, event -> {
                    event.setSpawningInstance(instance);
                    event.getPlayer().setRespawnPoint(new Pos(0, 100, 0));
                })
                .addListener(PlayerSpawnEvent.class, event -> {
                    event.getPlayer().setGameMode(GameMode.CREATIVE);
                })
                .addListener(PlayerChatEvent.class, event -> {
                    if (!event.getMessage().equals("save")) return;

                    var start = System.currentTimeMillis();
                    instance.saveChunksToStorage().join();

                    var time = System.currentTimeMillis() - start;
                    event.getPlayer().sendMessage("Done in " + (time) + "ms!");
                });

        server.start("0.0.0.0", 25565);
    }
}
