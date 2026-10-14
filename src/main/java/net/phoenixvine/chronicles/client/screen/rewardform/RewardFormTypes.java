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

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

public final class RewardFormTypes {

    private static final Map<String, RewardFormType<?>> BY_ID = new LinkedHashMap<>();
    private static final RewardFormType<?> UNKNOWN = new RewardFormType<>("?", "?", "Choose a reward type", false,
            true, QuestReward.class, v -> null, (r, v) -> {});

    private RewardFormTypes() {}

    public static RewardFormType<?> get(String id) {
        RewardFormType<?> type = BY_ID.get(id);
        return type != null ? type : UNKNOWN;
    }

    @Nullable
    public static String idFor(QuestReward reward) {
        for (RewardFormType<?> type : BY_ID.values()) {
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

    private static <T extends QuestReward> void add(String id, String label, String tooltip, boolean listed,
                                                    boolean allowedInEmergencyKit, Class<T> rewardClass,
                                                    RewardFormType.Factory factory,
                                                    java.util.function.BiConsumer<T, RewardFormValues> loader) {
        BY_ID.put(id, new RewardFormType<>(id, label, tooltip, listed, allowedInEmergencyKit, rewardClass, factory,
                loader));
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

    static {
        add("item", "Item", "Give the player one or more items", true, true, QuestReward.ItemReward.class,
                v -> v.item == null ? null :
                        new QuestReward.ItemReward(v.item.getItem(), v.intCount(),
                                v.item.hasTag() ? v.item.getTag().copy() : null, v.pushAe2),
                (r, v) -> {
                    v.pushAe2 = r.isPushToAe2();
                    v.item = new ItemStack(r.getItem(), r.getCount());
                    if (r.getNbt() != null) v.item.setTag(r.getNbt().copy());
                    v.count = r.getCount();
                });

        add("xp", "XP", "Award experience levels", true, true, QuestReward.XPReward.class,
                v -> new QuestReward.XPReward(v.intCount()),
                (r, v) -> v.count = r.getLevels());

        add("command", "Command", "Run a server command. Placeholders: {p}/%player%, {uuid}, {x} {y} {z}, {dim}", true,
                true, QuestReward.CommandReward.class,
                v -> v.text.isEmpty() ? null : new QuestReward.CommandReward(v.text, v.permLevel),
                (r, v) -> {
                    v.text = r.getCommand();
                    v.permLevel = r.getPermissionLevel();
                });

        add("loot_table", "Loot Table", "Roll a loot table and give all resulting items", true, true,
                QuestReward.LootTableReward.class,
                v -> v.text.isEmpty() ? null : new QuestReward.LootTableReward(new ResourceLocation(v.text)),
                (r, v) -> v.text = r.getLootTableId().toString());

        add("script_event", "Script Event", "Fire a Forge event for KubeJS or Java handlers", true, true,
                QuestReward.ScriptEventReward.class,
                v -> v.text.isEmpty() ? null : new QuestReward.ScriptEventReward(v.text, parseData(v.eventData)),
                (r, v) -> {
                    v.text = r.getEventId();
                    v.eventData = r.getData() != null && !r.getData().isEmpty() ? r.getData().toString() : "";
                });

        add("choice_box", "Choice Box",
                "A single slot the player resolves themselves - Menu mode lets them pick one of the options below, " +
                        "Lootbox mode grants a random one instantly",
                true, false, QuestReward.ChoiceBoxReward.class,
                v -> v.boxOptions.isEmpty() ? null : new QuestReward.ChoiceBoxReward(new ArrayList<>(v.boxOptions),
                        v.boxMode),
                (r, v) -> {
                    v.boxMode = r.getMode();
                    v.boxOptions.addAll(r.getOptions());
                });

        add("fluid", "Fluid", "Give the player a fluid (delivered to their linked AE2 network; buckets otherwise)",
                true, true, QuestReward.FluidReward.class,
                v -> {
                    ResourceLocation id = v.text.isEmpty() ? null : ResourceLocation.tryParse(v.text);
                    Fluid fluid = id != null ? BuiltInRegistries.FLUID.get(id) : null;
                    return fluid == null || fluid == Fluids.EMPTY ? null :
                            new QuestReward.FluidReward(fluid, v.intCount(), null, v.pushAe2);
                },
                (r, v) -> {
                    v.pushAe2 = r.isPushToAe2();
                    ResourceLocation id = BuiltInRegistries.FLUID.getKey(r.getFluid());
                    v.text = id != null ? id.toString() : "";
                    v.count = r.getAmountMb();
                });

        add("open_screen", "Open Screen",
                "Opens a registered external screen for the player when they claim this reward", true, true,
                QuestReward.OpenScreenReward.class,
                v -> {
                    ResourceLocation id = v.text.isEmpty() ? null : ResourceLocation.tryParse(v.text);
                    return id == null ? null : new QuestReward.OpenScreenReward(id);
                },
                (r, v) -> v.text = r.getScreenId() != null ? r.getScreenId().toString() : "");

        add("quest_action", "Quest Action",
                "Complete or reset another quest for the player (never the quest being claimed)", true, true,
                QuestReward.QuestActionReward.class,
                v -> v.text.isEmpty() ? null : new QuestReward.QuestActionReward(v.text, v.questAction),
                (r, v) -> {
                    v.text = r.getQuestId();
                    v.questAction = r.getAction();
                });

        add("external", "External",
                "A reward type another mod or script registered (PhoenixRewardRegistry); its handler receives the " +
                        "player and the data below",
                true, true, QuestReward.ExternalReward.class,
                v -> {
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
                },
                (r, v) -> {
                    v.text = r.getTypeId();
                    for (String key : r.getData().getAllKeys()) {
                        v.externalFields.put(key, fieldValueText(r.getData().get(key)));
                    }
                    v.eventData = r.getData().isEmpty() ? "" : r.getData().toString();
                });

        add("reward_table", "Reward Table", "Reference a named reward table (config/phoenix_chronicles/reward_tables/)",
                false, true, QuestReward.RewardTableReward.class,
                v -> v.text.isEmpty() ? null : new QuestReward.RewardTableReward(v.text),
                (r, v) -> v.text = r.getTableId());
    }
}
