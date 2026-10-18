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

/**
 * 女仆隙间终端界面：
 * - 绑定容器选择 + 分类标签页 + 物品网格（左键按预设数量拿取，Shift+左键加入过滤名单）
 * - 玩家物品栏（点击物品 = 直接加入白名单/黑名单）
 * - 女仆传输方向切换（箱子->女仆 / 女仆->箱子）
 * - 白名单/黑名单切换 + 类别匹配开关 + 9 格过滤名单（点击名单槽移除）
 * - 储罐取桶 / 拿取数量 / 玩家自动补货
 *
 * 布局：左列 = 箱子物品网格 + 玩家背包；右列 = 绑定信息与开关 + 过滤名单；底栏 = 操作按钮 + 提示。
 */
public class GapTerminalScreen extends Screen {

    private static final int GW = 360;
    private static final int GH = 288;

    private static final ItemCategory[] TABS = ItemCategory.values();
    private static final int COLS = 9;
    private static final int ROWS = 3;
    private static final int PER_PAGE = COLS * ROWS;

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
    private int rx;
    private int gridLeft;
    private int gridTop;
    private int filterLeft;
    private int filterTop;
    private int invLeft;
    private int invTop;
    private int hotTop;
    private final List<RowInfo> visible = new ArrayList<>();

    public GapTerminalScreen(TerminalSnapshot snapshot) {
        super(Component.translatable("maidgap.gui.title"));
        this.snap = snapshot;
        clampSelection();
    }

    public void updateSnapshot(TerminalSnapshot snapshot) {
        this.snap = snapshot;
        clampSelection();
        rebuildWidgets();
    }

    /** 终端模式下标题带上链接的女仆名（Screen.title 是 final，渲染时动态取）。 */
    private Component titleComponent() {
        if (snap.viaTerminal) {
            String name = snap.maidName == null || snap.maidName.isEmpty() ? "?" : snap.maidName;
            if (name.length() > 14) {
                name = name.substring(0, 14);
            }
            return Component.translatable("maidgap.gui.title.terminal", name);
        }
        return Component.translatable("maidgap.gui.title");
    }

    /** 给槽位配置子界面用：终端当前链接的女仆名（没有则空串）。 */
    public String linkedMaidName() {
        return snap.maidName == null ? "" : snap.maidName;
    }

    private void clampSelection() {
        if (selected >= snap.entries.size()) {
            selected = Math.max(0, snap.entries.size() - 1);
        }
    }

