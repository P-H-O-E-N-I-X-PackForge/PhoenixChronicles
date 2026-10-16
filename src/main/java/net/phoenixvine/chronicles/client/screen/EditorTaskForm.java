package net.phoenixvine.chronicles.client.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.phoenixvine.chronicles.client.render.ChroniclesUIKit;
import net.phoenixvine.chronicles.client.rich.CodeEditBox;
import net.phoenixvine.chronicles.client.screen.taskform.TaskFormType;
import net.phoenixvine.chronicles.client.screen.taskform.TaskFormType.SecondPicker;
import net.phoenixvine.chronicles.client.screen.taskform.TaskFormType.TargetPicker;
import net.phoenixvine.chronicles.client.screen.taskform.TaskFormTypes;
import net.phoenixvine.chronicles.client.screen.taskform.TaskFormValues;
import net.phoenixvine.chronicles.common.filter.IFluidFilter;
import net.phoenixvine.chronicles.common.filter.IItemFilter;
import net.phoenixvine.chronicles.common.item.FluidFilterTokenItem;
import net.phoenixvine.chronicles.common.item.ItemFilterTokenItem;
import net.phoenixvine.chronicles.common.model.QuestTask;
import net.phoenixvine.chronicles.common.registry.PhoenixTaskRegistry;
import net.phoenixvine.chronicles.common.tasks.*;
import net.phoenixvine.chronicles.common.util.ItemData;
import net.phoenixvine.chronicles.integration.ae2.AE2Compat;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

final class EditorTaskForm {

    private final TaskRewardEditorScreen screen;

    EditorTaskForm(TaskRewardEditorScreen screen) {
        this.screen = screen;
    }

    void buildTaskForm(String descVal, String targetVal, String secondVal, String countVal, String nbtVal) {
        int firstWidgetIndex = screen.children().size();
        TaskFormType form = TaskFormTypes.get(screen.taskType);
        int x = TaskRewardEditorScreen.MARGIN;
        int y = screen.formTop + 8;

        addTypeDropdown(x, y);
        y += rowHeight();

        addDescriptionBox(x, y, descVal);
        y += rowHeight();

        if (form.needsTarget()) {
            addTargetRow(form, x, y, targetVal);
            y += rowHeight();
        }

        screen.taskNbtBox = null;
        if (form.hasNbtBox()) {
            addNbtBox(x, y, nbtVal);
            y += rowHeight();
        }

        if (form.needsSecond()) {
            addSecondRow(form, x, y, secondVal);
        }

        addBottomRow(form, x, countVal);

        if (screen.emergencyMode) {
            removeWidgetsFrom(firstWidgetIndex);
        }
    }

    private static int rowHeight() {
        return TaskRewardEditorScreen.FIELD_H + TaskRewardEditorScreen.FIELD_GAP;
    }

    private void removeWidgetsFrom(int firstIndex) {
        screen.taskTypeDropOpen = false;
        for (var widget : new ArrayList<>(screen.children().subList(firstIndex, screen.children().size()))) {
            screen.removeWidgetBridge(widget);
        }
    }

    private void addTypeDropdown(int x, int y) {
        PhoenixTaskRegistry.TaskEntry curMeta = getTaskMeta(screen.taskType);
        String typeTooltip = curMeta != null && curMeta.editorTooltip() != null ?
                curMeta.editorTooltip().split("\n")[0] : "Choose the type of task to add";
        screen.addWidgetBridge(Button.builder(
                ChroniclesUIKit
                        .lit("§8Type: §7" + (curMeta != null ? curMeta.editorLabel() : screen.taskType) + " §8▾"),
                b -> {
                    screen.taskTypeDropOpen = !screen.taskTypeDropOpen;
                    screen.taskTypeDropScroll = 0;
                    screen.rewardTypeDropOpen = false;
                })
                .bounds(x, y, screen.colW, TaskRewardEditorScreen.FIELD_H)
                .tooltip(Tooltip.create(ChroniclesUIKit.lit(typeTooltip))).build());
    }

    private void addDescriptionBox(int x, int y, String value) {
        screen.taskDescBox = new CodeEditBox(Minecraft.getInstance().font, x, y, screen.colW,
                TaskRewardEditorScreen.FIELD_H, Component.empty());
        screen.setHintFitted(screen.taskDescBox, "§8Task label shown to player");
        screen.taskDescBox.setMaxLength(128);
        screen.taskDescBox.setValue(value);
        screen.addWidgetBridge(screen.taskDescBox);
    }

