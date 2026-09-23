package net.phoenixvine.chronicles.client.rich;

import net.phoenixvine.wiki.client.rich.RichBlock;
import net.phoenixvine.wiki.client.rich.WikiMarkdownParser;
import net.phoenixvine.wiki.client.rich.markdown.HeadingSectionGrouper;

import java.util.ArrayList;
import java.util.List;

public final class ChroniclesMarkdown {

    private ChroniclesMarkdown() {}

    public static List<RichBlock> parse(String input) {
        return fixupConditionals(WikiMarkdownParser.parse(input, false, true));
    }

    private static List<RichBlock> fixupConditionals(List<RichBlock> blocks) {
        List<RichBlock> out = new ArrayList<>(blocks.size());
        for (RichBlock b : blocks) out.add(fixupOne(b));
        return out;
    }

    private static RichBlock fixupOne(RichBlock b) {
        if (b instanceof ChroniclesConditionalSection cs) {
            return new ChroniclesConditionalSection(cs.condition(),
                    fixupConditionals(HeadingSectionGrouper.group(cs.thenChildren())),
                    fixupConditionals(HeadingSectionGrouper.group(cs.elseChildren())));
        } else if (b instanceof RichBlock.Callout c) {
            return new RichBlock.Callout(c.type(), c.title(), fixupConditionals(c.children()));
        } else if (b instanceof RichBlock.Details d) {
            return new RichBlock.Details(d.expandKey(), d.title(), fixupConditionals(d.children()));
        } else if (b instanceof RichBlock.CollapsibleSection s) {
            return new RichBlock.CollapsibleSection(s.level(), s.headingSpans(), s.collapseKey(),
                    fixupConditionals(s.children()));
        }
        return b;
    }
}
