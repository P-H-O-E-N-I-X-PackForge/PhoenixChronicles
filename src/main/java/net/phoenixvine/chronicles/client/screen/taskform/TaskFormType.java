package net.phoenixvine.chronicles.client.screen.taskform;

import net.minecraft.resources.ResourceLocation;
import net.phoenixvine.chronicles.common.model.QuestTask;

import org.jetbrains.annotations.Nullable;

import java.util.Collection;

public final class TaskFormType {

    public enum TargetPicker {
        NONE,
        ITEM,
        ITEM_LIST,
        FLUID,
        FLUID_LIST,
        BLOCK,
        ENTITY,
        REGISTRY,
        STRING,
        RECIPE_TYPE,
        ENERGY_TYPE
    }

    public enum SecondPicker {
        NONE,
        DIMENSION,
        ENERGY_SOURCE,
        RECIPE,
        MODE_TOGGLE
    }

    final String id;
    final Class<? extends QuestTask> taskClass;

    boolean needsTarget = false;
    boolean targetOptional = false;
    String targetHint = "§8Target id";
    int maxTargetLength = 160;
    TargetPicker targetPicker = TargetPicker.NONE;

    boolean needsSecond = false;
    String secondHint = "§8Secondary value";
    SecondPicker secondPicker = SecondPicker.NONE;

    boolean needsCount = false;
    String countHint = "§8Count";

    boolean nbtBox = false;
    boolean showConsume = false;
    boolean showSticky = false;
    boolean showAe2 = false;
    boolean showBlock = false;

    boolean generic = false;
    boolean canCreate = true;

    TaskFormType(String id, Class<? extends QuestTask> taskClass) {
        this.id = id;
        this.taskClass = taskClass;
    }

    void target(String hint, TargetPicker picker) {
        this.needsTarget = true;
        this.targetHint = hint;
        this.targetPicker = picker;
    }

    void second(String hint, SecondPicker picker) {
        this.needsSecond = true;
        this.secondHint = hint;
        this.secondPicker = picker;
    }

    void count(String hint) {
        this.needsCount = true;
        this.countHint = hint;
    }

    public String id() {
        return id;
    }

    public boolean handles(QuestTask task) {
        return taskClass != null && taskClass.isInstance(task);
    }

    public boolean needsTarget() {
        return needsTarget;
    }

    public boolean targetOptional() {
        return targetOptional;
    }

    public String targetHint() {
        return targetHint;
    }

    public int maxTargetLength() {
        return maxTargetLength;
    }

    public TargetPicker targetPicker() {
        return targetPicker;
    }

    public boolean needsSecond() {
        return needsSecond;
    }

    public String secondHint() {
        return secondHint;
    }

    public SecondPicker secondPicker() {
        return secondPicker;
    }

    public boolean needsCount() {
        return needsCount;
    }

    public String countHint() {
        return countHint;
    }

    public boolean hasNbtBox() {
        return nbtBox;
    }

    public boolean showConsume() {
        return showConsume;
    }

    public boolean showSticky() {
        return showSticky;
    }

    public boolean showAe2() {
        return showAe2;
    }

    public boolean showBlock() {
        return showBlock;
    }

    @Nullable
    public Collection<ResourceLocation> registryIds() {
        return TaskFormTypes.registryIds(this);
    }

    @Nullable
    public Collection<String> stringIds() {
        return TaskFormTypes.stringIds(this);
    }

    @Nullable
    public QuestTask create(TaskFormValues v) throws Exception {
        return TaskFormTypes.create(this, v);
    }

    public void load(QuestTask task, TaskFormValues v) {
        TaskFormTypes.load(this, task, v);
    }

    public void applySticky(QuestTask task, boolean sticky) {
        TaskFormTypes.setSticky(task, sticky);
    }

    @Nullable
    public String detail(QuestTask task) {
        return TaskFormTypes.detail(this, task);
    }
}