    private void addTargetRow(TaskFormType form, int x, int y, String value) {
        TargetPicker picker = form.targetPicker();
        int boxWidth = picker == TargetPicker.NONE ? screen.colW : screen.colW - 36;
        int buttonX = x + boxWidth;

        screen.taskTargetBox = new EditBox(Minecraft.getInstance().font, x, y, boxWidth,
                TaskRewardEditorScreen.FIELD_H, Component.empty());
        screen.setHintFitted(screen.taskTargetBox, form.targetHint());
        screen.taskTargetBox.setMaxLength(form.maxTargetLength());
        screen.taskTargetBox.setValue(value);
        screen.addWidgetBridge(screen.taskTargetBox);

        switch (picker) {
            case ITEM -> addItemPickerButton(buttonX, y);
            case ITEM_LIST -> addItemListPickerButton(buttonX, y);
            case FLUID -> addFluidPickerButton(buttonX, y);
            case FLUID_LIST -> addFluidListPickerButton(buttonX, y);
            case BLOCK -> addBlockPickerButton(buttonX, y);
            case ENTITY -> addEntityPickerButton(buttonX, y);
            case REGISTRY -> addRegistryPickerButton(form, buttonX, y);
            case RECIPE_TYPE -> addRecipeTypePickerButton(buttonX, y);
            case ENERGY_TYPE -> addEnergyTypeCycleButton(buttonX, y);
            case STRING -> addStringPickerButton(form, buttonX, y);
            case NONE -> {}
        }

        if (picker == TargetPicker.ITEM || picker == TargetPicker.ITEM_LIST) {
            addHeldItemFilterButton(buttonX, y);
        } else if (picker == TargetPicker.FLUID || picker == TargetPicker.FLUID_LIST) {
            addHeldFluidFilterButton(buttonX, y);
        }
    }

    private void addItemPickerButton(int buttonX, int y) {
        screen.addWidgetBridge(Button.builder(ChroniclesUIKit.lit("§7⊞"), b -> {
            if (Minecraft.getInstance() != null)
                Minecraft.getInstance().setScreen(new ItemPickerScreen(screen, stack -> {
                    if (applyPickedItemFilter(stack)) return;
                    ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
                    if (id != null && screen.taskTargetBox != null)
                        screen.taskTargetBox.setValue(id.toString());
                }).withBulk(this::bulkAddItemTasks));
        }).bounds(buttonX, y, 16, TaskRewardEditorScreen.FIELD_H)
                .tooltip(Tooltip.create(ChroniclesUIKit.lit(
                        "Pick an item. Right-click several in the picker to add one task per item at once " +
                                "(uses the count / consume / optional settings below).")))
                .build());
    }

    private void addItemListPickerButton(int buttonX, int y) {
        screen.addWidgetBridge(Button.builder(ChroniclesUIKit.lit("§7⊞"), b -> {
            if (Minecraft.getInstance() != null)
                Minecraft.getInstance().setScreen(new ItemPickerScreen(screen, stack -> {
                    if (applyPickedItemFilter(stack)) return;
                    ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
                    if (id == null || screen.taskTargetBox == null) return;
                    String cur = screen.taskTargetBox.getValue().trim();
                    screen.taskTargetBox.setValue(cur.isEmpty() ? id.toString() : cur + ";" + id);
                }));
        }).bounds(buttonX, y, 16, TaskRewardEditorScreen.FIELD_H)
                .tooltip(Tooltip.create(ChroniclesUIKit.lit("Add another item to the ANY-match list")))
                .build());
    }

    private void addFluidPickerButton(int buttonX, int y) {
        screen.addWidgetBridge(Button.builder(ChroniclesUIKit.lit("§3⊞"), b -> {
            if (Minecraft.getInstance() != null)
                Minecraft.getInstance().setScreen(new FluidPickerScreen(screen, fluidId -> {
                    if (screen.taskTargetBox != null) screen.taskTargetBox.setValue(fluidId);
                }));
        }).bounds(buttonX, y, 16, TaskRewardEditorScreen.FIELD_H).build());
    }

    private void addFluidListPickerButton(int buttonX, int y) {
        screen.addWidgetBridge(Button.builder(ChroniclesUIKit.lit("§3⊞"), b -> {
            if (Minecraft.getInstance() != null)
                Minecraft.getInstance().setScreen(new FluidPickerScreen(screen, fluidId -> {
                    if (screen.taskTargetBox == null) return;
                    String cur = screen.taskTargetBox.getValue().trim();
                    screen.taskTargetBox.setValue(cur.isEmpty() ? fluidId : cur + ";" + fluidId);
                }));
        }).bounds(buttonX, y, 16, TaskRewardEditorScreen.FIELD_H)
                .tooltip(Tooltip.create(ChroniclesUIKit.lit("Add another fluid to the ANY-match list")))
                .build());
    }

