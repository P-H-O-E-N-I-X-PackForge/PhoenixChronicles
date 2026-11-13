package net.phoenixvine.chronicles.client.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.phoenixvine.chronicles.client.render.ChroniclesUIKit;
import net.phoenixvine.chronicles.common.condition.ConditionEvaluator;
import net.phoenixvine.chronicles.common.condition.ThresholdCondition;
import net.phoenixvine.chronicles.common.flag.PhoenixQuestFlags;
import net.phoenixvine.chronicles.common.model.*;
import net.phoenixvine.chronicles.common.tasks.*;

import java.util.ArrayList;
import java.util.List;

final class TaskDescriptions {

    private final QuestTasksScreen screen;

    TaskDescriptions(QuestTasksScreen screen) {
        this.screen = screen;
    }

    java.util.List<String> wrapSubtitle(String subtitle, int maxW) {
        if (subtitle == null || subtitle.isBlank()) return java.util.List.of();
        java.util.List<String> lines = net.phoenixvine.chronicles.client.util.EffectText.wrap(
                Minecraft.getInstance().font,
                net.phoenixvine.chronicles.client.render.ChroniclesThemePalette.adapt(subtitle), Math.max(20, maxW));
        return lines.size() > QuestTasksScreen.SUBTITLE_MAX_LINES ?
                lines.subList(0, QuestTasksScreen.SUBTITLE_MAX_LINES) : lines;
    }

    java.util.List<net.minecraft.util.FormattedCharSequence> buildAllDescLines(
                                                                               List<QuestTask> tasks,
                                                                               java.util.List<net.minecraft.util.FormattedCharSequence> questDescLines) {
        java.util.List<net.minecraft.util.FormattedCharSequence> all = new java.util.ArrayList<>();
        for (QuestTask task : tasks) {
            if (task instanceof InfoTask info) {
                String body = info.getBody();
                if (body != null && !body.isBlank()) {
                    all.addAll(splitRespectingNewlines(Minecraft.getInstance().font, body,
                            screen.compact.cardW() - QuestTasksScreen.CARD_PAD * 2));
                }
            }
        }
        if (!all.isEmpty() && !questDescLines.isEmpty()) {

            all.add(net.minecraft.util.FormattedCharSequence.EMPTY);
        }
        all.addAll(questDescLines);
        return all;
    }

    java.util.List<net.minecraft.util.FormattedCharSequence> splitRespectingNewlines(net.minecraft.client.gui.Font font,
                                                                                     String text, int maxW) {
        java.util.List<net.minecraft.util.FormattedCharSequence> out = new java.util.ArrayList<>();
        for (String line : text.split("\n", -1)) {
            if (line.isEmpty()) {
                out.add(net.minecraft.util.FormattedCharSequence.EMPTY);
            } else {
                out.addAll(font.split(ChroniclesUIKit.lit(line), maxW));
            }
        }
        return out;
    }

    java.util.List<net.phoenixvine.wiki.client.rich.RichBlock> resolveConditionals(
                                                                                   java.util.List<net.phoenixvine.wiki.client.rich.RichBlock> blocks) {
        java.util.List<net.phoenixvine.wiki.client.rich.RichBlock> out = new java.util.ArrayList<>(blocks.size());
        for (net.phoenixvine.wiki.client.rich.RichBlock b : blocks) {
            if (b instanceof net.phoenixvine.chronicles.client.rich.ChroniclesConditionalSection cs) {
                boolean met = ConditionEvaluator.evaluate(cs.condition(),
                        this::isConditionMet);
                out.addAll(resolveConditionals(met ? cs.thenChildren() : cs.elseChildren()));
            } else if (b instanceof net.phoenixvine.wiki.client.rich.RichBlock.Callout c) {
                out.add(new net.phoenixvine.wiki.client.rich.RichBlock.Callout(c.type(), c.title(),
                        resolveConditionals(c.children())));
            } else if (b instanceof net.phoenixvine.wiki.client.rich.RichBlock.Details d) {
                out.add(new net.phoenixvine.wiki.client.rich.RichBlock.Details(d.expandKey(), d.title(),
                        resolveConditionals(d.children())));
            } else if (b instanceof net.phoenixvine.wiki.client.rich.RichBlock.CollapsibleSection s) {
                out.add(new net.phoenixvine.wiki.client.rich.RichBlock.CollapsibleSection(s.level(),
                        resolveSpans(s.headingSpans()), s.collapseKey(), resolveConditionals(s.children())));
            } else if (b instanceof net.phoenixvine.wiki.client.rich.RichBlock.Heading h) {
                out.add(new net.phoenixvine.wiki.client.rich.RichBlock.Heading(h.level(),
                        resolveSpans(h.spans()), h.collapsible()));
            } else if (b instanceof net.phoenixvine.wiki.client.rich.RichBlock.Paragraph p) {
                out.add(new net.phoenixvine.wiki.client.rich.RichBlock.Paragraph(resolveSpans(p.spans())));
            } else if (b instanceof net.phoenixvine.wiki.client.rich.RichBlock.ListItem li) {
                out.add(new net.phoenixvine.wiki.client.rich.RichBlock.ListItem(li.marker(), li.indent(),
                        resolveSpans(li.spans())));
            } else if (b instanceof net.phoenixvine.wiki.client.rich.RichBlock.Checklist cl) {
                out.add(new net.phoenixvine.wiki.client.rich.RichBlock.Checklist(cl.checkKey(),
                        cl.checkedDefault(), cl.indent(), resolveSpans(cl.spans())));
            } else if (b instanceof net.phoenixvine.wiki.client.rich.RichBlock.Quote q) {
                out.add(new net.phoenixvine.wiki.client.rich.RichBlock.Quote(resolveSpans(q.spans())));
            } else if (b instanceof net.phoenixvine.wiki.client.rich.RichBlock.Table t) {
                java.util.List<java.util.List<net.phoenixvine.wiki.client.rich.RichSpan>> header = new java.util.ArrayList<>();
                for (var cell : t.header()) header.add(resolveSpans(cell));
                java.util.List<java.util.List<java.util.List<net.phoenixvine.wiki.client.rich.RichSpan>>> rows = new java.util.ArrayList<>();
                for (var row : t.rows()) {
                    java.util.List<java.util.List<net.phoenixvine.wiki.client.rich.RichSpan>> newRow = new java.util.ArrayList<>();
                    for (var cell : row) newRow.add(resolveSpans(cell));
                    rows.add(newRow);
                }
                out.add(new net.phoenixvine.wiki.client.rich.RichBlock.Table(header, rows));
            } else {
                out.add(b);
            }
        }
        return out;
    }

