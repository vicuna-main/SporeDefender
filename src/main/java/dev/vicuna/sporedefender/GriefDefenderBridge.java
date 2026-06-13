package dev.vicuna.sporedefender;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

final class GriefDefenderBridge {
    private static final long INITIALIZE_RETRY_DELAY_MILLIS = 5_000L;
    private static final ConcurrentMap<ResourceLocation, UUID> WORLD_UUIDS = new ConcurrentHashMap<>();
    private static final Set<String> WARNED_MESSAGES = ConcurrentHashMap.newKeySet();

    private static volatile boolean initialized;
    private static volatile long nextInitializeAttemptMillis;
    private static volatile ClassLoader griefDefenderClassLoader;
    private static Method getCoreMethod;
    private static Method getClaimAtObjectMethod;
    private static Method getClaimAtUuidMethod;
    private static Method isWildernessMethod;
    private static Method getWorldUniqueIdMethod;

    private GriefDefenderBridge() {
    }

    static boolean isProtected(ServerLevel level, BlockPos pos) {
        Object claim = findClaim(level, pos);
        if (claim == null) {
            return false;
        }

        try {
            Object result = isWildernessMethod.invoke(claim);
            return !(result instanceof Boolean wilderness) || !wilderness;
        } catch (ReflectiveOperationException exception) {
            SporeDefender.LOGGER.warn("Could not read GriefDefender claim wilderness state; treating claim as protected.", exception);
            return true;
        }
    }

    private static Object findClaim(ServerLevel level, BlockPos pos) {
        if (!initialize()) {
            return null;
        }

        try {
            Object core = getCoreMethod.invoke(null);
            Object bukkitLocation = createBukkitLocation(level, pos);
            if (bukkitLocation != null) {
                Object claim = getClaimAtObjectMethod.invoke(core, bukkitLocation);
                if (claim != null) {
                    return claim;
                }
            }

            UUID worldId = worldUuid(core, level);
            if (worldId != null) {
                return getClaimAtUuidMethod.invoke(core, worldId, pos.getX(), pos.getY(), pos.getZ());
            }
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof IllegalStateException) {
                scheduleInitializeRetry();
                logUnavailableOnce("GriefDefender API is present but not initialized yet.");
            } else {
                logUnavailableOnce("Could not query GriefDefender claims: " + message(cause, exception));
            }
        } catch (IllegalStateException exception) {
            scheduleInitializeRetry();
            logUnavailableOnce("GriefDefender API is present but not initialized yet.");
        } catch (ReflectiveOperationException exception) {
            logUnavailableOnce("Could not query GriefDefender claims: " + exception.getMessage());
        }