    private void addBlockPickerButton(int buttonX, int y) {
        screen.addWidgetBridge(Button.builder(ChroniclesUIKit.lit("§7⊞"), b -> {
            if (Minecraft.getInstance() != null)
                Minecraft.getInstance().setScreen(new ItemPickerScreen(screen, stack -> {
                    Block block = Block.byItem(stack.getItem());
                    ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
                    if (id != null && screen.taskTargetBox != null)
                        screen.taskTargetBox.setValue(id.toString());
                }));
        }).bounds(buttonX, y, 16, TaskRewardEditorScreen.FIELD_H)
                .tooltip(Tooltip.create(
                        ChroniclesUIKit.lit("Browse items - picking one uses its block form")))
                .build());
    }

    private void addEntityPickerButton(int buttonX, int y) {
        screen.addWidgetBridge(Button.builder(ChroniclesUIKit.lit("§7⊞"), b -> {
            java.util.Collection<ResourceLocation> ids = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE
                    .keySet();
            if (Minecraft.getInstance() != null) {
                Minecraft.getInstance().setScreen(new EntityIdPickerScreen(screen, "Pick entity", ids, id -> {
                    if (screen.taskTargetBox != null) screen.taskTargetBox.setValue(id.toString());
                }));
            }
        }).bounds(buttonX, y, 16, TaskRewardEditorScreen.FIELD_H).build());
    }

    private void addRegistryPickerButton(TaskFormType form, int buttonX, int y) {
        screen.addWidgetBridge(Button.builder(ChroniclesUIKit.lit("§7⊞"), b -> {
            java.util.Collection<ResourceLocation> ids = form.registryIds();
            if (Minecraft.getInstance() != null && ids != null) {
                Minecraft.getInstance()
                        .setScreen(new RegistryIdPickerScreen(screen, "Pick " + screen.taskType, ids, id -> {
                            if (screen.taskTargetBox != null) screen.taskTargetBox.setValue(id.toString());
                        }));
            }
        }).bounds(buttonX, y, 16, TaskRewardEditorScreen.FIELD_H).build());
    }

    private void addRecipeTypePickerButton(int buttonX, int y) {
        screen.addWidgetBridge(Button.builder(ChroniclesUIKit.lit("§7⊞"), b -> {
            if (Minecraft.getInstance() != null) {
                Minecraft.getInstance().setScreen(new RegistryIdPickerScreen(screen, "Pick recipe type",
                        net.minecraft.core.registries.BuiltInRegistries.RECIPE_TYPE.keySet(), id -> {
                            if (screen.taskTargetBox != null) screen.taskTargetBox.setValue(id.toString());
                        }));
            }
        }).bounds(buttonX, y, 16, TaskRewardEditorScreen.FIELD_H)
                .tooltip(Tooltip.create(ChroniclesUIKit.lit("Browse recipe types, including every machine")))
                .build());
    }

    private void addEnergyTypeCycleButton(int buttonX, int y) {
        screen.addWidgetBridge(Button.builder(ChroniclesUIKit.lit("§6⟳"), b -> {
            if (screen.taskTargetBox == null) return;
            String[] options = { "FE", "EU", "ANY" };
            String cur = screen.taskTargetBox.getValue().trim().toUpperCase();
            int idx = java.util.Arrays.asList(options).indexOf(cur);
            screen.taskTargetBox.setValue(options[(idx + 1) % options.length]);
        }).bounds(buttonX, y, 16, TaskRewardEditorScreen.FIELD_H)
                .tooltip(Tooltip.create(ChroniclesUIKit.lit("Cycle FE / EU / ANY")))
                .build());
    }

    private void addStringPickerButton(TaskFormType form, int buttonX, int y) {
        screen.addWidgetBridge(Button.builder(ChroniclesUIKit.lit("§7⊞"), b -> {
            java.util.Collection<String> ids = form.stringIds();
            if (Minecraft.getInstance() != null && ids != null) {
                Minecraft.getInstance()
                        .setScreen(new StringIdPickerScreen(screen, "Pick " + screen.taskType, ids, id -> {
                            if (screen.taskTargetBox != null) screen.taskTargetBox.setValue(id);
                        }));
            }
        }).bounds(buttonX, y, 16, TaskRewardEditorScreen.FIELD_H).build());
    }

