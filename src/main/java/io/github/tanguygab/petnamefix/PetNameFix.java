package io.github.tanguygab.petnamefix;

import io.github.tanguygab.petnamefix.nms.NMSStorage;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

public final class PetNameFix extends JavaPlugin implements Listener {

    private PipelineInjector pipeline;

    @Override
    public void onEnable() {
        try {
            NMSStorage nms = new NMSStorage();
            if (nms.getMinorVersion() < 9) {
                getLogger().severe("Unsupported server software, this plugin is only required on MC 1.9-1.19.4, disabling");
                getServer().getPluginManager().disablePlugin(this);
                return;
            }
            NMSStorage.setInstance(nms);
            pipeline = new PipelineInjector();
            getServer().getPluginManager().registerEvents(this,this);
        } catch (ReflectiveOperationException e) {
            getLogger().severe("Unsupported server software/version (MC 1.9-1.19.4), disabling...");
            e.printStackTrace();
            getServer().getPluginManager().disablePlugin(this);
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

}
