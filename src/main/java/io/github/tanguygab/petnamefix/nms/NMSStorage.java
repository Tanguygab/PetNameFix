package io.github.tanguygab.petnamefix.nms;

import io.netty.channel.Channel;
import org.bukkit.Bukkit;

import java.lang.reflect.*;
import java.util.*;

public class NMSStorage {

    //instance of this class
    private static NMSStorage instance;


    //server minor version such as "16"
    private int minorVersion;
    private FunctionWithException<String, Class<?>> classFunction;

    private final boolean is1_19_3Plus = classExists("net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket");
    private final boolean is1_19_4Plus;

    //base
    public Field PLAYER_CONNECTION;
    public Field NETWORK_MANAGER;
    public Field CHANNEL;
    public final Method getHandle = Class.forName(Bukkit.getServer().getClass().getPackage().getName() + ".entity.CraftPlayer").getMethod("getHandle");

    //DataWatcher
    private Class<?> DataWatcher;
    private Class<?> DataWatcherItem;
    public Class<?> DataWatcherRegistry;
    public Constructor<?> newDataWatcher;
    public Constructor<?> newDataWatcherObject;
    public Field DataWatcherItem_TYPE;
    public Field DataWatcherItem_VALUE;
    public Field DataWatcherObject_SLOT;
    public Field DataWatcherObject_SERIALIZER;
    public Method DataWatcher_REGISTER;
    public Method DataWatcher_b;
    //1.19.3+
    protected Class<?> DataWatcher$DataValue;
    public Field DataWatcher$DataValue_POSITION;
    public Field DataWatcher$DataValue_VALUE;
    public Method DataWatcher_markDirty;

    //PacketPlayOutSpawnEntityLiving
    public Class<?> PacketPlayOutSpawnEntityLiving;

    public Field PacketPlayOutSpawnEntityLiving_DATAWATCHER;

    //other entity packets
    public Class<?> PacketPlayOutEntityMetadata;
    public Field PacketPlayOutEntityMetadata_LIST;

    public Class<?> ClientboundBundlePacket;
    public Field ClientboundBundlePacket_packets;

    /**
     * Creates new instance, initializes required NMS classes and fields
     */

    private void detectServerVersion() {
        FunctionWithException<String, Class<?>> classFunction = name -> Class.forName("net.minecraft." + name);
        String[] array = Bukkit.getServer().getClass().getPackage().getName().split("\\.");
        int minorVersion;
        if (array.length > 3) {
            // Normal packaging
            String serverPackage = array[3];
            minorVersion = Integer.parseInt(serverPackage.split("_")[1]);
            if (minorVersion < 17) {
                ClassLoader loader = NMSStorage.class.getClassLoader();
                classFunction = name -> loader.loadClass("net.minecraft.server." + serverPackage + "." + name);
            }
        } else {
            // Paper without CB relocation
            minorVersion = Integer.parseInt(Bukkit.getBukkitVersion().split("-")[0].split("\\.")[1]);
        }

        this.classFunction = classFunction;
        this.minorVersion = minorVersion;
    }

    public NMSStorage() throws ReflectiveOperationException {
        detectServerVersion();

        is1_19_4Plus = is1_19_3Plus && (minorVersion > 19 || !Bukkit.getServer().getClass().getPackage().getName().split("\\.")[3].equals("v1_19_R2"));

        if (minorVersion < 9) return;

        Class<?> entityPlayer = getClass("server.level.EntityPlayer", "EntityPlayer");
        Class<?> playerConnection = getClass("server.network.PlayerConnection", "PlayerConnection");
        PLAYER_CONNECTION = getFields(entityPlayer, playerConnection).get(0);
        DataWatcher = getClass("network.syncher.SynchedEntityData", "network.syncher.DataWatcher", "DataWatcher");
        DataWatcherItem = getClass("network.syncher.DataWatcher$Item", "DataWatcher$Item", "DataWatcher$WatchableObject", "WatchableObject");
        if (minorVersion < 20) newDataWatcher = DataWatcher.getConstructor(getClass("world.entity.Entity", "Entity"));
        DataWatcherItem_VALUE = getFields(DataWatcherItem, Object.class).get(0);
        PacketPlayOutSpawnEntityLiving = getClass("network.protocol.game.PacketPlayOutSpawnEntityLiving",
                "network.protocol.game.PacketPlayOutSpawnEntity", "PacketPlayOutSpawnEntityLiving", "Packet24MobSpawn");
        PacketPlayOutEntityMetadata = getClass("network.protocol.game.PacketPlayOutEntityMetadata", "PacketPlayOutEntityMetadata", "Packet40EntityMetadata");
        PacketPlayOutEntityMetadata_LIST = getFields(PacketPlayOutEntityMetadata, List.class).get(0);

        Class<?> NetworkManager = getClass("network.NetworkManager", "NetworkManager");
        NETWORK_MANAGER = getFields(playerConnection, NetworkManager).isEmpty()
                ? getFields(playerConnection.getSuperclass(), NetworkManager).get(0)
                : getFields(playerConnection, NetworkManager).get(0);
        CHANNEL = getFields(NetworkManager, Channel.class).get(0);

        initializeDataWatcher();
        if (minorVersion <= 14) PacketPlayOutSpawnEntityLiving_DATAWATCHER = getFields(PacketPlayOutSpawnEntityLiving, DataWatcher).get(0);
        if (is1_19_4Plus) {
            ClientboundBundlePacket = getClass("network.protocol.game.ClientboundBundlePacket");
            (ClientboundBundlePacket_packets = ClientboundBundlePacket.getSuperclass().getDeclaredFields()[0]).setAccessible(true);
        }
    }

