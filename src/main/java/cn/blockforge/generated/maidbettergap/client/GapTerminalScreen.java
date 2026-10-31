package cn.blockforge.generated.maidbettergap.client;

import cn.blockforge.generated.maidbettergap.gap.ContainerService;
import cn.blockforge.generated.maidbettergap.gap.GapItemData;
import cn.blockforge.generated.maidbettergap.gap.ItemCategory;
import cn.blockforge.generated.maidbettergap.gap.TagMatcher;
import cn.blockforge.generated.maidbettergap.gap.TerminalSnapshot;
import cn.blockforge.generated.maidbettergap.network.GapActionPacket;
import cn.blockforge.generated.maidbettergap.network.ModNetwork;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 女仆隙间终端界面：
 * - 左列：终端里存着的女仆列表（点击切换当前管理哪一只，顶部「删除」按钮进入删除模式）
 * - 中列：绑定容器选择 + 分类标签页 + 物品网格（左键按预设数量拿取，Shift+左键加入过滤名单）+ 玩家物品栏
 * - 右列：容器信息 + 传输方向 + 这个容器自己的黑白名单/类别匹配/9 格名单
 * - 底栏：拿取数量 / 自动补货 / 槽位配置 / 刷新 / 关闭
 *
 * 名单是「每个容器一套」的：容器没单独设过名单时沿用隙间的共用名单（老存档行为不变），
 * 点「名单:独立」之后这个容器才只认自己的名单，于是可以把小麦送进箱子1、小麦种子送进箱子2。
 */
public class GapTerminalScreen extends Screen {

    private static final int GW = 440;
    private static final int GH = 288;

    private static final ItemCategory[] TABS = ItemCategory.values();
    private static final int COLS = 9;
    private static final int ROWS = 3;
    private static final int PER_PAGE = COLS * ROWS;

    /** 女仆列表：宽 88、每行 11 像素，一屏最多 16 行，多的用滚轮翻。 */
    private static final int MAID_W = 88;
    private static final int MAID_ROW_H = 11;
    private static final int MAID_ROWS = 16;

    private final class RowInfo {
        final int slot;
        final ItemStack stack;

        RowInfo(int slot, ItemStack stack) {
            this.slot = slot;
            this.stack = stack;
        }
    }

    private TerminalSnapshot snap;
    private int tab;
    private int page;
    private int selected;
    // 布局锚点（init 里算好，render 与点击判定共用，避免重叠）
    private int left;
    private int top;
    private int maidLeft;
    private int maidTop;
    private int colLeft;
    private int rx;
    private int gridLeft;
    private int gridTop;
    private int filterLeft;
    private int filterTop;
    private int invLeft;
    private int invTop;
    private int hotTop;
    private final List<RowInfo> visible = new ArrayList<>();
    /** 面板的显示缩放：窗口放不下时小于 1（见 init）。 */
    private float scale = 1f;
    /** 自动刷新的倒计时（tick）。 */
    private int refreshCooldown = 20;
    /** 女仆列表的滚动行数（超过一屏才有效）。 */
    private int maidScroll = 0;
    /** 删除模式：先点顶部的「删除」，再点列表里的女仆才会真的移除，避免误触。 */
    private boolean deleteMode = false;
    /**
     * 女仆列表上的一次点击还没被服务端确认（等下一次快照刷新）。
     * 有了它，双击同一个位置也只会删掉一只女仆——否则第二下用的是旧下标，
     * 会误删列表里紧跟在后面的另一只。
     */
    private boolean maidActionPending = false;
    /** 女仆操作等待回包的兜底倒计时（tick）：包被限速丢掉时到点自动放行，不卡到下一次快照（L6）。 */
    private int maidActionTicks = 0;

    public GapTerminalScreen(TerminalSnapshot snapshot) {
        super(Component.translatable("maidgap.gui.title"));
        this.snap = snapshot;
        clampSelection();
    }

    public void updateSnapshot(TerminalSnapshot snapshot) {
        this.snap = snapshot;
        this.maidActionPending = false;
        clampSelection();
        rebuildWidgets();
    }

    /**
     * 标题：终端模式 =「隙间终端 · 女仆名」；拿着隙间本身打开 = 那件隙间的名字（如「隙间·增强」）。
     * 以前这两种情况都写死成「女仆隙间终端」，玩家拿着隙间右键会以为开错了界面。
     */
    private Component titleComponent() {
        if (snap.viaTerminal) {
            String name = snap.maidName == null || snap.maidName.isEmpty() ? "?" : snap.maidName;
            if (name.length() > 14) {
                name = name.substring(0, 14);
            }
            return Component.translatable("maidgap.gui.title.terminal", name);
        }
        String gapName = snap.gapName == null ? "" : snap.gapName;
        if (!gapName.isEmpty()) {
            if (gapName.length() > 24) {
                gapName = gapName.substring(0, 24);
            }
            return Component.literal(gapName);
        }
        return Component.translatable("maidgap.gui.title.gap");
    }

    /** 给槽位配置子界面用：终端当前管理的女仆名（没有则空串）。 */
    public String linkedMaidName() {
        return snap.maidName == null ? "" : snap.maidName;
    }

    private void clampSelection() {
        if (selected >= snap.entries.size()) {
            selected = Math.max(0, snap.entries.size() - 1);
        }
        if (selected < 0) {
            selected = 0;
        }
        int maxScroll = Math.max(0, snap.maids.size() - MAID_ROWS);
        if (maidScroll > maxScroll) {
            maidScroll = maxScroll;
        }
        if (maidScroll < 0) {
            maidScroll = 0;
        }
    }

