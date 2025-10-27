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

import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.List;
import java.util.Objects;
import java.util.logging.Logger;

public class PipelineInjector {

    private static final String DECODER_NAME = "PetNameFix";

    /** NMS Storage reference for quick access */
    private final NMSStorage nms = NMSStorage.getInstance();

    /** DataWatcher position of pet owner field */
    private final int petOwnerPosition = getPetOwnerPosition();

    /** Logger for debugging null metadata issues */
    private final Logger logger = Logger.getLogger("PetNameFix");

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
        if (channel != null && channel.pipeline().names().contains("packet_handler")) {
            try {
                uninject(player);
                channel.pipeline().addBefore("packet_handler", DECODER_NAME, new BukkitChannelDuplexHandler());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public void uninject(Player player) {
        final Channel channel = getChannel(player);
        if (channel != null && channel.pipeline().names().contains(DECODER_NAME)) {
            try {
                channel.pipeline().remove(DECODER_NAME);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
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
                            if (checkMetaData(pack)) {
                                return;
                            }
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

                // Final safety check: ensure metadata packets don't contain null elements before sending
                if (nms.PacketPlayOutEntityMetadata.isInstance(packet)) {
                    @SuppressWarnings("unchecked")
                    List<Object> items = (List<Object>) nms.PacketPlayOutEntityMetadata_LIST.get(packet);
                    if (items != null) {
                        long nullCount = items.stream().filter(Objects::isNull).count();
                        if (nullCount > 0) {
                            logger.warning("Final check: removing " + nullCount + " null element(s) from metadata packet before sending");
                            items.removeIf(Objects::isNull);
                        }
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
        return checkMetaData(packet, 0);
    }

    private boolean checkMetaData(Object packet, int retryCount) throws ReflectiveOperationException {
        if (retryCount > 3) {
            return false;
        }
        Object removedEntry = null;
        @SuppressWarnings("unchecked")
        List<Object> items = (List<Object>) nms.PacketPlayOutEntityMetadata_LIST.get(packet);
        if (items == null || items.isEmpty()) return false;

        // Pre-check: log if there are any null items in the list before processing
        long preNullCount = items.stream().filter(Objects::isNull).count();
        if (preNullCount > 0) {
            logger.warning("Detected " + preNullCount + " null element(s) in metadata list BEFORE processing - this may indicate an issue with packet creation");
        }
        
        try {
            for (Object item : items) {
                if (item == null) continue;
                int slot;
                Object value;
                try {
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
                } catch (Exception e) {
                    continue;
                }
            }
        } catch (ConcurrentModificationException e) {
            try {
                @SuppressWarnings("unchecked")
                List<Object> originalItems = (List<Object>) nms.PacketPlayOutEntityMetadata_LIST.get(packet);
                List<Object> itemsCopy = new ArrayList<>(originalItems);
                return processMetadataItems(itemsCopy, packet, retryCount + 1);
            } catch (Exception ex) {
                return false;
            }
        }
        
        if (removedEntry != null) {
            items.remove(removedEntry);

            // Remove any null elements that may have been introduced
            long nullCount = items.stream().filter(Objects::isNull).count();
            if (nullCount > 0) {
                logger.warning("Found " + nullCount + " null element(s) in metadata list after removing pet owner data, cleaning up...");
                items.removeIf(Objects::isNull);
            }

            if (items.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private boolean processMetadataItems(List<Object> itemsCopy, Object packet, int retryCount) throws ReflectiveOperationException {
        if (retryCount > 3) {
            return false;
        }

        Object removedEntry = null;
        
        for (Object item : itemsCopy) {
            if (item == null) continue;
            int slot;
            Object value;
            
            try {
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
            } catch (Exception e) {
                continue;
            }
        }
        
        if (removedEntry != null) {
            try {
                @SuppressWarnings("unchecked")
                List<Object> originalItems = (List<Object>) nms.PacketPlayOutEntityMetadata_LIST.get(packet);

                originalItems.remove(removedEntry);

                // Remove any null elements that may have been introduced
                long nullCount = originalItems.stream().filter(Objects::isNull).count();
                if (nullCount > 0) {
                    logger.warning("Found " + nullCount + " null element(s) in metadata list (retry path) after removing pet owner data, cleaning up...");
                    originalItems.removeIf(Objects::isNull);
                }

                if (originalItems.isEmpty()) {
                    return true;
                }
            } catch (Exception e) {
                return false;
            }
        }

        return false;
    }
}