    private void addHeldItemFilterButton(int buttonX, int y) {
        screen.addWidgetBridge(Button.builder(ChroniclesUIKit.lit("§d⚡"), b -> applyHeldItemFilter())
                .bounds(buttonX + 18, y, 16, TaskRewardEditorScreen.FIELD_H)
                .tooltip(Tooltip.create(ChroniclesUIKit.lit(
                        "Use the configured filter token held in your hand\n(from the Item/Fluid Filter items) as this task's match rule")))
                .build());
    }

    private void addHeldFluidFilterButton(int buttonX, int y) {
        screen.addWidgetBridge(Button.builder(ChroniclesUIKit.lit("§d⚡"), b -> applyHeldFluidFilter())
                .bounds(buttonX + 18, y, 16, TaskRewardEditorScreen.FIELD_H)
                .tooltip(Tooltip.create(ChroniclesUIKit.lit(
                        "Use the configured filter token held in your hand\n(from the Item/Fluid Filter items) as this task's match rule")))
                .build());
    }

    private void addNbtBox(int x, int y, String value) {
        screen.taskNbtBox = new EditBox(Minecraft.getInstance().font, x, y, screen.colW,
                TaskRewardEditorScreen.FIELD_H, Component.empty());
        screen.setHintFitted(screen.taskNbtBox, "§8NBT filter (optional)");
        screen.taskNbtBox.setMaxLength(512);
        screen.taskNbtBox.setTooltip(net.minecraft.client.gui.components.Tooltip.create(
                ChroniclesUIKit.lit(
                        "Subset NBT match. Item must contain ALL keys listed here.\nLeave blank to match any stack of the item.")));
        screen.taskNbtBox.setValue(value);
        screen.addWidgetBridge(screen.taskNbtBox);
    }

    private void addSecondRow(TaskFormType form, int x, int y, String value) {
        SecondPicker picker = form.secondPicker();
        int boxWidth = (picker == SecondPicker.DIMENSION || picker == SecondPicker.ENERGY_SOURCE ||
                picker == SecondPicker.RECIPE) ? screen.colW - 18 : screen.colW;
        int buttonX = x + boxWidth;

        screen.taskSecondaryBox = new EditBox(Minecraft.getInstance().font, x, y, boxWidth,
                TaskRewardEditorScreen.FIELD_H, Component.empty());
        screen.setHintFitted(screen.taskSecondaryBox, form.secondHint());
        screen.taskSecondaryBox.setMaxLength(128);
        screen.taskSecondaryBox.setValue(value);

        if (picker == SecondPicker.MODE_TOGGLE) {
            addModeToggleButton(x, y, value);
        } else {
            screen.addWidgetBridge(screen.taskSecondaryBox);
        }

        switch (picker) {
            case DIMENSION -> addDimensionPickerButton(buttonX, y);
            case RECIPE -> addRecipePickerButton(buttonX, y);
            case ENERGY_SOURCE -> addEnergySourceCycleButton(buttonX, y);
            case MODE_TOGGLE, NONE -> {}
        }
    }

    private void addModeToggleButton(int x, int y, String value) {
        String curMode = value.trim().equalsIgnoreCase("RIGHT_CLICK") ? "RIGHT_CLICK" : "PLACE";
        screen.taskSecondaryBox.setValue(curMode);
        screen.addWidgetBridge(Button.builder(ChroniclesUIKit.lit("§fMode: §e" + curMode), b -> {
            String next = screen.taskSecondaryBox.getValue().equalsIgnoreCase("PLACE") ? "RIGHT_CLICK" :
                    "PLACE";
            screen.taskSecondaryBox.setValue(next);
            b.setMessage(ChroniclesUIKit.lit("§fMode: §e" + next));
        }).bounds(x, y, screen.colW, TaskRewardEditorScreen.FIELD_H)
                .tooltip(Tooltip.create(ChroniclesUIKit.lit(
                        "PLACE: the player places the block.\nRIGHT_CLICK: the player right-clicks it.\n" +
                                "Click to switch.")))
                .build());
    }

    private void addDimensionPickerButton(int buttonX, int y) {
        screen.addWidgetBridge(Button.builder(ChroniclesUIKit.lit("§7⊞"), b -> {
            if (Minecraft.getInstance() == null || Minecraft.getInstance().getConnection() == null) return;
            java.util.Collection<ResourceLocation> ids = Minecraft.getInstance().getConnection().levels()
                    .stream()
                    .map(k -> k.location()).toList();
            Minecraft.getInstance().setScreen(new RegistryIdPickerScreen(screen, "Pick dimension", ids, id -> {
                if (screen.taskSecondaryBox != null) screen.taskSecondaryBox.setValue(id.toString());
            }));
        }).bounds(buttonX, y, 16, TaskRewardEditorScreen.FIELD_H).build());
    }

