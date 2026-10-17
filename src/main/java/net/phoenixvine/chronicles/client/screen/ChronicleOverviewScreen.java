package net.phoenixvine.chronicles.client.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.StringUtil;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.phoenixvine.chronicles.capability.PlayerQuestData;
import net.phoenixvine.chronicles.capability.QuestCapabilityProvider;
import net.phoenixvine.chronicles.client.profiler.FrameProfiler;
import net.phoenixvine.chronicles.client.registry.ExternalScreenRegistry;
import net.phoenixvine.chronicles.client.registry.SeenQuestTracker;
import net.phoenixvine.chronicles.client.render.*;
import net.phoenixvine.chronicles.client.screen.utils.*;
import net.phoenixvine.chronicles.client.screen.widgets.*;
import net.phoenixvine.chronicles.client.util.BackgroundPictureConfig;
import net.phoenixvine.chronicles.client.util.ChapterConfig;
import net.phoenixvine.chronicles.client.util.HiddenParts;
import net.phoenixvine.chronicles.common.codec.QuestChroniclesSettings;
import net.phoenixvine.chronicles.common.codec.QuestContentLoader;
import net.phoenixvine.chronicles.common.codec.QuestFileSaver;
import net.phoenixvine.chronicles.common.model.*;
import net.phoenixvine.chronicles.common.registry.QuestTreeRegistry;
import net.phoenixvine.chronicles.common.tasks.ItemRequirementTask;
import net.phoenixvine.chronicles.common.tasks.ScreenOpenedTask;
import net.phoenixvine.chronicles.common.util.ItemData;
import net.phoenixvine.chronicles.integration.phantasia.PhantasiaCompat;
import net.phoenixvine.chronicles.network.packet.C2SScreenOpenedTaskPacket;
import net.phoenixvine.chronicles.network.packet.S2CSyncPlayerProgressPacket;
import net.phoenixvine.wiki.client.screen.WikiTheme;
import net.phoenixvine.wiki.theme.PhoenixTheme;

import com.mojang.blaze3d.systems.RenderSystem;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.opengl.GL11;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Function;