        return null;
    }

    private static boolean initialize() {
        if (initialized) {
            return true;
        }

        long now = System.currentTimeMillis();
        if (now < nextInitializeAttemptMillis) {
            return false;
        }

        synchronized (GriefDefenderBridge.class) {
            if (initialized) {
                return true;
            }

            now = System.currentTimeMillis();
            if (now < nextInitializeAttemptMillis) {
                return false;
            }

            try {
                Class<?> griefDefender = findClass("com.griefdefender.api.GriefDefender");
                Class<?> core = findClass("com.griefdefender.api.Core");
                Class<?> claim = findClass("com.griefdefender.api.claim.Claim");

                getCoreMethod = griefDefender.getMethod("getCore");
                getClaimAtObjectMethod = core.getMethod("getClaimAt", Object.class);
                getClaimAtUuidMethod = core.getMethod("getClaimAt", UUID.class, int.class, int.class, int.class);
                getWorldUniqueIdMethod = core.getMethod("getWorldUniqueId", Object.class);
                isWildernessMethod = claim.getMethod("isWilderness");
                initialized = true;
                WARNED_MESSAGES.clear();
                SporeDefender.LOGGER.info("Connected to GriefDefender API through {}.", classLoaderName(griefDefender));
            } catch (ClassNotFoundException | NoSuchMethodException exception) {
                resetApiMethods();
                nextInitializeAttemptMillis = now + INITIALIZE_RETRY_DELAY_MILLIS;
                logUnavailableOnce("GriefDefender API classes are not visible to Spore Defender yet; claim protection will retry.");
            } catch (LinkageError error) {
                resetApiMethods();
                nextInitializeAttemptMillis = now + INITIALIZE_RETRY_DELAY_MILLIS;
                logUnavailableOnce("Could not load GriefDefender API classes: " + error.getMessage());
            }
        }

        return initialized;
    }

    private static Class<?> findClass(String name) throws ClassNotFoundException {
        if (name.startsWith("com.griefdefender.")) {
            ClassLoader pluginLoader = griefDefenderPluginClassLoader();
            if (pluginLoader != null) {
                try {
                    return Class.forName(name, false, pluginLoader);
                } catch (ClassNotFoundException ignored) {
                    // Fall through to the normal class loader chain.
                }
            }
        }

        try {
            return findNonPluginClass(name);
        } catch (ClassNotFoundException exception) {
            ClassLoader pluginLoader = griefDefenderPluginClassLoader();
            if (pluginLoader != null) {
                try {
                    return Class.forName(name, false, pluginLoader);
                } catch (ClassNotFoundException ignored) {
                    // Report the original failure because it reflects the normal lookup path.
                }
            }
            throw exception;
        }
    }

    private static Class<?> findNonPluginClass(String name) throws ClassNotFoundException {
        ClassNotFoundException failure = null;

        ClassLoader context = Thread.currentThread().getContextClassLoader();
        if (context != null) {
            try {
                return Class.forName(name, false, context);
            } catch (ClassNotFoundException exception) {
                failure = exception;
            }
        }

        ClassLoader ownLoader = GriefDefenderBridge.class.getClassLoader();
        if (ownLoader != null && ownLoader != context) {
            try {
                return Class.forName(name, false, ownLoader);
            } catch (ClassNotFoundException exception) {
                failure = exception;
            }
        }

        try {
            return Class.forName(name);
        } catch (ClassNotFoundException exception) {
            if (failure != null) {
                throw failure;
            }
            throw exception;
        }
    }

    private static ClassLoader griefDefenderPluginClassLoader() {
        ClassLoader cached = griefDefenderClassLoader;
        if (cached != null) {
            return cached;
        }

        try {
            Class<?> bukkit = findNonPluginClass("org.bukkit.Bukkit");
            Object pluginManager = bukkit.getMethod("getPluginManager").invoke(null);
            if (pluginManager == null) {
                return null;
            }

            Object plugin = pluginManager.getClass().getMethod("getPlugin", String.class).invoke(pluginManager, "GriefDefender");
            if (plugin == null) {
                return null;
            }

            ClassLoader loader = plugin.getClass().getClassLoader();
            if (loader != null) {
                griefDefenderClassLoader = loader;
            }
            return loader;
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return null;
        }
    }

    private static void resetApiMethods() {
        getCoreMethod = null;
        getClaimAtObjectMethod = null;
        getClaimAtUuidMethod = null;
        isWildernessMethod = null;
        getWorldUniqueIdMethod = null;
    }

    private static void scheduleInitializeRetry() {
        synchronized (GriefDefenderBridge.class) {
            initialized = false;
            resetApiMethods();
            nextInitializeAttemptMillis = System.currentTimeMillis() + INITIALIZE_RETRY_DELAY_MILLIS;
        }
    }

    private static String classLoaderName(Class<?> type) {
        ClassLoader loader = type.getClassLoader();
        if (loader == null) {
            return "bootstrap class loader";
        }
        return loader.getClass().getName();
    }

    private static String message(Throwable cause, Exception fallback) {
        if (cause == null) {
            return fallback.getMessage();
        }
        String message = cause.getMessage();
        if (message == null || message.isBlank()) {
            return cause.getClass().getName();
        }
        return message;
    }

    private static UUID worldUuid(Object core, ServerLevel level) {
        ResourceLocation dimension = level.dimension().location();
        UUID cached = WORLD_UUIDS.get(dimension);
        if (cached != null) {
            return cached;
        }

        UUID resolved = uuidFromBukkitWorld(level);
        if (resolved == null) {
            resolved = uuidFromGriefDefender(core, level);
        }

        if (resolved != null) {
            WORLD_UUIDS.put(dimension, resolved);
        }

        return resolved;
    }

    private static UUID uuidFromGriefDefender(Object core, ServerLevel level) {
        try {
            Object id = getWorldUniqueIdMethod.invoke(core, level);
            return id instanceof UUID uuid ? uuid : null;
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static UUID uuidFromBukkitWorld(ServerLevel level) {
        Object world = findBukkitWorld(level);
        if (world == null) {
            return null;
        }

        try {
            Object id = world.getClass().getMethod("getUID").invoke(world);
            return id instanceof UUID uuid ? uuid : null;
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static Object createBukkitLocation(ServerLevel level, BlockPos pos) {
        Object world = findBukkitWorld(level);
        if (world == null) {
            return null;
        }

        try {
            Class<?> worldClass = findClass("org.bukkit.World");
            Class<?> locationClass = findClass("org.bukkit.Location");
            Constructor<?> constructor = locationClass.getConstructor(worldClass, double.class, double.class, double.class);
            return constructor.newInstance(world, (double) pos.getX(), (double) pos.getY(), (double) pos.getZ());
        } catch (ReflectiveOperationException exception) {
            return null;
        }
    }

    private static Object findBukkitWorld(ServerLevel level) {
        Object direct = invokeNoArg(level, "getWorld");
        if (isBukkitWorld(direct)) {
            return direct;
        }

        Object bukkitWorld = invokeNoArg(level, "getBukkitWorld");
        if (isBukkitWorld(bukkitWorld)) {
            return bukkitWorld;
        }

        try {
            Class<?> bukkit = findClass("org.bukkit.Bukkit");
            Method getWorld = bukkit.getMethod("getWorld", String.class);
            for (String name : candidateWorldNames(level)) {
                try {
                    Object world = getWorld.invoke(null, name);
                    if (world != null) {
                        return world;
                    }
                } catch (ReflectiveOperationException ignored) {
                    // Try the next likely world name.
                }
            }
        } catch (ReflectiveOperationException ignored) {
            return null;
        }

        return null;
    }

    private static Object invokeNoArg(Object target, String methodName) {
        try {
            return target.getClass().getMethod(methodName).invoke(target);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static boolean isBukkitWorld(Object value) {
        return value != null && isTypeNamed(value.getClass(), "org.bukkit.World");
    }

    private static boolean isTypeNamed(Class<?> type, String name) {
        if (type == null) {
            return false;
        }
        if (name.equals(type.getName())) {
            return true;
        }
        for (Class<?> iface : type.getInterfaces()) {
            if (isTypeNamed(iface, name)) {
                return true;
            }
        }
        return isTypeNamed(type.getSuperclass(), name);
    }

    private static List<String> candidateWorldNames(ServerLevel level) {
        ResourceLocation dimension = level.dimension().location();
        String path = dimension.getPath();
        String levelName = level.getServer().getWorldData().getLevelName();

        List<String> names = new ArrayList<>();
        names.add(dimension.toString());
        names.add(path);

        if ("minecraft:overworld".equals(dimension.toString())) {
            names.add(levelName);
            names.add("world");
        } else if ("minecraft:the_nether".equals(dimension.toString())) {
            names.add(levelName + "_nether");
            names.add("world_nether");
        } else if ("minecraft:the_end".equals(dimension.toString())) {
            names.add(levelName + "_the_end");
            names.add("world_the_end");
        }

        return names.stream().distinct().toList();
    }

    private static void logUnavailableOnce(String message) {
        if (WARNED_MESSAGES.add(message)) {
            SporeDefender.LOGGER.warn(message);
        }
    }
}