    @Override
    protected void init() {
        // 面板固定 GW×GH，但窗口（GUI 缩放拉大时）可能放不下：
        // 放不下就整体等比缩小，而不是把下半截画到屏幕外。
        this.scale = Math.min(1f, Math.min((this.width - 4f) / GW, (this.height - 4f) / GH));
        if (this.scale <= 0f) {
            this.scale = 1f;
        }
        // 布局一律用“逻辑坐标”（未缩放），render 时统一乘 scale、鼠标坐标统一除 scale
        int logicalW = Math.round(this.width / this.scale);
        int logicalH = Math.round(this.height / this.scale);
        left = Math.max(2, (logicalW - GW) / 2);
        top = Math.max(2, (logicalH - GH) / 2);
        maidLeft = left + 4;
        maidTop = top + 44;
        colLeft = left + 96;
        rx = left + 272;
        gridLeft = colLeft + 4;
        gridTop = top + 52;
        invLeft = gridLeft;
        invTop = top + 136;
        hotTop = top + 194;
        filterLeft = rx;
        filterTop = top + 172;
        clampSelection();

        // 女仆列表顶部的「删除」开关（开着时点列表里的女仆 = 把她移出列表）
        if (snap.viaTerminal) {
            Button del = Button.builder(
                    Component.translatable(deleteMode ? "maidgap.gui.maid_delete_on" : "maidgap.gui.maid_delete"), btn -> {
                        deleteMode = !deleteMode;
                        rebuildWidgets();
                    }).bounds(maidLeft, top + 26, MAID_W, 14).build();
            del.setTooltip(Tooltip.create(Component.translatable("maidgap.gui.maid_delete_tip")));
            addRenderableWidget(del);
        }

        // 容器选择按钮（第一行；右边那行标题里放绑定计数，互不重叠）
        for (int i = 0; i < snap.entries.size(); i++) {
            final int idx = i;
            TerminalSnapshot.Entry entry = snap.entries.get(i);
            String mark = idx == selected ? "▶" : "  ";
            // 带 * = 这个容器用的是自己的独立名单（没带 = 沿用共用名单）
            if (entry.ownFilter) {
                mark = idx == selected ? "▶*" : " *";
            }
            Button bindBtn = Button.builder(
                            Component.literal(mark).append(Component.translatable("maidgap.gui.bind_n",
                                    String.valueOf(idx + 1))), btn -> {
                                selected = idx;
                                page = 0;
                                rebuildWidgets();
                            })
                    .bounds(colLeft + 4 + idx * 40, top + 16, 38, 16).build();
            bindBtn.setTooltip(Tooltip.create(Component.translatable("maidgap.gui.bind_tip",
                    entry.binding.pos().getX() + ", " + entry.binding.pos().getY() + ", "
                            + entry.binding.pos().getZ(),
                    Component.translatable(entry.ownFilter ? "maidgap.gui.scope_own" : "maidgap.gui.scope_shared"))));
            addRenderableWidget(bindBtn);
        }

        // 分类标签页（第二行，整行只有标签页）
        for (int i = 0; i < TABS.length; i++) {
            final int idx = i;
            addRenderableWidget(Button.builder(
                    Component.translatable("maidgap.tab." + TABS[i].name().toLowerCase(java.util.Locale.ROOT)),
                    btn -> {
                        tab = idx;
                        page = 0;
                        rebuildWidgets();
                    }).bounds(colLeft + 4 + i * 46, top + 34, 44, 14).build());
        }

        // 翻页（物品网格正下方）
        addRenderableWidget(Button.builder(Component.literal("◀"), btn -> {
            if (page > 0) {
                page--;
                rebuildWidgets();
            }
        }).bounds(gridLeft, top + 110, 30, 14).build());
        addRenderableWidget(Button.builder(Component.literal("▶"), btn -> {
            page++;
            rebuildWidgets();
        }).bounds(gridLeft + 34, top + 110, 30, 14).build());

        TerminalSnapshot.Entry sel = currentEntry();
        if (snap.holdingGap) {
            if (!snap.entries.isEmpty()) {
                addRenderableWidget(Button.builder(Component.translatable("maidgap.gui.unbind"), btn ->
                        ModNetwork.sendToServer(new GapActionPacket(ContainerService.ACT_UNBIND, selected, 0)))
                        .bounds(rx, top + 84, 152, 14).build());
            }

            addRenderableWidget(Button.builder(
                    Component.translatable("maidgap.gui.io", Component.translatable(
                            snap.maidToChest ? "maidgap.io.maid_to_chest" : "maidgap.io.chest_to_maid")), btn ->
                            ModNetwork.sendToServer(new GapActionPacket(ContainerService.ACT_TOGGLE_IO_MODE, 0, 0)))
                    .bounds(rx, top + 100, 152, 14).build());

            // 名单开关只在选中了容器时出现：每个容器一套名单，操作对象就是当前这个容器
            if (sel != null) {
                Button modeBtn = Button.builder(
                        Component.translatable(filterBlacklist() ? "maidgap.filter.black" : "maidgap.filter.white"), btn ->
                                ModNetwork.sendToServer(new GapActionPacket(
                                        ContainerService.ACT_TOGGLE_FILTER_MODE, selected, 0)))
                        .bounds(rx, top + 116, 74, 14).build();
                modeBtn.setTooltip(Tooltip.create(Component.translatable("maidgap.gui.filter_mode_tip")));
                addRenderableWidget(modeBtn);

                Button tagBtn = Button.builder(
                        Component.translatable("maidgap.gui.tag_match2",
                                Component.translatable(filterTagMatch() ? "maidgap.gui.on" : "maidgap.gui.off")), btn ->
                                ModNetwork.sendToServer(new GapActionPacket(
                                        ContainerService.ACT_TOGGLE_TAG_MATCH, selected, 0)))
                        .bounds(rx + 78, top + 116, 74, 14).build();
                tagBtn.setTooltip(Tooltip.create(Component.translatable("maidgap.gui.tag_tip")));
                addRenderableWidget(tagBtn);

                Button scopeBtn = Button.builder(
                        Component.translatable("maidgap.gui.scope",
                                Component.translatable(filterOwn() ? "maidgap.gui.scope_own" : "maidgap.gui.scope_shared")),
                        btn -> ModNetwork.sendToServer(new GapActionPacket(
                                ContainerService.ACT_TOGGLE_FILTER_SCOPE, selected, 0)))
                        .bounds(rx, top + 132, 74, 14).build();
                scopeBtn.setTooltip(Tooltip.create(Component.translatable("maidgap.gui.scope_tip")));
                addRenderableWidget(scopeBtn);

                if (filterUsed() > 0) {
                    addRenderableWidget(Button.builder(Component.translatable("maidgap.gui.clear_filter_short"), btn ->
                            ModNetwork.sendToServer(new GapActionPacket(
                                    ContainerService.ACT_CLEAR_FILTERS, selected, 0)))
                            .bounds(rx + 78, top + 132, 74, 14).build());
                }
            }
        }

        // 储罐取桶按钮（名单右侧，容量信息放悬停提示里）
        if (snap.holdingGap && sel != null && sel.status == TerminalSnapshot.STATUS_OK && sel.binding.isFluid()) {
            for (int i = 0; i < sel.fluids.size() && i < 3; i++) {
                final int realSlot = sel.tankSlots.get(i);
                FluidStack fs = sel.fluids.get(i);
                Button bucketBtn = Button.builder(
                        Component.translatable("maidgap.gui.bucket_of", truncate(fs.getDisplayName().getString(), 8)),
                        btn -> ModNetwork.sendToServer(
                                new GapActionPacket(ContainerService.ACT_EXTRACT_BUCKET, selected, realSlot)))
                        .bounds(rx + 58, top + 172 + i * 16, 94, 14).build();
                bucketBtn.setTooltip(Tooltip.create(Component.literal(
                        fs.getDisplayName().getString() + ": " + fs.getAmount() + "/" + sel.capacities.get(i) + " mB")));
                addRenderableWidget(bucketBtn);
            }
        }

        // 底栏：拿取数量 / 自动补货 / 槽位配置 / 刷新 / 关闭
        Button pickupBtn = Button.builder(
                Component.translatable("maidgap.gui.pickup", String.valueOf(snap.pickupAmount)), btn ->
                        ModNetwork.sendToServer(new GapActionPacket(ContainerService.ACT_CYCLE_PICKUP, 0, 0)))
                .bounds(left + 6, top + 234, 86, 18).build();
        pickupBtn.setTooltip(Tooltip.create(Component.translatable("maidgap.gui.pickup_tip")));
        addRenderableWidget(pickupBtn);

        Button refillBtn = Button.builder(
                Component.translatable("maidgap.gui.refill", snap.autoRefill
                        ? Component.translatable("maidgap.gui.on").withStyle(ChatFormatting.GREEN)
                        : Component.translatable("maidgap.gui.off").withStyle(ChatFormatting.RED)), btn ->
                        ModNetwork.sendToServer(new GapActionPacket(ContainerService.ACT_TOGGLE_REFILL, 0, 0)))
                .bounds(left + 116, top + 234, 94, 18).build();
        refillBtn.setTooltip(Tooltip.create(Component.translatable("maidgap.gui.refill_tip")));
        addRenderableWidget(refillBtn);

        if (snap.holdingGap) {
            Button slotBtn = Button.builder(Component.translatable("maidgap.gui.slot_config"), btn ->
                    this.minecraft.setScreen(new SlotConfigScreen(this, snap.slotLocks, snap.maidSlots)))
                    .bounds(left + 234, top + 234, 64, 18).build();
            slotBtn.setTooltip(Tooltip.create(Component.translatable("maidgap.gui.slot_tip")));
            addRenderableWidget(slotBtn);
        }
        addRenderableWidget(Button.builder(Component.translatable("maidgap.gui.refresh"), btn ->
                ModNetwork.sendToServer(new GapActionPacket(ContainerService.ACT_REFRESH, 0, 0)))
                .bounds(left + 322, top + 234, 44, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("maidgap.gui.close"), btn -> this.onClose())
                .bounds(left + 390, top + 234, 44, 18).build());

        rebuildVisible();
    }