    @Override
    protected void init() {
        // 窗口太窄时（GUI 缩放拉得很大）至少保证面板左边贴住屏幕，不会整块画到屏幕外
        left = Math.max(2, (this.width - GW) / 2);
        top = Math.max(2, (this.height - GH) / 2);
        rx = left + 196;
        gridLeft = left + 8;
        gridTop = top + 52;
        invLeft = left + 8;
        invTop = top + 136;
        hotTop = top + 194;
        filterLeft = rx;
        filterTop = top + 160;

        // 容器选择按钮（第一行，右侧是绑定计数文字，互不重叠）
        for (int i = 0; i < snap.entries.size(); i++) {
            final int idx = i;
            String mark = idx == selected ? "▶" : "  ";
            addRenderableWidget(Button.builder(Component.literal(mark + "绑" + (idx + 1)), btn -> {
                        selected = idx;
                        page = 0;
                        rebuildWidgets();
                    })
                    .bounds(left + 6 + idx * 40, top + 16, 38, 16)
                    .build());
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
                    }).bounds(left + 6 + i * 46, top + 34, 44, 14).build());
        }

        // 翻页（物品网格正下方）
        addRenderableWidget(Button.builder(Component.literal("◀"), btn -> {
            if (page > 0) {
                page--;
                rebuildWidgets();
            }
        }).bounds(left + 8, top + 110, 30, 14).build());
        addRenderableWidget(Button.builder(Component.literal("▶"), btn -> {
            page++;
            rebuildWidgets();
        }).bounds(left + 42, top + 110, 30, 14).build());

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

            int used = 0;
            for (ItemStack f : snap.filters) {
                if (!f.isEmpty()) {
                    used++;
                }
            }
            addRenderableWidget(Button.builder(
                    Component.translatable("maidgap.gui.filter",
                            Component.translatable(snap.blacklist ? "maidgap.filter.black" : "maidgap.filter.white"),
                            String.valueOf(used), String.valueOf(GapItemData.FILTER_SLOTS)), btn ->
                            ModNetwork.sendToServer(new GapActionPacket(ContainerService.ACT_TOGGLE_FILTER_MODE, 0, 0)))
                    .bounds(rx, top + 116, 152, 14).build());

            Button tagBtn = Button.builder(
                    Component.translatable("maidgap.gui.tag_match", Component.translatable(
                            snap.tagMatch ? "maidgap.gui.on" : "maidgap.gui.off")), btn ->
                            ModNetwork.sendToServer(new GapActionPacket(ContainerService.ACT_TOGGLE_TAG_MATCH, 0, 0)))
                    .bounds(rx, top + 132, 152, 14).build();
            tagBtn.setTooltip(Tooltip.create(Component.translatable("maidgap.gui.tag_tip")));
            addRenderableWidget(tagBtn);

            if (used > 0) {
                addRenderableWidget(Button.builder(Component.translatable("maidgap.gui.clear_filter"), btn ->
                        ModNetwork.sendToServer(new GapActionPacket(ContainerService.ACT_CLEAR_FILTERS, 0, 0)))
                        .bounds(rx + 58, top + 162, 94, 14).build());
            }
        }

        // 储罐取桶按钮（过滤名单右侧一列，容量信息放悬停提示里）
        TerminalSnapshot.Entry sel = currentEntry();
        if (snap.holdingGap && sel != null && sel.status == TerminalSnapshot.STATUS_OK && sel.binding.isFluid()) {
            for (int i = 0; i < sel.fluids.size() && i < 3; i++) {
                final int realSlot = sel.tankSlots.get(i);
                FluidStack fs = sel.fluids.get(i);
                Button bucketBtn = Button.builder(
                        Component.translatable("maidgap.gui.bucket_of", truncate(fs.getDisplayName().getString(), 8)),
                        btn -> ModNetwork.sendToServer(
                                new GapActionPacket(ContainerService.ACT_EXTRACT_BUCKET, selected, realSlot)))
                        .bounds(rx + 58, top + 180 + i * 16, 94, 14).build();
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
                .bounds(left + 96, top + 234, 94, 18).build();
        refillBtn.setTooltip(Tooltip.create(Component.translatable("maidgap.gui.refill_tip")));
        addRenderableWidget(refillBtn);

        if (snap.holdingGap) {
            Button slotBtn = Button.builder(Component.translatable("maidgap.gui.slot_config"), btn ->
                    this.minecraft.setScreen(new SlotConfigScreen(this, snap.slotLocks, snap.maidSlots)))
                    .bounds(left + 194, top + 234, 64, 18).build();
            slotBtn.setTooltip(Tooltip.create(Component.translatable("maidgap.gui.slot_tip")));
            addRenderableWidget(slotBtn);
        }
        addRenderableWidget(Button.builder(Component.translatable("maidgap.gui.refresh"), btn ->
                ModNetwork.sendToServer(new GapActionPacket(ContainerService.ACT_REFRESH, 0, 0)))
                .bounds(left + 262, top + 234, 44, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("maidgap.gui.close"), btn -> this.onClose())
                .bounds(left + 310, top + 234, 44, 18).build());

        rebuildVisible();
    }

    private TerminalSnapshot.Entry currentEntry() {
        if (snap.entries.isEmpty()) {
            return null;
        }
        return snap.entries.get(Math.min(selected, snap.entries.size() - 1));
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

        // 面板底色必须先画：super.render 画的是按钮，画在后面会把按钮整片盖住
        guiGraphics.fill(left, top, left + GW, top + GH, 0xF0101014);
        guiGraphics.fill(left, top, left + GW, top + 2, 0xFF8A2BE2);
        // 左右列分隔线
        guiGraphics.fill(rx - 6, top + 52, rx - 5, top + 226, 0xFF2A2A3A);

        super.render(guiGraphics, mouseX, mouseY, partialTick);

        // 标题
        guiGraphics.drawCenteredString(this.font, titleComponent(), left + GW / 2, top + 4, 0xE0C8FF);

        // 绑定计数：放在右栏顶部（容器按钮变多时（最多 8 个）也不会和它撞在一起）
        if (snap.holdingGap) {
            Component count = Component.translatable("maidgap.gui.bind_count",
                    String.valueOf(snap.entries.size()), String.valueOf(snap.maxContainers));
            guiGraphics.drawString(this.font, count, rx, top + 20, 0xC8B4FF, false);
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
                left + 78, top + 114, 0xC0C0C0, false);

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

        // 左列小字提示（只占左半宽度）
        guiGraphics.drawString(this.font,
                Component.translatable("maidgap.gui.hint3", String.valueOf(snap.maxContainers))
                        .withStyle(ChatFormatting.DARK_GRAY),
                left + 6, top + 220, 0x909090, false);

        // 底部两行提示（整行宽度）
        guiGraphics.drawString(this.font,
                Component.translatable("maidgap.gui.hint").withStyle(ChatFormatting.GRAY),
                left + 6, top + 258, 0xA0A0A0, false);
        guiGraphics.drawString(this.font,
                Component.translatable(snap.viaTerminal ? "maidgap.gui.hint2.terminal" : "maidgap.gui.hint2")
                        .withStyle(ChatFormatting.GRAY),
                left + 6, top + 268, 0xA0A0A0, false);

        // 右栏：绑定容器信息
        if (snap.viaTerminal && snap.linkMissing) {
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

        // 过滤名单 3x3
        int hoverFilter = -1;
        if (snap.holdingGap) {
            guiGraphics.drawString(this.font,
                    Component.translatable(snap.blacklist ? "maidgap.filter.black" : "maidgap.filter.white")
                            .withStyle(snap.blacklist ? ChatFormatting.RED : ChatFormatting.GREEN),
                    filterLeft, filterTop - 11, 0xC8B4FF, false);
            for (int i = 0; i < GapItemData.FILTER_SLOTS; i++) {
                int row = i / 3;
                int col = i % 3;
                int x = filterLeft + col * 18;
                int y = filterTop + row * 18;
                guiGraphics.fill(x, y, x + 18, y + 18, 0x40FFFFFF);
                if (i < snap.filters.size() && !snap.filters.get(i).isEmpty()) {
                    guiGraphics.renderItem(snap.filters.get(i), x + 1, y + 1);
                    if (snap.tagMatch) {
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
        if (hoverIdx >= 0 && snap.holdingGap) {
            List<Component> tooltip = Screen.getTooltipFromItem(this.minecraft, visible.get(hoverIdx).stack);
            tooltip.add(Component.translatable("maidgap.gui.click_pick", String.valueOf(snap.pickupAmount))
                    .withStyle(ChatFormatting.YELLOW));
            tooltip.add(Component.translatable("maidgap.gui.shift_filter",
                    Component.translatable(snap.blacklist ? "maidgap.filter.black" : "maidgap.filter.white"))
                    .withStyle(ChatFormatting.GOLD));
            guiGraphics.renderTooltip(this.font, tooltip, java.util.Optional.empty(), mouseX, mouseY);
        } else if (hoverIdx >= 0) {
            guiGraphics.renderTooltip(this.font,
                    Screen.getTooltipFromItem(this.minecraft, visible.get(hoverIdx).stack),
                    java.util.Optional.empty(), mouseX, mouseY);
        } else if (hoverFilter >= 0) {
            List<Component> tooltip = new ArrayList<>(
                    Screen.getTooltipFromItem(this.minecraft, snap.filters.get(hoverFilter)));
            tooltip.add(Component.translatable("maidgap.gui.click_remove").withStyle(ChatFormatting.YELLOW));
            if (snap.tagMatch) {
                List<ResourceLocation> tags = TagMatcher.categoryTagIds(snap.filters.get(hoverFilter));
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
            guiGraphics.renderTooltip(this.font, tooltip, java.util.Optional.empty(), mouseX, mouseY);
        } else if (hoverInv >= 0 && inv != null && !inv.getItem(hoverInv).isEmpty()) {
            List<Component> tooltip = new ArrayList<>(
                    Screen.getTooltipFromItem(this.minecraft, inv.getItem(hoverInv)));
            if (snap.holdingGap) {
                tooltip.add(Component.translatable("maidgap.gui.inv_click",
                        Component.translatable(snap.blacklist ? "maidgap.filter.black" : "maidgap.filter.white"))
                        .withStyle(ChatFormatting.GOLD));
            }
            guiGraphics.renderTooltip(this.font, tooltip, java.util.Optional.empty(), mouseX, mouseY);
        }
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
        if (super.mouseClicked(mouseX, mouseY, mouseButton)) {
            return true;
        }
        if (mouseButton != 0 || !snap.holdingGap) {
            return false;
        }

        // 过滤名单槽位：点击移除
        for (int i = 0; i < GapItemData.FILTER_SLOTS && i < snap.filters.size(); i++) {
            int row = i / 3;
            int col = i % 3;
            int x = filterLeft + col * 18;
            int y = filterTop + row * 18;
            if (mouseX >= x && mouseX < x + 18 && mouseY >= y && mouseY < y + 18) {
                if (!snap.filters.get(i).isEmpty()) {
                    ModNetwork.sendToServer(new GapActionPacket(ContainerService.ACT_REMOVE_FILTER, 0, i));
                    return true;
                }
            }
        }

        // 玩家物品栏：点击物品 = 加入过滤名单
        if (this.minecraft != null && this.minecraft.player != null) {
            int idx = playerInvSlotAt(mouseX, mouseY);
            if (idx >= 0) {
                if (!this.minecraft.player.getInventory().getItem(idx).isEmpty()) {
                    ModNetwork.sendToServer(new GapActionPacket(
                            ContainerService.ACT_ADD_FILTER_INV, 0, idx));
                }
                return true;
            }
        }

        // 箱子物品网格：左键拿取 / Shift+左键加入过滤名单
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
    public boolean isPauseScreen() {
        return false;
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
