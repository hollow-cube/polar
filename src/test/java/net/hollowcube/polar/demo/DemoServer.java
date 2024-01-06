package net.hollowcube.polar.demo;

import net.hollowcube.polar.PolarLoader;
import net.hollowcube.polar.PolarWorld;
import net.hollowcube.polar.PolarWriter;
import net.minestom.server.MinecraftServer;
import net.minestom.server.command.builder.Command;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.GameMode;
import net.minestom.server.entity.Player;
import net.minestom.server.event.player.AsyncPlayerConfigurationEvent;
import net.minestom.server.event.player.PlayerChatEvent;
import net.minestom.server.event.player.PlayerSpawnEvent;
import net.minestom.server.instance.InstanceContainer;
import net.minestom.server.instance.LightingChunk;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;

public class DemoServer {
    public static void main(String[] args) throws Exception {
        System.setProperty("minestom.chunk-view-distance", "16");
        System.setProperty("minestom.use-new-chunk-sending", "true");

        System.setProperty("minestom.new-chunk-sending-count-per-interval", "50");
        System.setProperty("minestom.new-chunk-sending-send-interval", "1");

        var server = MinecraftServer.init();

        var instance = MinecraftServer.getInstanceManager().createInstanceContainer();

        // Unlit
//        instance.setChunkSupplier(LightingChunk::new);
//        instance.setChunkLoader(new PolarLoader(Path.of("./src/test/resources/hcspawn.polar")));
        // Lit
        instance.setChunkLoader(new PolarLoader(Path.of("./hcspawn.polar")));


        MinecraftServer.getGlobalEventHandler()
                .addListener(AsyncPlayerConfigurationEvent.class, event -> {
                    event.setSpawningInstance(instance);
                    event.getPlayer().setRespawnPoint(new Pos(0, 40, 0, 90, 0));
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


        var saveCommand = new Command("save");
        saveCommand.addSyntax((sender, context) -> {
            if (!(sender instanceof Player player)) return;
            player.sendMessage("Saving...");
            var polarLoader = new PolarLoader(new PolarWorld());
            ((InstanceContainer) player.getInstance()).setChunkLoader(polarLoader);
            player.getInstance().saveInstance().join();
            try {
                Files.write(Path.of("./hcspawn.polar"), PolarWriter.write(polarLoader.world()), StandardOpenOption.CREATE);
            } catch (Exception e) {
                e.printStackTrace();
            }
            player.sendMessage("Saved!");
        });
        MinecraftServer.getCommandManager().register(saveCommand);


        var lightCommand = new Command("light");
        lightCommand.addSyntax((sender, context) -> {
            if (!(sender instanceof Player player)) return;
            player.sendMessage("Light!");

            var section = player.getInstance().getChunkAt(player.getPosition()).getSectionAt(player.getPosition().blockY());
            player.sendMessage(section.toString());

            player.sendMessage(Arrays.toString(section.skyLight().array()));
        });
        MinecraftServer.getCommandManager().register(lightCommand);

        server.start("0.0.0.0", 25565);
    }
}