    private java.util.List<net.phoenixvine.wiki.client.rich.RichSpan> resolveSpans(
                                                                                   java.util.List<net.phoenixvine.wiki.client.rich.RichSpan> spans) {
        boolean anyConditional = false;
        for (net.phoenixvine.wiki.client.rich.RichSpan s : spans) {
            if (s instanceof net.phoenixvine.wiki.client.rich.RichSpan.ConditionalTip) {
                anyConditional = true;
                break;
            }
        }
        if (!anyConditional) return spans;

        java.util.List<net.phoenixvine.wiki.client.rich.RichSpan> out = new java.util.ArrayList<>(spans.size());
        for (net.phoenixvine.wiki.client.rich.RichSpan s : spans) {
            out.add(s instanceof net.phoenixvine.wiki.client.rich.RichSpan.ConditionalTip ct ?
                    resolveConditionalTip(ct) : s);
        }
        return out;
    }

    private net.phoenixvine.wiki.client.rich.RichSpan resolveConditionalTip(
                                                                            net.phoenixvine.wiki.client.rich.RichSpan.ConditionalTip ct) {
        for (net.phoenixvine.wiki.client.rich.RichSpan.TipCandidate candidate : ct.candidates()) {
            String expr = candidate.conditionExpr();
            if (expr == null || expr.isBlank()) {
                return new net.phoenixvine.wiki.client.rich.RichSpan.Tip(ct.label(), ct.style(),
                        candidate.tooltip());
            }
            try {
                if (ConditionEvaluator.evaluate(
                        net.phoenixvine.chronicles.common.condition.ConditionExprParser.parse(expr),
                        this::isConditionMet)) {
                    return new net.phoenixvine.wiki.client.rich.RichSpan.Tip(ct.label(), ct.style(),
                            candidate.tooltip());
                }
            } catch (net.phoenixvine.chronicles.common.condition.ConditionSyntaxException ignored) {}
        }

        return new net.phoenixvine.wiki.client.rich.RichSpan.Text(ct.label(), ct.style());
    }

    private boolean isConditionMet(String type, String value) {
        if (value == null || value.isEmpty() || Minecraft.getInstance().player == null) return false;
        if (type.equalsIgnoreCase("quest")) {
            return net.phoenixvine.chronicles.QuestAPI.isCompleted(Minecraft.getInstance().player, value);
        }
        if (type.equalsIgnoreCase("quest_unlocked")) {
            return net.phoenixvine.chronicles.QuestAPI.isUnlocked(Minecraft.getInstance().player, value);
        }
        if (type.equalsIgnoreCase("quest_progress")) {
            ThresholdCondition.Parsed p = ThresholdCondition
                    .parse(value);
            long percent = Math.round(net.phoenixvine.chronicles.QuestAPI.getProgress(Minecraft.getInstance().player,
                    p.id()) * 100f);
            return ThresholdCondition.test(percent, p.op(), p.threshold());
        }
        if (type.equalsIgnoreCase("flag")) {
            return PhoenixQuestFlags.evaluate(value, null, Minecraft.getInstance().player,
                    "quest text :::if");
        }
        return false;
    }

