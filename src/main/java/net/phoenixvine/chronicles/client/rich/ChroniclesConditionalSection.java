package net.phoenixvine.chronicles.client.rich;

import net.phoenixvine.chronicles.common.condition.ConditionNode;
import net.phoenixvine.wiki.client.rich.RichBlock;
import net.phoenixvine.wiki.client.rich.RichSpan;

import java.util.List;

public record ChroniclesConditionalSection(ConditionNode condition, List<RichBlock> thenChildren,
                                           List<RichBlock> elseChildren)
        implements RichBlock {

    @Override
    public List<RichSpan> spans() {
        return List.of();
    }
}
