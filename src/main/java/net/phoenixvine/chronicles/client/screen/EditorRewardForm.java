package net.phoenixvine.chronicles.client.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.phoenixvine.chronicles.client.render.ChroniclesUIKit;
import net.phoenixvine.chronicles.client.screen.rewardform.RewardFormTypes;
import net.phoenixvine.chronicles.client.screen.rewardform.RewardFormValues;
import net.phoenixvine.chronicles.common.model.QuestReward;
import net.phoenixvine.chronicles.common.model.RewardTable;
import net.phoenixvine.chronicles.common.registry.PhoenixRewardRegistry;
import net.phoenixvine.chronicles.common.registry.PhoenixTaskRegistry;
import net.phoenixvine.chronicles.common.tasks.*;
import net.phoenixvine.chronicles.common.util.ItemData;
import net.phoenixvine.chronicles.integration.ae2.AE2Compat;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class EditorRewardForm {

    private final TaskRewardEditorScreen screen;

    EditorRewardForm(TaskRewardEditorScreen screen) {
        this.screen = screen;
    }

    void buildRewardForm(String rCountVal, String rCommandVal, String rEventDataVal, String boCountVal,
                         String boNbtVal, String boTableVal) {
        int rx = screen.splitX;
        int rfy = screen.formTop + 8;

        String rewardTypeTooltip = RewardFormTypes.get(screen.rewardType).tooltip();
        screen.addWidgetBridge(Button.builder(
                ChroniclesUIKit.lit("§8Type: §7" + RewardFormTypes.get(screen.rewardType).label() + " §8▾"),
                b -> {
                    screen.rewardTypeDropOpen = !screen.rewardTypeDropOpen;
                    screen.taskTypeDropOpen = false;
                })
                .bounds(rx, rfy, screen.colW, TaskRewardEditorScreen.FIELD_H)
                .tooltip(Tooltip.create(ChroniclesUIKit.lit(rewardTypeTooltip))).build());
        rfy += TaskRewardEditorScreen.FIELD_H + TaskRewardEditorScreen.FIELD_GAP;

        boolean editingBoxOption = screen.rewardType.equals("choice_box") && screen.editingBoxOptionIndex >= 0;
        boolean editingTableOption = editingBoxOption && screen.editingBoxOptionIndex < screen.boxOptions.size() &&
                screen.boxOptions.get(screen.editingBoxOptionIndex) instanceof QuestReward.RewardTableReward;
        boolean editingItemOption = editingBoxOption && !editingTableOption;

        if (screen.rewardType.equals("item")) {
            buildItemRewardInputs(rx, rfy, rCountVal);
        } else if (screen.rewardType.equals("fluid")) {
            buildFluidRewardInputs(rx, rfy, rCountVal, rCommandVal);
        } else if (screen.rewardType.equals("xp")) {
            buildXpRewardInputs(rx, rfy, rCountVal);
        } else if (screen.rewardType.equals("script_event")) {
            buildScriptEventRewardInputs(rx, rfy, rCommandVal, rEventDataVal);
        } else if (screen.rewardType.equals("reward_table")) {
            buildRewardTableInputs(rx, rfy, rCommandVal);
        } else if (editingTableOption) {
            buildTableOptionEditor(rx, rfy, boTableVal);
        } else if (editingItemOption) {
            buildItemOptionEditor(rx, rfy, boCountVal, boNbtVal);
        } else if (screen.rewardType.equals("choice_box")) {
            buildChoiceBoxInputs(rx, rfy);
        } else if (screen.rewardType.equals("external")) {
            buildExternalRewardInputs(rx, rfy, rCommandVal, rEventDataVal);
        } else if (screen.rewardType.equals("open_screen")) {
            buildOpenScreenInputs(rx, rfy, rCommandVal);
        } else {
            buildTextRewardInputs(rx, rfy, rCommandVal);
        }

        screen.addWidgetBridge(Button.builder(screen.hideLabel(rewardHiddenParts), b -> {
            rewardHiddenParts = (rewardHiddenParts + 1) % 4;
            screen.rebuildWidgets();
        }).bounds(rx + screen.colW - 80 - 4 - 62, screen.formBottom - TaskRewardEditorScreen.FIELD_H - 4, 62,
                TaskRewardEditorScreen.FIELD_H)
                .tooltip(Tooltip.create(
                        ChroniclesUIKit.lit(TaskRewardEditorScreen.HIDE_TOOLTIP.formatted("quest is completed"))))
                .build());
        screen.addWidgetBridge(Button.builder(
                ChroniclesUIKit.lit(screen.editingRewardIndex >= 0 ? "§b✎ Update Reward" : "§a✔ Add Reward"),
                b -> commitRewardFromForm())
                .bounds(rx + screen.colW - 80, screen.formBottom - TaskRewardEditorScreen.FIELD_H - 4, 80,
                        TaskRewardEditorScreen.FIELD_H)
                .tooltip(Tooltip.create(ChroniclesUIKit.lit(screen.editingRewardIndex >= 0 ?
                        "Save changes to this reward (right-click it again to cancel)" :
                        "Add this reward to the quest (Ctrl+Z to undo)")))
                .build());
    }

    private void buildItemRewardInputs(int rx, int rfy, String rCountVal) {
        String itemLabel = rewardPickedItem != null ? "§f" + rewardPickedItem.getHoverName().getString() :
                "§8Pick Item";
        screen.addWidgetBridge(Button.builder(ChroniclesUIKit.lit(itemLabel), b -> {
            if (Minecraft.getInstance() != null)
                Minecraft.getInstance().setScreen(new ItemPickerScreen(screen, stack -> {
                    rewardPickedItem = stack;
                    screen.rebuildWidgets();
                }).withBulk(this::bulkAddItemRewards));
        }).bounds(rx, rfy, screen.colW - 44, TaskRewardEditorScreen.FIELD_H)
                .tooltip(Tooltip.create(ChroniclesUIKit.lit(
                        "Pick an item. Right-click several in the picker to add one reward per item at once.")))
                .build());
        screen.rewardCountBox = new EditBox(Minecraft.getInstance().font, rx + screen.colW - 42, rfy, 42,
                TaskRewardEditorScreen.FIELD_H, Component.empty());
        screen.setHintFitted(screen.rewardCountBox, "§8Qty");
        screen.rewardCountBox.setMaxLength(4);
        screen.rewardCountBox.setValue(rCountVal);
        screen.addWidgetBridge(screen.rewardCountBox);
        if (AE2Compat.isAvailable()) {
            rfy += TaskRewardEditorScreen.FIELD_H + TaskRewardEditorScreen.FIELD_GAP;
            screen.addWidgetBridge(rewardAe2Button(rx, rfy, screen.colW, "items"));
        }
    }

    private void buildFluidRewardInputs(int rx, int rfy, String rCountVal, String rCommandVal) {
        screen.rewardCommandBox = new EditBox(Minecraft.getInstance().font, rx, rfy, screen.colW - 62,
                TaskRewardEditorScreen.FIELD_H, Component.empty());
        screen.setHintFitted(screen.rewardCommandBox, "§8Fluid id  (e.g. gtceu:oxygen)");
        screen.rewardCommandBox.setMaxLength(128);
        screen.rewardCommandBox.setValue(rCommandVal);
        screen.addWidgetBridge(screen.rewardCommandBox);
        screen.addWidgetBridge(Button.builder(ChroniclesUIKit.lit("§3⊞"), b -> {
            if (Minecraft.getInstance() != null)
                Minecraft.getInstance().setScreen(new FluidPickerScreen(screen, fluidId -> {
                    screen.pendingRewardCommand = fluidId;
                    screen.pendingRewardCount = screen.rewardCountBox != null ? screen.rewardCountBox.getValue() :
                            screen.pendingRewardCount;
                    screen.forcePendingRewardValues = true;
                    screen.rebuildWidgets();
                }));
        }).bounds(rx + screen.colW - 60, rfy, 16, TaskRewardEditorScreen.FIELD_H)
                .tooltip(Tooltip.create(ChroniclesUIKit.lit("Pick a fluid")))
                .build());
        screen.rewardCountBox = new EditBox(Minecraft.getInstance().font, rx + screen.colW - 42, rfy, 42,
                TaskRewardEditorScreen.FIELD_H, Component.empty());
        screen.setHintFitted(screen.rewardCountBox, "§8mB");
        screen.rewardCountBox.setMaxLength(8);
        screen.rewardCountBox.setValue(rCountVal);
        screen.addWidgetBridge(screen.rewardCountBox);
        rfy += TaskRewardEditorScreen.FIELD_H + TaskRewardEditorScreen.FIELD_GAP;
        if (AE2Compat.isAvailable()) {
            screen.addWidgetBridge(rewardAe2Button(rx, rfy, screen.colW, "fluid"));
        } else {
            screen.addWidgetBridge(Button.builder(ChroniclesUIKit.lit("§8No AE2: whole buckets only"), b -> {})
                    .bounds(rx, rfy, screen.colW, TaskRewardEditorScreen.FIELD_H)
                    .tooltip(Tooltip.create(ChroniclesUIKit.lit(
                            "Without Applied Energistics 2 installed, fluid rewards are delivered as whole\n" +
                                    "buckets (if the fluid has a bucket item).")))
                    .build());
        }
    }

    private void buildXpRewardInputs(int rx, int rfy, String rCountVal) {
        screen.rewardCountBox = new EditBox(Minecraft.getInstance().font, rx, rfy, screen.colW,
                TaskRewardEditorScreen.FIELD_H, Component.empty());
        screen.setHintFitted(screen.rewardCountBox, "§8XP levels to award");
        screen.rewardCountBox.setMaxLength(5);
        screen.rewardCountBox.setValue(rCountVal);
        screen.addWidgetBridge(screen.rewardCountBox);
    }

    private void buildScriptEventRewardInputs(int rx, int rfy, String rCommandVal, String rEventDataVal) {
        screen.rewardCommandBox = new EditBox(Minecraft.getInstance().font, rx, rfy, screen.colW,
                TaskRewardEditorScreen.FIELD_H, Component.empty());
        screen.setHintFitted(screen.rewardCommandBox, "§8Event ID  (e.g. unlock_end)");
        screen.rewardCommandBox.setMaxLength(128);
        screen.rewardCommandBox.setValue(rCommandVal);
        screen.addWidgetBridge(screen.rewardCommandBox);
        rfy += TaskRewardEditorScreen.FIELD_H + TaskRewardEditorScreen.FIELD_GAP;
        screen.rewardEventDataBox = new EditBox(Minecraft.getInstance().font, rx, rfy, screen.colW,
                TaskRewardEditorScreen.FIELD_H, Component.empty());
        screen.setHintFitted(screen.rewardEventDataBox, "§8NBT data  {key:\"val\"}  (optional)");
        screen.rewardEventDataBox.setMaxLength(256);
        screen.rewardEventDataBox.setValue(rEventDataVal);
        screen.addWidgetBridge(screen.rewardEventDataBox);
    }

    private void buildRewardTableInputs(int rx, int rfy, String rCommandVal) {
        screen.rewardCommandBox = new EditBox(Minecraft.getInstance().font, rx, rfy, screen.colW - 20,
                TaskRewardEditorScreen.FIELD_H, Component.empty());
        screen.setTableIdHint(screen.rewardCommandBox);
        screen.rewardCommandBox.setMaxLength(128);
        screen.rewardCommandBox.setValue(rCommandVal);
        screen.addWidgetBridge(screen.rewardCommandBox);
        screen.addWidgetBridge(Button.builder(ChroniclesUIKit.lit("🎲"), b -> {
            String tid = screen.rewardCommandBox.getValue().trim();
            if (Minecraft.getInstance() != null && !tid.isEmpty())
                Minecraft.getInstance().setScreen(new RewardTableSimulatorScreen(screen, tid));
        }).bounds(rx + screen.colW - 18, rfy, 18, TaskRewardEditorScreen.FIELD_H)
                .tooltip(Tooltip.create(ChroniclesUIKit.lit("Simulate 1000 rolls against this table")))
                .build());
    }

    private void buildTableOptionEditor(int rx, int rfy, String boTableVal) {
        screen.boxOptionTableBox = new EditBox(Minecraft.getInstance().font, rx, rfy, screen.colW - 20,
                TaskRewardEditorScreen.FIELD_H, Component.empty());
        screen.setTableIdHint(screen.boxOptionTableBox);
        screen.boxOptionTableBox.setMaxLength(128);
        screen.boxOptionTableBox.setValue(boTableVal);
        screen.addWidgetBridge(screen.boxOptionTableBox);
        screen.addWidgetBridge(Button.builder(ChroniclesUIKit.lit("🎲"), b -> {
            String tid = screen.boxOptionTableBox.getValue().trim();
            if (Minecraft.getInstance() != null && !tid.isEmpty())
                Minecraft.getInstance().setScreen(new RewardTableSimulatorScreen(screen, tid));
        }).bounds(rx + screen.colW - 18, rfy, 18, TaskRewardEditorScreen.FIELD_H)
                .tooltip(Tooltip.create(ChroniclesUIKit.lit("Simulate 1000 rolls against this table")))
                .build());
        rfy += TaskRewardEditorScreen.FIELD_H + TaskRewardEditorScreen.FIELD_GAP;

        screen.addWidgetBridge(
                Button.builder(ChroniclesUIKit.lit("§a✔ Save Option"), b -> commitBoxOptionEdit())
                        .bounds(rx, rfy, screen.colW / 2 - 2, TaskRewardEditorScreen.FIELD_H)
                        .tooltip(Tooltip.create(ChroniclesUIKit.lit("Save changes to this option")))
                        .build());
        screen.addWidgetBridge(Button.builder(ChroniclesUIKit.lit("§7Cancel"), b -> cancelBoxOptionEdit())
                .bounds(rx + screen.colW / 2 + 2, rfy, screen.colW / 2 - 2, TaskRewardEditorScreen.FIELD_H)
                .build());
    }

    private void buildItemOptionEditor(int rx, int rfy, String boCountVal, String boNbtVal) {
        String itemLabel = boxOptionPickedItem != null ?
                "§f" + boxOptionPickedItem.getHoverName().getString() : "§8Pick Item";
        screen.addWidgetBridge(Button.builder(ChroniclesUIKit.lit(itemLabel), b -> {
            if (Minecraft.getInstance() != null)
                Minecraft.getInstance().setScreen(new ItemPickerScreen(screen, stack -> {
                    boxOptionPickedItem = stack;
                    screen.rebuildWidgets();
                }));
        }).bounds(rx, rfy, screen.colW - 44, TaskRewardEditorScreen.FIELD_H).build());
        screen.boxOptionCountBox = new EditBox(Minecraft.getInstance().font, rx + screen.colW - 42, rfy, 42,
                TaskRewardEditorScreen.FIELD_H, Component.empty());
        screen.setHintFitted(screen.boxOptionCountBox, "§8Qty");
        screen.boxOptionCountBox.setMaxLength(4);
        screen.boxOptionCountBox.setValue(boCountVal);
        screen.addWidgetBridge(screen.boxOptionCountBox);
        rfy += TaskRewardEditorScreen.FIELD_H + TaskRewardEditorScreen.FIELD_GAP;

        screen.boxOptionNbtBox = new EditBox(Minecraft.getInstance().font, rx, rfy, screen.colW,
                TaskRewardEditorScreen.FIELD_H, Component.empty());
        screen.setHintFitted(screen.boxOptionNbtBox, "§8NBT  {display:{Name:'...'}}  (optional)");
        screen.boxOptionNbtBox.setMaxLength(256);
        screen.boxOptionNbtBox.setValue(boNbtVal);
        screen.addWidgetBridge(screen.boxOptionNbtBox);
        rfy += TaskRewardEditorScreen.FIELD_H + TaskRewardEditorScreen.FIELD_GAP;

        screen.addWidgetBridge(Button.builder(ChroniclesUIKit.lit("§a✔ Save Option"), b -> commitBoxOptionEdit())
                .bounds(rx, rfy, screen.colW / 2 - 2, TaskRewardEditorScreen.FIELD_H)
                .tooltip(Tooltip.create(ChroniclesUIKit.lit("Save changes to this option")))
                .build());
        screen.addWidgetBridge(Button.builder(ChroniclesUIKit.lit("§7Cancel"), b -> cancelBoxOptionEdit())
                .bounds(rx + screen.colW / 2 + 2, rfy, screen.colW / 2 - 2, TaskRewardEditorScreen.FIELD_H)
                .build());
    }

    private void buildChoiceBoxInputs(int rx, int rfy) {
        screen.addWidgetBridge(Button.builder(
                ChroniclesUIKit.lit("§8Mode: §7" +
                        (switch (boxMode) {
                            case LOOTBOX -> "Lootbox";
                            case ALL -> "All";
                            default -> "Menu";
                        }) + " §8▾"),
                b -> {
                    boxMode = switch (boxMode) {
                        case MENU -> QuestReward.ChoiceBoxReward.Mode.LOOTBOX;
                        case LOOTBOX -> QuestReward.ChoiceBoxReward.Mode.ALL;
                        default -> QuestReward.ChoiceBoxReward.Mode.MENU;
                    };
                    screen.rebuildWidgets();
                })
                .bounds(rx, rfy,
                        screen.colW - TaskRewardEditorScreen.CHOICE_ADD_ITEM_W -
                                TaskRewardEditorScreen.CHOICE_ADD_TABLE_W - 4,
                        TaskRewardEditorScreen.FIELD_H)
                .tooltip(Tooltip.create(ChroniclesUIKit.lit(
                        "Menu: player clicks the box and picks which option they get.\n" +
                                "Lootbox: player clicks the box and the server grants a random option.\n" +
                                "All: every option is granted when the quest is claimed.")))
                .build());
        screen.addWidgetBridge(Button.builder(ChroniclesUIKit.lit("§a+ Item"), b -> {
            if (Minecraft.getInstance() != null)
                Minecraft.getInstance().setScreen(new ItemPickerScreen(screen, stack -> {
                    screen.boxOptions
                            .add(new QuestReward.ItemReward(stack.getItem(), Math.max(1, stack.getCount())));
                    screen.rebuildWidgets();
                }));
        }).bounds(
                rx + screen.colW - TaskRewardEditorScreen.CHOICE_ADD_ITEM_W -
                        TaskRewardEditorScreen.CHOICE_ADD_TABLE_W - 2,
                rfy, TaskRewardEditorScreen.CHOICE_ADD_ITEM_W, TaskRewardEditorScreen.FIELD_H)
                .tooltip(Tooltip.create(ChroniclesUIKit.lit(
                        "Add an item option to this choice box (right-click an option to edit its qty/NBT)")))
                .build());
        screen.addWidgetBridge(Button.builder(ChroniclesUIKit.lit("§6+ Table"), b -> {
            screen.boxOptions.add(new QuestReward.RewardTableReward(""));
            startEditingBoxOption(screen.boxOptions.size() - 1);
        }).bounds(rx + screen.colW - TaskRewardEditorScreen.CHOICE_ADD_TABLE_W, rfy,
                TaskRewardEditorScreen.CHOICE_ADD_TABLE_W, TaskRewardEditorScreen.FIELD_H)
                .tooltip(Tooltip.create(ChroniclesUIKit.lit(
                        "Add a reward table option: picking it rolls that table (right-click it to change the table)")))
                .build());
        rfy += TaskRewardEditorScreen.FIELD_H + TaskRewardEditorScreen.FIELD_GAP;

        if (boxMode == QuestReward.ChoiceBoxReward.Mode.LOOTBOX && !screen.boxOptions.isEmpty()) {
            screen.addWidgetBridge(Button.builder(ChroniclesUIKit.lit("🎲 Simulate rolls"), b -> {
                java.util.List<QuestReward.WeightedReward> weighted = screen.boxOptions.stream()
                        .map(r -> new QuestReward.WeightedReward(r, 1)).toList();
                RewardTable preview = new RewardTable("preview", "Lootbox preview", weighted, 1);
                if (Minecraft.getInstance() != null)
                    Minecraft.getInstance().setScreen(new RewardTableSimulatorScreen(screen, preview));
            }).bounds(rx, rfy, screen.colW, TaskRewardEditorScreen.FIELD_H)
                    .tooltip(Tooltip.create(
                            ChroniclesUIKit
                                    .lit("Roll this lootbox many times to see how often each option comes up")))
                    .build());
            rfy += TaskRewardEditorScreen.FIELD_H + TaskRewardEditorScreen.FIELD_GAP;
        }

        screen.boxOptionsListX = rx;
        screen.boxOptionsListY = rfy;
        screen.boxOptionsListW = screen.colW;
        screen.boxOptionsListBottom = screen.formBottom - TaskRewardEditorScreen.FIELD_H - 4 -
                TaskRewardEditorScreen.FIELD_GAP;
        int visibleRows = Math.max(1,
                (screen.boxOptionsListBottom - screen.boxOptionsListY) / TaskRewardEditorScreen.BOX_OPTION_ROW_H);
        int maxScroll = Math.max(0, screen.boxOptions.size() - visibleRows);
        screen.boxOptionsScroll = Math.max(0, Math.min(screen.boxOptionsScroll, maxScroll));
    }

    private void buildExternalRewardInputs(int rx, int rfy, String rCommandVal, String rEventDataVal) {
        PhoenixRewardRegistry.Entry externalEntry = PhoenixRewardRegistry.get(rCommandVal.trim());
        externalEntryAtBuild = externalEntry;
        screen.rewardCommandBox = new EditBox(Minecraft.getInstance().font, rx, rfy, screen.colW - 20,
                TaskRewardEditorScreen.FIELD_H, Component.empty());
        screen.setHintFitted(screen.rewardCommandBox, "§8Registered external reward id");
        screen.rewardCommandBox.setMaxLength(128);
        screen.rewardCommandBox.setValue(rCommandVal);
        screen.rewardCommandBox.setResponder(v -> {
            if (PhoenixRewardRegistry.get(v.trim()) != externalEntryAtBuild) {
                screen.pendingRewardCommand = v;
                screen.pendingRewardEventData = screen.rewardEventDataBox != null ?
                        screen.rewardEventDataBox.getValue() :
                        screen.pendingRewardEventData;
                screen.forcePendingRewardValues = true;
                screen.rebuildWidgets();
            }
        });
        screen.addWidgetBridge(screen.rewardCommandBox);
        screen.addWidgetBridge(Button.builder(ChroniclesUIKit.lit("§7⊞"), b -> {
            java.util.List<ResourceLocation> ids = new ArrayList<>();
            for (PhoenixRewardRegistry.Entry e : PhoenixRewardRegistry.all()) {
                ResourceLocation id = ResourceLocation.tryParse(e.typeId());
                if (id != null) ids.add(id);
            }
            if (Minecraft.getInstance() != null && !ids.isEmpty()) {
                Minecraft.getInstance()
                        .setScreen(new RegistryIdPickerScreen(screen, "Pick external reward", ids, id -> {
                            screen.pendingRewardCommand = id.toString();
                            screen.forcePendingRewardValues = true;
                            screen.rebuildWidgets();
                        }));
            }
        }).bounds(rx + screen.colW - 18, rfy, 18, TaskRewardEditorScreen.FIELD_H)
                .tooltip(Tooltip.create(ChroniclesUIKit.lit("Browse registered external reward types")))
                .build());
        rfy += TaskRewardEditorScreen.FIELD_H + TaskRewardEditorScreen.FIELD_GAP;

        if (externalEntry != null && !externalEntry.fields().isEmpty()) {
            for (PhoenixTaskRegistry.FieldDef field : externalEntry.fields()) {
                String current = externalFieldValues.getOrDefault(field.id(), "");
                if (field.type() == PhoenixTaskRegistry.FieldDef.FieldType.BOOLEAN) {
                    boolean on = Boolean.parseBoolean(current);
                    screen.addWidgetBridge(Button.builder(
                            ChroniclesUIKit.lit("§8" + field.label() + ": " + (on ? "§aON" : "§7OFF")), b -> {
                                externalFieldValues.put(field.id(), String.valueOf(!on));
                                screen.rebuildWidgets();
                            }).bounds(rx, rfy, screen.colW, TaskRewardEditorScreen.FIELD_H).build());
                } else {
                    EditBox box = new EditBox(Minecraft.getInstance().font, rx, rfy, screen.colW,
                            TaskRewardEditorScreen.FIELD_H, Component.empty());
                    screen.setHintFitted(box, "§8" + field.label() +
                            (field.hint() != null ? "  (" + field.hint() + ")" : ""));
                    box.setMaxLength(256);
                    box.setValue(current);
                    box.setResponder(v -> externalFieldValues.put(field.id(), v));
                    screen.addWidgetBridge(box);
                }
                rfy += TaskRewardEditorScreen.FIELD_H + TaskRewardEditorScreen.FIELD_GAP;
            }
        } else {
            screen.rewardEventDataBox = new EditBox(Minecraft.getInstance().font, rx, rfy, screen.colW,
                    TaskRewardEditorScreen.FIELD_H, Component.empty());
            screen.setHintFitted(screen.rewardEventDataBox, "§8NBT data  {key:\"val\"}  (optional)");
            screen.rewardEventDataBox.setMaxLength(256);
            screen.rewardEventDataBox.setValue(rEventDataVal);
            screen.addWidgetBridge(screen.rewardEventDataBox);
        }
    }

    private void buildOpenScreenInputs(int rx, int rfy, String rCommandVal) {
        screen.rewardCommandBox = new EditBox(Minecraft.getInstance().font, rx, rfy, screen.colW - 20,
                TaskRewardEditorScreen.FIELD_H, Component.empty());
        screen.setHintFitted(screen.rewardCommandBox, "§8Registered external screen id");
        screen.rewardCommandBox.setMaxLength(128);
        screen.rewardCommandBox.setValue(rCommandVal);
        screen.addWidgetBridge(screen.rewardCommandBox);
        screen.addWidgetBridge(Button.builder(ChroniclesUIKit.lit("§7⊞"), b -> {
            java.util.Collection<ResourceLocation> ids = net.phoenixvine.chronicles.client.registry.ExternalScreenRegistry
                    .registeredIds();
            if (Minecraft.getInstance() != null && !ids.isEmpty()) {
                Minecraft.getInstance()
                        .setScreen(new RegistryIdPickerScreen(screen, "Pick external screen", ids, id -> {
                            screen.pendingRewardCommand = id.toString();
                            screen.forcePendingRewardValues = true;
                            screen.rebuildWidgets();
                        }));
            }
        }).bounds(rx + screen.colW - 18, rfy, 18, TaskRewardEditorScreen.FIELD_H)
                .tooltip(Tooltip.create(ChroniclesUIKit.lit("Browse registered external screens")))
                .build());
    }

    private void buildTextRewardInputs(int rx, int rfy, String rCommandVal) {
        String hint = screen.rewardType.equals("loot_table") ?
                "§8Loot table id  (e.g. minecraft:chests/simple_dungeon)" :
                screen.rewardType.equals("quest_action") ?
                        "§8Quest id  (e.g. my_quest or phoenix_chronicles:my_quest)" :
                        "§8/give {p} …";
        boolean questPicker = screen.rewardType.equals("quest_action");
        screen.rewardCommandBox = new EditBox(Minecraft.getInstance().font, rx, rfy,
                questPicker ? screen.colW - 22 : screen.colW, TaskRewardEditorScreen.FIELD_H,
                Component.empty());
        screen.setHintFitted(screen.rewardCommandBox, hint);
        screen.rewardCommandBox.setMaxLength(256);
        screen.rewardCommandBox.setValue(rCommandVal);
        screen.addWidgetBridge(screen.rewardCommandBox);
        if (questPicker) {
            screen.addWidgetBridge(Button.builder(ChroniclesUIKit.lit("§7⊞"), b -> {
                if (screen.rewardCommandBox != null)
                    screen.pendingRewardCommand = screen.rewardCommandBox.getValue();
                if (Minecraft.getInstance() != null && screen.questNode != null) {
                    Minecraft.getInstance()
                            .setScreen(ParentSelectorScreen.singleSelect(screen, screen.questNode, picked -> {
                                screen.pendingRewardCommand = picked.getId().toString();
                                screen.forcePendingRewardValues = true;
                                screen.rebuildWidgets();
                            }));
                }
            }).bounds(rx + screen.colW - 18, rfy, 18, TaskRewardEditorScreen.FIELD_H)
                    .tooltip(Tooltip.create(ChroniclesUIKit.lit(
                            "Pick a quest (starts in this quest's chapter; use the dropdown for others)")))
                    .build());
        }
        if (screen.rewardType.equals("command")) {
            rfy += TaskRewardEditorScreen.FIELD_H + TaskRewardEditorScreen.FIELD_GAP;
            screen.addWidgetBridge(rewardPermButton(rx, rfy, screen.colW));
        } else if (screen.rewardType.equals("quest_action")) {
            rfy += TaskRewardEditorScreen.FIELD_H + TaskRewardEditorScreen.FIELD_GAP;
            screen.addWidgetBridge(rewardQuestActionButton(rx, rfy, screen.colW));
        }
    }

    private Button rewardQuestActionButton(int x, int y, int w) {
        boolean complete = rewardQuestAction == QuestReward.QuestActionReward.Action.COMPLETE;
        return Button.builder(
                ChroniclesUIKit.lit("§7Action: §f" + (complete ? "Complete quest" : "Reset progress")),
                b -> {
                    if (screen.rewardCommandBox != null)
                        screen.pendingRewardCommand = screen.rewardCommandBox.getValue();
                    screen.forcePendingRewardValues = true;
                    rewardQuestAction = complete ? QuestReward.QuestActionReward.Action.RESET :
                            QuestReward.QuestActionReward.Action.COMPLETE;
                    screen.rebuildWidgets();
                })
                .bounds(x, y, w, TaskRewardEditorScreen.FIELD_H)
                .tooltip(Tooltip.create(ChroniclesUIKit.lit(
                        "Complete: marks the quest done for the player (children unlock as normal).\n" +
                                "Reset: clears the quest's progress so it can be done again.")))
                .build();
    }

    private Button rewardPermButton(int x, int y, int w) {
        return Button.builder(
                ChroniclesUIKit.lit("§7Runs as: §f" + screen.permLabel(rewardPermLevel)),
                b -> {
                    if (screen.rewardCommandBox != null)
                        screen.pendingRewardCommand = screen.rewardCommandBox.getValue();
                    screen.forcePendingRewardValues = true;
                    rewardPermLevel = (rewardPermLevel + 1) % 5;
                    screen.rebuildWidgets();
                })
                .bounds(x, y, w, TaskRewardEditorScreen.FIELD_H)
                .tooltip(Tooltip.create(ChroniclesUIKit.lit(
                        "Permission level the command runs with (click to cycle).\n" +
                                "Lower levels limit what a quest author can make the server run.\n" +
                                "Note: target selectors (@p, @a, @e, @s) need level 2 or higher;\n" +
                                "use {p} for the claiming player at levels 0-1.")))
                .build();
    }

    private Button rewardAe2Button(int x, int y, int w, String what) {
        return Button.builder(
                ChroniclesUIKit.lit(rewardPushAe2 ? "§bAE2: push to network" : "§8AE2: off (inventory)"),
                b -> {
                    if (screen.rewardCountBox != null) screen.pendingRewardCount = screen.rewardCountBox.getValue();
                    if (screen.rewardCommandBox != null)
                        screen.pendingRewardCommand = screen.rewardCommandBox.getValue();
                    screen.forcePendingRewardValues = true;
                    rewardPushAe2 = !rewardPushAe2;
                    screen.rebuildWidgets();
                })
                .bounds(x, y, w, TaskRewardEditorScreen.FIELD_H)
                .tooltip(Tooltip.create(ChroniclesUIKit.lit(
                        "ON (default when AE2 is installed): insert the " + what + " straight into the player's\n" +
                                "linked Applied Energistics 2 ME network. Anything that doesn't fit, or a player\n" +
                                "with no linked terminal, gets it in their inventory instead.")))
                .build();
    }

    private void bulkAddItemRewards(List<ItemStack> picked) {
        if (screen.editingRewardIndex >= 0) return;
        int count = 1;
        try {
            count = Math.max(1,
                    Integer.parseInt(screen.rewardCountBox != null ? screen.rewardCountBox.getValue().trim() : "1"));
        } catch (NumberFormatException ignored) {}
        final int qty = count;
        final int hidden = rewardHiddenParts;
        final List<QuestReward> added = new ArrayList<>();
        for (ItemStack stack : picked) {
            QuestReward r = new QuestReward.ItemReward(stack.getItem(), qty, ItemData.capture(stack), rewardPushAe2);
            r.setHiddenParts(hidden);
            added.add(r);
        }
        screen.pushUndo(() -> {
            screen.rewards.addAll(added);
            screen.rewardListScroll = Integer.MAX_VALUE;
        });
        rewardPickedItem = null;
        screen.rebuildWidgets();
    }

    private void commitRewardFromForm() {
        String countS = screen.rewardCountBox != null ? screen.rewardCountBox.getValue().trim() : "1";
        int count = 1;
        try {
            count = Math.max(1, Integer.parseInt(countS));
        } catch (NumberFormatException ignored) {}

        RewardFormValues values = new RewardFormValues();
        values.count = count;
        values.text = screen.rewardCommandBox != null ? screen.rewardCommandBox.getValue().trim() : "";
        values.eventData = screen.rewardEventDataBox != null ? screen.rewardEventDataBox.getValue() : "";
        values.item = rewardPickedItem;
        values.pushAe2 = rewardPushAe2;
        values.permLevel = rewardPermLevel;
        values.questAction = rewardQuestAction;
        values.boxMode = boxMode;
        values.boxOptions = screen.boxOptions;
        values.externalFields = externalFieldValues;
        QuestReward reward = RewardFormTypes.get(screen.rewardType).create(values);

        if (reward != null) {
            reward.setHiddenParts(rewardHiddenParts);
            screen.pushUndo(() -> {
                if (screen.editingRewardIndex >= 0 && screen.editingRewardIndex < screen.rewards.size()) {
                    screen.rewards.set(screen.editingRewardIndex, reward);
                } else {
                    screen.rewards.add(reward);
                    screen.rewardListScroll = Integer.MAX_VALUE;
                }
                screen.editingRewardIndex = -1;
            });
            rewardPickedItem = null;
            rewardHiddenParts = 0;
            rewardPushAe2 = true;
            rewardPermLevel = QuestReward.CommandReward.DEFAULT_PERMISSION_LEVEL;
            rewardQuestAction = QuestReward.QuestActionReward.Action.COMPLETE;
            screen.rewardTypeDropOpen = false;
            screen.pendingRewardCount = screen.pendingRewardCommand = screen.pendingRewardEventData = "";
            externalFieldValues.clear();
            screen.boxOptions.clear();
            boxMode = QuestReward.ChoiceBoxReward.Mode.MENU;
            screen.editingBoxOptionIndex = -1;
            boxOptionPickedItem = null;
            screen.forcePendingRewardValues = true;
            screen.rebuildWidgets();
        }
    }

    void resetFieldsForTypeChange() {
        rewardPickedItem = null;
        rewardPushAe2 = true;
        rewardPermLevel = QuestReward.CommandReward.DEFAULT_PERMISSION_LEVEL;
        rewardQuestAction = QuestReward.QuestActionReward.Action.COMPLETE;
        screen.pendingRewardCount = screen.pendingRewardCommand = screen.pendingRewardEventData = "";
        externalFieldValues.clear();
        screen.boxOptions.clear();
        boxMode = QuestReward.ChoiceBoxReward.Mode.MENU;
        screen.editingBoxOptionIndex = -1;
        boxOptionPickedItem = null;
        screen.forcePendingRewardValues = true;
    }

    void startEditingReward(int idx) {
        if (idx < 0 || idx >= screen.rewards.size()) return;
        QuestReward r = screen.rewards.get(idx);
        rewardPickedItem = null;
        rewardHiddenParts = r.getHiddenParts();
        rewardPushAe2 = true;
        rewardPermLevel = QuestReward.CommandReward.DEFAULT_PERMISSION_LEVEL;
        rewardQuestAction = QuestReward.QuestActionReward.Action.COMPLETE;
        screen.pendingRewardCount = "1";
        screen.pendingRewardCommand = "";
        screen.pendingRewardEventData = "";
        externalFieldValues.clear();
        screen.boxOptions.clear();
        boxMode = QuestReward.ChoiceBoxReward.Mode.MENU;
        screen.editingBoxOptionIndex = -1;
        boxOptionPickedItem = null;

        String typeId = RewardFormTypes.idFor(r);
        if (typeId == null) return;
        RewardFormValues values = new RewardFormValues();
        RewardFormTypes.get(typeId).load(r, values);
        screen.rewardType = typeId;
        rewardPickedItem = values.item;
        rewardPushAe2 = values.pushAe2;
        rewardPermLevel = values.permLevel;
        rewardQuestAction = values.questAction;
        screen.pendingRewardCount = String.valueOf(values.count);
        screen.pendingRewardCommand = values.text;
        screen.pendingRewardEventData = values.eventData;
        externalFieldValues.putAll(values.externalFields);
        boxMode = values.boxMode;
        screen.boxOptions.addAll(values.boxOptions);

        screen.editingRewardIndex = idx;
        screen.forcePendingRewardValues = true;
        screen.taskTypeDropOpen = false;
        screen.rewardTypeDropOpen = false;
        screen.rebuildWidgets();
    }

    void cancelRewardEdit() {
        screen.editingRewardIndex = -1;
        rewardPickedItem = null;
        rewardPushAe2 = true;
        rewardPermLevel = QuestReward.CommandReward.DEFAULT_PERMISSION_LEVEL;
        rewardQuestAction = QuestReward.QuestActionReward.Action.COMPLETE;
        screen.pendingRewardCount = screen.pendingRewardCommand = screen.pendingRewardEventData = "";
        externalFieldValues.clear();
        screen.boxOptions.clear();
        boxMode = QuestReward.ChoiceBoxReward.Mode.MENU;
        screen.editingBoxOptionIndex = -1;
        boxOptionPickedItem = null;
        screen.forcePendingRewardValues = true;
        screen.rebuildWidgets();
    }

    void startEditingBoxOption(int idx) {
        if (idx < 0 || idx >= screen.boxOptions.size()) return;
        if (screen.boxOptions.get(idx) instanceof QuestReward.RewardTableReward table) {
            screen.editingBoxOptionIndex = idx;
            screen.pendingBoxOptionTable = table.getTableId();
            screen.forcePendingBoxOptionValues = true;
            screen.rebuildWidgets();
            return;
        }
        if (!(screen.boxOptions.get(idx) instanceof QuestReward.ItemReward ir)) return;

        screen.editingBoxOptionIndex = idx;
        boxOptionPickedItem = new ItemStack(ir.getItem(), ir.getCount());
        ItemData.applyReward(boxOptionPickedItem, ir.getNbt());
        screen.pendingBoxOptionCount = String.valueOf(ir.getCount());
        screen.pendingBoxOptionNbt = ir.getNbt() != null && !ir.getNbt().isEmpty() ? ir.getNbt().toString() : "";
        screen.forcePendingBoxOptionValues = true;
        screen.rebuildWidgets();
    }

    private void commitBoxOptionEdit() {
        if (screen.editingBoxOptionIndex >= 0 && screen.editingBoxOptionIndex < screen.boxOptions.size() &&
                screen.boxOptions.get(screen.editingBoxOptionIndex) instanceof QuestReward.RewardTableReward) {
            String tid = screen.boxOptionTableBox != null ? screen.boxOptionTableBox.getValue().trim() : "";
            if (tid.isEmpty()) screen.boxOptions.remove(screen.editingBoxOptionIndex);
            else screen.boxOptions.set(screen.editingBoxOptionIndex, new QuestReward.RewardTableReward(tid));
            cancelBoxOptionEdit();
            return;
        }
        if (screen.editingBoxOptionIndex < 0 || screen.editingBoxOptionIndex >= screen.boxOptions.size() ||
                boxOptionPickedItem == null) {
            cancelBoxOptionEdit();
            return;
        }

        int count = 1;
        try {
            count = Math.max(1, Integer.parseInt(screen.boxOptionCountBox.getValue().trim()));
        } catch (NumberFormatException ignored) {}

        net.minecraft.nbt.CompoundTag nbt = null;
        String nbtStr = screen.boxOptionNbtBox != null ? screen.boxOptionNbtBox.getValue().trim() : "";
        if (!nbtStr.isEmpty()) {
            try {
                nbt = net.minecraft.nbt.TagParser.parseTag(nbtStr);
            } catch (Exception ignored) {}
        }

        boolean keepPush = !(screen.boxOptions
                .get(screen.editingBoxOptionIndex) instanceof QuestReward.ItemReward old) ||
                old.isPushToAe2();
        screen.boxOptions.set(screen.editingBoxOptionIndex,
                new QuestReward.ItemReward(boxOptionPickedItem.getItem(), count, nbt, keepPush));
        cancelBoxOptionEdit();
    }

    void cancelBoxOptionEdit() {
        if (screen.editingBoxOptionIndex >= 0 && screen.editingBoxOptionIndex < screen.boxOptions.size() &&
                screen.boxOptions.get(screen.editingBoxOptionIndex) instanceof QuestReward.RewardTableReward table &&
                table.getTableId().isBlank()) {
            screen.boxOptions.remove(screen.editingBoxOptionIndex);
        }
        screen.pendingBoxOptionTable = "";
        screen.editingBoxOptionIndex = -1;
        boxOptionPickedItem = null;
        screen.pendingBoxOptionCount = "1";
        screen.pendingBoxOptionNbt = "";
        screen.forcePendingBoxOptionValues = true;
        screen.rebuildWidgets();
    }

    void renderBoxOptionsList(GuiGraphics g, int mx, int my) {
        screen.boxOptionRowRects.clear();
        if (screen.boxOptions.isEmpty()) {
            ChroniclesUIKit.drawString(g, Minecraft.getInstance().font,
                    "§8No options yet - use §7+ Item §8or §7+ Table", screen.boxOptionsListX,
                    screen.boxOptionsListY,
                    screen.C_TEXT_FAINT,
                    false);
            return;
        }

        int visibleRows = Math.max(1,
                (screen.boxOptionsListBottom - screen.boxOptionsListY) / TaskRewardEditorScreen.BOX_OPTION_ROW_H);

        int ry = screen.boxOptionsListY;
        int end = Math.min(screen.boxOptions.size(), screen.boxOptionsScroll + visibleRows);
        int hoveredRowY = -1;
        for (int i = screen.boxOptionsScroll; i < end; i++) {
            QuestReward opt = screen.boxOptions.get(i);
            boolean hov = mx >= screen.boxOptionsListX && mx < screen.boxOptionsListX + screen.boxOptionsListW &&
                    my >= ry && my < ry + TaskRewardEditorScreen.BOX_OPTION_ROW_H;
            if (hov) {
                g.fill(screen.boxOptionsListX, ry, screen.boxOptionsListX + screen.boxOptionsListW,
                        ry + TaskRewardEditorScreen.BOX_OPTION_ROW_H,
                        TaskRewardEditorScreen.C_ROW_HOVER);
                hoveredRowY = ry;
            }

            String label = opt.getSummary().getString();
            int maxW = screen.boxOptionsListW - 12;
            if (Minecraft.getInstance().font.width(label) > maxW)
                label = Minecraft.getInstance().font.plainSubstrByWidth(label, Math.max(0, maxW - 6)) + "…";
            ChroniclesUIKit.drawString(g, Minecraft.getInstance().font, "§7" + label, screen.boxOptionsListX + 1,
                    ry + 3, screen.C_TEXT_DIM, false);
            ChroniclesUIKit.drawString(g, Minecraft.getInstance().font, "§c✕",
                    screen.boxOptionsListX + screen.boxOptionsListW - 9, ry + 3, 0xFFFF5555,
                    false);

            screen.boxOptionRowRects.add(new int[] { screen.boxOptionsListX, ry, screen.boxOptionsListW,
                    TaskRewardEditorScreen.BOX_OPTION_ROW_H });
            ry += TaskRewardEditorScreen.BOX_OPTION_ROW_H;
        }

        int maxScroll = Math.max(0, screen.boxOptions.size() - visibleRows);
        if (maxScroll > 0) {
            if (screen.boxOptionsScroll > 0)
                ChroniclesUIKit.drawString(g, Minecraft.getInstance().font, "§8▲",
                        screen.boxOptionsListX + screen.boxOptionsListW - 9, screen.boxOptionsListY - 8,
                        screen.C_TEXT_FAINT,
                        false);
            if (screen.boxOptionsScroll < maxScroll)
                ChroniclesUIKit.drawString(g, Minecraft.getInstance().font, "§8▼",
                        screen.boxOptionsListX + screen.boxOptionsListW - 9,
                        screen.boxOptionsListBottom + 1,
                        screen.C_TEXT_FAINT, false);
        }

        if (hoveredRowY >= 0) {
            String tip = "Right-click to edit";
            int tipW = Minecraft.getInstance().font.width(tip) + 8;
            int tipH = 14;
            int tipX = Math.min(mx + 10, screen.boxOptionsListX + screen.boxOptionsListW - tipW);
            tipX = Math.max(tipX, 0);
            int tipY = my + 12;
            g.fill(tipX, tipY, tipX + tipW, tipY + tipH, TaskRewardEditorScreen.C_TOOLTIP_BG);
            screen.drawBorder(g, tipX, tipY, tipW, tipH, screen.C_ACCENT);
            ChroniclesUIKit.drawString(g, Minecraft.getInstance().font, "§f" + tip, tipX + 4, tipY + 3, 0xFFFFFFFF,
                    false);
        }
    }

    private int rewardHiddenParts = 0;

    private boolean rewardPushAe2 = true;

    private int rewardPermLevel = QuestReward.CommandReward.DEFAULT_PERMISSION_LEVEL;

    private QuestReward.QuestActionReward.Action rewardQuestAction = QuestReward.QuestActionReward.Action.COMPLETE;

    private ItemStack rewardPickedItem = null;

    private QuestReward.ChoiceBoxReward.Mode boxMode = QuestReward.ChoiceBoxReward.Mode.MENU;

    private ItemStack boxOptionPickedItem = null;

    private final Map<String, String> externalFieldValues = new LinkedHashMap<>();

    private PhoenixRewardRegistry.Entry externalEntryAtBuild;
}