    static java.util.List<String> splitDescPages(String raw) {
        if (raw == null || raw.isEmpty()) return java.util.List.of("");
        String[] parts = QuestTasksScreen.DESC_PAGE_BREAK.split(raw);
        return parts.length == 0 ? java.util.List.of("") : java.util.List.of(parts);
    }

    void renderDescPager(GuiGraphics g, int x, int y, int w, int h, int mx, int my, int pageCount) {
        String pageLabel = "Page " + (screen.descPage + 1) + "/" + pageCount;
        int arrowW = Minecraft.getInstance().font.width("◀") + 8;
        int labelW = Minecraft.getInstance().font.width(pageLabel) + 10;
        int pw = arrowW * 2 + labelW;
        int ph = 14;
        int px = x + (w - pw) / 2;
        int py = y + h - ph - 2;

        boolean canPrev = screen.descPage > 0;
        boolean canNext = screen.descPage < pageCount - 1;
        boolean overLeft = mx >= px && mx < px + arrowW && my >= py && my < py + ph;
        boolean overRight = mx >= px + pw - arrowW && mx < px + pw && my >= py && my < py + ph;

        g.fill(px, py, px + pw, py + ph, 0xCC0A0A0E);
        screen.drawBorder(g, px, py, pw, ph);

        if (canPrev && overLeft) g.fill(px + 1, py + 1, px + arrowW, py + ph - 1, 0x33FFFFFF);
        if (canNext && overRight) g.fill(px + pw - arrowW, py + 1, px + pw - 1, py + ph - 1, 0x33FFFFFF);

        g.fill(px + arrowW, py + 2, px + arrowW + 1, py + ph - 2, 0x33FFFFFF);
        g.fill(px + pw - arrowW - 1, py + 2, px + pw - arrowW, py + ph - 2, 0x33FFFFFF);

        int dimColor = 0xFF3A3A42;
        int leftColor = !canPrev ? dimColor : (overLeft ? screen.C_TEXT : screen.C_TEXT_DIM);
        int rightColor = !canNext ? dimColor : (overRight ? screen.C_TEXT : screen.C_TEXT_DIM);
        ChroniclesUIKit.drawCenteredString(g, Minecraft.getInstance().font, "◀", px + arrowW / 2, py + 3, leftColor);
        ChroniclesUIKit.drawCenteredString(g, Minecraft.getInstance().font, pageLabel, px + arrowW + labelW / 2, py + 3,
                screen.C_TEXT_DIM);
        ChroniclesUIKit.drawCenteredString(g, Minecraft.getInstance().font, "▶", px + pw - arrowW / 2, py + 3,
                rightColor);

        screen.descPagerX = px;
        screen.descPagerY = py;
        screen.descPagerW = pw;
        screen.descPagerH = ph;
        screen.descPagerPageCount = pageCount;
    }

    void refreshEffectiveContent() {
        if (Minecraft.getInstance() == null || Minecraft.getInstance().player == null || screen.node == null) return;
        net.minecraft.server.MinecraftServer server = Minecraft.getInstance().getSingleplayerServer();
        if (server == null) return;

        List<QuestTask> liveTasks = screen.node.getEffectiveTasks(server, Minecraft.getInstance().player);
        List<QuestReward> liveRewards = screen.node.getEffectiveRewards(server, Minecraft.getInstance().player);
        if (liveTasks.equals(screen.content.effectiveTasks()) && liveRewards.equals(screen.content.effectiveRewards()))
            return;

        screen.content = new FullQuestData(screen.content.title(), screen.content.description(), screen.content.tasks(),
                liveTasks, liveRewards);
    }

    String currentFullDescriptionRaw() {
        if (screen.fullscreen.liveDescOverride != null) return screen.fullscreen.liveDescOverride;
        String mdDesc = (screen.content != null && screen.content.description() != null) ?
                screen.content.description().getString() : "";
        return mdDesc.isBlank() ? screen.node.getDescriptionRaw().getString() : mdDesc;
    }

    String currentDisplayDescriptionText() {
        if (screen.fullscreen.liveDescOverride != null) return screen.fullscreen.liveDescOverride;
        String mdDesc = (screen.content != null && screen.content.description() != null) ?
                screen.content.description().getString() : "";
        return mdDesc.isBlank() ? screen.content.description().getString() : mdDesc;
    }

    String currentDescriptionPageText(String fullText) {
        java.util.List<String> pages = splitDescPages(fullText);
        screen.descPage = Math.max(0, Math.min(screen.descPage, pages.size() - 1));
        return pages.get(screen.descPage);
    }
}
