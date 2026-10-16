package net.phoenixvine.chronicles.client.screen.rewardform;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.phoenixvine.chronicles.common.model.QuestReward;
import net.phoenixvine.chronicles.common.registry.PhoenixRewardRegistry;
import net.phoenixvine.chronicles.common.registry.PhoenixTaskRegistry;
import net.phoenixvine.chronicles.common.util.ItemData;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

public final class RewardFormTypes {

    private static final Map<String, RewardFormType> BY_ID = new LinkedHashMap<>();
    private static final RewardFormType UNKNOWN = new RewardFormType("?", "?", "Choose a reward type", false, true,
            QuestReward.class);

    private RewardFormTypes() {}

    public static RewardFormType get(String id) {
        RewardFormType type = BY_ID.get(id);
        return type != null ? type : UNKNOWN;
    }

    @Nullable
    public static String idFor(QuestReward reward) {
        for (RewardFormType type : BY_ID.values()) {
            if (type.handles(reward)) return type.id();
        }
        return null;
    }

    public static String[] listedIds(boolean emergencyKit) {
        return BY_ID.values().stream()
                .filter(RewardFormType::listed)
                .filter(type -> !emergencyKit || type.allowedInEmergencyKit())
                .map(RewardFormType::id)
                .toArray(String[]::new);
    }

    private static void add(String id, String label, String tooltip, boolean listed, boolean allowedInEmergencyKit,
                            Class<? extends QuestReward> rewardClass) {
        BY_ID.put(id, new RewardFormType(id, label, tooltip, listed, allowedInEmergencyKit, rewardClass));
    }

    private static CompoundTag parseData(String text) {
        if (text == null || text.isBlank()) return new CompoundTag();
        try {
            return TagParser.parseTag(text.trim());
        } catch (Exception ignored) {
            return new CompoundTag();
        }
    }

    private static String fieldValueText(Tag value) {
        if (value instanceof NumericTag number) {
            return value instanceof ByteTag ? String.valueOf(number.getAsByte() != 0) :
                    String.valueOf(number.getAsNumber());
        }
        return value.getAsString();
    }

    @Nullable
    static QuestReward create(String id, RewardFormValues v) {
        return switch (id) {
            case "item" -> v.item == null ? null :
                    new QuestReward.ItemReward(v.item.getItem(), v.intCount(), ItemData.capture(v.item), v.pushAe2);
            case "xp" -> new QuestReward.XPReward(v.intCount());
            case "command" -> v.text.isEmpty() ? null : new QuestReward.CommandReward(v.text, v.permLevel);
            case "loot_table" -> v.text.isEmpty() ? null :
                    new QuestReward.LootTableReward(ResourceLocation.parse(v.text));
            case "script_event" -> v.text.isEmpty() ? null :
                    new QuestReward.ScriptEventReward(v.text, parseData(v.eventData));
            case "choice_box" -> v.boxOptions.isEmpty() ? null :
                    new QuestReward.ChoiceBoxReward(new ArrayList<>(v.boxOptions), v.boxMode);
            case "fluid" -> createFluid(v);
            case "open_screen" -> {
                ResourceLocation screenId = v.text.isEmpty() ? null : ResourceLocation.tryParse(v.text);
                yield screenId == null ? null : new QuestReward.OpenScreenReward(screenId);
            }
            case "quest_action" -> v.text.isEmpty() ? null : new QuestReward.QuestActionReward(v.text, v.questAction);
            case "external" -> createExternal(v);
            case "reward_table" -> v.text.isEmpty() ? null : new QuestReward.RewardTableReward(v.text);
            default -> null;
        };
    }

    @Nullable
    private static QuestReward createFluid(RewardFormValues v) {
        ResourceLocation id = v.text.isEmpty() ? null : ResourceLocation.tryParse(v.text);
        Fluid fluid = id != null ? BuiltInRegistries.FLUID.get(id) : null;
        return fluid == null || fluid == Fluids.EMPTY ? null :
                new QuestReward.FluidReward(fluid, v.intCount(), null, v.pushAe2);
    }

    @Nullable
    private static QuestReward createExternal(RewardFormValues v) {
        if (v.text.isEmpty()) return null;
        PhoenixRewardRegistry.Entry entry = PhoenixRewardRegistry.get(v.text);
        CompoundTag data = new CompoundTag();
        if (entry != null && !entry.fields().isEmpty()) {
            for (PhoenixTaskRegistry.FieldDef field : entry.fields()) {
                String value = v.externalFields.getOrDefault(field.id(), "").trim();
                if (value.isEmpty()) continue;
                switch (field.type()) {
                    case INTEGER -> {
                        try {
                            data.putInt(field.id(), Integer.parseInt(value));
                        } catch (NumberFormatException ignored) {}
                    }
                    case BOOLEAN -> data.putBoolean(field.id(), Boolean.parseBoolean(value));
                    default -> data.putString(field.id(), value);
                }
            }
        } else {
            data = parseData(v.eventData);
        }
        return new QuestReward.ExternalReward(v.text, data);
    }

