package io.github.tanguygab.petnamefix;

import com.google.common.base.Optional;
import io.github.tanguygab.petnamefix.nms.DataWatcher;
import io.github.tanguygab.petnamefix.nms.DataWatcherItem;
import io.github.tanguygab.petnamefix.nms.NMSStorage;
import io.netty.channel.*;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.lang.reflect.InvocationTargetException;
import java.util.ConcurrentModificationException;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.WeakHashMap;
import java.util.function.Function;

public class PipelineInjector {

    private static final String DECODER_NAME = "PetNameFix";

    /** NMS Storage reference for quick access */
    private final NMSStorage nms = NMSStorage.getInstance();

    /** DataWatcher position of pet owner field */
    private final int petOwnerPosition = getPetOwnerPosition();

    /** Logger of last interacts to prevent feature not working on 1.16 */
    private final WeakHashMap<Player, Long> lastInteractFix = new WeakHashMap<>();

    /**
     * Since 1.16, client sends interact packet twice for entities affected
     * by removed owner field. Because of that, we need to cancel the duplicated
     * packet to avoid double toggle and preventing the entity from getting
     * its pose changed. The duplicated packet is usually sent instantly in the
     * same millisecond, however, when installing ProtocolLib with MyPet, the delay
     * is up to 3 ticks. When holding right-click on an entity, interact is sent every
     * 200 milliseconds, which is the value we should not go above. Optimal value is
     * therefore between 150 and 200 milliseconds.
     */
    private static final int INTERACT_COOLDOWN = 160;

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

    private int getMinorVersion(Player p) {
        return 19;
    }

    public void inject(Player player) {
        final Channel channel = getChannel(player);
        if (getMinorVersion(player) < 8 || channel == null) return; //hello A248
        if (!channel.pipeline().names().contains("packet_handler")) {
            //fake player or waterfall bug
            return;
        }
        uninject(player);
        try {
            channel.pipeline().addBefore("packet_handler", DECODER_NAME, getChannelFunction().apply(player));
        } catch (NoSuchElementException | IllegalArgumentException e) {
            //I don't really know how does this keep happening but whatever
        }
    }

    public void uninject(Player player) {
        final Channel channel = getChannel(player);
        if (getMinorVersion(player) < 8 || channel == null) return; //hello A248
        try {
            if (channel.pipeline().names().contains(DECODER_NAME)) channel.pipeline().remove(DECODER_NAME);
        } catch (NoSuchElementException ignored) {}
    }

    private Channel getChannel(Player player) {
        try {
            if (nms.CHANNEL != null) return (Channel) nms.CHANNEL.get(nms.NETWORK_MANAGER.get(NMSStorage.getInstance().PLAYER_CONNECTION.get(nms.getHandle.invoke(player))));
        } catch (IllegalAccessException | InvocationTargetException e) {
            e.printStackTrace();
        }
        return null;
    }

    public Function<Player, ChannelDuplexHandler> getChannelFunction() {
        return BukkitChannelDuplexHandler::new;
    }

    private boolean isInteract(Object action) {
        return nms.getMinorVersion() >= 17 ? nms.PacketPlayInUseEntity$d.isInstance(action) : action.toString().equals("INTERACT");
    }

    public class BukkitChannelDuplexHandler extends ChannelDuplexHandler {

        private final Player player;

        public BukkitChannelDuplexHandler(Player player) {
            this.player = player;
        }

        @Override
        public void channelRead(ChannelHandlerContext context, Object packet) throws Exception {
            if (nms.PacketPlayInUseEntity.isInstance(packet) && isInteract(nms.PacketPlayInUseEntity_ACTION.get(packet))) {
                if (System.currentTimeMillis() - lastInteractFix.getOrDefault(player, 0L) < INTERACT_COOLDOWN) {
                    //last interact packet was sent right now, cancelling to prevent double-toggle due to this feature enabled
                    return;
                }
                //this is the first packet, saving player so the next packet can be cancelled
                lastInteractFix.put(player, System.currentTimeMillis());
            }
            super.channelRead(context, packet);
        }

        @Override
        public void write(ChannelHandlerContext ctx, Object packet, ChannelPromise promise) throws Exception {
            if (nms.PacketPlayOutEntityMetadata.isInstance(packet)) {
                Object removedEntry = null;
                List<Object> items = (List<Object>) nms.PacketPlayOutEntityMetadata_LIST.get(packet);
                if (items == null) return;
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
                            }
                        }
                    }
                } catch (ConcurrentModificationException e) {
                    //no idea how can this list change in another thread since it's created for the packet but whatever, try again
                    write(ctx,packet,promise);
                }
                if (removedEntry != null) items.remove(removedEntry);
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
        }
    }

}
