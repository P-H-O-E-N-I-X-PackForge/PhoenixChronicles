package net.phoenixvine.chronicles.client.registry;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.forgespi.language.ModFileScanData;
import net.phoenixvine.chronicles.PhoenixChronicles;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;

public final class ScreenAutoRegistrar {

    private static final String SCREEN_CLASS = Screen.class.getName();
    private static final String NAMESPACE = "screen";

    private ScreenAutoRegistrar() {}

    public static void registerAll() {
        Map<String, String> parentOf = collectParents();
        Map<String, Boolean> isScreenCache = new HashMap<>();
        int registered = 0;

        for (String className : parentOf.keySet()) {
            if (className.contains("$") || className.contains("mixin")) continue;
            if (!extendsScreen(className, parentOf, isScreenCache)) continue;

            Class<?> clazz = loadClass(className);
            if (clazz == null || Modifier.isAbstract(clazz.getModifiers()) || !Modifier.isPublic(clazz.getModifiers()))
                continue;
            if (!hasUsableConstructor(clazz)) continue;

            ResourceLocation id = idFor(className);
            if (id == null || ExternalScreenRegistry.isRegistered(id)) continue;
            ExternalScreenRegistry.register(id, node -> create(clazz));
            registered++;
        }
        PhoenixChronicles.LOGGER.info("Auto-registered {} screens for quest screen pickers", registered);
    }

    private static Map<String, String> collectParents() {
        Map<String, String> parentOf = new HashMap<>();
        for (ModFileScanData data : ModList.get().getAllScanData()) {
            for (ModFileScanData.ClassData classData : data.getClasses()) {
                if (classData.parent() != null) {
                    parentOf.put(classData.clazz().getClassName(), classData.parent().getClassName());
                }
            }
        }
        return parentOf;
    }

    private static boolean extendsScreen(String className, Map<String, String> parentOf,
                                         Map<String, Boolean> cache) {
        Boolean cached = cache.get(className);
        if (cached != null) return cached;

        boolean result;
        if (className.equals(SCREEN_CLASS)) {
            result = true;
        } else if (parentOf.containsKey(className)) {
            result = extendsScreen(parentOf.get(className), parentOf, cache);
        } else {
            Class<?> clazz = loadClass(className);
            result = clazz != null && Screen.class.isAssignableFrom(clazz);
        }
        cache.put(className, result);
        return result;
    }

    private static Class<?> loadClass(String className) {
        try {
            return Class.forName(className, false, ScreenAutoRegistrar.class.getClassLoader());
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static boolean hasUsableConstructor(Class<?> clazz) {
        try {
            for (Constructor<?> constructor : clazz.getConstructors()) {
                Class<?>[] params = constructor.getParameterTypes();
                if (params.length == 0 || (params.length == 1 && params[0] == Screen.class)) return true;
            }
        } catch (Throwable ignored) {}
        return false;
    }

    private static ResourceLocation idFor(String className) {
        String path = className.toLowerCase().replace('.', '/');
        return ResourceLocation.tryParse(NAMESPACE + ":" + path);
    }

    private static Screen create(Class<?> clazz) {
        try {
            for (Constructor<?> constructor : clazz.getConstructors()) {
                if (constructor.getParameterCount() == 0) return (Screen) constructor.newInstance();
            }
            Screen parent = Minecraft.getInstance().screen;
            return (Screen) clazz.getConstructor(Screen.class).newInstance(parent);
        } catch (Throwable e) {
            PhoenixChronicles.LOGGER.warn("Could not open auto-registered screen {}", clazz.getName(), e);
            return null;
        }
    }
}