    private TerminalSnapshot.Entry currentEntry() {
        if (snap.entries.isEmpty()) {
            return null;
        }
        return snap.entries.get(Math.min(selected, snap.entries.size() - 1));
    }

    // ------------------------------------------------------------------
    // 名单：当前容器生效的那一套（容器有独立名单就是它自己的，否则是共用名单）
    // ------------------------------------------------------------------

    private List<ItemStack> filterList() {
        TerminalSnapshot.Entry e = currentEntry();
        return e == null ? snap.filters : e.filters;
    }

    private boolean filterBlacklist() {
        TerminalSnapshot.Entry e = currentEntry();
        return e == null ? snap.blacklist : e.blacklist;
    }

    private boolean filterTagMatch() {
        TerminalSnapshot.Entry e = currentEntry();
        return e == null ? snap.tagMatch : e.tagMatch;
    }

    private boolean filterOwn() {
        TerminalSnapshot.Entry e = currentEntry();
        return e != null && e.ownFilter;
    }

    private int filterUsed() {
        int used = 0;
        for (ItemStack f : filterList()) {
            if (!f.isEmpty()) {
                used++;
            }
        }
        return used;
    }

    private void rebuildVisible() {
        visible.clear();
        TerminalSnapshot.Entry e = currentEntry();
        if (e == null || e.status != TerminalSnapshot.STATUS_OK || e.binding.isFluid()) {
            return;
        }
        ItemCategory cat = TABS[tab];
        for (int i = 0; i < e.stacks.size(); i++) {
            ItemStack stack = e.stacks.get(i);
            if (!stack.isEmpty() && cat.matches(stack)) {
                visible.add(new RowInfo(e.slots.get(i), stack));
            }
        }
        int maxPage = Math.max(0, (visible.size() - 1) / PER_PAGE);
        if (page > maxPage) {
            page = maxPage;
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics);

        // 整体等比缩放：下面的所有绘制都按“逻辑坐标”写，鼠标坐标换算回逻辑坐标再判定
        guiGraphics.pose().pushPose();
        if (scale != 1f) {
            guiGraphics.pose().scale(scale, scale, 1f);
            mouseX = Math.round(mouseX / scale);
            mouseY = Math.round(mouseY / scale);
        }

        // 面板底色必须先画：super.render 画的是按钮，画在后面会把按钮整片盖住
        guiGraphics.fill(left, top, left + GW, top + GH, 0xF0101014);
        guiGraphics.fill(left, top, left + GW, top + 2, 0xFF8A2BE2);
        // 分列竖线：左列女仆列表 / 中列背包与箱子 / 右列设置
        guiGraphics.fill(maidLeft + MAID_W + 3, top + 10, maidLeft + MAID_W + 4, top + 226, 0xFF2A2A3A);
        guiGraphics.fill(rx - 6, top + 52, rx - 5, top + 226, 0xFF2A2A3A);

        super.render(guiGraphics, mouseX, mouseY, partialTick);

        // 标题：给右上角「已绑定 n/m」按实际宽度留位置（拿着隙间时标题是物品名，长短不一）
        Component bindCount = snap.holdingGap
                ? Component.translatable("maidgap.gui.bind_count",
                        String.valueOf(snap.entries.size()), String.valueOf(snap.maxContainers))
                : null;
        int titleRight = bindCount == null ? left + GW : left + GW - this.font.width(bindCount) - 8;
        guiGraphics.drawCenteredString(this.font, titleComponent(), left + (titleRight - left) / 2, top + 4, 0xE0C8FF);

        // 绑定计数：放在标题那一行右端
        if (bindCount != null) {
            guiGraphics.drawString(this.font, bindCount,
                    left + GW - 4 - this.font.width(bindCount), top + 4, 0xC8B4FF, false);
        }

        // —— 左列：女仆列表 ——
        int hoverMaid = -1;
        if (snap.viaTerminal) {
            guiGraphics.drawString(this.font,
                    Component.translatable("maidgap.gui.maid_list",
                            String.valueOf(snap.maids.size()), String.valueOf(snap.maidLimit)),
                    maidLeft, top + 14, 0xC8B4FF, false);
            for (int i = 0; i < MAID_ROWS; i++) {
                int idx = maidScroll + i;
                if (idx >= snap.maids.size()) {
                    break;
                }
                TerminalSnapshot.MaidEntry m = snap.maids.get(idx);
                int y = maidTop + i * MAID_ROW_H;
                boolean active = m.uuid != null && m.uuid.equals(snap.activeMaid);
                boolean hovered = mouseX >= maidLeft && mouseX < maidLeft + MAID_W
                        && mouseY >= y && mouseY < y + MAID_ROW_H;
                if (active) {
                    guiGraphics.fill(maidLeft, y, maidLeft + MAID_W, y + MAID_ROW_H, 0x508A2BE2);
                }
                if (hovered) {
                    guiGraphics.fill(maidLeft, y, maidLeft + MAID_W, y + MAID_ROW_H,
                            deleteMode ? 0x60FF3030 : 0x30FFFFFF);
                    hoverMaid = idx;
                }
                // 状态文字走 lang（英文环境要能翻译）：非本人 > 离线 > 无隙间
                Component status = m.notOwner ? Component.translatable("maidgap.gui.status_notowner")
                        : !m.online ? Component.translatable("maidgap.gui.status_offline")
                        : !m.hasGap ? Component.translatable("maidgap.gui.status_nogap") : null;
                int statusW = status == null ? 0 : this.font.width(status) + 2;
                String name = m.name == null || m.name.isEmpty() ? "?" : m.name;
                int nameRoom = MAID_W - 10 - statusW;
                name = trimToWidth(name, Math.max(12, nameRoom));
                int color = !m.online ? 0x9A9A9A : active ? 0xE0C8FF : 0xE8E8E8;
                guiGraphics.drawString(this.font, active ? "▶" : " ", maidLeft + 1, y + 2,
                        active ? 0xD0A0FF : 0x707070, false);
                guiGraphics.drawString(this.font, name, maidLeft + 9, y + 2, color, false);
                if (status != null) {
                    int statusColor = m.notOwner ? 0xC0A060 : m.online ? 0xFF8080 : 0x808080;
                    guiGraphics.drawString(this.font, status,
                            maidLeft + MAID_W - statusW, y + 2, statusColor, false);
                }
            }
            if (snap.maids.isEmpty()) {
                guiGraphics.drawString(this.font, Component.translatable("maidgap.gui.maid_empty")
                        .withStyle(ChatFormatting.DARK_GRAY), maidLeft, maidTop, 0x909090, false);
            } else if (snap.maids.size() > MAID_ROWS) {
                // 超过一屏：底部画“n/m”页码，光有滚轮不好发现（L5）
                int shown = Math.min(MAID_ROWS, snap.maids.size() - maidScroll);
                String pageLabel = (maidScroll + 1) + "-" + (maidScroll + shown) + "/" + snap.maids.size();
                // 画在列表下方空档（最后一行底 + 2），不会和任何一行文字重叠
                guiGraphics.drawString(this.font, pageLabel, maidLeft + 2,
                        maidTop + MAID_ROWS * MAID_ROW_H + 2, 0x9A8AC0, false);
            }
        } else {
            guiGraphics.drawString(this.font, Component.translatable("maidgap.gui.maid_list_title"),
                    maidLeft, top + 14, 0x808080, false);
            guiGraphics.drawString(this.font, Component.translatable("maidgap.gui.maid_list_only"),
                    maidLeft, maidTop, 0x707070, false);
        }

        // 物品网格（箱子内容）
        int start = page * PER_PAGE;
        int hoverIdx = -1;
        for (int i = 0; i < PER_PAGE; i++) {
            int row = i / COLS;
            int col = i % COLS;
            int x = gridLeft + col * 18;
            int y = gridTop + row * 18;
            guiGraphics.fill(x, y, x + 18, y + 18, 0x30FFFFFF);
            int dataIdx = start + i;
            if (dataIdx < visible.size()) {
                RowInfo info = visible.get(dataIdx);
                guiGraphics.renderItem(info.stack, x + 1, y + 1);
                guiGraphics.renderItemDecorations(this.font, info.stack, x + 1, y + 1);
                if (mouseX >= x && mouseX < x + 18 && mouseY >= y && mouseY < y + 18) {
                    hoverIdx = dataIdx;
                }
            }
        }

        // 页码
        int totalPages = Math.max(1, (visible.size() + PER_PAGE - 1) / PER_PAGE);
        guiGraphics.drawString(this.font,
                Component.translatable("maidgap.gui.page", String.valueOf(page + 1), String.valueOf(totalPages)),
                gridLeft + 70, top + 114, 0xC0C0C0, false);

        // 玩家物品栏
        Inventory inv = this.minecraft == null || this.minecraft.player == null
                ? null : this.minecraft.player.getInventory();
        int hoverInv = -1;
        if (inv != null) {
            guiGraphics.drawString(this.font,
                    Component.translatable("maidgap.gui.inv_label").withStyle(ChatFormatting.GRAY),
                    invLeft, top + 126, 0xA0A0A0, false);
            for (int row = 0; row < 3; row++) {
                for (int col = 0; col < 9; col++) {
                    int idx = 9 + row * 9 + col;
                    int x = invLeft + col * 18;
                    int y = invTop + row * 18;
                    if (drawInvSlot(guiGraphics, inv, idx, x, y, mouseX, mouseY)) {
                        hoverInv = idx;
                    }
                }
            }
            for (int col = 0; col < 9; col++) {
                int x = invLeft + col * 18;
                if (drawInvSlot(guiGraphics, inv, col, x, hotTop, mouseX, mouseY)) {
                    hoverInv = col;
                }
            }
        }

        // 中列小字提示：只占中列宽度（到右栏分界线为止），放不下就折行或截断，
        // 不会再压到右边那个 3x3 的名单框上（译文变长、窗口变窄都一样安全）
        int hintWidth = rx - 10 - gridLeft;
        List<String> hints = wrapLines(
                Component.translatable("maidgap.gui.hint3", String.valueOf(snap.maxContainers)).getString(),
                hintWidth, 2);
        for (int i = 0; i < hints.size(); i++) {
            guiGraphics.drawString(this.font, hints.get(i), gridLeft, top + 214 + i * 10, 0x909090, false);
        }

        // 底部两行提示（整行宽度）
        guiGraphics.drawString(this.font,
                Component.translatable("maidgap.gui.hint").withStyle(ChatFormatting.GRAY),
                left + 6, top + 258, 0xA0A0A0, false);
        guiGraphics.drawString(this.font,
                Component.translatable(snap.viaTerminal ? "maidgap.gui.hint2.terminal" : "maidgap.gui.hint2")
                        .withStyle(ChatFormatting.GRAY),
                left + 6, top + 268, 0xA0A0A0, false);

        // 右栏：绑定容器信息
        if (snap.viaTerminal && snap.notOwner) {
            guiGraphics.drawString(this.font, Component.translatable("maidgap.gui.not_owner")
                    .withStyle(ChatFormatting.RED), rx, top + 60, 0xFF8080, false);
            guiGraphics.drawString(this.font, Component.translatable("maidgap.gui.not_owner.2")
                    .withStyle(ChatFormatting.GRAY), rx, top + 72, 0xC0C0C0, false);
        } else if (snap.viaTerminal && snap.linkMissing) {
            guiGraphics.drawString(this.font, Component.translatable("maidgap.gui.link_missing")
                    .withStyle(ChatFormatting.RED), rx, top + 60, 0xFF8080, false);
            guiGraphics.drawString(this.font, Component.translatable("maidgap.gui.link_missing.2")
                    .withStyle(ChatFormatting.GRAY), rx, top + 72, 0xC0C0C0, false);
        } else if (snap.viaTerminal && snap.maidHasNoGap) {
            guiGraphics.drawString(this.font, Component.translatable("maidgap.gui.maid_no_gap")
                    .withStyle(ChatFormatting.RED), rx, top + 60, 0xFF8080, false);
            guiGraphics.drawString(this.font, Component.translatable("maidgap.gui.maid_no_gap.2")
                    .withStyle(ChatFormatting.GRAY), rx, top + 72, 0xC0C0C0, false);
        } else if (!snap.holdingGap) {
            guiGraphics.drawString(this.font, Component.translatable("maidgap.gui.nogap")
                    .withStyle(ChatFormatting.RED), rx, top + 60, 0xFF8080, false);
        } else {
            TerminalSnapshot.Entry e = currentEntry();
            if (e == null) {
                guiGraphics.drawString(this.font, Component.translatable("maidgap.gui.empty")
                        .withStyle(ChatFormatting.GRAY), rx, top + 60, 0xC0C0C0, false);
            } else {
                ItemStack icon = blockIcon(e.blockId);
                if (!icon.isEmpty()) {
                    guiGraphics.renderItem(icon, rx, top + 52);
                }
                String posText = e.binding.pos().getX() + ", " + e.binding.pos().getY() + ", "
                        + e.binding.pos().getZ() + " @ " + shortDim(e.binding.dimension());
                guiGraphics.drawString(this.font, Component.literal(posText), rx + 20, top + 56, 0xE0E0E0, false);
                guiGraphics.drawString(this.font, Component.translatable("maidgap.status." + statusKey(e.status))
                                .withStyle(statusFormat(e.status)),
                        rx + 20, top + 68, 0xFFFFFF, false);
            }
        }

        // 名单 3x3（每个容器一套：标签上写清楚是共用还是独立）
        int hoverFilter = -1;
        if (snap.holdingGap && currentEntry() != null) {
            List<ItemStack> filters = filterList();
            Component label = Component.translatable("maidgap.gui.filter_label",
                    Component.translatable(filterBlacklist() ? "maidgap.filter.black" : "maidgap.filter.white"),
                    Component.translatable(filterOwn() ? "maidgap.gui.scope_own" : "maidgap.gui.scope_shared"),
                    String.valueOf(filterUsed()), String.valueOf(GapItemData.FILTER_SLOTS));
            guiGraphics.drawString(this.font, label, filterLeft, filterTop - 11,
                    filterBlacklist() ? 0xFF9090 : 0x90FF90, false);
            for (int i = 0; i < GapItemData.FILTER_SLOTS; i++) {
                int row = i / 3;
                int col = i % 3;
                int x = filterLeft + col * 18;
                int y = filterTop + row * 18;
                guiGraphics.fill(x, y, x + 18, y + 18, 0x40FFFFFF);
                if (i < filters.size() && !filters.get(i).isEmpty()) {
                    guiGraphics.renderItem(filters.get(i), x + 1, y + 1);
                    if (filterTagMatch()) {
                        // 类别匹配开启：条目上画 # 角标
                        guiGraphics.drawString(this.font, "#", x + 10, y + 9, 0xFFD700, true);
                    }
                    if (mouseX >= x && mouseX < x + 18 && mouseY >= y && mouseY < y + 18) {
                        hoverFilter = i;
                    }
                }
            }
        }

        // 悬停提示（最后画，压在所有元素上面）
        if (hoverMaid >= 0) {
            TerminalSnapshot.MaidEntry m = snap.maids.get(hoverMaid);
            List<Component> tooltip = new ArrayList<>();
            tooltip.add(Component.literal(m.name == null || m.name.isEmpty() ? "?" : m.name));
            if (m.notOwner) {
                // 终端转手过、女仆已不是自己的：说清楚原因，别只报一个误导性的“离线”
                tooltip.add(Component.translatable("maidgap.gui.maid_notowner")
                        .withStyle(ChatFormatting.GOLD));
            } else {
                tooltip.add(Component.translatable(m.online ? "maidgap.gui.maid_online" : "maidgap.gui.maid_offline")
                        .withStyle(m.online ? ChatFormatting.GREEN : ChatFormatting.GRAY));
                if (m.online && !m.hasGap) {
                    tooltip.add(Component.translatable("maidgap.gui.maid_nogap").withStyle(ChatFormatting.RED));
                }
            }
            tooltip.add(Component.translatable(deleteMode ? "maidgap.gui.maid_click_delete" : "maidgap.gui.maid_click_switch")
                    .withStyle(deleteMode ? ChatFormatting.RED : ChatFormatting.YELLOW));
            guiGraphics.renderTooltip(this.font, tooltip, Optional.empty(), mouseX, mouseY);
        } else if (hoverIdx >= 0 && snap.holdingGap) {
            List<Component> tooltip = Screen.getTooltipFromItem(this.minecraft, visible.get(hoverIdx).stack);
            tooltip.add(Component.translatable("maidgap.gui.click_pick", String.valueOf(snap.pickupAmount))
                    .withStyle(ChatFormatting.YELLOW));
            tooltip.add(Component.translatable("maidgap.gui.shift_filter",
                    Component.translatable(filterBlacklist() ? "maidgap.filter.black" : "maidgap.filter.white"))
                    .withStyle(ChatFormatting.GOLD));
            guiGraphics.renderTooltip(this.font, tooltip, Optional.empty(), mouseX, mouseY);
        } else if (hoverIdx >= 0) {
            guiGraphics.renderTooltip(this.font,
                    Screen.getTooltipFromItem(this.minecraft, visible.get(hoverIdx).stack),
                    Optional.empty(), mouseX, mouseY);
        } else if (hoverFilter >= 0) {
            List<ItemStack> filters = filterList();
            List<Component> tooltip = new ArrayList<>(
                    Screen.getTooltipFromItem(this.minecraft, filters.get(hoverFilter)));
            tooltip.add(Component.translatable("maidgap.gui.click_remove").withStyle(ChatFormatting.YELLOW));
            if (filterTagMatch()) {
                List<ResourceLocation> tags = TagMatcher.categoryTagIds(filters.get(hoverFilter));
                if (tags.isEmpty()) {
                    tooltip.add(Component.translatable("maidgap.gui.tag_none").withStyle(ChatFormatting.GRAY));
                } else {
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < tags.size() && i < 3; i++) {
                        if (i > 0) {
                            sb.append("  ");
                        }
                        sb.append('#').append(tags.get(i));
                    }
                    tooltip.add(Component.translatable("maidgap.gui.tag_covers",
                            String.valueOf(tags.size()), sb.toString()).withStyle(ChatFormatting.AQUA));
                }
            }
            guiGraphics.renderTooltip(this.font, tooltip, Optional.empty(), mouseX, mouseY);
        } else if (hoverInv >= 0 && inv != null && !inv.getItem(hoverInv).isEmpty()) {
            List<Component> tooltip = new ArrayList<>(
                    Screen.getTooltipFromItem(this.minecraft, inv.getItem(hoverInv)));
            if (snap.holdingGap) {
                tooltip.add(Component.translatable("maidgap.gui.inv_click",
                        Component.translatable(filterBlacklist() ? "maidgap.filter.black" : "maidgap.filter.white"))
                        .withStyle(ChatFormatting.GOLD));
            }
            guiGraphics.renderTooltip(this.font, tooltip, Optional.empty(), mouseX, mouseY);
        }

