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
            NMSStorage nms = new NMSStorage();
            NMSStorage.setInstance(nms);
            if (nms.getMinorVersion() < 9) {
                getLogger().severe("Unsupported server software, this plugin is only required on MC 1.9+, disabling");
                getServer().getPluginManager().disablePlugin(this);
                return;
            }
            pipeline = new PipelineInjector();
            getServer().getPluginManager().registerEvents(this,this);
        } catch (ReflectiveOperationException e) {
            e.printStackTrace();
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
