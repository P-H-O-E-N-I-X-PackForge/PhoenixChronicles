package net.phoenixvine.chronicles.client.screen.taskform;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.phoenixvine.chronicles.common.model.QuestTask;
import net.phoenixvine.chronicles.common.tasks.ExternalTriggerTask;
import net.phoenixvine.chronicles.common.tasks.MultiblockFormedTask;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TaskFormTypesTest {

    private static TaskFormValues values(String target, long count) {
        TaskFormValues v = new TaskFormValues();
        v.taskId = new ResourceLocation("phoenix_chronicles", "task_test");
        v.description = Component.literal("test");
        v.target = target;
        v.count = count;
        return v;
    }

    private static QuestTask create(String typeId, TaskFormValues v) throws Exception {
        QuestTask task = TaskFormTypes.get(typeId).create(v);
        assertNotNull(task, typeId);
        return task;
    }

    @Test
    void idForMatchesTheTypeThatCreatedTheTask() throws Exception {
        assertEquals("checkmark", TaskFormTypes.idFor(create("checkmark", values("", 1))));
        assertEquals("experience", TaskFormTypes.idFor(create("experience", values("", 5))));
        assertEquals("timer", TaskFormTypes.idFor(create("timer", values("", 30))));
        assertEquals("info", TaskFormTypes.idFor(create("info", values("hello", 1))));
        assertEquals("external_trigger", TaskFormTypes.idFor(create("external_trigger", values("mod:thing", 2))));
        assertEquals("gt_multiblock", TaskFormTypes.idFor(create("gt_multiblock", values("*", 1))));
        assertEquals("view_machine", TaskFormTypes.idFor(create("view_machine", values("gtceu:x", 3))));
    }

    @Test
    void externalTriggerRoundTrips() throws Exception {
        ExternalTriggerTask task = (ExternalTriggerTask) create("external_trigger", values("mod:thing", 4));
        TaskFormValues loaded = new TaskFormValues();
        TaskFormTypes.get("external_trigger").load(task, loaded);
        assertEquals("mod:thing", loaded.target);
        assertEquals(4, loaded.count);
    }

    @Test
    void multiblockAnyAndSpecificRoundTrip() throws Exception {
        MultiblockFormedTask any = (MultiblockFormedTask) create("gt_multiblock", values("*", 2));
        assertNull(any.getMachineId());
        TaskFormValues loaded = new TaskFormValues();
        TaskFormTypes.get("gt_multiblock").load(any, loaded);
        assertEquals("*", loaded.target);
        assertEquals(2, loaded.count);

        MultiblockFormedTask one = (MultiblockFormedTask) create("gt_multiblock",
                values("gtceu:electric_blast_furnace", 1));
        assertEquals("gtceu:electric_blast_furnace", one.getMachineId().toString());
        assertNull(TaskFormTypes.get("gt_multiblock").create(values("not a valid id!", 1)));
    }

    @Test
    void infoTimerExperienceAndMachineRoundTrip() throws Exception {
        TaskFormValues loaded = new TaskFormValues();
        TaskFormTypes.get("info").load(create("info", values("body text", 1)), loaded);
        assertEquals("body text", loaded.target);

        loaded = new TaskFormValues();
        TaskFormTypes.get("timer").load(create("timer", values("", 45)), loaded);
        assertEquals(45, loaded.count);

        loaded = new TaskFormValues();
        TaskFormTypes.get("experience").load(create("experience", values("", 7)), loaded);
        assertEquals(7, loaded.count);

        loaded = new TaskFormValues();
        TaskFormTypes.get("view_machine").load(create("view_machine", values("gtceu:x", 9)), loaded);
        assertEquals("gtceu:x", loaded.target);
        assertEquals(9, loaded.count);
    }

    @Test
    void formShapeMatchesWhatTheScreenUsedToHardcode() {
        assertFalse(TaskFormTypes.get("experience").needsTarget());
        assertFalse(TaskFormTypes.get("dimension").needsTarget());
        assertFalse(TaskFormTypes.get("checkmark").needsTarget());
        assertFalse(TaskFormTypes.get("timer").needsTarget());
        assertTrue(TaskFormTypes.get("timer").needsCount());
        assertTrue(TaskFormTypes.get("info").targetOptional());
        assertEquals(512, TaskFormTypes.get("info").maxTargetLength());
        assertTrue(TaskFormTypes.get("item_check").hasNbtBox());
        assertTrue(TaskFormTypes.get("item_check").showSticky());
        assertTrue(TaskFormTypes.get("item_check").showAe2());
        assertFalse(TaskFormTypes.get("tag_item").showAe2());
        assertTrue(TaskFormTypes.get("block_interact").needsSecond());
        assertEquals(TaskFormType.SecondPicker.MODE_TOGGLE, TaskFormTypes.get("block_interact").secondPicker());
        assertEquals(TaskFormType.TargetPicker.ITEM_LIST, TaskFormTypes.get("filter_item").targetPicker());
    }
}
