package net.phoenixvine.chronicles.integration.gtceu;

import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.loading.LoadingModList;
import net.phoenixvine.chronicles.PhoenixChronicles;

import org.jetbrains.annotations.NotNull;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Single source of truth for "should the GregTech features be on, and for which GT API line?".
 * <p>
 * Chronicles supports the GTM 7 and GTM 8 lines. Being "some GTCEu" is not enough: forks and odd snapshots
 * change the internals the mixins target, and a mixin that cannot find its target crashes the game on load.
 * So the integration is only enabled when the installed GTCEu is recognised as 7 or 8 AND the exact classes
 * and methods our mixins inject into are really there. Anything else leaves every GT feature switched off.
 * <p>
 * Uses {@link LoadingModList} and reads class bytes straight from the mod file, so it is safe to call from the
 * mixin plugin (before {@code ModList} exists) as well as at runtime. This class must never import GTCEu classes.
 */
public final class GTCEuCompat {

    private GTCEuCompat() {}

    public static final String GTCEU_MOD_ID = "gtceu";

    private static final String BASE = "com/gregtechceu/gtceu/api/";

    private static Integer cachedLine;

    /** 8 for the GTM 8 line, 7 for GTM 7, or -1 when GTCEu is absent or not safely supported. */
    public static int apiLine() {
        Integer c = cachedLine;
        if (c == null) cachedLine = c = detect();
        return c;
    }

    /** True only when GTCEu is present, recognised, and every mixin target we need exists. */
    public static boolean isAvailable() {
        return apiLine() != -1;
    }

    public static boolean usesNewApi() {
        return apiLine() == 8;
    }

    /** True when some GTCEu is installed but we refuse to hook it (for diagnostics). */
    public static boolean isUnsupportedPresent() {
        LoadingModList list = LoadingModList.get();
        return list != null && list.getModFileById(GTCEU_MOD_ID) != null && !isAvailable();
    }

    private static int detect() {
        try {
            LoadingModList list = LoadingModList.get();
            if (list == null) return -1;
            var file = list.getModFileById(GTCEU_MOD_ID);
            if (file == null) return -1;
            var jar = file.getFile();

            Path marker = jar.findResource("com", "gregtechceu", "gtceu", "api", "multiblock",
                    "MultiblockWorldSavedData.class");
            int line = marker != null && Files.exists(marker) ? 8 : 7;

            Path multiblock = jar.findResource("com", "gregtechceu", "gtceu", "api", "machine", "multiblock",
                    "MultiblockControllerMachine.class");
            Path recipeLogic = line == 8 ?
                    jar.findResource("com", "gregtechceu", "gtceu", "api", "machine", "trait", "recipe",
                            "RecipeLogic.class") :
                    jar.findResource("com", "gregtechceu", "gtceu", "api", "machine", "trait", "RecipeLogic.class");

            if (!declares(multiblock, "onStructureFormed", "()V", null)) return -1;
            if (!declares(recipeLogic, "onRecipeFinish", "()V", "lastRecipe")) return -1;
            return line;
        } catch (Throwable t) {
            // Any failure reading the install means we can't vouch for it: stay disabled.
            return -1;
        }
    }

    /** Does the class file declare the given method (and field, if non-null)? */
    private static boolean declares(Path classFile, String method, String desc, String field) throws Exception {
        if (classFile == null || !Files.exists(classFile)) return false;
        ClassNode node = new ClassNode();
        new ClassReader(Files.readAllBytes(classFile)).accept(node, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG);
        boolean hasMethod = node.methods.stream().anyMatch(m -> m.name.equals(method) && m.desc.equals(desc));
        boolean hasField = field == null || node.fields.stream().anyMatch(f -> f.name.equals(field));
        return hasMethod && hasField;
    }

    public static void init(@NotNull IEventBus modEventBus) {
        String name = "net.phoenixvine.chronicles.integration.gtceu." + (usesNewApi() ? "GtmBootstrap8" :
                "GtmBootstrap7");
        try {
            ((GtmBootstrap) Class.forName(name).getDeclaredConstructor().newInstance()).init(modEventBus);
        } catch (ReflectiveOperationException | LinkageError e) {
            PhoenixChronicles.LOGGER.error("Could not start the GregTech integration ({}); GregTech features of " +
                    "Chronicles are disabled.", name, e);
        }
    }
}
