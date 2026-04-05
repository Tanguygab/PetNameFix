package io.github.tanguygab.petnamefix;

import io.netty.channel.*;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.SynchedEntityData.DataValue;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.minecraft.world.entity.TamableAnimal;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;

import java.lang.reflect.Field;
import java.util.ConcurrentModificationException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class PipelineInjector {

    private static final String DECODER_NAME = "PetNameFix";
    private static final Field CONNECTION;
    /** DataWatcher position of pet owner field */
    private static final int PET_OWNER_POSITION;

    static {
        try {
            CONNECTION = ServerCommonPacketListenerImpl.class.getDeclaredField("connection");
            CONNECTION.setAccessible(true);
            Field petOwnerId = TamableAnimal.class.getDeclaredField("DATA_OWNERUUID_ID");
            petOwnerId.setAccessible(true);
            PET_OWNER_POSITION = ((EntityDataAccessor<?>)petOwnerId.get(null)).id();
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }


    public PipelineInjector() {
        Bukkit.getServer().getOnlinePlayers().forEach(this::inject);
    }

    public void unload() {
        Bukkit.getServer().getOnlinePlayers().forEach(this::uninject);
    }

    public void inject(Player player) {
        final Channel channel = getChannel(player);
        if (channel != null && channel.pipeline().names().contains("packet_handler"))
            try {
                uninject(player);
                channel.pipeline().addBefore("packet_handler", DECODER_NAME, new BukkitChannelDuplexHandler());
            } catch (Exception ignored) {}
    }

    public void uninject(Player player) {
        final Channel channel = getChannel(player);
        if (channel != null && channel.pipeline().names().contains(DECODER_NAME))
            channel.pipeline().remove(DECODER_NAME);
    }

    private Channel getChannel(Player player) {
        try {
            return ((Connection)CONNECTION.get(((CraftPlayer)player).getHandle().connection)).channel;
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    public class BukkitChannelDuplexHandler extends ChannelDuplexHandler {

        @Override
        public void write(ChannelHandlerContext ctx, Object packet, ChannelPromise promise) throws Exception {
            switch (packet) {
                case ClientboundBundlePacket bundle -> {
                    for (Object pack : bundle.subPackets()) {
                        if (pack instanceof ClientboundSetEntityDataPacket add) {
                            checkMetaData(add);
                        }
                    }
                }
                case ClientboundSetEntityDataPacket add -> {
                    if (checkMetaData(add)) return;
                }
                default -> {}
            }
            super.write(ctx, packet, promise);
        }
    }

    private boolean checkMetaData(ClientboundSetEntityDataPacket packet) {
        DataValue<?> removedEntry = null;
        List<DataValue<?>> items = packet.packedItems();
        if (items.isEmpty()) return false;
        
        try {
            if (items.contains(null)) items.removeIf(Objects::isNull);
            for (DataValue<?> item : items) {
                Object value = item.value();
                if (item.id() == PET_OWNER_POSITION) {
                    if (value instanceof Optional || value instanceof com.google.common.base.Optional) {
                        removedEntry = item;
                        break;
                    }
                }
            }
        } catch (ConcurrentModificationException e) {
            return checkMetaData(packet);
        }
        
        if (removedEntry != null) {
            items.remove(removedEntry);
            return items.isEmpty();
        }
        return false;
    }
}