public class ChronicleOverviewScreen extends Screen
                                     implements ScreenContext, NodeCtxMenuState, QuestEditOpsState, BulkOpsPanelState,
                                     NodeContextMenuBuilderState,
                                     net.phoenixvine.chronicles.client.audio.ChroniclesAudio.AudioSource {

    final GraphEditorState editorState = new GraphEditorState();

    final OverviewChapters chapters = new OverviewChapters(this);
    public String stateFilter = "ALL";

    public int chapterQuestCount(String chapter) {
        return chapters.chapterQuestCount(chapter);
    }

    public List<ResourceLocation> questIdsInChapter(String chapter) {
        return chapters.questIdsInChapter(chapter);
    }

    public List<ResourceLocation> questIdsInCategory(String categoryId) {
        return chapters.questIdsInCategory(categoryId);
    }

    public List<String> buildChapterList() {
        return chapters.buildChapterList();
    }

    public boolean catMatches(QuestNode n) {
        return chapters.catMatches(n);
    }

    public int unclaimedRewardCount() {
        return chapters.unclaimedRewardCount();
    }

    final OverviewEditModes editModes = new OverviewEditModes(this);

    final OverviewCanvasInput canvasInput = new OverviewCanvasInput(this);

    final OverviewContextMenus menus = new OverviewContextMenus(this);

    public void openContextMenuFor(int mx, int my, List<SidebarPanel.MenuAction> actions) {
        menus.openContextMenuFor(mx, my, actions);
    }

    @Override
    public List<CtxItem> buildCtxItems() {
        return menus.buildCtxItems();
    }

    @Override
    public int menuHeight(List<CtxItem> items) {
        return menus.menuHeight(items);
    }

    @Override
    public int ctxMoveCatX(int catCount) {
        return menus.ctxMoveCatX(catCount);
    }

    @Override
    public int ctxMoveCatYClamped(List<CtxItem> items, int catCount) {
        return menus.ctxMoveCatYClamped(items, catCount);
    }

    final OverviewSidebar sidebar = new OverviewSidebar(this);

    public int sidebarW() {
        return sidebar.sidebarW();
    }

    public List<SidebarRow> buildSidebarRows() {
        return sidebar.buildSidebarRows();
    }

    final OverviewHeader header = new OverviewHeader(this);

    public int[][] computeHeaderBarLayout(int cr) {
        return header.computeHeaderBarLayout(cr);
    }

    final OverviewKeys keys = new OverviewKeys(this);

    @Override
    public net.phoenixvine.chronicles.common.model.QuestAudio desiredAudio() {
        return net.phoenixvine.chronicles.client.util.ChapterConfig.musicFor(selectedChapter);
    }

    public static final int HEADER_H = 38;
    public static final int TOOLBAR_Y = 22;
    public static final int TOOLBAR_H = 16;
    public static final int NODE_SIZE = 32;
    public static final int C_NBORD_SEL = 0xFF6688FF;

    public static final int C_CTX_BG = 0xFF1A1A22;
    public static final int C_CTX_HOVER = 0xFF252532;
    public static final int C_CTX_BORDER = 0xFF8844AA;
    public static final int C_CTX_SEP = 0xFF2A2A38;
    public static final int C_CTX_TEXT = 0xFFCCCCD8;
    public static final int C_CTX_DANGER = 0xFFCC4444;
    static final int C_PROG_ACT = 0xFFBB8800;
    static final float ZOOM_MIN = 0.12f;
    static final float ZOOM_MAX = 2.5f;
    static final float ZOOM_STEP = 0.12f;

    private static final float POSITION_ZOOM_EXPONENT = 0.68f;
    private static final long POST_MOVE_UNDO_WINDOW_MS = 1000;
    static final float PIC_EDIT_MIN_SIZE = 4f, PIC_EDIT_MAX_SIZE = 4096f;
    public static final int CTX_ROW = 16;
    public static final int CTX_SEP = 5;
    public static final int CTX_W = 128;
    public static final int CTX_MOVE_CAT_MAX_ROWS = 10;
    public static final Set<ResourceLocation> collapsedSubtreeRoots = new HashSet<>();
    static final int[] GRID_SNAP_CYCLE = { 1, 4, 8, 16, 32, 64, 128 };
    public static final long OPEN_FADE_MS = 120;
    private static final long TOOLTIP_DELAY_MS = 0;

    public static final int MIN_NODE_PX = 8;

    public static final int GROUP_LABEL_BAR_H = 11;
    final Map<ResourceLocation, String> searchCache = new HashMap<>();
    final SidebarPanel sidebarPanel = new SidebarPanel();

    public SidebarPanel sidebarPanelInstance() {
        return sidebarPanel;
    }

    private final Map<Item, ItemStack> iconStackCache = new HashMap<>();
    private final Map<QuestTask, ItemStack> nbtIconStackCache = new IdentityHashMap<>();

    public ItemStack cachedIconStack(Item icon) {
        return iconStackCache.computeIfAbsent(icon, ItemStack::new);
    }

    private final Map<String, Integer> dbgShapeCounts = new HashMap<>();

    private final Set<ResourceLocation> hiddenByCollapse = new HashSet<>();
    private final MinimapRenderer minimap = new MinimapRenderer();
    final Map<ResourceLocation, int[]> nodeScreenPos = new LinkedHashMap<>();
    final Map<ResourceLocation, NodeHitbox> nodeButtons = new LinkedHashMap<>();
    final DependencyLineRenderer depLineRenderer = new DependencyLineRenderer();
    private final Map<String, int[]> progressCache = new HashMap<>();
    private final Map<String, Boolean> attentionCache = new HashMap<>();
    private final Map<String, Boolean> rewardsCache = new HashMap<>();
    final ValidationPanel validationPanel = new ValidationPanel(this);
    final StatsPanel statsPanel = new StatsPanel(this);
    private final TutorialOverlay tutorialOverlay = new TutorialOverlay();

    @Override
    public void toggleStatsPanel() {
        if (statsPanel.isOpen()) {
            statsPanel.close();
        } else {
            statsPanel.open();
            validationPanel.close();
        }
    }

    final Set<ResourceLocation> unlockPathHighlight = new HashSet<>();

    QuestNode nonDevCtxNode;
    final List<Runnable> pendingDeferredDraws = new ArrayList<>();
    final Set<ResourceLocation> subgraphNodes = new HashSet<>();
    final ToolbarPanel toolbarPanel = new ToolbarPanel();

    public ToolbarPanel toolbarPanelInstance() {
        return toolbarPanel;
    }

    public BulkOpsPanel bulkOpsPanelInstance() {
        return bulkOpsPanel;
    }

    final PaletteState palette = new PaletteState();
    String selectedChapter = "";
    private String viewChapterTracker = null;
    ResourceLocation lastHoveredNodeId = null;
    private int dbgFull3DIconCount = 0;
    private int dbgCustomIconCount = 0;
    private int dbgPickedTextureIconCount = 0;
    private int dbgFluidIconCount = 0;
    private int dbgGlyphIconCount = 0;
    boolean isDevMode = false;
    private String feedbackMsg = "";
    private long feedbackAtMs = 0L;
    private static final long FEEDBACK_DURATION_MS = 5000L;
    final UndoRedoManager undoRedo = new UndoRedoManager(this::setFeedback);
    int viewOffX = 0, viewOffY = 0;
    int pendingPanDX = 0, pendingPanDY = 0;
    float zoom = 1.0f;
    boolean boxSelecting = false;
    int boxSelectStartX, boxSelectStartY, boxSelectCurX, boxSelectCurY;
    Set<ResourceLocation> boxSelectBaseSelection = null;
    boolean hideCompleted = false;
    int dragGrabX = 0, dragGrabY = 0;
    int dragOrigX = 0, dragOrigY = 0;
    boolean pickupPlaceActive = false;

    boolean middleDragPickupActive = false;

    boolean quickDepKeyDown = false;

    final PictureContextMenu pictureCtxMenu = new PictureContextMenu(this);
    private final TogglePanel.NodeContextMenu nodeCtxMenu = new TogglePanel.NodeContextMenu(this);

    public GraphEditorState editorStateInstance() {
        return editorState;
    }

    final DragController dragController = new DragController(this, this, editorState);
    private final GraphLayoutEngine layoutEngine = new GraphLayoutEngine(this, this, this);
    private final QuestEditOps questEditOps = new QuestEditOps(this, this, editorState);
    private final BulkOpsPanel bulkOpsPanel = new BulkOpsPanel(this, this, editorState);
    private final NodeRenderer nodeRenderer = new NodeRenderer(this, this, editorState);
    @Nullable
    BackgroundPictureConfig.Picture pictureEditMode = null;
    float pictureEditStartX, pictureEditStartY, pictureEditStartW, pictureEditStartH;
    @Nullable
    QuestNode nodeSizeEditMode = null;
    QuestNode.NodeSize nodeSizeEditStartSize;
    int nodeSizeEditStartOverridePx, nodeSizeEditStartX, nodeSizeEditStartY;
    StringBuilder nodeSizeTypedBuffer = null;
    double nodeSizeDragAccX = 0, nodeSizeDragAccY = 0;
    boolean ctxOpen = false;
    private long ctxOpenTimeMs = 0;
    int ctxX, ctxY;

    float ctxScale = 1f;
    private int ctxRawX, ctxRawY;
    QuestNode ctxNode = null;
    boolean ctxMoveCatOpen = false;
    int ctxMoveCatScroll = 0;
    @Nullable
    private QuestGroup ctxGroup = null;
    private boolean renderingAsBackdrop = false;

    Object phantasiaPreview = null;
    List<String> chapterListCache = null;
    static final long CHAPTER_LIST_CACHE_TTL_MS = 2000;
    QuestNode linkDragSource = null;
    int linkDragX, linkDragY;
    int gridSnap = 8;
    boolean gridSnapEnabled = true;
    private GridDisplayMode gridDisplayMode = GridDisplayMode.ON_DRAG;
    boolean dragForceSnap = false;

    private long openTimeMs = -1;
    private ResourceLocation tooltipHoverNodeId = null;
    private long tooltipHoverStartMs = 0;
    boolean testMode = false;
    private PlayerQuestData testModeData = new PlayerQuestData();
    boolean minimapOpen = false;
    private boolean mmDragging = false;
    @Nullable
    PlayerQuestData playerData = null;
    private int lastSeenProgressVersion = -1;
    private String lastTickHand = null;

    private final Screen parent;

    public ChronicleOverviewScreen(Screen parent) {
        super(ChroniclesUIKit.lit("Chronicles"));

        this.parent = parent;

        selectedChapter = QuestChroniclesSettings.get().getLastChapter();

        QuestChroniclesSettings s = QuestChroniclesSettings.get();
        hideCompleted = s.isHideCompletedByDefault();
        gridSnap = s.getDefaultGridSnap();
        nonDevCtxNode = null;
    }

    public ChronicleOverviewScreen() {
        this(null);
    }

    public static Path chaptersFile() {
        return Minecraft.getInstance().gameDirectory.toPath()
                .resolve("config").resolve("phoenix_chronicles").resolve("categories.txt");
    }

    static void invalidateNodeCachesUpChain(Screen from, QuestNode node) {
        Screen s = from;
        for (int i = 0; i < 8; i++) {
            if (s instanceof ChronicleOverviewScreen overview) {
                overview.invalidateNodeCaches(node);
                return;
            }
            if (s instanceof QuestCreatorScreen qcs) s = qcs.getParentScreen();
            else if (s instanceof QuestTasksScreen qts) s = qts.getParentScreen();
            else if (s instanceof TaskRewardEditorScreen tres) s = tres.getParentScreen();
            else if (s instanceof VariantEditorScreen ves) s = ves.getParentScreen();
            else break;
        }
    }

    public static int nodeBorderThickness(int sz) {
        return Math.max(1, Math.min(4, sz / 28));
    }

    public static int blendColor(int base, int over, float a) {
        int br = (base >> 16) & 0xFF, bg = (base >> 8) & 0xFF, bb = base & 0xFF;
        int or = (over >> 16) & 0xFF, og = (over >> 8) & 0xFF, ob = over & 0xFF;
        return 0xFF000000 | ((int) (br + (or - br) * a) << 16) | ((int) (bg + (og - bg) * a) << 8) |
                (int) (bb + (ob - bb) * a);
    }

    public static float animPulse(float base, float amplitude, double periodDivisor) {
        if (QuestChroniclesSettings.get().isReduceMotion()) return base;
        return base + amplitude * (float) Math.sin(System.currentTimeMillis() / periodDivisor);
    }

    public static FullQuestData loadMarkdownContent(Path mdPath) {
        Component title = Component.empty();
        StringBuilder desc = new StringBuilder();

        boolean pendingParagraphBreak = false;
        try (BufferedReader r = Files.newBufferedReader(mdPath, StandardCharsets.UTF_8)) {
            String line;
            while ((line = r.readLine()) != null) {
                String t = line.trim();
                if (t.startsWith("# ") && title.getString().isEmpty()) {
                    title = Component.literal(t.substring(2).trim());
                } else if (t.matches("-{3,}")) {

                    if (!desc.isEmpty()) desc.append("\n\n");
                    desc.append(t);
                    pendingParagraphBreak = true;
                } else if (!t.startsWith("#") && !t.isEmpty()) {
                    if (!desc.isEmpty()) desc.append(pendingParagraphBreak ? "\n\n" : ' ');
                    desc.append(t);
                    pendingParagraphBreak = false;
                } else if (t.isEmpty()) {
                    pendingParagraphBreak = true;
                }
            }
        } catch (IOException ignored) {}
        return new FullQuestData(title, Component.literal(desc.toString().trim()), List.of(), Collections.emptyList(),
                Collections.emptyList());
    }

    @Override
    public float posZoom() {
        return zoom < 1f ? (float) Math.pow(zoom, POSITION_ZOOM_EXPONENT) : zoom;
    }

    @Override
    public float rawZoom() {
        return zoom;
    }

    @Override
    public void renderBackground(GuiGraphics context, int mouseX, int mouseY, float delta) {}

    private static float effectiveToRawZoom(float effective) {
        return effective < 1f ? (float) Math.pow(effective, 1f / POSITION_ZOOM_EXPONENT) : effective;
    }

    private void recomputeHiddenByCollapse() {
        hiddenByCollapse.clear();
        for (ResourceLocation rootId : collapsedSubtreeRoots) {
            QuestNode n = QuestTreeRegistry.getQuest(rootId);
            if (n != null) collectDescendants(n, hiddenByCollapse);
        }
    }

    private void collectDescendants(QuestNode node, Set<ResourceLocation> out) {
        for (QuestNode child : node.getChildren()) {
            if (out.add(child.getId())) collectDescendants(child, out);
        }
    }

    @Override
    public void toggleSubtreeCollapse(QuestNode node) {
        if (!collapsedSubtreeRoots.remove(node.getId())) collapsedSubtreeRoots.add(node.getId());
        rebuild();
    }

    public QuestState getState(QuestNode node) {
        if (testMode) return testModeData.getQuestState(node.getId(), QuestState.LOCKED);
        if (playerData == null) return QuestState.LOCKED;
        return playerData.getQuestState(node.getId(), QuestState.LOCKED);
    }

    public boolean isGatedHidden(QuestNode node) {
        if (node.isSelfGatedHidden(this::getState)) return true;
        return isAncestorGatedHidden(node);
    }

    private final Map<ResourceLocation, Boolean> ancestorGatedCache = new HashMap<>();

    @Override
    public boolean isHiddenFromPlayer(QuestNode n) {
        if (minecraft == null || minecraft.player == null) return false;
        MinecraftServer server = minecraft.getSingleplayerServer();
        QuestNode.Visibility vis = n.getEffectiveVisibility(server, minecraft.player);
        return (vis == QuestNode.Visibility.HIDDEN && getState(n) == QuestState.LOCKED) || isAncestorGatedHidden(n);
    }

    boolean isAncestorGatedHidden(QuestNode node) {
        if (!QuestChroniclesSettings.get().isCascadeHiddenQuests()) return false;
        Boolean cached = ancestorGatedCache.get(node.getId());
        if (cached != null) return cached;
        boolean result = node.isAncestorGatedHidden(this::getState);
        ancestorGatedCache.put(node.getId(), result);
        return result;
    }

    @Override
    public @Nullable QuestNode resolveLinkTarget(QuestNode node) {
        return node.isLinkStub() ? QuestTreeRegistry.getQuest(node.getLinkTarget()) : node;
    }

    private Item fallbackTaskIcon(QuestNode node) {
        for (QuestTask task : node.getTasks()) {
            if (HiddenParts.taskTargetHidden(node, task)) continue;
            ResourceLocation id = task.getDisplayItemId();
            Item item = BuiltInRegistries.ITEM.get(id);
            if (item != null && item != Items.AIR) return item;
        }
        return null;
    }

    public QuestTask fallbackTaskIconTask(QuestNode node) {
        for (QuestTask task : node.getTasks()) {
            if (HiddenParts.taskTargetHidden(node, task)) continue;
            ResourceLocation id = task.getDisplayItemId();
            Item item = BuiltInRegistries.ITEM.get(id);
            if (item != null && item != Items.AIR) return task;
        }
        return null;
    }

    public QuestTask matchingIconTask(QuestNode node, Item icon) {
        for (QuestTask task : node.getTasks()) {
            if (task instanceof ItemRequirementTask t && t.getItem() == icon) {
                return task;
            }
        }
        return null;
    }

    public ItemStack nbtAwareIconStack(QuestTask task, Item icon) {
        if (!(task instanceof ItemRequirementTask t) || t.getNbtFilter() == null ||
                t.getNbtFilter().isEmpty()) {
            return cachedIconStack(icon);
        }
        return nbtIconStackCache.computeIfAbsent(task, k -> {
            ItemStack stack = new ItemStack(icon);
            ItemData.setTag(stack, t.getNbtFilter().copy());
            return stack;
        });
    }

    QuestState getDisplayState(QuestNode node) {
        QuestNode target = resolveLinkTarget(node);
        return getState(target != null ? target : node);
    }

    public boolean isTaskDone(QuestTask task) {
        if (minecraft == null || minecraft.player == null) return false;
        return task.isCompletedFor(minecraft.player);
    }

    public void setSelectedChapter(String chapter) {
        selectedChapter = chapter;
    }

    public void invalidateChapterCaches() {
        chapterListCache = null;
    }

    @Override
    public ChronicleOverviewScreen thisScreen() {
        return this;
    }

    Function<String, int[]> progressLookup() {
        return cat -> progressCache.computeIfAbsent(cat, chapters::computeChapterProgress);
    }

    Function<String, Boolean> attentionLookup() {
        return cat -> attentionCache.computeIfAbsent(cat, chapters::computeChapterHasAttention);
    }

    Function<String, Boolean> rewardsLookup() {
        return cat -> rewardsCache.computeIfAbsent(cat, chapters::computeChapterHasUnclaimedRewards);
    }

    @Override
    protected void init() {
        refreshPalette();
        QuestGroupManager.invalidate();
        openTimeMs = System.currentTimeMillis();
        rebuild();
    }

    @Override
    protected void repositionElements() {
        softRebuild();
    }

    private int lineThemeSignature = 0;
    private boolean lineThemeSignatureKnown = false;

    private void rebuildLinesIfThemeChanged() {
        int sig = java.util.Objects.hash(palette.lineLocked, palette.lineDone, palette.lineActive);
        if (!lineThemeSignatureKnown) {
            lineThemeSignatureKnown = true;
            lineThemeSignature = sig;
            return;
        }
        if (sig != lineThemeSignature) {
            lineThemeSignature = sig;
            buildLineCache();
        }
    }

    private void refreshPalette() {
        PhoenixTheme t = PhoenixTheme.current();
        ChroniclesThemePalette.refresh(t);
        palette.refresh(t);
    }

    @Override
    public Path groupsConfigPath() {
        return Minecraft.getInstance().gameDirectory.toPath()
                .resolve("config").resolve("phoenix_chronicles");
    }

    void invalidateNodeCaches(QuestNode node) {
        if (node == null) return;
        validationPanel.invalidate(node.getId());
        searchCache.remove(node.getId());
        progressCache.remove(node.getChapter());
        attentionCache.remove(node.getChapter());
        rewardsCache.remove(node.getChapter());
    }

    @Override
    public void rebuild() {
        clearWidgets();
        ancestorGatedCache.clear();
        nodeScreenPos.clear();
        nodeButtons.clear();
        searchCache.clear();
        progressCache.clear();
        attentionCache.clear();
        rewardsCache.clear();
        validationPanel.clear();
        chapterListCache = null;
        ctxOpen = false;
        ctxMoveCatOpen = false;
        ctxGroup = null;

        QuestGroupManager.load(groupsConfigPath());
        recomputeHiddenByCollapse();

        if (minecraft != null && minecraft.player != null) {

            isDevMode = !QuestChroniclesSettings.get().isDevModeDisabled() && minecraft.player.hasPermissions(2);
            playerData = QuestCapabilityProvider.of(minecraft.player).orElse(null);
        }

        int cl = sidebarW(), cr = width;

        List<String> cats = buildChapterList();
        if (!cats.isEmpty() && !cats.contains(selectedChapter)) selectedChapter = cats.get(0);

        if (!selectedChapter.equals(viewChapterTracker)) {
            restoreViewForChapter();
            viewChapterTracker = selectedChapter;
            QuestChroniclesSettings settings = QuestChroniclesSettings.get();
            if (!selectedChapter.equals(settings.getLastChapter())) {
                settings.setLastChapter(selectedChapter);
                settings.save();
            }
        }

        for (QuestNode root : QuestTreeRegistry.getRootChapters().values()) {
            if (!catMatches(root)) continue;
            placeNodeRecursive(root, cl, cr);
        }

        for (QuestNode n : QuestTreeRegistry.getAllQuests().values()) {
            if (catMatches(n)) {
                placeNodeRecursive(n, cl, cr);
                net.phoenixvine.chronicles.client.render.QuestIconCache.get(n.getId().getPath());
            }
        }
        buildLineCache();

        sidebarPanel.syncLayoutBaseX(cl);
    }

    private void restoreViewForChapter() {
        if (!applyFitView()) {
            zoom = 1.0f;
            viewOffX = 0;
            viewOffY = 0;
        }
    }

    private void placeNodeRecursive(QuestNode node, int cl, int cr) {
        layoutEngine.placeNodeRecursive(node, cl, cr);
    }

    public int scaledNodeSize(QuestNode node) {
        return layoutEngine.scaledNodeSize(node);
    }

    public int scaledNodeSize() {
        return layoutEngine.scaledNodeSize();
    }

    void onNodeClicked(QuestNode node) {
        onNodeClicked(node, false);
    }

    void onNodeClicked(QuestNode node, boolean openFullscreen) {
        if (node == null) return;
        ctxOpen = false;
        ctxMoveCatOpen = false;

        QuestNode target = resolveLinkTarget(node);
        if (target == null) {
            setFeedback("§cBroken link: %s", node.getLinkTarget());
            return;
        }

        QuestState st = getState(target);

        editorState.selectedNode = target;
        if (editorState.subgraphMode) rebuildSubgraph();

        if (testMode) {
            if (st == QuestState.COMPLETED) {
                testModeData.setQuestState(Objects.requireNonNull(target).getId(), QuestState.LOCKED);
            } else {
                testModeData.setQuestState(Objects.requireNonNull(target).getId(), QuestState.COMPLETED);
            }
            propagateTestUnlocks();
            softRebuild();
            return;
        }

        if (!isDevMode && isGatedHidden(Objects.requireNonNull(target))) return;

        String externalScreenIdStr = target.getExternalScreenId();
        if (minecraft != null && externalScreenIdStr != null && !externalScreenIdStr.isEmpty()) {
            ResourceLocation externalScreenId = ResourceLocation
                    .tryParse(externalScreenIdStr);
            if (externalScreenId != null &&
                    ExternalScreenRegistry.isRegistered(externalScreenId)) {
                Screen external = ExternalScreenRegistry
                        .open(externalScreenId,
                                target);
                if (external != null) {
                    for (QuestTask task : target.getEffectiveTasks(minecraft.getSingleplayerServer(),
                            minecraft.player)) {
                        if (task instanceof ScreenOpenedTask screenTask &&
                                screenTask.matchesScreen(externalScreenIdStr) &&
                                minecraft.player != null && !task.isCompletedFor(minecraft.player)) {
                            net.neoforged.neoforge.network.PacketDistributor.sendToServer(
                                    new C2SScreenOpenedTaskPacket(
                                            task.getTaskId()));
                        }
                    }
                    minecraft.setScreen(external);
                    return;
                }
            }
        }

        if (minecraft != null) {

            Path mdPath = QuestFileSaver.getQuestMarkdownPath(Objects.requireNonNull(target));

            QuestContentLoader.syncActiveLocaleFromClient();
            Path resolvedMdPath = QuestContentLoader
                    .resolveLocaleFile(mdPath, Objects.requireNonNull(target).getId().getPath());

            FullQuestData mdData = loadMarkdownContent(resolvedMdPath);
            MinecraftServer server = minecraft.getSingleplayerServer();

            Component effTitle = mdData.title() != null && !mdData.title().getString().isBlank() ? mdData.title() :
                    target.getEffectiveTitleRaw(server, minecraft.player);
            Component effDesc = mdData.description() != null && !mdData.description().getString().isBlank() ?
                    mdData.description() : target.getEffectiveDescriptionRaw(server, minecraft.player);
            FullQuestData fd = new FullQuestData(effTitle, effDesc, mdData.tasks(),
                    target.getEffectiveTasks(server, minecraft.player),
                    target.getEffectiveRewards(server, minecraft.player));
            assert playerData != null;
            SeenQuestTracker.markSeen(target.getId());
            minecraft.setScreen(
                    new QuestTasksScreen(this, Objects.requireNonNull(target), fd, playerData, openFullscreen));
        }
    }

    @Override
    public void autoArrangeChapter() {
        layoutEngine.autoArrangeChapter();
    }

    @Override
    public void rotateChapter90() {
        layoutEngine.rotateChapter90();
    }

    private void propagateTestUnlocks() {
        for (QuestNode n : QuestTreeRegistry.getAllQuests().values()) {
            if (testModeData.getQuestState(n.getId(), QuestState.LOCKED) != QuestState.COMPLETED)
                testModeData.setQuestState(n.getId(), QuestState.LOCKED);
        }

        boolean changed = true;
        while (changed) {
            changed = false;
            for (QuestNode n : QuestTreeRegistry.getAllQuests().values()) {
                if (testModeData.getQuestState(n.getId(), QuestState.LOCKED) != QuestState.LOCKED) continue;
                boolean prereqsMet = n.getPrerequisites().isEmpty() ||
                        n.getPrerequisites().stream().allMatch(
                                p -> testModeData.getQuestState(p.getId(), QuestState.LOCKED) == QuestState.COMPLETED);
                if (prereqsMet) {
                    testModeData.setQuestState(n.getId(), QuestState.UNLOCKED);
                    changed = true;
                }
            }
        }
    }

    @Override
    public void rebuildSubgraph() {
        subgraphNodes.clear();
        if (editorState.selectedNode == null) return;
        subgraphNodes.add(editorState.selectedNode.getId());

        ArrayDeque<QuestNode> queue = new ArrayDeque<>();
        queue.add(editorState.selectedNode);
        while (!queue.isEmpty()) {
            QuestNode cur = queue.poll();
            for (QuestNode p : cur.getPrerequisites()) {
                if (subgraphNodes.add(p.getId())) queue.add(p);
            }
        }

        queue.add(editorState.selectedNode);
        while (!queue.isEmpty()) {
            QuestNode cur = queue.poll();
            for (QuestNode c : cur.getChildren()) {
                if (subgraphNodes.add(c.getId())) queue.add(c);
            }
        }
    }

    public void navigateToNode(QuestNode node) {
        if (!node.getChapter().equals(selectedChapter)) {
            selectedChapter = node.getChapter();
            rebuild();
        }
        int canvasW = width - sidebarW();
        int canvasH = height - HEADER_H;
        viewOffX = (int) (canvasW / 2f - node.getCustomX() * posZoom());
        viewOffY = (int) (canvasH / 2f - node.getCustomY() * posZoom());
        onNodeClicked(node);
    }

    @Override
    public void buildLineCache() {
        depLineRenderer.rebuildFromGraph(this, sidebar.sidebarVisualW(), this::catMatches, palette.lineLocked,
                palette.lineDone, palette.lineActive, QuestChroniclesSettings.get());
    }

    void softRebuild() {
        Map<String, int[]> savedProgress = new HashMap<>(progressCache);
        Map<String, Boolean> savedAttention = new HashMap<>(attentionCache);
        Map<String, Boolean> savedRewards = new HashMap<>(rewardsCache);
        Map<ResourceLocation, List<String>> savedValidation = validationPanel.snapshot();
        List<String> savedCats = chapterListCache;
        rebuild();
        progressCache.putAll(savedProgress);
        attentionCache.putAll(savedAttention);
        rewardsCache.putAll(savedRewards);
        validationPanel.restore(savedValidation);
        chapterListCache = savedCats;
    }

    void rescaleForZoom() {
        int cl = sidebar.sidebarVisualW(), cr = width;
        for (Map.Entry<ResourceLocation, NodeHitbox> e : nodeButtons.entrySet()) {
            QuestNode n = QuestTreeRegistry.getQuest(e.getKey());
            if (n == null) continue;
            NodeHitbox btn = e.getValue();

            int cx = n.getCustomX();
            int cy = n.getCustomY();
            int sz = scaledNodeSize(n);
            int sx = (int) (cx * posZoom()) + viewOffX + cl - sz / 2;
            int sy = (int) (cy * posZoom()) + viewOffY + HEADER_H - sz / 2;
            btn.x = sx;
            btn.y = sy;
            btn.w = sz;
            btn.h = sz;
            btn.visible = sx + sz > cl && sx < cr && sy + sz > HEADER_H && sy < height;
            int[] pos = nodeScreenPos.get(e.getKey());
            if (pos != null) {
                pos[0] = sx;
                pos[1] = sy;
            }
        }
        buildLineCache();
    }

    void panCanvas(int dx, int dy) {
        int cl = sidebar.sidebarVisualW(), cr = width;
        int sz = scaledNodeSize();
        for (Map.Entry<ResourceLocation, NodeHitbox> e : nodeButtons.entrySet()) {
            NodeHitbox btn = e.getValue();
            int nx = btn.getX() + dx;
            int ny = btn.getY() + dy;
            btn.setX(nx);
            btn.setY(ny);
            int[] pos = nodeScreenPos.get(e.getKey());
            if (pos != null) {
                pos[0] = nx;
                pos[1] = ny;
            }
            QuestNode n = QuestTreeRegistry.getQuest(e.getKey());
            int nsz = n != null ? scaledNodeSize(n) : sz;
            btn.visible = nx + nsz > cl && nx < cr && ny + nsz > HEADER_H && ny < height;
        }

        buildLineCache();
    }

    void openWiki() {
        if (minecraft == null) return;
        PhoenixTheme theme = PhoenixTheme.current();
        WikiTheme wikiTheme = new WikiTheme(
                theme.bg.getColor(), theme.panel.getColor(), theme.header.getColor(), theme.border.getColor(),
                theme.accent.getColor(), theme.text.getColor(), theme.textDim.getColor(), theme.textFaint.getColor(),
                theme.done.getColor(), theme.activeColor.getColor());
        minecraft.setScreen(new ChronicleWikiScreen(this, wikiTheme));
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (keys.tryHandleDragCancelEscape(key)) return true;
        if (keys.tryHandleNodeSizeTypedInput(key)) return true;

        boolean ctrl = (mods & 2) != 0;
        boolean shift = (mods & 1) != 0;

        if (keys.tryHandleSmokeTestKeybind(key)) return true;

        if (keys.tryHandleQuickKeybinds(key, scan, ctrl, mods)) return true;
        if (key == 256 && keys.tryHandleEscapeDispatch()) return true;

        if (key == 256 && isDevMode && !editorState.multiSelection.isEmpty()) {
            editorState.multiSelection.clear();
            bulkOpsPanel.reset();
            return true;
        }

        if (keys.tryHandleUndoRedoKeybind(key, ctrl, shift)) return true;
        if (keys.tryHandleActionKeybinds(key, scan, ctrl, shift)) return true;

        if (key == 68) {
            quickDepKeyDown = true;
        }

        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean keyReleased(int key, int scan, int mods) {
        if (key == 68) {
            quickDepKeyDown = false;
        }
        return super.keyReleased(key, scan, mods);
    }

    @Override
    public void questCopy(QuestNode node) {
        questEditOps.questCopy(node);
    }

    @Override
    public void questPaste() {
        questEditOps.questPaste();
    }

    @Override
    public void chainMultiSelection() {
        questEditOps.chainMultiSelection();
    }

    @Override
    public void fanFromLeftmost() {
        questEditOps.fanFromLeftmost();
    }

    void runFtbImport() {
        questEditOps.runFtbImport();
    }

    @Override
    public void duplicateQuest(QuestNode source) {
        questEditOps.duplicateQuest(source);
    }

    @Override
    public void createLinkStubAt(int canvasX, int canvasY, QuestNode target) {
        questEditOps.createLinkStubAt(canvasX, canvasY, target);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double delta) {
        if (menus.tryHandleMoveCatDropdownScroll(mx, my, delta)) return true;
        if (editModes.tryHandleNodeSizeEditScroll(delta)) return true;
        if (editModes.tryHandlePictureEditScroll(delta)) return true;
        if (statsPanel.isVisible(this) && statsPanel.mouseScrolled(mx, my, 0.0, delta)) return true;

        int cl = sidebarW(), cr = width;
        if (mx <= cl && my > HEADER_H) {

            sidebarPanel.scrollBy(delta, sidebar.sidebarContentHeight(), sidebar.sidebarScrollAreaHeight());
            return true;
        }
        if (mx <= cl || mx >= cr || my <= HEADER_H) return super.mouseScrolled(mx, my, 0.0, delta);

        return canvasInput.handleCanvasZoomScroll(mx, my, delta, cl, cr);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (sidebar.tryHandleSidebarContextMenuClick(mx, my, btn)) return true;
        if (menus.tryHandleDepLineContextMenuClick(mx, my, btn)) return true;
        if (menus.tryHandlePictureContextMenuClick(mx, my, btn)) return true;
        if (menus.tryHandleNodeContextMenuClick(mx, my, btn)) return true;
        if (menus.tryHandleNonDevCtxMenuClick(mx, my, btn)) return true;

        if (editModes.tryHandleNodeResizeModeClick(btn)) return true;
        if (editModes.tryHandlePictureEditModeClick(btn)) return true;

        if (btn == 0 && tutorialOverlay.mouseClicked(this, mx, my, btn)) return true;

        if (tryHandleMinimapClick(mx, my, btn)) return true;

        int cl = sidebarW(), cr = width;

        if (btn == 0) {
            if (canvasInput.tryHandleLeftClickUi(mx, my, cl, cr)) return true;
        }

        if (sidebar.tryHandleLangExportClick(mx, my, btn)) return true;

        if (sidebar.tryHandleSidebarRowContextMenuClick(mx, my, btn)) return true;

        if (bulkOpsPanel.mouseClicked(mx, my, btn, cl)) return true;

        if (canvasInput.tryHandleMultiSelectToggleClick(mx, my, btn)) return true;

        if (canvasInput.tryHandlePickupPlaceReleaseClick(btn)) return true;

        if (canvasInput.tryHandleMiddleDragReleaseClick(btn)) return true;

        if (canvasInput.tryHandleQuickDepDragStart(mx, my, btn)) return true;

        if (canvasInput.tryHandleShiftDragStart(mx, my, btn, cl)) return true;

        if (canvasInput.tryHandleMiddleClickNodeDragStart(mx, my, btn)) return true;

        if (canvasInput.tryHandleShiftRightClickForceComplete(mx, my, btn, cl, cr)) return true;

        if (menus.tryHandleDevRightClickContextMenu(mx, my, btn, cl, cr)) return true;

        menus.handleNonDevRightClick(mx, my, btn, cl, cr);

        if (canvasInput.tryHandleCanvasClick(mx, my, btn, cl, cr)) return true;

        return super.mouseClicked(mx, my, btn);
    }

    private boolean tryHandleMinimapClick(double mx, double my, int btn) {
        if (btn == 0 && isInMinimap(mx, my)) {
            mmDragging = true;
            minimapPanTo(mx, my, sidebarW());
            rescaleForZoom();
            return true;
        }
        return false;
    }

    @Override
    public void mouseMoved(double mx, double my) {
        if ((pickupPlaceActive || middleDragPickupActive) && editorState.draggedNode != null) {
            dragController.updateDraggedNodeScreenPos(mx, my, dragController.currentDragSnap());
            depLineRenderer.refreshEdgeEndpoints(editorState.draggedNode.getId(), this::nodeCenterForLine, posZoom(),
                    QuestChroniclesSettings.get());
        }
        super.mouseMoved(mx, my);
    }

    @Nullable
    int[] nodeCenterForLine(ResourceLocation id) {
        int[] pos = nodeScreenPos.get(id);
        QuestNode n = QuestTreeRegistry.getQuest(id);
        if (pos == null || n == null) return null;
        int sz = scaledNodeSize(n);
        return new int[] { pos[0] + sz / 2, pos[1] + sz / 2 };
    }

    @Override
    public boolean mouseDragged(double mx, double my, int btn, double dx, double dy) {
        if (sidebar.tryHandleSidebarRowDragThreshold(mx, my, btn)) return true;
        if (editModes.tryHandleNodeSizeEditDrag(btn, dx, dy)) return true;
        if (editModes.tryHandlePictureEditDrag(btn, dx, dy)) return true;

        if (btn == 0 && boxSelecting) {
            boxSelectCurX = (int) mx;
            boxSelectCurY = (int) my;
            canvasInput.updateBoxSelection();
            return true;
        }
        if (btn == 0 && mmDragging) {
            minimapPanTo(mx, my, sidebarW());
            rescaleForZoom();
            return true;
        }
        if (btn == 0 && linkDragSource != null) {
            linkDragX = (int) mx;
            linkDragY = (int) my;
            return true;
        }
        if (btn == 0) {
            if (canvasInput.tryHandleLeftButtonDragMove(mx, my, dx, dy)) return true;
        }

        if (btn == 2 && editorState.draggedNode != null) {
            dragController.updateDraggedNodeScreenPos(mx, my, dragController.currentDragSnap());
            depLineRenderer.refreshEdgeEndpoints(editorState.draggedNode.getId(), this::nodeCenterForLine, posZoom(),
                    QuestChroniclesSettings.get());
            return true;
        }
        return super.mouseDragged(mx, my, btn, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int btn) {
        editModes.resetNodeSizeDragAccumulator(btn);

        if (btn == 0 && boxSelecting) {
            boxSelecting = false;
            boxSelectBaseSelection = null;
            return true;
        }

        if (sidebar.tryHandleSidebarRowDrop(mx, my, btn)) return true;

        if (tryHandleMinimapDragEnd(btn)) return true;

        if (canvasInput.tryHandleLinkDragEnd(mx, my, btn)) return true;

        if (btn == 0) {
            if (canvasInput.tryHandleLeftButtonDragRelease()) return true;
        }

        if (canvasInput.tryHandleMiddleButtonDragRelease(btn)) return true;

        return super.mouseReleased(mx, my, btn);
    }

    private boolean tryHandleMinimapDragEnd(int btn) {
        if (btn == 0 && mmDragging) {
            mmDragging = false;

            rescaleForZoom();
            return true;
        }
        return false;
    }

    private String stampChapterKey = "";
    private boolean stampComplete = false;
    private long stampSinceMs = 0;
    private int stampFrame = 0;

    private void refreshChapterStamp() {
        boolean chapterChanged = !selectedChapter.equals(stampChapterKey);
        if (!chapterChanged && ++stampFrame % 30 != 0) return;
        stampChapterKey = selectedChapter;
        var tally = net.phoenixvine.chronicles.client.util.QuestCompletionStats.compute(this).chaptersRequired
                .get(selectedChapter.toUpperCase(java.util.Locale.ROOT));
        boolean complete = tally != null && tally.total > 0 && tally.done >= tally.total;
        if (complete && !stampComplete) stampSinceMs = chapterChanged ? 0 : System.currentTimeMillis();
        if (!complete) stampSinceMs = 0;
        stampComplete = complete;
    }

    @Override
    public void render(@NotNull GuiGraphics g, int mx, int my, float partial) {
        FrameProfiler.begin("TOTAL render()");
        refreshChapterStamp();

        refreshPalette();
        rebuildLinesIfThemeChanged();
        pendingDeferredDraws.clear();
        sidebar.updateSidebarHoverPeek(mx, my);

        if (pendingPanDX != 0 || pendingPanDY != 0) {
            panCanvas(pendingPanDX, pendingPanDY);
            pendingPanDX = 0;
            pendingPanDY = 0;
        }

        dragController.handleLiveDragging(mx, my);

        int cl = sidebar.sidebarVisualW();
        int cr = width;
        int sz = scaledNodeSize();

        long animTick = QuestChroniclesSettings.get().isReduceMotion() ? 0L : System.currentTimeMillis();

        FrameProfiler.begin("header");
        header.renderHeaderAndBaseLayout(g, mx, my, cl, cr);
        FrameProfiler.end("header");

        PhantasiaCompat.tickPreview(phantasiaPreview);

        switch (gridDisplayMode) {
            case ALWAYS -> dragController.renderSnapGridOverlay(g, cl, cr);
            case CURSOR_BOX -> dragController.renderSnapCursorBox(g, mx, my, cl, cr);
            case ON_DRAG -> {
                if (editorState.draggedNode != null) dragController.renderSnapGridOverlay(g, cl, cr);
            }
        }

        sidebar.renderSidebarPanel(g, mx, my);

        renderCanvasLayers(g, mx, my, cl, cr, animTick);

        FrameProfiler.setCounter("screenWidgets", this.renderables.size());
        FrameProfiler.begin("widgets (super.render)");
        if (!renderingAsBackdrop) super.render(g, mx, my, partial);
        FrameProfiler.end("widgets (super.render)");

        renderDepLines(g, mx, my, cl, cr, animTick);

        renderNodesAndDetails(g, mx, my, cl, cr, sz);
        renderLinkDragHint(g);
        if (nodeSizeEditMode != null) {
            int[] pos = nodeScreenPos.get(nodeSizeEditMode.getId());
            if (pos != null) {
                int nsz = scaledNodeSize(nodeSizeEditMode);
                ChroniclesUIKit.drawBorder(g, pos[0] - 2, pos[1] - 2, nsz + 4, nsz + 4, 0xFFFFCC33);
                if (nodeSizeTypedBuffer != null) {
                    String typed = "§f" + nodeSizeTypedBuffer + "§7px§8 (Enter to apply)";
                    ChroniclesUIKit.drawCenteredString(g, font, typed, pos[0] + nsz / 2, pos[1] - 12, 0xFFFFCC33);
                }
            }
        }

        FrameProfiler.begin("overlays");
        renderScreenOverlays(g, mx, my, cl, cr, sz);
        FrameProfiler.end("overlays");

        if (editorState.draggedNode != null) dragController.renderDragSnapPosBox(g, mx, my);

        RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, false);

        for (Runnable r : pendingDeferredDraws) r.run();
        pendingDeferredDraws.clear();

        if (stampComplete && QuestChroniclesSettings.get().isShowChapterStamp()) {
            g.enableScissor(cl, HEADER_H, cr, height);
            net.phoenixvine.chronicles.client.render.ChapterStamp.draw(g, font,
                    cr - net.phoenixvine.chronicles.client.render.ChapterStamp.W / 2 - 14, HEADER_H + 34,
                    stampSinceMs, palette.textDone);
            g.disableScissor();
        }

        if (minimapOpen) renderMinimap(g, mx, my, cl, cr);

        FrameProfiler.end("TOTAL render()");
        FrameProfiler.endFrame();
        if (FrameProfiler.isEnabled()) renderProfilerPanel(g);
    }

    private void renderCanvasLayers(GuiGraphics g, int mx, int my, int cl, int cr, long animTick) {
        g.enableScissor(cl, HEADER_H, cr, height);

        FrameProfiler.begin("background");

        CanvasBackgroundRenderer.drawBackground(g, cl, HEADER_H, cr, height, selectedChapter, posZoom(), viewOffX,
                viewOffY, this.gridSnap);

        BackgroundPictureRenderer.render(g, cl, HEADER_H, cr, height, selectedChapter, posZoom(), viewOffX, viewOffY);
        if (pictureEditMode != null) {
            int[] rect = BackgroundPictureRenderer.screenRect(pictureEditMode, cl, HEADER_H, posZoom(), viewOffX,
                    viewOffY);
            int outlineW = rect[2] - rect[0], outlineH = rect[3] - rect[1];
            g.pose().pushPose();
            g.pose().translate((rect[0] + rect[2]) / 2f, (rect[1] + rect[3]) / 2f, 0f);
            g.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(pictureEditMode.rotation));
            ChroniclesUIKit.drawBorder(g, -outlineW / 2 - 1, -outlineH / 2 - 1, outlineW + 2, outlineH + 2,
                    0xFFFFCC33);
            g.pose().popPose();
        }
        FrameProfiler.end("background");

        FrameProfiler.begin("groups");
        for (QuestGroup grp : QuestGroupManager.forChapter(selectedChapter)) {
            renderQuestGroup(g, grp, cl, cr);
        }
        FrameProfiler.end("groups");

        g.disableScissor();
    }

    private void renderDepLines(GuiGraphics g, int mx, int my, int cl, int cr, long animTick) {
        g.enableScissor(cl, HEADER_H, cr, height);

        ResourceLocation hoveredNodeId = null;
        for (Map.Entry<ResourceLocation, NodeHitbox> e : nodeButtons.entrySet()) {
            if (e.getValue().visible && e.getValue().isMouseOver(mx, my)) {
                hoveredNodeId = e.getKey();
                break;
            }
        }
        lastHoveredNodeId = hoveredNodeId;

        FrameProfiler.setCounter("nodes", nodeButtons.size());
        FrameProfiler.setCounter("zoom%", Math.round(zoom * 100));
        depLineRenderer.render(g, animTick, hoveredNodeId, this::getState, palette.lineActive, palette.lineDone,
                palette.lineAlmost,
                palette.lineLocked);

        if (linkDragSource != null) {
            int[] srcPos = nodeScreenPos.get(linkDragSource.getId());
            if (srcPos != null) {
                int sz2 = scaledNodeSize();
                int sx = srcPos[0] + sz2 / 2, sy = srcPos[1] + sz2 / 2;
                depLineRenderer.renderLinkDragPreview(g, sx, sy, linkDragX, linkDragY, animTick, posZoom());
            }
        }

        g.disableScissor();
    }

    private void renderLinkDragHint(GuiGraphics g) {
        if (linkDragSource == null) return;
        int[] srcPos = nodeScreenPos.get(linkDragSource.getId());
        if (srcPos == null) return;
        int sz2 = scaledNodeSize();
        int sx = srcPos[0] + sz2 / 2, sy = srcPos[1] + sz2 / 2;
        ChroniclesUIKit.drawString(g, font, "§dRelease on a quest to link", sx - 50, sy - 14, 0xFFAA66FF, false);
    }

    private void renderNodesAndDetails(GuiGraphics g, int mx, int my, int cl, int cr, int sz) {
        nodeRenderer.renderNodesAndDetails(g, mx, my, cl, cr, sz);
    }

    private void renderScreenOverlays(GuiGraphics g, int mx, int my, int cl, int cr, int sz) {
        if (boxSelecting) {
            int x0 = Math.min(boxSelectStartX, boxSelectCurX), x1 = Math.max(boxSelectStartX, boxSelectCurX);
            int y0 = Math.min(boxSelectStartY, boxSelectCurY), y1 = Math.max(boxSelectStartY, boxSelectCurY);
            g.fill(x0, y0, x1, y1, 0x2255CCFF);
            ChroniclesUIKit.drawBorder(g, x0, y0, x1 - x0, y1 - y0, 0xFF55CCFF);
        }

        if (!feedbackMsg.isEmpty() && System.currentTimeMillis() - feedbackAtMs < FEEDBACK_DURATION_MS) {
            g.fill(cl, height - 13, cr, height, palette.header);
            g.fill(cl, height - 13, cl + 1, height, palette.selAccent);
            String clipped = font.plainSubstrByWidth("§7" + feedbackMsg, Math.max(0, cr - cl - 12));
            ChroniclesUIKit.drawString(g, font, clipped, cl + 6, height - 10, palette.textDim);
        }

        if (!sidebar.isSidebarNarrow()) {

            g.enableScissor(0, 0, sidebar.sidebarVisualW(), height);

            RenderSystem.disableDepthTest();
            sidebar.renderSidebarNewChapterButton(g, mx, my);
            sidebar.renderSidebarGear(g, mx, my);
            g.flush();
            RenderSystem.enableDepthTest();
            g.disableScissor();
        }

        if (tutorialOverlay.isVisible(this)) tutorialOverlay.render(this, g, mx, my, cl, cr);

        if (!renderingAsBackdrop && editorState.draggedNode == null && !ctxOpen &&
                !(minimapOpen && isInMinimap(mx, my))) {
            ResourceLocation nowHoverId = null;
            for (Map.Entry<ResourceLocation, int[]> entry : nodeScreenPos.entrySet()) {
                QuestNode node = QuestTreeRegistry.getQuest(entry.getKey());
                if (node == null) continue;
                NodeHitbox btn = nodeButtons.get(node.getId());
                if (btn == null || !btn.visible || !btn.isMouseOver(mx, my)) continue;
                nowHoverId = node.getId();
                break;
            }
            if (!Objects.equals(nowHoverId, tooltipHoverNodeId)) {
                tooltipHoverNodeId = nowHoverId;
                tooltipHoverStartMs = System.currentTimeMillis();
            }
            if (nowHoverId != null && System.currentTimeMillis() - tooltipHoverStartMs >= TOOLTIP_DELAY_MS) {
                QuestNode tipNode = QuestTreeRegistry.getQuest(nowHoverId);

                if (tipNode != null) pendingDeferredDraws.add(() -> renderNodeTooltip(g, tipNode, mx, my));
            }
        }

        if (!renderingAsBackdrop && nodeCtxMenu.isVisible(this)) nodeCtxMenu.render(this, g, mx, my, cl, cr);
        if (!renderingAsBackdrop && pictureCtxMenu.isVisible(this)) pictureCtxMenu.render(this, g, mx, my, cl, cr);

        if (!unlockPathHighlight.isEmpty()) {
            float blink = ChronicleOverviewScreen.animPulse(0.7f, 0.3f, 400.0);
            int ringAlpha = (int) (blink * 0xAA) & 0xFF;
            g.enableScissor(cl, HEADER_H, cr, height);
            for (ResourceLocation uid : unlockPathHighlight) {
                int[] upos = nodeScreenPos.get(uid);
                if (upos == null) continue;
                NodeHitbox uhb = nodeButtons.get(uid);
                if (uhb == null || !uhb.visible) continue;
                int ux = upos[0], uy = upos[1];
                g.fill(ux - 3, uy - 3, ux + sz + 3, uy - 2, (ringAlpha << 24) | 0x0088FF);
                g.fill(ux - 3, uy + sz + 2, ux + sz + 3, uy + sz + 3, (ringAlpha << 24) | 0x0088FF);
                g.fill(ux - 3, uy - 2, ux - 2, uy + sz + 2, (ringAlpha << 24) | 0x0088FF);
                g.fill(ux + sz + 2, uy - 2, ux + sz + 3, uy + sz + 2, (ringAlpha << 24) | 0x0088FF);
            }
            g.disableScissor();
            ChroniclesUIKit.drawString(g, font, "§bUnlock path. §8Esc to clear", cl + 6, height - 10, 0xFF4488FF,
                    false);
        }

        if (!renderingAsBackdrop) menus.renderNonDevCtxMenu(g, (int) mx, (int) my);

        if (depLineRenderer.isContextMenuOpen()) depLineRenderer.renderContextMenu(g, font, mx, my, width, height);
        if (sidebarPanel.contextMenuOpen()) {
            sidebarPanel.renderContextMenu(g, font, (int) mx, (int) my, width, height, sidebar.sidebarColors());
        }

        for (TogglePanel p : List.of(validationPanel, statsPanel)) {
            if (p.isVisible(this)) pendingDeferredDraws.add(() -> p.render(this, g, mx, my, cl, cr));
        }

        if (isDevMode && editorState.multiSelection.size() >= 2) {
            bulkOpsPanel.render(g, mx, my, cl);
        }

        if (openTimeMs > 0) {
            long elapsed = System.currentTimeMillis() - openTimeMs;
            if (elapsed < OPEN_FADE_MS) {
                float t = 1f - (float) elapsed / OPEN_FADE_MS;
                int fadeAlpha = (int) (t * t * 0xFF) & 0xFF;
                if (fadeAlpha > 0) g.fill(0, 0, width, height, (fadeAlpha << 24) | 0x000000);
            }
        }
    }

    private void renderProfilerPanel(GuiGraphics g) {
        var sections = FrameProfiler.sortedSections();
        int panelW = 260;
        int rowH = 11;
        int extraRows = 2;
        int panelH = 20 + 10 + (extraRows + sections.size()) * rowH + 10;
        int px = width - panelW - 4;
        int py = 4;

        g.pose().pushPose();
        g.pose().translate(0, 0, 400f);
        g.fill(px, py, px + panelW, py + panelH, 0xEE0D0D12);
        g.fill(px, py, px + panelW, py + 1, 0xFF00AA55);
        ChroniclesUIKit.drawString(g, font, "§aProfiler §8(Ctrl+P close, Ctrl+Shift+P log now)", px + 5, py + 4,
                0xFFDDDDDD, false);

        Runtime rt = Runtime.getRuntime();
        long usedMb = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024);
        long maxMb = rt.maxMemory() / (1024 * 1024);
        ChroniclesUIKit.drawString(g, font, "§8Heap: §7" + usedMb + "MB §8/ §7" + maxMb + "MB", px + 5, py + 15,
                0xFFAAAAAA, false);

        int gapY = py + 26;
        double gapAvg = FrameProfiler.wallClockGapAvgMs();
        double gapMax = FrameProfiler.wallClockGapMaxMs();
        int gapColor = gapMax > 50 ? 0xFFFF5555 : gapMax > 25 ? 0xFFFFAA33 : 0xFF7A9AAA;
        ChroniclesUIKit.drawString(g, font, "§8Frame gap: §7" +
                String.format("%.2fms (max %.2fms)", gapAvg, gapMax), px + 5, gapY, gapColor, false);

        long gcCount = FrameProfiler.gcCountThisWindow();
        long gcTimeMs = FrameProfiler.gcTimeMsThisWindow();
        int gcColor = gcTimeMs > 0 ? 0xFFFF5555 : 0xFF7A9AAA;
        ChroniclesUIKit.drawString(g, font, "§8GC: §7" + gcCount + " collections, " + gcTimeMs + "ms",
                px + 5, gapY + rowH, gcColor, false);

        double localMax = sections.isEmpty() ? 1.0 : sections.get(0).getValue();
        int y = gapY + rowH * 2 + 12;
        for (var entry : sections) {
            double ms = entry.getValue();
            double worst = FrameProfiler.maxMsFor(entry.getKey());

            float frac = localMax > 0 ? (float) (ms / localMax) : 0;
            int barColor = frac > 0.66f ? 0xFFFF5555 : frac > 0.33f ? 0xFFFFAA33 : 0xFF55CC77;
            int barW = (int) (frac * (panelW - 110));
            g.fill(px + 5, y + 1, px + 5 + Math.max(1, barW), y + rowH - 2, barColor);
            ChroniclesUIKit.drawString(g, font, entry.getKey(), px + 5, y + 1, 0xFF888898, false);

            String msStr = String.format("%.2f §8/ §7%.2fms", ms, worst);
            ChroniclesUIKit.drawString(g, font, msStr, px + panelW - font.width(StringUtil.stripColor(msStr)) - 5,
                    y + 1, 0xFFCCCCCC, false);
            y += rowH;
        }
        g.pose().popPose();
    }

    void openSearchOverlay() {
        if (minecraft != null) minecraft.setScreen(new SearchOverlayScreen(this));
    }

    private ResourceLocation resolveShapeTexture(QuestNode node) {
        return nodeRenderer.resolveShapeTexture(node);
    }

    private void renderNodeShape(GuiGraphics g, QuestNode node, int x, int y, int sz,
                                 boolean hovered, boolean selected) {
        nodeRenderer.renderNodeShape(g, node, x, y, sz, hovered, selected);
    }

    private void renderNodeDetails(GuiGraphics g, QuestNode node, int x, int y, int sz,
                                   boolean hovered, boolean selected) {
        nodeRenderer.renderNodeDetails(g, node, x, y, sz, hovered, selected);
    }

    private void renderQuestGroup(GuiGraphics g, QuestGroup grp, int cl, int cr) {
        nodeRenderer.renderQuestGroup(g, grp, cl, cr);
    }

    private void renderGroupIcon(GuiGraphics g, QuestGroup.GroupIcon icon, int x, int y, int size) {
        nodeRenderer.renderGroupIcon(g, icon, x, y, size);
    }

    @Nullable
    QuestGroup groupAtLabelBar(double mx, double my, int cl) {
        return nodeRenderer.groupAtLabelBar(mx, my, cl);
    }

    BackgroundPictureConfig.Picture pictureAt(double mx, double my, int cl) {
        return nodeRenderer.pictureAt(mx, my, cl);
    }

    private void drawProgressArc(GuiGraphics g, int cx, int cy, int r,
                                 float fraction, int fillColor, int bgColor) {
        nodeRenderer.drawProgressArc(g, cx, cy, r, fraction, fillColor, bgColor);
    }

    public void renderForChildScreen(GuiGraphics g) {
        Minecraft mc = Minecraft.getInstance();
        int liveW = mc.getWindow().getGuiScaledWidth();
        int liveH = mc.getWindow().getGuiScaledHeight();
        if (liveW != this.width || liveH != this.height) {
            this.width = liveW;
            this.height = liveH;
            softRebuild();
        }

        renderingAsBackdrop = true;
        try {
            render(g, -9999, -9999, 0f);
        } finally {
            renderingAsBackdrop = false;
        }
        g.flush();
        RenderSystem.disableScissor();
    }

    public String friendly(String cat) {
        if (cat == null || cat.equals("ALL")) return "All Chapters";
        String resolved = ChapterConfig.getResolvedDisplayName(cat);
        if (resolved != null) return resolved;
        StringBuilder sb = new StringBuilder();
        for (String w : cat.toLowerCase().replace("_", " ").split(" "))
            if (!w.isEmpty()) sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1)).append(' ');
        return sb.toString().trim();
    }

    @Override
    public Font font() {
        return this.font;
    }

    @Override
    public int width() {
        return this.width;
    }

    @Override
    public int height() {
        return this.height;
    }

    @Override
    public boolean isDevMode() {
        return this.isDevMode;
    }

    @Override
    public boolean isRenderingAsBackdrop() {
        return this.renderingAsBackdrop;
    }

    @Override
    public Map<ResourceLocation, int[]> nodeScreenPos() {
        return this.nodeScreenPos;
    }

    @Override
    public String selectedChapter() {
        return this.selectedChapter;
    }

    @Override
    public UndoRedoManager undoRedo() {
        return this.undoRedo;
    }

    @Override
    public List<String> validationIssues(QuestNode node) {
        return validationPanel.issuesFor(node);
    }

    @Override
    @Nullable
    public BackgroundPictureConfig.Picture pictureEditMode() {
        return this.pictureEditMode;
    }

    @Override
    public void setPictureEditMode(@Nullable BackgroundPictureConfig.Picture picture) {
        this.pictureEditMode = picture;
        if (picture != null) {
            pictureEditStartX = picture.x;
            pictureEditStartY = picture.y;
            pictureEditStartW = picture.w;
            pictureEditStartH = picture.h;
        }
    }

    @Override
    public int colorBorder() {
        return palette.border;
    }

    @Override
    public int colorSelectAccent() {
        return palette.selAccent;
    }

    @Override
    public int colorText() {
        return palette.text;
    }

    @Override
    public int colorTextDim() {
        return palette.textDim;
    }

    @Override
    public int colorTextFaint() {
        return palette.textFaint;
    }

    @Override
    public int colorNodeBorderDone() {
        return palette.nbordDone;
    }

    @Override
    public void setCtxOpen(boolean open) {
        ctxOpen = open;
    }

    @Override
    public void setCtxOpenTimeMs(long timeMs) {
        ctxOpenTimeMs = timeMs;
    }

    @Override
    public void setCtxX(int x) {
        ctxX = x;
    }

    @Override
    public void setCtxY(int y) {
        ctxY = y;
    }

    @Override
    public int ctxRawX() {
        return ctxRawX;
    }

    @Override
    public int ctxRawY() {
        return ctxRawY;
    }

    @Override
    public void setCtxRawX(int x) {
        ctxRawX = x;
    }

    @Override
    public void setCtxRawY(int y) {
        ctxRawY = y;
    }

    @Override
    public void setCtxNode(@Nullable QuestNode node) {
        ctxNode = node;
    }

    @Override
    public void setCtxGroup(@Nullable QuestGroup group) {
        ctxGroup = group;
    }

    @Override
    public void setTestMode(boolean testMode) {
        this.testMode = testMode;
    }

    @Override
    public void setTestModeData(PlayerQuestData data) {
        this.testModeData = data;
    }

    @Override
    public void setGridSnapEnabled(boolean enabled) {
        this.gridSnapEnabled = enabled;
    }

    @Override
    public GridDisplayMode gridDisplayMode() {
        return gridDisplayMode;
    }

    @Override
    public void setGridDisplayMode(GridDisplayMode mode) {
        this.gridDisplayMode = mode;
    }

    @Override
    public void setNodeSizeEditMode(@Nullable QuestNode node) {
        this.nodeSizeEditMode = node;
        nodeSizeTypedBuffer = null;
        if (node != null) {
            nodeSizeEditStartSize = node.getNodeSize();
            nodeSizeEditStartOverridePx = node.getSizeOverridePx();
            nodeSizeEditStartX = node.getCustomX();
            nodeSizeEditStartY = node.getCustomY();
        }
    }

    @Override
    public void setNodeSizeDragAccX(double v) {
        this.nodeSizeDragAccX = v;
    }

    @Override
    public void setNodeSizeDragAccY(double v) {
        this.nodeSizeDragAccY = v;
    }

    @Override
    public boolean ctxOpen() {
        return ctxOpen;
    }

    @Override
    public long ctxOpenTimeMs() {
        return ctxOpenTimeMs;
    }

    @Override
    public int ctxX() {
        return ctxX;
    }

    @Override
    public int ctxY() {
        return ctxY;
    }

    @Override
    public float ctxScale() {
        return ctxScale;
    }

    @Override
    public void setCtxScale(float scale) {
        ctxScale = scale;
    }

    @Override
    @Nullable
    public QuestNode ctxNode() {
        return ctxNode;
    }

    @Override
    @Nullable
    public QuestGroup ctxGroup() {
        return ctxGroup;
    }

    @Override
    public boolean ctxMoveCatOpen() {
        return ctxMoveCatOpen;
    }

    @Override
    public void setCtxMoveCatOpen(boolean open) {
        ctxMoveCatOpen = open;
    }

    @Override
    public int ctxMoveCatScroll() {
        return ctxMoveCatScroll;
    }

    @Override
    public void setCtxMoveCatScroll(int scroll) {
        ctxMoveCatScroll = scroll;
    }

    @Override
    public int viewOffX() {
        return viewOffX;
    }

    @Override
    public int viewOffY() {
        return viewOffY;
    }

    public int gridSnap() {
        return gridSnap;
    }

    @Override
    public boolean gridSnapEnabled() {
        return gridSnapEnabled;
    }

    public boolean dragForceSnap() {
        return dragForceSnap;
    }

    public Map<ResourceLocation, NodeHitbox> nodeButtons() {
        return nodeButtons;
    }

    public int dragGrabX() {
        return dragGrabX;
    }

    public int dragGrabY() {
        return dragGrabY;
    }

    public int dragOrigX() {
        return dragOrigX;
    }

    public int dragOrigY() {
        return dragOrigY;
    }

    public Set<ResourceLocation> hiddenByCollapse() {
        return hiddenByCollapse;
    }

    public boolean hideCompleted() {
        return hideCompleted;
    }

    @Override
    public PlayerQuestData playerData() {
        return playerData;
    }

    public void resetViewOffset() {
        viewOffX = 0;
        viewOffY = 0;
    }

    public Set<ResourceLocation> subgraphNodes() {
        return subgraphNodes;
    }

    @Override
    public boolean testMode() {
        return testMode;
    }

    public PlayerQuestData testModeData() {
        return testModeData;
    }

    public int colorNodeLocked() {
        return palette.nodeLocked;
    }

    public int colorNodeUnlocked() {
        return palette.nodeUnlocked;
    }

    public int colorNodeActive() {
        return palette.nodeActive;
    }

    public int colorNodeDone() {
        return palette.nodeDone;
    }

    public int colorNodeBorderLocked() {
        return palette.nbordLocked;
    }

    public int colorNodeBorderUnlocked() {
        return palette.nbordUnlocked;
    }

    public int colorNodeBorderActive() {
        return palette.nbordActive;
    }

    public int colorNodeBorderDev() {
        return palette.nbordDev;
    }

    public int colorTextDone() {
        return palette.textDone;
    }

    public int colorTextActive() {
        return palette.textAct;
    }

    @Override
    public int colorBorderLit() {
        return palette.borderLit;
    }

    public boolean validationPanelOpen() {
        return validationPanel.isOpen();
    }

    @Override
    public boolean statsPanelOpen() {
        return statsPanel.isOpen();
    }

    public boolean minimapOpen() {
        return minimapOpen;
    }

    public int dbgFull3DIconCount() {
        return dbgFull3DIconCount;
    }

    public void setDbgFull3DIconCount(int count) {
        dbgFull3DIconCount = count;
    }

    public int dbgCustomIconCount() {
        return dbgCustomIconCount;
    }

    public void setDbgCustomIconCount(int count) {
        dbgCustomIconCount = count;
    }

    public int dbgPickedTextureIconCount() {
        return dbgPickedTextureIconCount;
    }

    public void setDbgPickedTextureIconCount(int count) {
        dbgPickedTextureIconCount = count;
    }

    public int dbgFluidIconCount() {
        return dbgFluidIconCount;
    }

    public void setDbgFluidIconCount(int count) {
        dbgFluidIconCount = count;
    }

    public int dbgGlyphIconCount() {
        return dbgGlyphIconCount;
    }

    public void setDbgGlyphIconCount(int count) {
        dbgGlyphIconCount = count;
    }

    public Map<String, Integer> dbgShapeCounts() {
        return dbgShapeCounts;
    }

    record EmergencyPill(int ready, long soonestCooldownMs) {}

    public String shortLabel(QuestNode node) {
        MinecraftServer server = minecraft != null ? minecraft.getSingleplayerServer() : null;
        String t = node.getEffectiveTitleRaw(server, minecraft.player).getString();

        int maxW = (int) (scaledNodeSize(node) * 1.6f) + 40;
        return net.phoenixvine.chronicles.client.util.EffectText.width(font, t) > maxW ?
                net.phoenixvine.chronicles.client.util.EffectText.cut(font, t, maxW - 4) + "…" : t;
    }

    @Override
    public String shortName(QuestNode node, int maxW) {
        MinecraftServer server = minecraft != null ? minecraft.getSingleplayerServer() : null;
        String t = node.getEffectiveTitleRaw(server, minecraft.player).getString();
        return net.phoenixvine.chronicles.client.util.EffectText.width(font, t) > maxW ?
                net.phoenixvine.chronicles.client.util.EffectText.cut(font, t, maxW - 4) + "…" : t;
    }

    void fitToCanvas() {
        if (!applyFitView()) return;
        rebuild();
    }

    private boolean applyFitView() {
        int cl = sidebarW(), cr = width;
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE;
        for (QuestNode n : QuestTreeRegistry.getAllQuests().values()) {
            if (!catMatches(n)) continue;

            int nx = n.getCustomX();
            int ny = n.getCustomY();
            minX = Math.min(minX, nx - NODE_SIZE / 2);
            minY = Math.min(minY, ny - NODE_SIZE / 2);
            maxX = Math.max(maxX, nx + NODE_SIZE / 2);
            maxY = Math.max(maxY, ny + NODE_SIZE / 2);
        }
        if (minX == Integer.MAX_VALUE) return false;
        int canvasW = cr - cl - 20, canvasH = height - HEADER_H - 20;
        int contentW = maxX - minX, contentH = maxY - minY;
        float targetEffectiveZoom = Math.min((float) canvasW / contentW, (float) canvasH / contentH);
        zoom = Math.max(ZOOM_MIN, Math.min(ZOOM_MAX, effectiveToRawZoom(targetEffectiveZoom)));
        viewOffX = (int) (canvasW / 2f - (minX + contentW / 2f) * posZoom()) + 10;
        viewOffY = (int) (canvasH / 2f - (minY + contentH / 2f) * posZoom()) + 10;
        return true;
    }

    private void renderNodeTooltip(GuiGraphics g, QuestNode node, int mx, int my) {
        nodeRenderer.renderNodeTooltip(g, node, mx, my);
    }

    public void setFeedback(String msg, Object... args) {
        feedbackMsg = msg.formatted(args);
        feedbackAtMs = System.currentTimeMillis();
    }

    @Override
    public void pushUndo(String undoMsg, Runnable revertAction, Runnable redoAction) {
        String redoMsg = undoMsg.startsWith("Undo: ") ? "Redo: " + undoMsg.substring("Undo: ".length()) :
                "Redo: " + undoMsg;
        undoRedo.push(
                () -> {
                    revertAction.run();
                    setFeedback(undoMsg);
                },
                () -> {
                    redoAction.run();
                    setFeedback(redoMsg);
                });
    }

    @Override
    public void setFeedbackDone(String doneMsg, Object... args) {
        setFeedback(doneMsg + "  (Ctrl+Z to undo)", args);
    }

    void openLineSettingsFor(QuestNode parentNode) {
        if (minecraft != null) minecraft.setScreen(new DepLineSettingsScreen(this, selectedChapter, parentNode));
    }

    void rebuildFromExternal() {
        rebuild();
    }

    void saveNodeHideDepLineToDisk(QuestNode node) {
        QuestFileSaver.updateHideDepLine(node);
    }

    public void saveNodeToDisk(QuestNode node) {
        QuestFileSaver.updateNodePosition(node);
    }

    public void refreshEdgeEndpointsFor(QuestNode node) {
        depLineRenderer.refreshEdgeEndpoints(node.getId(), this::nodeCenterForLine, posZoom(),
                QuestChroniclesSettings.get());
    }

    @Override
    public void saveNodeShapeToDisk(QuestNode node, String shape) {
        QuestFileSaver.updateNodeShape(node, shape);
    }

    @Override
    public void saveNodeChapterToDisk(QuestNode node, String cat) {
        QuestFileSaver.updateNodeChapter(node, cat);
    }

    @Override
    public void saveNodePrereqsToDisk(QuestNode node) {
        QuestFileSaver.updateNodePrerequisites(node);
    }

    @Override
    public void saveNodeShapeTextureToDisk(QuestNode node) {
        QuestFileSaver.updateNodeShapeTexture(node);
    }

    @Override
    public void deleteQuestFiles(QuestNode node) {
        QuestFileSaver.deleteQuestFiles(node);
    }

    public int[] minimapBounds(int cr) {
        return minimap.bounds(cr, height);
    }

    private void renderMinimap(GuiGraphics g, int mx, int my, int cl, int cr) {
        minimap.render(g, font, cl, cr, width, height, HEADER_H, posZoom(), viewOffX, viewOffY,
                this::catMatches, this::getState);
    }

    private boolean isInMinimap(double x, double y) {
        return minimap.isInMinimap(x, y, minimapOpen, width, height);
    }

    public void minimapPanTo(double sx, double sy, int cl) {
        int[] p = minimap.panTo(sx, sy, cl, width, height, HEADER_H, posZoom());
        if (p == null) return;
        viewOffX = p[0];
        viewOffY = p[1];
    }

    @Override
    public void onClose() {
        PhantasiaCompat.closePreview(phantasiaPreview);
        phantasiaPreview = null;
        quickDepKeyDown = false;
        linkDragSource = null;
        minecraft.setScreen(parent);
    }

    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void tick() {
        super.tick();

        int v = S2CSyncPlayerProgressPacket.getVersion();
        if (v != lastSeenProgressVersion) {
            lastSeenProgressVersion = v;
            ancestorGatedCache.clear();
            progressCache.clear();
            attentionCache.clear();
            rewardsCache.clear();
        }

        if (minecraft != null && minecraft.player != null) {
            String currentHand = minecraft.player.getMainHandItem().getItem().toString();
            if (this.lastTickHand == null) this.lastTickHand = currentHand;
            if (!currentHand.equals(this.lastTickHand)) {
                this.lastTickHand = currentHand;
                softRebuild();
            }
        }
    }

    public enum GridDisplayMode {

        ON_DRAG("On Move"),
        ALWAYS("Always"),
        CURSOR_BOX("Cursor Box");

        public final String label;

        GridDisplayMode(String label) {
            this.label = label;
        }

        public GridDisplayMode next() {
            GridDisplayMode[] v = values();
            return v[(ordinal() + 1) % v.length];
        }
    }

    public static final class NodeHitbox {

        public int x;
        public int y;
        public int w;
        public int h;
        public boolean visible = true;
        public boolean active = true;

        int getX() {
            return x;
        }

        public void setX(int nx) {
            x = nx;
        }

        int getY() {
            return y;
        }

        public void setY(int ny) {
            y = ny;
        }

        public boolean isMouseOver(double mx, double my) {
            return visible && mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    public record CtxItem(String label, String color, boolean isSep, boolean isDanger, Runnable action) {

        public static CtxItem sep() {
            return new CtxItem("", "", true, false, () -> {});
        }
    }
}