    private void addRecipePickerButton(int buttonX, int y) {
        screen.addWidgetBridge(Button.builder(ChroniclesUIKit.lit("§7⊞"), b -> {
            if (Minecraft.getInstance() == null || Minecraft.getInstance().level == null) return;
            ResourceLocation wantedType = screen.taskTargetBox != null ?
                    ResourceLocation.tryParse(screen.taskTargetBox.getValue().trim()) : null;
            java.util.List<ResourceLocation> ids = new ArrayList<>();
            for (net.minecraft.world.item.crafting.RecipeHolder<?> recipe : Minecraft.getInstance().level
                    .getRecipeManager()
                    .getRecipes()) {
                ResourceLocation typeKey = net.minecraft.core.registries.BuiltInRegistries.RECIPE_TYPE
                        .getKey(recipe.value().getType());
                if (wantedType == null || wantedType.equals(typeKey)) ids.add(recipe.id());
            }
            Minecraft.getInstance().setScreen(new RegistryIdPickerScreen(screen,
                    wantedType != null ? "Pick " + wantedType + " recipe" : "Pick recipe", ids, id -> {
                        if (screen.taskSecondaryBox != null) screen.taskSecondaryBox.setValue(id.toString());
                    }));
        }).bounds(buttonX, y, 16, TaskRewardEditorScreen.FIELD_H)
                .tooltip(Tooltip.create(ChroniclesUIKit.lit(
                        "Browse the recipes of the type above (fill the type in first to narrow the list)")))
                .build());
    }

    private void addEnergySourceCycleButton(int buttonX, int y) {
        screen.addWidgetBridge(Button.builder(ChroniclesUIKit.lit("§6⟳"), b -> {
            if (screen.taskSecondaryBox == null) return;
            String[] options = { "INVENTORY", "HELD", "BLOCK" };
            String cur = screen.taskSecondaryBox.getValue().trim().toUpperCase();
            int idx = java.util.Arrays.asList(options).indexOf(cur);
            screen.taskSecondaryBox.setValue(options[(idx + 1) % options.length]);
        }).bounds(buttonX, y, 16, TaskRewardEditorScreen.FIELD_H)
                .tooltip(Tooltip.create(ChroniclesUIKit.lit("Cycle INVENTORY / HELD / BLOCK")))
                .build());
    }

    private void addBottomRow(TaskFormType form, int x, String countValue) {
        int y = screen.formBottom - TaskRewardEditorScreen.FIELD_H - 4;
        int gap = 4;
        int buttonsX = x;
        int buttonsWidth = screen.colW;

        if (form.needsCount()) {
            addCountBox(x, y, form.countHint(), countValue);
            buttonsX = x + 52 + gap;
            buttonsWidth = screen.colW - 52 - gap;
        }

        List<Button.Builder> buttons = new ArrayList<>();
        List<Integer> idealWidths = new ArrayList<>();
        if (form.showConsume()) {
            buttons.add(consumeButton());
            idealWidths.add(54);
        }
        if (AE2Compat.isAvailable() && form.showAe2()) {
            buttons.add(ae2Button());
            idealWidths.add(40);
        }
        if (form.showSticky()) {
            buttons.add(stickyButton());
            idealWidths.add(56);
        }
        buttons.add(optionalButton());
        idealWidths.add(50);
        buttons.add(hideButton());
        idealWidths.add(62);
        buttons.add(submitButton());
        idealWidths.add(46);

        placeButtonsInRow(buttons, idealWidths, buttonsX, y, buttonsWidth, gap);
    }

    private void addCountBox(int x, int y, String hint, String value) {
        screen.taskCountBox = new EditBox(Minecraft.getInstance().font, x, y, 52, TaskRewardEditorScreen.FIELD_H,
                Component.empty());
        screen.setHintFitted(screen.taskCountBox, hint);
        screen.taskCountBox.setMaxLength(8);
        screen.taskCountBox.setValue(value);
        screen.addWidgetBridge(screen.taskCountBox);
    }

    private void placeButtonsInRow(List<Button.Builder> buttons, List<Integer> idealWidths, int x, int y,
                                   int availableWidth, int gap) {
        int minWidth = 26;
        int idealTotal = gap * (buttons.size() - 1);
        for (int width : idealWidths) idealTotal += width;

        double scale = 1.0;
        if (idealTotal > availableWidth) {
            scale = Math.max(minWidth * buttons.size() / (double) idealTotal, availableWidth / (double) idealTotal);
        }

        int cursor = x;
        for (int i = 0; i < buttons.size(); i++) {
            int width = Math.max(minWidth, (int) Math.round(idealWidths.get(i) * scale));
            screen.addWidgetBridge(buttons.get(i).bounds(cursor, y, width, TaskRewardEditorScreen.FIELD_H).build());
            cursor += width + gap;
        }
    }