    static void load(String id, QuestReward reward, RewardFormValues v) {
        switch (id) {
            case "item" -> {
                QuestReward.ItemReward r = (QuestReward.ItemReward) reward;
                v.pushAe2 = r.isPushToAe2();
                v.item = new ItemStack(r.getItem(), r.getCount());
                ItemData.applyReward(v.item, r.getNbt());
                v.count = r.getCount();
            }
            case "xp" -> v.count = ((QuestReward.XPReward) reward).getLevels();
            case "command" -> {
                QuestReward.CommandReward r = (QuestReward.CommandReward) reward;
                v.text = r.getCommand();
                v.permLevel = r.getPermissionLevel();
            }
            case "loot_table" -> v.text = ((QuestReward.LootTableReward) reward).getLootTableId().toString();
            case "script_event" -> {
                QuestReward.ScriptEventReward r = (QuestReward.ScriptEventReward) reward;
                v.text = r.getEventId();
                v.eventData = r.getData() != null && !r.getData().isEmpty() ? r.getData().toString() : "";
            }
            case "choice_box" -> {
                QuestReward.ChoiceBoxReward r = (QuestReward.ChoiceBoxReward) reward;
                v.boxMode = r.getMode();
                v.boxOptions.addAll(r.getOptions());
            }
            case "fluid" -> {
                QuestReward.FluidReward r = (QuestReward.FluidReward) reward;
                v.pushAe2 = r.isPushToAe2();
                ResourceLocation fluidId = BuiltInRegistries.FLUID.getKey(r.getFluid());
                v.text = fluidId != null ? fluidId.toString() : "";
                v.count = r.getAmountMb();
            }
            case "open_screen" -> {
                QuestReward.OpenScreenReward r = (QuestReward.OpenScreenReward) reward;
                v.text = r.getScreenId() != null ? r.getScreenId().toString() : "";
            }
            case "quest_action" -> {
                QuestReward.QuestActionReward r = (QuestReward.QuestActionReward) reward;
                v.text = r.getQuestId();
                v.questAction = r.getAction();
            }
            case "external" -> {
                QuestReward.ExternalReward r = (QuestReward.ExternalReward) reward;
                v.text = r.getTypeId();
                for (String key : r.getData().getAllKeys()) {
                    v.externalFields.put(key, fieldValueText(r.getData().get(key)));
                }
                v.eventData = r.getData().isEmpty() ? "" : r.getData().toString();
            }
            case "reward_table" -> v.text = ((QuestReward.RewardTableReward) reward).getTableId();
            default -> {}
        }
    }

    static {
        add("item", "Item", "Give the player one or more items", true, true, QuestReward.ItemReward.class);
        add("xp", "XP", "Award experience levels", true, true, QuestReward.XPReward.class);
        add("command", "Command", "Run a server command. Placeholders: {p}/%player%, {uuid}, {x} {y} {z}, {dim}", true,
                true, QuestReward.CommandReward.class);
        add("loot_table", "Loot Table", "Roll a loot table and give all resulting items", true, true,
                QuestReward.LootTableReward.class);
        add("script_event", "Script Event", "Fire a Forge event for KubeJS or Java handlers", true, true,
                QuestReward.ScriptEventReward.class);
        add("choice_box", "Choice Box",
                "A single slot the player resolves themselves - Menu mode lets them pick one of the options below, " +
                        "Lootbox mode grants a random one instantly",
                true, false, QuestReward.ChoiceBoxReward.class);
        add("fluid", "Fluid", "Give the player a fluid (delivered to their linked AE2 network; buckets otherwise)",
                true, true, QuestReward.FluidReward.class);
        add("open_screen", "Open Screen",
                "Opens a registered external screen for the player when they claim this reward", true, true,
                QuestReward.OpenScreenReward.class);
        add("quest_action", "Quest Action",
                "Complete or reset another quest for the player (never the quest being claimed)", true, true,
                QuestReward.QuestActionReward.class);
        add("external", "External",
                "A reward type another mod or script registered (PhoenixRewardRegistry); its handler receives the " +
                        "player and the data below",
                true, true, QuestReward.ExternalReward.class);
        add("reward_table", "Reward Table", "Reference a named reward table (config/phoenix_chronicles/reward_tables/)",
                false, true, QuestReward.RewardTableReward.class);
    }
}