        guiGraphics.pose().popPose();
    }

    /** 画一个玩家背包槽，返回鼠标是否悬停其上。 */
    private boolean drawInvSlot(GuiGraphics guiGraphics, Inventory inv, int idx, int x, int y,
                                int mouseX, int mouseY) {
        guiGraphics.fill(x, y, x + 18, y + 18, 0x30FFFFFF);
        ItemStack st = inv.getItem(idx);
        if (!st.isEmpty()) {
            guiGraphics.renderItem(st, x + 1, y + 1);
            guiGraphics.renderItemDecorations(this.font, st, x + 1, y + 1);
        }
        return mouseX >= x && mouseX < x + 18 && mouseY >= y && mouseY < y + 18;
    }

    // ------------------------------------------------------------------

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int mouseButton) {
        // 画面做了等比缩放：鼠标坐标要换算回逻辑坐标，按钮的判定框也是按逻辑坐标放的
        mouseX /= scale;
        mouseY /= scale;
        if (super.mouseClicked(mouseX, mouseY, mouseButton)) {
            return true;
        }
        if (mouseButton != 0) {
            return false;
        }

        // 女仆列表：点一下就切换管理对象；删除模式下点一下才把她移出列表
        if (snap.viaTerminal) {
            int maidIdx = maidRowAt(mouseX, mouseY);
            if (maidIdx >= 0) {
                if (maidActionPending) {
                    return true; // 上一次操作还没回包，先别把同一只（或它后面那只）再点一遍
                }
                maidActionPending = true;
                maidActionTicks = 60; // 兜底：万一这个包被服务端限速丢掉，3 秒后自动放行（L6）
                if (deleteMode) {
                    ModNetwork.sendToServer(new GapActionPacket(ContainerService.ACT_REMOVE_MAID, maidIdx, 0));
                    // 删完自动退出删除模式：想再删就再点一次「删除」，杜绝连点误删（L1）
                    deleteMode = false;
                    // 马上重建按钮：文案是点「删除」时定格进按钮对象的，不重建会等下一次
                    // 快照（约 1 秒）才变回「删除」，被限速丢弃时更久（复审报告 R7）
                    rebuildWidgets();
                } else {
                    ModNetwork.sendToServer(new GapActionPacket(ContainerService.ACT_SELECT_MAID, maidIdx, 0));
                }
                return true;
            }
        }

        if (!snap.holdingGap) {
            return false;
        }

        // 名单槽位：点击移除（改的是当前选中容器的名单）。
        // 没有容器时这片是空白（名单格根本没画），不能再响应点击——
        // 否则 filterList() 会拿共用名单来命中判定，点空白处就悄悄删掉共用名单的一格（M2）。
        List<ItemStack> filters = currentEntry() == null ? List.of() : filterList();
        for (int i = 0; i < GapItemData.FILTER_SLOTS && i < filters.size(); i++) {
            int row = i / 3;
            int col = i % 3;
            int x = filterLeft + col * 18;
            int y = filterTop + row * 18;
            if (mouseX >= x && mouseX < x + 18 && mouseY >= y && mouseY < y + 18) {
                if (!filters.get(i).isEmpty()) {
                    ModNetwork.sendToServer(new GapActionPacket(
                            ContainerService.ACT_REMOVE_FILTER, selected, i));
                    return true;
                }
            }
        }

        // 玩家物品栏：点击物品 = 加入当前容器的名单
        if (this.minecraft != null && this.minecraft.player != null) {
            int idx = playerInvSlotAt(mouseX, mouseY);
            if (idx >= 0) {
                if (!this.minecraft.player.getInventory().getItem(idx).isEmpty()) {
                    ModNetwork.sendToServer(new GapActionPacket(
                            ContainerService.ACT_ADD_FILTER_INV, selected, idx));
                }
                return true;
            }
        }

        // 箱子物品网格：左键拿取 / Shift+左键加入名单
        if (currentEntry() == null) {
            return false;
        }
        int start = page * PER_PAGE;
        for (int i = 0; i < PER_PAGE; i++) {
            int row = i / COLS;
            int col = i % COLS;
            int x = gridLeft + col * 18;
            int y = gridTop + row * 18;
            if (mouseX >= x && mouseX < x + 18 && mouseY >= y && mouseY < y + 18) {
                int dataIdx = start + i;
                if (dataIdx < visible.size()) {
                    int realSlot = visible.get(dataIdx).slot;
                    if (hasShiftDown()) {
                        ModNetwork.sendToServer(new GapActionPacket(
                                ContainerService.ACT_ADD_FILTER, selected, realSlot));
                    } else {
                        ModNetwork.sendToServer(new GapActionPacket(
                                ContainerService.ACT_EXTRACT_ITEM, selected, realSlot));
                    }
                    return true;
                }
            }
        }
        return false;
    }

    /** 鼠标命中的女仆列表行（返回在整份列表里的下标），未命中返回 -1。 */
    private int maidRowAt(double mouseX, double mouseY) {
        if (mouseX < maidLeft || mouseX >= maidLeft + MAID_W) {
            return -1;
        }
        for (int i = 0; i < MAID_ROWS; i++) {
            int y = maidTop + i * MAID_ROW_H;
            if (mouseY >= y && mouseY < y + MAID_ROW_H) {
                int idx = maidScroll + i;
                return idx < snap.maids.size() ? idx : -1;
            }
        }
        return -1;
    }

    /** 鼠标命中的玩家背包槽（主背包 9-35 三行 + 快捷栏 0-8），未命中返回 -1。 */
    private int playerInvSlotAt(double mouseX, double mouseY) {
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                int x = invLeft + col * 18;
                int y = invTop + row * 18;
                if (mouseX >= x && mouseX < x + 18 && mouseY >= y && mouseY < y + 18) {
                    return 9 + row * 9 + col;
                }
            }
        }
        for (int col = 0; col < 9; col++) {
            int x = invLeft + col * 18;
            if (mouseX >= x && mouseX < x + 18 && mouseY >= hotTop && mouseY < hotTop + 18) {
                return col;
            }
        }
        return -1;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int mouseButton) {
        // 按钮的“松手”判定也用的是逻辑坐标，必须一起换算，否则缩小时点不动按钮
        return super.mouseReleased(mouseX / scale, mouseY / scale, mouseButton);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int mouseButton, double dragX, double dragY) {
        return super.mouseDragged(mouseX / scale, mouseY / scale, mouseButton, dragX / scale, dragY / scale);
    }

    /** 女仆列表超过一屏时，滚轮上下翻。 */
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        double lx = mouseX / scale;
        double ly = mouseY / scale;
        if (lx >= maidLeft && lx < maidLeft + MAID_W && ly >= maidTop && ly < maidTop + MAID_ROWS * MAID_ROW_H) {
            int maxScroll = Math.max(0, snap.maids.size() - MAID_ROWS);
            if (maxScroll > 0) {
                maidScroll = Math.max(0, Math.min(maxScroll, maidScroll + (delta < 0 ? 1 : -1)));
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public void tick() {
        super.tick();
        // 女仆操作兜底：正常几十毫秒就有快照回来清了 pending，这条只在包被丢弃时生效
        if (maidActionPending && maidActionTicks > 0 && --maidActionTicks <= 0) {
            maidActionPending = false;
        }
        // 每 20 tick（1 秒）自动刷新一次：别人动过绑定的箱子、女仆刚搬完东西，
        // 界面都会跟着变，不用玩家自己记得点「刷新」。
        // 服务端刷新包的最小间隔是 10 tick，这里留足余量，不会被打包限速挡掉。
        if (--refreshCooldown > 0) {
            return;
        }
        refreshCooldown = 20;
        ModNetwork.sendToServer(new GapActionPacket(ContainerService.ACT_REFRESH, 0, 0));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /**
     * 把一段文字折成最多 maxLines 行、每行不超过 maxWidth 像素。
     * 先按换行符分段，再按像素宽度逐字折断；实在放不下就在最后一行末尾加省略号，
     * 宁可少显示几个字，也不让文字跑到别的面板上。
     */
    private List<String> wrapLines(String text, int maxWidth, int maxLines) {
        List<String> out = new ArrayList<>();
        if (text == null || text.isEmpty() || maxWidth <= 0 || maxLines <= 0) {
            return out;
        }
        boolean overflow = false;
        for (String para : text.split("\n", -1)) {
            String rest = para;
            while (!rest.isEmpty()) {
                if (out.size() >= maxLines) {
                    overflow = true;
                    break;
                }
                int cut = rest.length();
                while (cut > 0 && this.font.width(rest.substring(0, cut)) > maxWidth) {
                    cut--;
                }
                if (cut <= 0) {
                    cut = 1;
                }
                out.add(rest.substring(0, cut));
                rest = rest.substring(cut).stripLeading();
            }
            if (overflow) {
                break;
            }
        }
        if (overflow && !out.isEmpty()) {
            int i = out.size() - 1;
            String s = out.get(i);
            while (!s.isEmpty() && this.font.width(s + "…") > maxWidth) {
                s = s.substring(0, s.length() - 1);
            }
            out.set(i, s + "…");
        }
        return out;
    }

    /** 把文字裁到指定像素宽度以内（女仆列表一行放不下长名字）。 */
    private String trimToWidth(String s, int maxWidth) {
        if (s == null) {
            return "";
        }
        if (this.font.width(s) <= maxWidth) {
            return s;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (this.font.width(sb.toString() + c) > maxWidth) {
                break;
            }
            sb.append(c);
        }
        return sb.toString();
    }

    private static ItemStack blockIcon(String blockId) {
        if (blockId == null || blockId.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ResourceLocation id = ResourceLocation.tryParse(blockId);
        if (id == null) {
            return ItemStack.EMPTY;
        }
        Block block = ForgeRegistries.BLOCKS.getValue(id);
        if (block == null) {
            return ItemStack.EMPTY;
        }
        return new ItemStack(block.asItem());
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }

    private static String shortDim(String dim) {
        int idx = dim.indexOf(':');
        String s = idx >= 0 ? dim.substring(idx + 1) : dim;
        return s.length() > 12 ? s.substring(0, 12) : s;
    }

    private static String statusKey(int status) {
        return switch (status) {
            case TerminalSnapshot.STATUS_OK -> "ok";
            case TerminalSnapshot.STATUS_NOT_LOADED -> "not_loaded";
            case TerminalSnapshot.STATUS_OUT_OF_RANGE -> "out_of_range";
            case TerminalSnapshot.STATUS_NO_TARGET -> "no_target";
            case TerminalSnapshot.STATUS_NOT_ALLOWED -> "not_allowed";
            default -> "no_capability";
        };
    }

    private static ChatFormatting statusFormat(int status) {
        return status == TerminalSnapshot.STATUS_OK ? ChatFormatting.GREEN : ChatFormatting.RED;
    }
}