    /**
     * Sets new instance
     * @param instance - new instance
     */
    public static void setInstance(NMSStorage instance) {
        NMSStorage.instance = instance;
    }

    /**
     * Returns instance
     * @return instance
     */
    public static NMSStorage getInstance() {
        return instance;
    }

    private void initializeDataWatcher() throws ReflectiveOperationException {
        Class<?> DataWatcherObject = getClass("network.syncher.DataWatcherObject", "DataWatcherObject");
        DataWatcherRegistry = getClass("network.syncher.DataWatcherRegistry", "DataWatcherRegistry");
        Class<?> DataWatcherSerializer = getClass("network.syncher.DataWatcherSerializer", "DataWatcherSerializer");
        newDataWatcherObject = DataWatcherObject.getConstructor(int.class, DataWatcherSerializer);
        DataWatcherItem_TYPE = getFields(DataWatcherItem, DataWatcherObject).get(0);
        DataWatcherObject_SLOT = getFields(DataWatcherObject, int.class).get(0);
        DataWatcherObject_SERIALIZER = getFields(DataWatcherObject, DataWatcherSerializer).get(0);
        DataWatcher_REGISTER = getMethod(DataWatcher, new String[]{"register", "method_12784", "a"}, DataWatcherObject, Object.class);
        if (!is1_19_3Plus()) return;
        DataWatcher$DataValue = getClass("network.syncher.SynchedEntityData$DataValue","network.syncher.DataWatcher$b","network.syncher.DataWatcher$c");
        DataWatcher$DataValue_POSITION = getFields(DataWatcher$DataValue, int.class).get(0);
        DataWatcher$DataValue_VALUE = getFields(DataWatcher$DataValue, Object.class).get(0);
        DataWatcher_b = DataWatcher.getMethod("b");
        DataWatcher_markDirty = getMethods(DataWatcher, DataWatcherObject).get(0);
    }

    /**
     * Returns class with given potential names in same order
     * @param names - possible class names
     * @return class for specified name(s)
     * @throws ClassNotFoundException if class does not exist
     */
    private Class<?> getClass(String... names) throws ClassNotFoundException {
        for (String name : names) {
            try {
                return classFunction.apply(name);
            } catch (Exception ignored) {
                // not the first class name in array
            }
        }
        throw new ClassNotFoundException("No class found with possible names " + Arrays.toString(names));
    /*
        for (String name : names)
            try {return minorVersion >= 17 ? Class.forName(name) : getLegacyClass(name);}
            catch (ClassNotFoundException e) {/*not the first class name in array/}
        throw new ClassNotFoundException("No class found with possible names " + Arrays.toString(names));
    */
    }

    /**
     * Returns method with specified possible names and parameters. Throws exception if no such method was found
     * @param clazz - class to get method from
     * @param names - possible method names
     * @param parameterTypes - parameter types of the method
     * @return method with specified name and parameters
     * @throws NoSuchMethodException if no such method exists
     */
    private Method getMethod(Class<?> clazz, String[] names, Class<?>... parameterTypes) throws NoSuchMethodException {
        for (String name : names)
            try {return clazz.getMethod(name, parameterTypes);}
            catch (NoSuchMethodException e) {/*not the first method in array*/}

        List<String> list = new ArrayList<>();
        for (Method m : clazz.getMethods()) {
            if (m.getParameterCount() != parameterTypes.length) continue;
            Class<?>[] types = m.getParameterTypes();
            boolean valid = true;
            for (int i=0; i<types.length; i++) {
                if (types[i] != parameterTypes[i]) {
                    valid = false;
                    break;
                }
            }
            if (valid) list.add(m.getName());
        }
        throw new NoSuchMethodException("No method found with possible names " + Arrays.toString(names) + " with parameters " +
                Arrays.toString(parameterTypes) + " in class " + clazz.getName() + ". Methods with matching parameters: " + list);
    }

    private List<Method> getMethods(Class<?> clazz, Class<?>... parameterTypes){
        List<Method> list = new ArrayList<>();
        for (Method m : clazz.getDeclaredMethods()) {
            if (m.getReturnType() != void.class || m.getParameterCount() != parameterTypes.length || !Modifier.isPublic(m.getModifiers())) continue;
            Class<?>[] types = m.getParameterTypes();
            boolean valid = true;
            for (int i=0; i<types.length; i++) {
                if (types[i] != parameterTypes[i]) {
                    valid = false;
                    break;
                }
            }
            if (valid) list.add(m);
        }
        return list;
    }

    /**
     * Returns all fields of class with defined class type
     * @param clazz - class to check fields of
     * @param type - field type to check for
     * @return list of all fields with specified class type
     */
    private List<Field> getFields(Class<?> clazz, Class<?> type){
        if (clazz == null) throw new IllegalArgumentException("Source class cannot be null");
        List<Field> list = new ArrayList<>();
        for (Field field : clazz.getDeclaredFields()) {
            if (field.getType() == type) {
                field.setAccessible(true);
                list.add(field);
            }
        }
        return list;
    }

    public int getMinorVersion() {
        return minorVersion;
    }

    public boolean is1_19_3Plus() {
        return is1_19_3Plus;
    }

    public boolean is1_19_4Plus() {
        return is1_19_4Plus;
    }

    private boolean classExists(String path) {
        try {
            Class.forName(path);
            return true;
        } catch (ClassNotFoundException | NullPointerException e) {
            return false;
        }
    }
}