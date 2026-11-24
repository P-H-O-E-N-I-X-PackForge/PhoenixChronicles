package net.phoenixvine.chronicles.integration.gtceu;

import net.neoforged.fml.loading.LoadingModList;
import net.neoforged.neoforgespi.language.IModInfo;

import org.apache.maven.artifact.versioning.ArtifactVersion;

/**
 * Single source of truth for "should the GregTech features be on?".
 * <p>
 * Chronicles' GT hooks (multiblock-formed mixin, recipe-finished mixin, EU energy checks, the
 * {@code gt_multiblock} task) are only built and tested against the GTCEu 8.x snapshot line. Forks and
 * older builds change the targeted internals and can crash on load, so everything GT-related is gated on
 * this check instead of just "gtceu is installed".
 * <p>
 * Uses {@link LoadingModList} so it is safe to call from the mixin plugin (before {@code ModList} exists)
 * as well as at runtime. This class must never import GTCEu classes.
 */
public final class GTCEuCompat {

    private GTCEuCompat() {}

    public static final String GTCEU_MOD_ID = "gtceu";

    /** Minimum GTCEu major version the integration supports. */
    public static final int MIN_MAJOR = 8;

    private static Boolean cached;

    /** True only when GTCEu is present AND is the supported 8.x line. */
    public static boolean isAvailable() {
        Boolean c = cached;
        if (c == null) cached = c = detect();
        return c;
    }

    /** True when some GTCEu is installed but it is not the supported version (for diagnostics). */
    public static boolean isUnsupportedPresent() {
        LoadingModList list = LoadingModList.get();
        return list != null && list.getModFileById(GTCEU_MOD_ID) != null && !isAvailable();
    }

    private static boolean detect() {
        try {
            LoadingModList list = LoadingModList.get();
            if (list == null || list.getModFileById(GTCEU_MOD_ID) == null) return false;
            for (IModInfo mod : list.getModFileById(GTCEU_MOD_ID).getMods()) {
                if (!GTCEU_MOD_ID.equals(mod.getModId())) continue;
                ArtifactVersion v = mod.getVersion();
                return v != null && v.getMajorVersion() >= MIN_MAJOR;
            }
        } catch (Throwable ignored) {
            // Any failure reading the version means we can't vouch for the install: stay disabled.
        }
        return false;
    }
}
