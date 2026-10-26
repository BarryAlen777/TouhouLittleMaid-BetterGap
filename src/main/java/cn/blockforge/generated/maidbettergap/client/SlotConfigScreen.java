package cn.blockforge.generated.maidbettergap.client;

import cn.blockforge.generated.maidbettergap.gap.GapItemData;
import cn.blockforge.generated.maidbettergap.network.ModNetwork;
import cn.blockforge.generated.maidbettergap.network.SlotLocksPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;

/**
 * 女仆槽位配置界面（对齐车万女仆原版「槽位配置」的玩法）：
 * - 6×6 = 女仆背包 36 格，右侧 2 格 = 主手 / 副手（剑、盾图标提示）
 * - 点击格子 = 锁定/解锁；锁定的槽位，隙间搬运（双向）都不会碰
 * - 「完成」把 38 字节数组写回隙间物品（原版 SlotConfigData 同格式，实时生效），
 *   「取消」直接返回不改
 *
 * 界面里会显示女仆背包当前的物品（终端链接的女仆在场时），方便认出该锁哪一格。
 */
public class SlotConfigScreen extends Screen {

    private static final int PW = 212;
    private static final int PH = 216;
    private static final int COLS = 6;
    private static final int ROWS = 6;
    private static final int SLOT = 18;

    private final GapTerminalScreen parent;
    private final byte[] locks;
    private final List<ItemStack> display;

    private int left;
    private int top;
    private int gridLeft;
    private int gridTop;
    private int handX;
    /** 面板的显示缩放：窗口放不下 212×216 时小于 1（和终端界面同一套做法）。 */
    private float scale = 1f;

    public SlotConfigScreen(GapTerminalScreen parent, byte[] currentLocks, List<ItemStack> maidSlots) {
        super(Component.translatable("maidgap.slot.title"));
        this.parent = parent;
        this.locks = new byte[GapItemData.ORIGINAL_SLOT_NUM];
        if (currentLocks != null) {
            System.arraycopy(currentLocks, 0, this.locks, 0,
                    Math.min(currentLocks.length, this.locks.length));
        }
        this.display = maidSlots == null ? List.of() : maidSlots;
    }

    @Override
    protected void init() {
        // 和终端界面一致：窗口放不下就整体等比缩小，别把下半截画到屏幕外
        this.scale = Math.min(1f, Math.min((this.width - 4f) / PW, (this.height - 4f) / PH));
        if (this.scale <= 0f) {
            this.scale = 1f;
        }
        int logicalW = Math.round(this.width / this.scale);
        int logicalH = Math.round(this.height / this.scale);
        left = Math.max(2, (logicalW - PW) / 2);
        top = Math.max(2, (logicalH - PH) / 2);
        gridLeft = left + 14;
        gridTop = top + 26;
        handX = gridLeft + COLS * SLOT + 22;

        addRenderableWidget(Button.builder(Component.translatable("maidgap.slot.done"), btn -> {
            ModNetwork.sendToServer(new SlotLocksPacket(locks));
            this.minecraft.setScreen(parent);
        }).bounds(left + 38, top + PH - 30, 62, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("maidgap.slot.cancel"), btn ->
                this.minecraft.setScreen(parent))
                .bounds(left + 112, top + PH - 30, 62, 18).build());
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g);

        // 整体等比缩放：下面全用逻辑坐标，鼠标坐标换算回逻辑坐标再判定
        g.pose().pushPose();
        if (scale != 1f) {
            g.pose().scale(scale, scale, 1f);
            mouseX = Math.round(mouseX / scale);
            mouseY = Math.round(mouseY / scale);
        }

        // 面板必须先画：super.render 画的是按钮，画在后面会把按钮盖住
        g.fill(left, top, left + PW, top + PH, 0xF0101014);
        g.fill(left, top, left + PW, top + 2, 0xFF8A2BE2);

        super.render(g, mouseX, mouseY, partialTick);

        g.drawCenteredString(this.font, titleComponent(), left + PW / 2, top + 7, 0xE0C8FF);

        // 背包 36 格 + 主手/副手 2 格
        int locked = 0;
        for (int i = 0; i < GapItemData.ORIGINAL_SLOT_NUM; i++) {
            int x;
            int y;
            if (i < COLS * ROWS) {
                x = gridLeft + (i % COLS) * SLOT;
                y = gridTop + (i / COLS) * SLOT;
            } else if (i == 36) {
                x = handX;
                y = gridTop;
            } else {
                x = handX;
                y = gridTop + SLOT;
            }
            if (locks[i] != 0) {
                locked++;
            }
            drawSlot(g, i, x, y, mouseX, mouseY);
        }

