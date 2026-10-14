package net.phoenixvine.chronicles.common.util;

import net.minecraft.SharedConstants;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.util.datafix.fixes.References;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

public final class ItemData {

    private ItemData() {}

    @Nullable
    public static CompoundTag getTag(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data == null || data.isEmpty() ? null : data.copyTag();
    }

    @Nullable
    public static CompoundTag getTag(FluidStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data == null || data.isEmpty() ? null : data.copyTag();
    }

    public static net.minecraft.core.component.DataComponentPatch patchOf(@Nullable CompoundTag tag) {
        if (tag == null || tag.isEmpty()) return net.minecraft.core.component.DataComponentPatch.EMPTY;
        return net.minecraft.core.component.DataComponentPatch.builder()
                .set(DataComponents.CUSTOM_DATA, CustomData.of(tag.copy())).build();
    }

    public static boolean hasTag(ItemStack stack) {
        return getTag(stack) != null;
    }

    public static void setTag(ItemStack stack, @Nullable CompoundTag tag) {
        if (tag == null || tag.isEmpty()) {
            stack.remove(DataComponents.CUSTOM_DATA);
        } else {
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag.copy()));
        }
    }

    public static void edit(ItemStack stack, Consumer<CompoundTag> editor) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, editor);
    }

    public static void removeKey(ItemStack stack, String key) {
        if (stack.has(DataComponents.CUSTOM_DATA)) edit(stack, tag -> tag.remove(key));
    }

    // ---- reward tags -------------------------------------------------------------------------------------------
    // A reward's stored "nbt" is either a full component patch under COMPONENTS_KEY (current format), or a bare 1.20.1
    // NBT tag (old quest files) which is run through the vanilla data fixer when applied.

    private static final org.slf4j.Logger LOGGER = LogUtils.getLogger();
    private static final String COMPONENTS_KEY = "components";
    /** Data version of 1.20.1, the last version that stored item data as a bare NBT tag. */
    private static final int LEGACY_DATA_VERSION = 3465;

    /** The stack's whole component patch as a reward tag, or null when the stack has no extra data. */
    @Nullable
    public static CompoundTag capture(ItemStack stack) {
        if (stack.isEmpty() || registries() == null) return getTag(stack);
        CompoundTag patch = encodePatch(stack.getComponentsPatch());
        if (patch == null) return null;
        CompoundTag out = new CompoundTag();
        out.put(COMPONENTS_KEY, patch);
        return out;
    }

    /**
     * What an NBT filter is matched against: the stack's {@code custom_data} contents at the root, plus every
     * component under {@code components} (for example {@code {components:{"minecraft:enchantments":{levels:{...}}}}}).
     */
    @Nullable
    public static CompoundTag matchView(ItemStack stack) {
        return matchView(getTag(stack), stack.getComponentsPatch());
    }

    @Nullable
    public static CompoundTag matchView(FluidStack stack) {
        return matchView(getTag(stack), stack.getComponentsPatch());
    }

    @Nullable
    private static CompoundTag matchView(@Nullable CompoundTag custom, DataComponentPatch patch) {
        CompoundTag encoded = encodePatch(patch);
        if (encoded == null) return custom;
        CompoundTag view = custom == null ? new CompoundTag() : custom;
        view.put(COMPONENTS_KEY, encoded);
        return view;
    }

    @Nullable
    private static CompoundTag encodePatch(DataComponentPatch patch) {
        HolderLookup.Provider registries = registries();
        if (patch.isEmpty() || registries == null) return null;
        DataResult<Tag> encoded = DataComponentPatch.CODEC.encodeStart(
                registries.createSerializationContext(NbtOps.INSTANCE),
                patch);
        Tag tag = encoded.resultOrPartial(err -> LOGGER.warn("Couldn't encode item data: {}", err)).orElse(null);
        return tag instanceof CompoundTag compound && !compound.isEmpty() ? compound : null;
    }

    /** Applies a reward tag (current or legacy format) to the stack. */
    public static void applyReward(ItemStack stack, @Nullable CompoundTag tag) {
        if (tag == null || tag.isEmpty() || stack.isEmpty()) return;
        HolderLookup.Provider registries = registries();
        if (tag.contains(COMPONENTS_KEY, Tag.TAG_COMPOUND)) {
            if (registries == null) return;
            DataComponentPatch.CODEC.parse(registries.createSerializationContext(NbtOps.INSTANCE),
                    tag.getCompound(COMPONENTS_KEY))
                    .resultOrPartial(err -> LOGGER.warn("Couldn't read item data of a reward: {}", err))
                    .ifPresent(stack::applyComponents);
            return;
        }
        if (registries != null && applyLegacy(stack, tag, registries)) return;
        setTag(stack, tag);
    }

    private static boolean applyLegacy(ItemStack stack, CompoundTag legacy, HolderLookup.Provider registries) {
        try {
            CompoundTag old = new CompoundTag();
            old.putString("id",
                    net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
            old.putByte("Count", (byte) 1);
            old.put("tag", legacy.copy());
            Tag fixed = DataFixers.getDataFixer().update(References.ITEM_STACK, new Dynamic<>(NbtOps.INSTANCE, old),
                    LEGACY_DATA_VERSION, SharedConstants.getCurrentVersion().getDataVersion().getVersion()).getValue();
            if (!(fixed instanceof CompoundTag upgraded)) return false;
            ItemStack parsed = ItemStack.parse(registries, upgraded).orElse(ItemStack.EMPTY);
            if (parsed.isEmpty()) return false;
            stack.applyComponents(parsed.getComponentsPatch());
            return true;
        } catch (RuntimeException e) {
            LOGGER.warn("Couldn't upgrade legacy item data {}", legacy, e);
            return false;
        }
    }

    /** Registry access for the side we are on, or null when no world or server is available yet. */
    @Nullable
    private static HolderLookup.Provider registries() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) return server.registryAccess();
        return FMLEnvironment.dist == Dist.CLIENT ? ClientSide.registries() : null;
    }

    private static final class ClientSide {

        @Nullable
        static HolderLookup.Provider registries() {
            var level = net.minecraft.client.Minecraft.getInstance().level;
            return level == null ? null : level.registryAccess();
        }
    }
}
