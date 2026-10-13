package net.phoenixvine.chronicles.integration.kubejs;

import net.neoforged.fml.loading.FMLEnvironment;
import net.phoenixvine.chronicles.QuestAPI;
import net.phoenixvine.chronicles.client.render.IQuestBackground;
import net.phoenixvine.chronicles.client.render.background.BackgroundEffect;
import net.phoenixvine.chronicles.client.render.background.BackgroundEffects;
import net.phoenixvine.chronicles.client.render.background.GlitchShearBackground;
import net.phoenixvine.chronicles.client.render.background.QuestBackgroundBuilder;
import net.phoenixvine.chronicles.client.render.background.SunBackground;
import net.phoenixvine.chronicles.common.event.PhoenixQuestScriptRewardEvent;
import net.phoenixvine.chronicles.common.event.QuestEvent;
import net.phoenixvine.chronicles.common.flag.PhoenixQuestFlags;
import net.phoenixvine.chronicles.common.model.QuestNode;
import net.phoenixvine.chronicles.common.model.QuestState;
import net.phoenixvine.chronicles.common.model.QuestTask;
import net.phoenixvine.chronicles.common.registry.PhoenixTaskRegistry;
import net.phoenixvine.chronicles.common.registry.QuestBackgroundRegistry;
import net.phoenixvine.chronicles.common.tasks.ScriptTask;

import dev.latvian.mods.kubejs.plugin.ClassFilter;
import dev.latvian.mods.kubejs.plugin.KubeJSPlugin;
import dev.latvian.mods.kubejs.script.BindingRegistry;

public class PhoenixChroniclesKubeJSPlugin implements KubeJSPlugin {

    @Override
    public void registerClasses(ClassFilter filter) {
        filter.allow(QuestAPI.class);
        filter.allow(QuestState.class);
        filter.allow(QuestNode.class);

        filter.allow(QuestEvent.class);
        filter.allow(PhoenixQuestScriptRewardEvent.class);

        filter.allow(QuestTask.class);
        filter.allow(ScriptTask.class);
        filter.allow(PhoenixTaskRegistry.class);
        filter.allow(PhoenixTaskRegistry.Builder.class);
        filter.allow(PhoenixTaskRegistry.FieldDef.class);
        filter.allow(PhoenixQuestFlags.class);
        filter.allow(net.phoenixvine.chronicles.common.registry.PhoenixRewardRegistry.class);
        filter.allow(net.phoenixvine.chronicles.common.registry.PhoenixRewardRegistry.Builder.class);
        filter.allow(net.phoenixvine.chronicles.integration.archive.ArchiveEntries.class);
        filter.allow(net.phoenixvine.chronicles.integration.archive.ArchiveEntries.Builder.class);

        if (FMLEnvironment.dist.isClient()) {
            filter.allow(QuestBackgroundRegistry.class);
            filter.allow(QuestBackgroundBuilder.class);
            filter.allow(QuestBackgroundBuilder.BlendMode.class);
            filter.allow(BackgroundEffects.class);
            filter.allow(BackgroundEffect.class);
            filter.allow(IQuestBackground.class);
            filter.allow(SunBackground.class);
            filter.allow(GlitchShearBackground.class);
        }
    }

    @Override
    public void registerBindings(BindingRegistry registry) {
        registry.add("QuestAPI", QuestAPI.class);
        registry.add("PhoenixTaskRegistry", PhoenixTaskRegistry.class);
        registry.add("PhoenixQuestFlags", PhoenixQuestFlags.class);
        registry.add("PhoenixRewardRegistry", net.phoenixvine.chronicles.common.registry.PhoenixRewardRegistry.class);
        registry.add("ArchiveEntries", net.phoenixvine.chronicles.integration.archive.ArchiveEntries.class);

        if (FMLEnvironment.dist.isClient()) {
            registry.add("QuestBackgroundRegistry", QuestBackgroundRegistry.class);
            registry.add("QuestBackgroundBuilder", QuestBackgroundBuilder.class);
            registry.add("BackgroundEffects", BackgroundEffects.class);
            registry.add("SunBackground", SunBackground.class);
            registry.add("GlitchShearBackground", GlitchShearBackground.class);
        }
    }
}
