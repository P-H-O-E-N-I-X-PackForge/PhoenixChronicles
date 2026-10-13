package net.phoenixvine.chronicles.capability;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import net.phoenixvine.chronicles.PhoenixChronicles;

import java.util.Optional;
import java.util.function.Supplier;

public final class QuestCapabilityProvider {

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister
            .create(NeoForgeRegistries.ATTACHMENT_TYPES, PhoenixChronicles.MOD_ID);

    public static final Supplier<AttachmentType<PlayerQuestData>> PLAYER_QUESTS = ATTACHMENTS.register(
            "player_quests",
            () -> AttachmentType.serializable(PlayerQuestData::new).copyOnDeath().build());

    private QuestCapabilityProvider() {}

    public static Optional<PlayerQuestData> of(Entity entity) {
        return entity instanceof Player ? Optional.of(entity.getData(PLAYER_QUESTS)) : Optional.empty();
    }
}