    private Button.Builder consumeButton() {
        return Button.builder(ChroniclesUIKit.lit(taskConsume ? "§aConsume" : "§8Consume"), b -> {
            taskConsume = !taskConsume;
            screen.rebuildWidgets();
        }).tooltip(Tooltip.create(ChroniclesUIKit.lit(
                "Item tasks: the player clicks the task to hand the items in (shop style).\n" +
                        "Other types: taken when rewards are claimed.")));
    }

    private Button.Builder ae2Button() {
        return Button.builder(ChroniclesUIKit.lit(taskCheckAe2Storage ? "§bAE2" : "§8AE2"), b -> {
            taskCheckAe2Storage = !taskCheckAe2Storage;
            screen.rebuildWidgets();
        }).tooltip(Tooltip.create(ChroniclesUIKit.lit(
                "ON (default when AE2 is installed): also count/withdraw matching items or\n" +
                        "fluid stored in your linked Applied Energistics 2 ME network, in addition\n" +
                        "to the player's inventory.")));
    }

    private Button.Builder stickyButton() {
        return Button.builder(ChroniclesUIKit.lit(taskSticky ? "§bSticky" : "§8Sticky"), b -> {
            taskSticky = !taskSticky;
            screen.rebuildWidgets();
        }).tooltip(Tooltip.create(ChroniclesUIKit.lit(
                "ON (default): once satisfied, stays satisfied - placing/using the item\n" +
                        "later won't un-complete this task.\n" +
                        "OFF: re-checked live - task un-completes if you stop holding enough.")));
    }

    private Button.Builder optionalButton() {
        return Button.builder(ChroniclesUIKit.lit(taskOptional ? "§eOptional" : "§8Optional"), b -> {
            taskOptional = !taskOptional;
            screen.rebuildWidgets();
        }).tooltip(Tooltip.create(ChroniclesUIKit.lit("Task is optional: won't block quest completion")));
    }

    private Button.Builder hideButton() {
        return Button.builder(screen.hideLabel(taskHiddenParts), b -> {
            taskHiddenParts = (taskHiddenParts + 1) % 4;
            screen.rebuildWidgets();
        }).tooltip(Tooltip.create(ChroniclesUIKit.lit(
                TaskRewardEditorScreen.HIDE_TOOLTIP.formatted("task is completed"))));
    }

    private Button.Builder submitButton() {
        boolean editing = screen.editingTaskIndex >= 0;
        return Button.builder(ChroniclesUIKit.lit(editing ? "§b✎ Update" : "§a✔ Add"), b -> commitTaskFromForm())
                .tooltip(Tooltip.create(
                        ChroniclesUIKit.lit(editing ? "Save changes to this task (right-click it again to cancel)" :
                                "Add this task to the quest (Ctrl+Z to undo)")));
    }

    private boolean applyPickedItemFilter(ItemStack stack) {
        if (!(stack.getItem() instanceof ItemFilterTokenItem)) return false;
        IItemFilter filter = ItemFilterTokenItem.getFilter(stack);
        if (filter == null) return false;
        screen.taskType = "filter_item";
        screen.pendingPickedItemFilter = filter;
        screen.pendingTaskTarget = filter.describe();
        screen.forcePendingTaskValues = true;
        screen.rebuildWidgets();
        return true;
    }

    private void applyHeldItemFilter() {
        if (Minecraft.getInstance() == null || Minecraft.getInstance().player == null) return;
        if (applyPickedItemFilter(Minecraft.getInstance().player.getMainHandItem())) return;
        applyPickedItemFilter(Minecraft.getInstance().player.getOffhandItem());
    }

    private void applyHeldFluidFilter() {
        if (Minecraft.getInstance() == null || Minecraft.getInstance().player == null) return;
        ItemStack held = Minecraft.getInstance().player.getMainHandItem();
        IFluidFilter filter = held.getItem() instanceof FluidFilterTokenItem ?
                FluidFilterTokenItem.getFilter(held) : null;
        if (filter == null) {
            held = Minecraft.getInstance().player.getOffhandItem();
            filter = held.getItem() instanceof FluidFilterTokenItem ? FluidFilterTokenItem.getFilter(held) : null;
        }
        if (filter == null) return;
        screen.taskType = "filter_fluid";
        screen.pendingPickedFluidFilter = filter;
        screen.pendingTaskTarget = filter.describe();
        screen.forcePendingTaskValues = true;
        screen.rebuildWidgets();
    }

