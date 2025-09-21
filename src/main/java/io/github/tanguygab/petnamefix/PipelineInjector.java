package io.github.tanguygab.petnamefix;

import com.google.common.base.Optional;
import io.github.tanguygab.petnamefix.nms.DataWatcher;
import io.github.tanguygab.petnamefix.nms.DataWatcherItem;
import io.github.tanguygab.petnamefix.nms.NMSStorage;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ConcurrentModificationException;
import java.util.List;

public class PipelineInjector {

    private static final String DECODER_NAME = "PetNameFix";

    /** NMS Storage reference for quick access */
    private final NMSStorage nms = NMSStorage.getInstance();

    /** DataWatcher position of pet owner field */
    private final int petOwnerPosition = getPetOwnerPosition();

    public PipelineInjector() {
        Bukkit.getServer().getOnlinePlayers().forEach(this::inject);
    }

    public void unload() {
        Bukkit.getServer().getOnlinePlayers().forEach(this::uninject);
    }

    private int getPetOwnerPosition() {
        int version = nms.getMinorVersion();
        return version >= 17 ? 18
                : version >= 15 ? 17
                : version == 14 ? 16
                : version >= 10 ? 14
                : 13;
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
            if (nms.CHANNEL != null)
                return (Channel) nms.CHANNEL.get(nms.NETWORK_MANAGER.get(NMSStorage.getInstance().PLAYER_CONNECTION.get(nms.getHandle.invoke(player))));
        } catch (Exception e) {e.printStackTrace();}
        return null;
    }

    public class BukkitChannelDuplexHandler extends ChannelDuplexHandler {

        @Override
        public void write(ChannelHandlerContext ctx, Object packet, ChannelPromise promise) throws Exception {
            try {
                if (nms.is1_19_4Plus() && nms.ClientboundBundlePacket.isInstance(packet)) {
                    Iterable<?> packets = (Iterable<?>) nms.ClientboundBundlePacket_packets.get(packet);
                    for (Object pack : packets) {
                        if (nms.PacketPlayOutEntityMetadata.isInstance(pack)) {
                            checkMetaData(pack);
                        }
                    }
                } else if (nms.PacketPlayOutEntityMetadata.isInstance(packet)) {
                    if (checkMetaData(packet)) return;
                } else if (nms.PacketPlayOutSpawnEntityLiving.isInstance(packet) && nms.PacketPlayOutSpawnEntityLiving_DATAWATCHER != null) {
                    //<1.15
                    DataWatcher watcher = DataWatcher.fromNMS(nms.PacketPlayOutSpawnEntityLiving_DATAWATCHER.get(packet));
                    DataWatcherItem petOwner = watcher.getItem(petOwnerPosition);
                    if (petOwner != null && (petOwner.getValue() instanceof java.util.Optional || petOwner.getValue() instanceof Optional)) {
                        watcher.removeValue(petOwnerPosition);
                        nms.PacketPlayOutSpawnEntityLiving_DATAWATCHER.set(packet,watcher.toNMS());
                    }
                }
                super.write(ctx, packet, promise);
            } catch (Exception e) {
                super.write(ctx, packet, promise);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private boolean checkMetaData(Object packet) throws ReflectiveOperationException {
        Object removedEntry = null;
        List<Object> items = (List<Object>) nms.PacketPlayOutEntityMetadata_LIST.get(packet);
        if (items == null || items.isEmpty()) return false;
        
        try {
            for (Object item : items) {
                if (item == null) continue;
                int slot;
                Object value;
                if (nms.is1_19_3Plus()) {
                    slot = nms.DataWatcher$DataValue_POSITION.getInt(item);
                    value = nms.DataWatcher$DataValue_VALUE.get(item);
                } else {
                    slot = nms.DataWatcherObject_SLOT.getInt(nms.DataWatcherItem_TYPE.get(item));
                    value = nms.DataWatcherItem_VALUE.get(item);
                }
                if (slot == petOwnerPosition) {
                    if (value instanceof java.util.Optional || value instanceof com.google.common.base.Optional) {
                        removedEntry = item;
                        break;
                    }
                }
            }
        } catch (ConcurrentModificationException e) {
            return checkMetaData(packet);
        }
        
        if (removedEntry != null) {
            if (items.size() <= 1) {
                return true;
            }
            items.remove(removedEntry);
            
            if (items.isEmpty()) {
                return true;
            }
        }
        return false;
    }
}
