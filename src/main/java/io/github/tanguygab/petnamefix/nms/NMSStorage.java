package io.github.tanguygab.petnamefix.nms;

import io.github.tanguygab.petnamefix.PetNameFix;
import io.netty.channel.Channel;
import org.bukkit.Bukkit;

import java.lang.reflect.*;
import java.util.*;

public class NMSStorage {

    //instance of this class
    private static NMSStorage instance;

    //server package, such as "v1_16_R3"
    private final String serverPackage = Bukkit.getServer().getClass().getPackage().getName().split("\\.")[3];

    //server minor version such as "16"
    private final int minorVersion = Integer.parseInt(serverPackage.split("_")[1]);

    private final boolean is1_19_3Plus = classExists("net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket");
    private final boolean is1_19_4Plus = is1_19_3Plus && !serverPackage.equals("v1_19_R2");

    //base
    private final Class<?> EntityPlayer = getNMSClass("net.minecraft.server.level.EntityPlayer", "EntityPlayer");
    private final Class<?> PlayerConnection = getNMSClass("net.minecraft.server.network.PlayerConnection", "PlayerConnection");
    public final Field PLAYER_CONNECTION = getFields(EntityPlayer, PlayerConnection).get(0);
    public Field NETWORK_MANAGER;
    public Field CHANNEL;
    public final Method getHandle = Class.forName("org.bukkit.craftbukkit." + serverPackage + ".entity.CraftPlayer").getMethod("getHandle");

    //DataWatcher
    private final Class<?> DataWatcher = getNMSClass("net.minecraft.network.syncher.DataWatcher", "DataWatcher");
    private final Class<?> DataWatcherItem = getNMSClass("net.minecraft.network.syncher.DataWatcher$Item", "DataWatcher$Item", "DataWatcher$WatchableObject", "WatchableObject");
    public Class<?> DataWatcherRegistry;
    public final Constructor<?> newDataWatcher = DataWatcher.getConstructors()[0];
    public Constructor<?> newDataWatcherObject;
    public Field DataWatcherItem_TYPE;
    public final Field DataWatcherItem_VALUE = getFields(DataWatcherItem, Object.class).get(0);
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
    public final Class<?> PacketPlayOutSpawnEntityLiving = getNMSClass("net.minecraft.network.protocol.game.PacketPlayOutSpawnEntityLiving",
            "net.minecraft.network.protocol.game.PacketPlayOutSpawnEntity", "PacketPlayOutSpawnEntityLiving", "Packet24MobSpawn");

    public Field PacketPlayOutSpawnEntityLiving_DATAWATCHER;

    //other entity packets
    public final Class<?> PacketPlayOutEntityMetadata = getNMSClass("net.minecraft.network.protocol.game.PacketPlayOutEntityMetadata", "PacketPlayOutEntityMetadata", "Packet40EntityMetadata");
    public final Field PacketPlayOutEntityMetadata_LIST = getFields(PacketPlayOutEntityMetadata, List.class).get(0);

    public Class<?> ClientboundBundlePacket;
    public Field ClientboundBundlePacket_packets;

    /**
     * Creates new instance, initializes required NMS classes and fields
     * @throws	ReflectiveOperationException
     * 			If any class, field or method fails to load
     */
    public NMSStorage() throws ReflectiveOperationException {
        if (minorVersion < 9) return;
        Class<?> NetworkManager = getNMSClass("net.minecraft.network.NetworkManager", "NetworkManager");
        NETWORK_MANAGER = getFields(PlayerConnection, NetworkManager).get(0);
        CHANNEL = getFields(NetworkManager, Channel.class).get(0);

        initializeDataWatcher();
        if (minorVersion <= 14) PacketPlayOutSpawnEntityLiving_DATAWATCHER = getFields(PacketPlayOutSpawnEntityLiving, DataWatcher).get(0);
        if (is1_19_4Plus) {
            ClientboundBundlePacket = getNMSClass("net.minecraft.network.protocol.game.ClientboundBundlePacket");
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
        Class<?> DataWatcherObject = getNMSClass("net.minecraft.network.syncher.DataWatcherObject", "DataWatcherObject");
        DataWatcherRegistry = getNMSClass("net.minecraft.network.syncher.DataWatcherRegistry", "DataWatcherRegistry");
        Class<?> DataWatcherSerializer = getNMSClass("net.minecraft.network.syncher.DataWatcherSerializer", "DataWatcherSerializer");
        newDataWatcherObject = DataWatcherObject.getConstructor(int.class, DataWatcherSerializer);
        DataWatcherItem_TYPE = getFields(DataWatcherItem, DataWatcherObject).get(0);
        DataWatcherObject_SLOT = getFields(DataWatcherObject, int.class).get(0);
        DataWatcherObject_SERIALIZER = getFields(DataWatcherObject, DataWatcherSerializer).get(0);
        DataWatcher_REGISTER = getMethod(DataWatcher, new String[]{"register", "method_12784", "a"}, DataWatcherObject, Object.class);
        if (!is1_19_3Plus()) return;
        DataWatcher$DataValue = Class.forName("net.minecraft.network.syncher.DataWatcher$b");
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
    private Class<?> getNMSClass(String... names) throws ClassNotFoundException {
        for (String name : names)
            try {return minorVersion >= 17 ? Class.forName(name) : getLegacyClass(name);}
            catch (ClassNotFoundException e) {/*not the first class name in array*/}
        throw new ClassNotFoundException("No class found with possible names " + Arrays.toString(names));
    }

    /**
     * Returns class from given name
     * @param name - class name
     * @return class from given name
     * @throws ClassNotFoundException if class was not found
     */
    private Class<?> getLegacyClass(String name) throws ClassNotFoundException {
        try {
            return Class.forName("net.minecraft.server." + serverPackage + "." + name);
        } catch (ClassNotFoundException | NullPointerException e) {
            try {
                //modded server?
                Class<?> clazz = PetNameFix.class.getClassLoader().loadClass("net.minecraft.server." + serverPackage + "." + name);
                if (clazz != null) return clazz;
                throw new ClassNotFoundException(name);
            } catch (ClassNotFoundException | NullPointerException e1) {
                //maybe fabric?
                return Class.forName(name);
            }
        }
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