    private void bulkAddItemTasks(List<ItemStack> picked) {
        if (screen.editingTaskIndex >= 0 || screen.taskTargetBox == null) return;
        String descTemplate = screen.taskDescBox != null ? screen.taskDescBox.getValue().trim() : "";
        String countVal = screen.taskCountBox != null ? screen.taskCountBox.getValue() : "1";
        boolean optional = taskOptional, consume = taskConsume, sticky = taskSticky;
        boolean ae2 = taskCheckAe2Storage;
        int hidden = taskHiddenParts;
        for (ItemStack stack : picked) {
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
            if (id == null || screen.taskTargetBox == null) continue;
            String name = stack.getHoverName().getString();
            String verb = screen.taskType.equals("craft_item") ? "Craft " : "Collect ";
            screen.taskTargetBox.setValue(id.toString());
            if (screen.taskDescBox != null) {
                screen.taskDescBox
                        .setValue(descTemplate.isEmpty() ? verb + name : descTemplate.replace("{item}", name));
            }
            if (screen.taskCountBox != null) screen.taskCountBox.setValue(countVal);
            taskOptional = optional;
            taskConsume = consume;
            taskSticky = sticky;
            taskCheckAe2Storage = ae2;
            taskHiddenParts = hidden;
            commitTaskFromForm();
        }
    }

    private void commitTaskFromForm() {
        String desc = screen.taskDescBox != null ? screen.taskDescBox.getValue().trim() : "";
        String target = screen.taskTargetBox != null ? screen.taskTargetBox.getValue().trim() : "";
        String second = screen.taskSecondaryBox != null ? screen.taskSecondaryBox.getValue().trim() : "";
        String countS = screen.taskCountBox != null ? screen.taskCountBox.getValue().trim() : "1";
        int count = 1;
        try {
            count = Math.max(1, Integer.parseInt(countS));
        } catch (NumberFormatException ignored) {}

        if (desc.isEmpty() && screen.taskType.equals("item_check") && !target.isEmpty()) {
            try {
                Item targetItem = BuiltInRegistries.ITEM.get(ResourceLocation.parse(target));
                if (targetItem != null) {
                    String itemName = new net.minecraft.world.item.ItemStack(targetItem).getHoverName().getString();
                    desc = count > 1 ? "Collect " + count + "x " + itemName : "Collect " + itemName;
                    if (screen.taskDescBox != null) screen.taskDescBox.setValue(desc);
                }
            } catch (Exception ignored) {}
        }

        TaskFormType form = TaskFormTypes.get(screen.taskType);
        if (desc.isEmpty() || (form.needsTarget() && !form.targetOptional() && target.isEmpty())) return;

        ResourceLocation taskId = (screen.editingTaskIndex >= 0 && screen.editingTaskIndex < screen.tasks.size()) ?
                screen.tasks.get(screen.editingTaskIndex).getTaskId() :
                ResourceLocation.fromNamespaceAndPath("phoenix_chronicles", "task_" + screen.taskType + "_" +
                        java.util.UUID.randomUUID().toString().replace("-", ""));
        TaskFormValues values = new TaskFormValues();
        values.taskId = taskId;
        values.description = ChroniclesUIKit.lit(desc);
        values.target = target;
        values.second = second;
        values.nbt = screen.taskNbtBox != null ? screen.taskNbtBox.getValue().trim() : "";
        values.count = count;
        values.consume = taskConsume;
        values.sticky = taskSticky;
        values.checkAe2 = taskCheckAe2Storage;
        values.itemFilter = screen.pendingPickedItemFilter;
        values.fluidFilter = screen.pendingPickedFluidFilter;
        QuestTask task = null;
        try {
            task = form.create(values);
        } catch (Exception ignored) {}

        if (task != null) {
            task.setOptional(taskOptional);
            task.setHiddenParts(taskHiddenParts);
            form.applySticky(task, taskSticky);
            QuestTask finalTask = task;
            screen.pushUndo(() -> {
                if (screen.editingTaskIndex >= 0 && screen.editingTaskIndex < screen.tasks.size()) {
                    screen.tasks.set(screen.editingTaskIndex, finalTask);
                } else {
                    screen.tasks.add(finalTask);
                    screen.taskListScroll = Integer.MAX_VALUE;
                }
                screen.editingTaskIndex = -1;
            });
            screen.taskTypeDropOpen = false;
            taskOptional = false;
            taskHiddenParts = 0;
            taskSticky = true;
            taskConsume = false;
            taskCheckAe2Storage = AE2Compat.isAvailable();
            screen.pendingTaskDesc = screen.pendingTaskTarget = screen.pendingTaskSecondary = screen.pendingTaskNbt = "";
            screen.pendingTaskCount = "1";
            screen.pendingPickedItemFilter = null;
            screen.pendingPickedFluidFilter = null;
            screen.forcePendingTaskValues = true;
            screen.rebuildWidgets();
        }
    }

