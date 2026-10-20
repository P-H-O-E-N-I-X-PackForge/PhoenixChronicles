package net.phoenixvine.chronicles.client.screen.taskform;

import net.minecraft.resources.ResourceLocation;
import net.phoenixvine.chronicles.common.model.QuestTask;

import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

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

    @FunctionalInterface
    public interface Factory {

        @Nullable
        QuestTask create(TaskFormValues v) throws Exception;
    }

    private final String id;
    private final Class<? extends QuestTask> taskClass;
    private final boolean needsTarget;
    private final boolean targetOptional;
    private final String targetHint;
    private final int maxTargetLength;
    private final TargetPicker targetPicker;
    private final boolean needsSecond;
    private final String secondHint;
    private final SecondPicker secondPicker;
    private final boolean needsCount;
    private final String countHint;
    private final boolean nbtBox;
    private final boolean showConsume;
    private final boolean showSticky;
    private final boolean showAe2;
    private final boolean showBlock;
    private final Supplier<Collection<ResourceLocation>> registryIds;
    private final Supplier<Collection<String>> stringIds;
    private final Factory factory;
    private final BiConsumer<QuestTask, TaskFormValues> loader;
    private final Function<QuestTask, String> detail;
    private final Predicate<QuestTask> stickyGetter;
    private final BiConsumer<QuestTask, Boolean> stickySetter;
    private final Predicate<QuestTask> ae2Getter;

    private TaskFormType(Builder<?> b) {
        this.id = b.id;
        this.taskClass = b.taskClass;
        this.needsTarget = b.needsTarget;
        this.targetOptional = b.targetOptional;
        this.targetHint = b.targetHint;
        this.maxTargetLength = b.maxTargetLength;
        this.targetPicker = b.targetPicker;
        this.needsSecond = b.needsSecond;
        this.secondHint = b.secondHint;
        this.secondPicker = b.secondPicker;
        this.needsCount = b.needsCount;
        this.countHint = b.countHint;
        this.nbtBox = b.nbtBox;
        this.showConsume = b.showConsume;
        this.showSticky = b.stickyGetter != null;
        this.showAe2 = b.ae2Getter != null;
        this.showBlock = b.showBlock;
        this.registryIds = b.registryIds;
        this.stringIds = b.stringIds;
        this.factory = b.factory;
        this.loader = b.loader;
        this.detail = b.detail;
        this.stickyGetter = b.stickyGetter;
        this.stickySetter = b.stickySetter;
        this.ae2Getter = b.ae2Getter;
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
        return registryIds == null ? null : registryIds.get();
    }

    @Nullable
    public Collection<String> stringIds() {
        return stringIds == null ? null : stringIds.get();
    }

    @Nullable
    public QuestTask create(TaskFormValues v) throws Exception {
        return factory == null ? null : factory.create(v);
    }

    public void load(QuestTask task, TaskFormValues v) {
        if (stickyGetter != null) v.sticky = stickyGetter.test(task);
        if (ae2Getter != null) v.checkAe2 = ae2Getter.test(task);
        if (loader != null) loader.accept(task, v);
    }

    public void applySticky(QuestTask task, boolean sticky) {
        if (stickySetter != null) stickySetter.accept(task, sticky);
    }

    @Nullable
    public String detail(QuestTask task) {
        return detail == null ? null : detail.apply(task);
    }

    public static <T extends QuestTask> Builder<T> of(String id, Class<T> taskClass) {
        return new Builder<>(id, taskClass);
    }

    public static final class Builder<T extends QuestTask> {

        private final String id;
        private final Class<T> taskClass;
        private boolean needsTarget = false;
        private boolean targetOptional = false;
        private String targetHint = "§8Target id";
        private int maxTargetLength = 160;
        private TargetPicker targetPicker = TargetPicker.NONE;
        private boolean needsSecond = false;
        private String secondHint = "§8Secondary value";
        private SecondPicker secondPicker = SecondPicker.NONE;
        private boolean needsCount = false;
        private String countHint = "§8Count";
        private boolean nbtBox = false;
        private boolean showConsume = false;
        private Supplier<Collection<ResourceLocation>> registryIds;
        private Supplier<Collection<String>> stringIds;
        private Factory factory;
        private BiConsumer<QuestTask, TaskFormValues> loader;
        private Function<QuestTask, String> detail;
        private Predicate<QuestTask> stickyGetter;
        private BiConsumer<QuestTask, Boolean> stickySetter;
        private Predicate<QuestTask> ae2Getter;
        private boolean showBlock = false;

        private Builder(String id, Class<T> taskClass) {
            this.id = id;
            this.taskClass = taskClass;
        }

        public Builder<T> target(String hint) {
            this.needsTarget = true;
            this.targetHint = hint;
            return this;
        }

        public Builder<T> targetOptional() {
            this.targetOptional = true;
            return this;
        }

        public Builder<T> maxTargetLength(int max) {
            this.maxTargetLength = max;
            return this;
        }

        public Builder<T> targetPicker(TargetPicker picker) {
            this.targetPicker = picker;
            return this;
        }

        public Builder<T> registryIds(Supplier<Collection<ResourceLocation>> ids) {
            this.registryIds = ids;
            return this;
        }

        public Builder<T> stringIds(Supplier<Collection<String>> ids) {
            this.stringIds = ids;
            return this;
        }

        public Builder<T> second(String hint, SecondPicker picker) {
            this.needsSecond = true;
            this.secondHint = hint;
            this.secondPicker = picker;
            return this;
        }

        public Builder<T> count(String hint) {
            this.needsCount = true;
            this.countHint = hint;
            return this;
        }

        public Builder<T> nbtBox() {
            this.nbtBox = true;
            return this;
        }

        public Builder<T> consume() {
            this.showConsume = true;
            return this;
        }

        public Builder<T> sticky(Predicate<T> getter, BiConsumer<T, Boolean> setter) {
            this.stickyGetter = task -> getter.test(taskClass.cast(task));
            this.stickySetter = (task, value) -> setter.accept(taskClass.cast(task), value);
            return this;
        }

        public Builder<T> blockSource() {
            this.showBlock = true;
            return this;
        }

        public Builder<T> ae2(Predicate<T> getter) {
            this.ae2Getter = task -> getter.test(taskClass.cast(task));
            return this;
        }

        public Builder<T> create(Factory factory) {
            this.factory = factory;
            return this;
        }

        public Builder<T> load(BiConsumer<T, TaskFormValues> loader) {
            this.loader = (task, v) -> loader.accept(taskClass.cast(task), v);
            return this;
        }

        public Builder<T> detail(Function<T, String> detail) {
            this.detail = task -> detail.apply(taskClass.cast(task));
            return this;
        }

        public TaskFormType build() {
            return new TaskFormType(this);
        }
    }
}
