package io.github.tanguygab.petnamefix;

import io.github.tanguygab.petnamefix.nms.NMSStorage;
import org.bukkit.entity.Tameable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.java.JavaPlugin;

public final class PetNameFix extends JavaPlugin implements Listener {

    private PipelineInjector pipeline;

    @Override
    public void onEnable() {
        try {
            NMSStorage nms = new NMSStorage();
            if (nms.getMinorVersion() < 9) {
                disable();
                return;
            }
            NMSStorage.setInstance(nms);
            pipeline = new PipelineInjector();
            getServer().getPluginManager().registerEvents(this,this);
        } catch (ReflectiveOperationException e) {
            disable();
            e.printStackTrace();
        }
    }

    private void disable() {
        getLogger().severe("Unsupported server software/version (MC 1.9-1.21.6), disabling...");
        getServer().getPluginManager().disablePlugin(this);
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

    @EventHandler(ignoreCancelled = true)
    public void onClick(PlayerInteractEntityEvent e) {
        if (!(e.getRightClicked() instanceof Tameable)) return;
        Tameable pet = (Tameable) e.getRightClicked();
        if (pet.isTamed() && pet.getOwner() == e.getPlayer() && e.getHand() == EquipmentSlot.OFF_HAND)
            e.setCancelled(true);
    }

}