    void startEditingTask(int idx) {
        if (idx < 0 || idx >= screen.tasks.size()) return;
        screen.pendingPickedItemFilter = null;
        screen.pendingPickedFluidFilter = null;
        QuestTask t = screen.tasks.get(idx);
        screen.editingTaskIndex = idx;
        screen.taskType = TaskFormTypes.idFor(t);
        taskOptional = t.isOptional();
        taskHiddenParts = t.getHiddenParts();

        TaskFormValues values = new TaskFormValues();
        values.consume = true;
        values.sticky = true;
        values.checkAe2 = AE2Compat.isAvailable();
        TaskFormTypes.get(screen.taskType).load(t, values);
        taskConsume = values.consume;
        taskSticky = values.sticky;
        taskCheckAe2Storage = values.checkAe2;
        screen.pendingTaskDesc = t.getDescriptionRaw().getString();
        screen.pendingTaskTarget = values.target;
        screen.pendingTaskSecondary = values.second;
        screen.pendingTaskCount = String.valueOf(values.count);
        screen.pendingTaskNbt = values.nbt;

        screen.forcePendingTaskValues = true;
        screen.taskTypeDropOpen = false;
        screen.rewardTypeDropOpen = false;
        screen.rebuildWidgets();
    }

    void resetFieldsForTypeChange() {
        screen.pendingTaskDesc = screen.taskDescBox != null ? screen.taskDescBox.getValue() : "";
        screen.pendingTaskTarget = screen.pendingTaskSecondary = screen.pendingTaskCount = screen.pendingTaskNbt = "";
        screen.forcePendingTaskValues = true;
    }

    void cancelTaskEdit() {
        screen.editingTaskIndex = -1;
        taskOptional = false;
        taskHiddenParts = 0;
        taskCheckAe2Storage = AE2Compat.isAvailable();
        screen.pendingTaskDesc = screen.pendingTaskTarget = screen.pendingTaskSecondary = screen.pendingTaskCount = screen.pendingTaskNbt = "";
        screen.pendingPickedItemFilter = null;
        screen.pendingPickedFluidFilter = null;
        screen.forcePendingTaskValues = true;
        screen.rebuildWidgets();
    }

    private PhoenixTaskRegistry.TaskEntry getTaskMeta(String typeId) {
        PhoenixTaskRegistry.TaskEntry e = PhoenixTaskRegistry.get(typeId);
        List<PhoenixTaskRegistry.TaskEntry> all = PhoenixTaskRegistry.getEditorTypes();
        return e != null ? e : (all.isEmpty() ? null : all.get(0));
    }

    PhoenixTaskRegistry.TaskEntry getTaskMetaByClass(QuestTask task) {
        try {
            String typeId = task.serializeNBT().getString("type");
            return getTaskMeta(typeId);
        } catch (Exception ignored) {}
        List<PhoenixTaskRegistry.TaskEntry> all = PhoenixTaskRegistry.getEditorTypes();
        return all.isEmpty() ? null : all.get(0);
    }

    ItemStack getTaskIconStack(QuestTask task) {
        ResourceLocation id = task.getDisplayItemId();
        if (id == null) return ItemStack.EMPTY;
        Item item = BuiltInRegistries.ITEM.get(id);
        if (item == null || item == Items.AIR) return ItemStack.EMPTY;
        ItemStack stack = new ItemStack(item);

        if (task instanceof ItemRequirementTask t && t.getNbtFilter() != null && !t.getNbtFilter().isEmpty()) {
            ItemData.setTag(stack, t.getNbtFilter().copy());
        }
        return stack;
    }

    @Nullable
    String getTaskDetailString(QuestTask task) {
        return TaskFormTypes.detail(task);
    }

    private boolean taskConsume = false;

    private boolean taskOptional = false;

    private int taskHiddenParts = 0;

    private boolean taskSticky = true;

    private boolean taskCheckAe2Storage = AE2Compat.isAvailable();
}
