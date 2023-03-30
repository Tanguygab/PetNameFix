package io.github.tanguygab.petnamefix;

import io.github.tanguygab.petnamefix.nms.NMSStorage;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

public final class PetNameFix extends JavaPlugin implements Listener {

    private PipelineInjector pipeline;

    @Override
    public void onEnable() {
        try {
            NMSStorage.setInstance(new NMSStorage());
            pipeline = new PipelineInjector();
            getServer().getPluginManager().registerEvents(this,this);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void onDisable() {
        if (pipeline == null) return;
        pipeline.unload();
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        pipeline.inject(e.getPlayer());
    }
    @EventHandler
    public void onLeave(PlayerQuitEvent e) {
        pipeline.uninject(e.getPlayer());
    }
}
