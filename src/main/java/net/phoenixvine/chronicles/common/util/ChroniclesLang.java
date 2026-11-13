package net.phoenixvine.chronicles.common.util;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

public final class ChroniclesLang {

    private ChroniclesLang() {}

    public static MutableComponent tr(String key, Object... args) {
        Object[] safe = new Object[args.length];
        for (int i = 0; i < args.length; i++) {
            Object arg = args[i];
            boolean allowed = arg instanceof Component || arg instanceof Number || arg instanceof Boolean ||
                    arg instanceof String;
            safe[i] = allowed ? arg : String.valueOf(arg);
        }
        return Component.translatable(key, safe);
    }
}