        // 主手/副手文字提示（放在两格右侧）
        g.drawString(this.font, Component.translatable("maidgap.slot.main_hand"),
                handX + SLOT + 4, gridTop + 5, 0xA0A0A0, false);
        g.drawString(this.font, Component.translatable("maidgap.slot.off_hand"),
                handX + SLOT + 4, gridTop + SLOT + 5, 0xA0A0A0, false);

        // 图例 + 计数
        g.drawString(this.font, Component.translatable("maidgap.slot.legend")
                        .withStyle(ChatFormatting.GRAY),
                left + 14, gridTop + ROWS * SLOT + 8, 0xA0A0A0, false);
        Component count = Component.translatable("maidgap.slot.locked_count", String.valueOf(locked));
        g.drawString(this.font, count, left + PW - 10 - this.font.width(count),
                gridTop + ROWS * SLOT + 8, locked > 0 ? 0xFF9090 : 0x90C890, false);

        // 悬停说明
        int hover = slotAt(mouseX, mouseY);
        if (hover >= 0) {
            Component tip = Component.translatable(locks[hover] != 0
                    ? "maidgap.slot.tip_locked" : "maidgap.slot.tip_open");
            g.renderTooltip(this.font, tip, mouseX, mouseY);
        }

        g.pose().popPose();
    }

    /** 命中的槽位号（0-35 背包、36 主手、37 副手），未命中返回 -1。 */
    private int slotAt(double mouseX, double mouseY) {
        for (int i = 0; i < GapItemData.ORIGINAL_SLOT_NUM; i++) {
            int x;
            int y;
            if (i < COLS * ROWS) {
                x = gridLeft + (i % COLS) * SLOT;
                y = gridTop + (i / COLS) * SLOT;
            } else if (i == 36) {
                x = handX;
                y = gridTop;
            } else {
                x = handX;
                y = gridTop + SLOT;
            }
            if (mouseX >= x && mouseX < x + SLOT && mouseY >= y && mouseY < y + SLOT) {
                return i;
            }
        }
        return -1;
    }

    private Component titleComponent() {
        String name = parent.linkedMaidName();
        if (name != null && !name.isEmpty()) {
            if (name.length() > 14) {
                name = name.substring(0, 14);
            }
            return Component.translatable("maidgap.slot.title.who", name);
        }
        return Component.translatable("maidgap.slot.title");
    }

    private void drawSlot(GuiGraphics g, int index, int x, int y, int mouseX, int mouseY) {
        boolean isLock = locks[index] != 0;
        // 槽底
        g.fill(x, y, x + SLOT, y + SLOT, isLock ? 0xFF4A1A1A : 0xFF2A2A33);
        // 内容：女仆的物品；主手/副手没东西时画剑/盾图标提示
        ItemStack shown = index < display.size() ? display.get(index) : ItemStack.EMPTY;
        if (index >= 36 && shown.isEmpty()) {
            // 主手画剑、副手画桶，对齐原版隙间槽位配置的提示图标
            shown = new ItemStack(index == 36 ? Items.IRON_SWORD : Items.BUCKET);
        }
        if (!shown.isEmpty()) {
            g.renderItem(shown, x + 1, y + 1);
            g.renderItemDecorations(this.font, shown, x + 1, y + 1);
        }
        // 锁定遮罩
        if (isLock) {
            g.fill(x + 1, y + 1, x + SLOT - 1, y + SLOT - 1, 0x99CC2222);
            g.drawCenteredString(this.font, "×", x + SLOT / 2, y + 5, 0xFFFFFF);
        }
        // 边框
        int edge = isLock ? 0xFFE06060 : 0xFF555566;
        g.renderOutline(x, y, SLOT, SLOT, edge);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int mouseButton) {
        // 画面做了等比缩放：鼠标坐标要换算回逻辑坐标（按钮与格子的判定框都是逻辑坐标）
        mouseX /= scale;
        mouseY /= scale;
        if (super.mouseClicked(mouseX, mouseY, mouseButton)) {
            return true;
        }
        if (mouseButton != 0) {
            return false;
        }
        int hit = slotAt(mouseX, mouseY);
        if (hit >= 0) {
            locks[hit] = locks[hit] == 0 ? (byte) 1 : (byte) 0;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int mouseButton) {
        return super.mouseReleased(mouseX / scale, mouseY / scale, mouseButton);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int mouseButton, double dragX, double dragY) {
        return super.mouseDragged(mouseX / scale, mouseY / scale, mouseButton, dragX / scale, dragY / scale);
    }

    /** 关掉（ESC）也回终端，不写数据。 */
    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
