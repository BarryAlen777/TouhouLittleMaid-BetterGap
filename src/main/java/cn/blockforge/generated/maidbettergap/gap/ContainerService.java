package cn.blockforge.generated.maidbettergap.gap;

import cn.blockforge.generated.maidbettergap.MaidGapConfig;
import cn.blockforge.generated.maidbettergap.ModItems;
import cn.blockforge.generated.maidbettergap.compat.tlm.MaidLinkService;
import cn.blockforge.generated.maidbettergap.network.ModNetwork;
import cn.blockforge.generated.maidbettergap.network.TerminalSnapshotPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.items.wrapper.InvWrapper;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.fml.ModList;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 隙间终端的服务端逻辑。
 *
 * 数据模型与车万女仆原版一致：绑定、传输方向、白名单/黑名单都存在
 * 「隙间物品自己的 NBT」上（见 {@link GapItemData}），所以同一件隙间
 * 拿在手里是玩家终端，放进女仆饰品栏就是女仆的自动搬运器。
 *
 * 容器识别走 Forge 能力接口，精妙存储等只要暴露 IItemHandler / IFluidHandler 的模组容器都能绑。
 */
public final class ContainerService {

    private static final int MAX_LISTED_SLOTS = 180;
    /** 一桶 = 1000 mB；换桶必须严格有这么多，少一点都不给（否则剩 1 mB 也能换整桶 = 无限刷桶）。 */
    private static final int BUCKET_VOLUME = 1000;
    /** 取物/解绑这类动作包的最短间隔（tick），防作弊客户端狂发把服务器拖垮。 */
    private static final int ACTION_MIN_TICKS = 1;
    /** 刷新包更宽松一点：只限制它别被拿来刷网络流量。 */
    private static final int REFRESH_MIN_TICKS = 10;
    /** 打开终端（G 键 / 右键空气）的最短间隔：一轮「构建快照」比一个普通动作贵得多。 */
    private static final int OPEN_MIN_TICKS = 4;
    /** 一次快照最多打包这么多件物品（防止超大仓库把网络包撑爆，客户端直接掉线）。 */
    private static final int MAX_TOTAL_STACKS = 1024;
    /** 快照包的实际字节上限（保守取 1 MB，超了就少列一些物品）。 */
    private static final int MAX_SNAPSHOT_BYTES = 1000 * 1000;
    /** “刚用光”的物品只在这么多个游戏刻内算数（600 tick = 30 秒）。 */
    private static final int RECENT_USE_WINDOW_TICKS = 600;

    public static final int ACT_EXTRACT_ITEM = 0;
    public static final int ACT_EXTRACT_BUCKET = 1;
    public static final int ACT_UNBIND = 2;
    public static final int ACT_TOGGLE_REFILL = 3;
    public static final int ACT_CYCLE_PICKUP = 4;
    public static final int ACT_TOGGLE_IO_MODE = 5;
    public static final int ACT_TOGGLE_FILTER_MODE = 6;
    public static final int ACT_ADD_FILTER = 7;
    public static final int ACT_REMOVE_FILTER = 8;
    public static final int ACT_CLEAR_FILTERS = 9;
    /** 开/关「按类别匹配」（名单条目按物品标签覆盖整类物品）。 */
    public static final int ACT_TOGGLE_TAG_MATCH = 10;
    /** 把玩家背包第 slot 格的物品加入过滤名单。 */
    public static final int ACT_ADD_FILTER_INV = 11;
    /** 只是刷新界面（不发任何改动）。 */
    public static final int ACT_REFRESH = 12;
    /** 切换终端当前管理的女仆（entryIndex = 女仆列表下标）。 */
    public static final int ACT_SELECT_MAID = 13;
    /** 把一只女仆移出终端列表（entryIndex = 女仆列表下标）。 */
    public static final int ACT_REMOVE_MAID = 14;
    /** 切换这个容器的名单是“共用”还是“独立”（entryIndex = 容器下标）。 */
    public static final int ACT_TOGGLE_FILTER_SCOPE = 15;
    /** 允许的最大动作号（坏包/旧客户端发来的乱码直接丢弃）。 */
    private static final int MAX_ACTION = ACT_TOGGLE_FILTER_SCOPE;

    private ContainerService() {}

    // ------------------------------------------------------------------
    // 哪些物品算「隙间」：本模组的终端 + 配置白名单里的原模组物品
    // ------------------------------------------------------------------

    public static boolean isGapItem(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        if (stack.getItem() == ModItems.MAID_GAP_TERMINAL.get()) {
            return true;
        }
        List<? extends String> extra = MaidGapConfig.EXTRA_GAP_ITEMS.get();
        if (extra == null || extra.isEmpty()) {
            return false;
        }
        ResourceLocation key = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (key == null) {
            return false;
        }
        String id = key.toString();
        String ns = key.getNamespace();
        for (String s : extra) {
            if (s == null) {
                continue;
            }
            String t = s.trim().toLowerCase(Locale.ROOT);
            if (t.isEmpty()) {
                continue;
            }
            if (t.indexOf(':') > 0 ? t.equals(id) : t.equals(ns)) {
                return true;
            }
        }
        return false;
    }

    /** 玩家当前可用的隙间物品：主手优先，其次副手；没有返回 EMPTY。 */
    public static ItemStack heldGap(ServerPlayer player) {
        ItemStack main = player.getMainHandItem();
        if (isGapItem(main)) {
            return main;
        }
        ItemStack off = player.getOffhandItem();
        if (isGapItem(off)) {
            return off;
        }
        return ItemStack.EMPTY;
    }

    /** 玩家手里的「隙间终端」（主手优先，其次副手）；没有返回空。 */
    public static ItemStack heldTerminal(ServerPlayer player) {
        ItemStack main = player.getMainHandItem();
        if (main.getItem() == ModItems.GAP_TERMINAL.get()) {
            return main;
        }
        ItemStack off = player.getOffhandItem();
        return off.getItem() == ModItems.GAP_TERMINAL.get() ? off : ItemStack.EMPTY;
    }

    /**
     * 终端里选中第 index 个容器；越界（快照过期、刚解绑、坏包）返回 null。
     * 【规矩】名单类动作拿到 null 必须直接拒绝并刷新界面，绝不能“回落共用名单”——
     * 否则玩家点一下已经消失的容器，就会悄悄改掉另一套全局生效的名单。
     */
    @Nullable
    private static Binding filterBinding(ItemStack gap, int index, String dim) {
        List<Binding> list = GapItemData.bindings(gap, dim);
        if (index < 0 || index >= list.size()) {
            return null;
        }
        return list.get(index);
    }

    /** 车万女仆是否加载（终端链接分支的类加载保护）。 */
    public static boolean tlmLoaded() {
        return ModList.get().isLoaded("touhou_little_maid");
    }

    /**
     * 终端界面/绑定要操作的目标隙间：
     * - 手持「隙间终端」→ 链接女仆饰品栏里的那件隙间（活引用，改完实时生效）；
     * - 手持隙间物品 → 就是手里那件（旧行为）。
     */
    public static GapTarget gapTarget(ServerPlayer sp) {
        ItemStack main = sp.getMainHandItem();
        ItemStack off = sp.getOffhandItem();
        ItemStack terminal = main.getItem() == ModItems.GAP_TERMINAL.get() ? main
                : off.getItem() == ModItems.GAP_TERMINAL.get() ? off : null;
        if (terminal != null) {
            if (tlmLoaded()) {
                try {
                    return MaidLinkService.resolve(sp, terminal);
                } catch (LinkageError err) {
                    // 车万女仆版本对不上：退回“没链接”状态，绝不把异常扔给网络包/服务器线程
                    return GapTarget.emptyTerminal(GapLinkData.savedName(terminal));
                }
            }
            return GapTarget.emptyTerminal(GapLinkData.savedName(terminal));
        }
        return GapTarget.held(heldGap(sp));
    }

    /** 目标隙间的 NBT 被改过之后：同步手持物品提示 + 女仆饰品槽（终端模式）。 */
    private static void syncGap(ServerPlayer sp, GapTarget t) {
        syncHeld(sp);
        if (t.maid != null) {
            MaidLinkService.syncBauble(t);
        }
    }

    /** 没有可用目标隙间时该说什么。 */
    private static String noTargetKey(GapTarget t) {
        if (!t.terminal) {
            return "msg.maidgap.needheld";
        }
        if (t.notOwner) {
            return "msg.maidgap.not_owner";
        }
        return t.linkMissing ? "msg.maidgap.link_none" : "msg.maidgap.maid_nogap";
    }

    // ------------------------------------------------------------------
    // 绑定 / 解绑（写入手持隙间物品的 NBT）
    // ------------------------------------------------------------------

    /**
     * 潜行+右键容器：绑定/解绑。
     *
     * @return true = 这次点击已经被隙间处理掉（调用方应当拦下它）；false = 没有可操作的目标，
     *         让原版/其他模组照常处理这次右键（否则手里拿着没链接的终端时连箱子都打不开）
     */
    public static boolean tryBindAt(ServerPlayer player, BlockPos pos) {
        Level level = player.level();
        BlockState state = level.getBlockState(pos);
        // 先看方块本身：不是方块实体就直接放行（草方块、石头…），连提示都不刷
        BlockEntity be = level.getBlockEntity(pos);
        if (be == null) {
            return false;
        }
        IItemHandler items = findItems(be);
        IFluidHandler fluids = MaidGapConfig.ALLOW_FLUID_TANKS.get() ? findFluids(be) : null;
        if (items == null && fluids == null) {
            // 是方块实体但不是容器（比如告示牌、熔炉被别的模组挡了能力）：给个明确的失败原因
            message(player, "msg.maidgap.nocap", ChatFormatting.RED, null);
            return false;
        }

        GapTarget target = gapTarget(player);
        ItemStack gap = target.stack;
        if (gap.isEmpty()) {
            message(player, noTargetKey(target), ChatFormatting.RED, null);
            return false;
        }
        String namespace = namespaceOf(state);
        if (!MaidGapConfig.modAllowed(namespace)) {
            message(player, "msg.maidgap.blocked", ChatFormatting.RED, namespace.isEmpty() ? "?" : namespace);
            return false;
        }
        if (!MaidGapConfig.INFINITE_RANGE.get()) {
            int max = MaidGapConfig.MAX_BIND_DISTANCE.get();
            if (player.blockPosition().distSqr(pos) > (double) max * max) {
                message(player, "msg.maidgap.range", ChatFormatting.RED, String.valueOf(max));
                return true;
            }
        }

        byte kind = items != null ? Binding.KIND_ITEM : Binding.KIND_FLUID;
        String dim = level.dimension().location().toString();
        Binding candidate = new Binding(dim, pos.immutable(), kind);
        GapItemData.BindResult result = GapItemData.bindOrToggle(gap, candidate,
                MaidGapConfig.MAX_BOUND_CONTAINERS.get(), dim);
        syncGap(player, target);

        switch (result) {
            case BOUND -> {
                feedbackBind(player, level, pos);
                // “（储罐）”走 lang，不再硬编码中文（英文环境要能翻译）
                message(player, kind == Binding.KIND_FLUID ? "msg.maidgap.bound_tank" : "msg.maidgap.bound",
                        ChatFormatting.LIGHT_PURPLE,
                        describe(pos) + " · " + state.getBlock().getName().getString());
            }
            case UNBOUND -> {
                feedbackUnbind(player);
                message(player, "msg.maidgap.unbound", ChatFormatting.GRAY, describe(pos));
            }
            case FULL -> {
                message(player, "msg.maidgap.full", ChatFormatting.RED,
                        String.valueOf(MaidGapConfig.MAX_BOUND_CONTAINERS.get()));
                // 绑定满了也说清楚：这次点击算被隙间处理掉，别再让箱子界面跟着弹出来
                return true;
            }
        }
        return true;
    }

    // ------------------------------------------------------------------
    // 访问解析（玩家终端侧：距离/维度规则）
    // ------------------------------------------------------------------

    public static final class Resolved {
        public final int status;
        public final ServerLevel level;
        public final BlockEntity blockEntity;
        public final IItemHandler items;
        public final IFluidHandler fluids;

        Resolved(int status, ServerLevel level, BlockEntity blockEntity, IItemHandler items, IFluidHandler fluids) {
            this.status = status;
            this.level = level;
            this.blockEntity = blockEntity;
            this.items = items;
            this.fluids = fluids;
        }
    }

    private static final Resolved FAIL_NOT_LOADED = new Resolved(TerminalSnapshot.STATUS_NOT_LOADED, null, null, null, null);
    private static final Resolved FAIL_OUT_OF_RANGE = new Resolved(TerminalSnapshot.STATUS_OUT_OF_RANGE, null, null, null, null);
    private static final Resolved FAIL_NO_TARGET = new Resolved(TerminalSnapshot.STATUS_NO_TARGET, null, null, null, null);
    private static final Resolved FAIL_NOT_ALLOWED = new Resolved(TerminalSnapshot.STATUS_NOT_ALLOWED, null, null, null, null);
    private static final Resolved FAIL_NO_CAPABILITY = new Resolved(TerminalSnapshot.STATUS_NO_CAPABILITY, null, null, null, null);

    public static Resolved resolve(ServerPlayer user, Binding b) {
        ResourceLocation dimId = ResourceLocation.tryParse(b.dimension());
        if (dimId == null) {
            return FAIL_NO_TARGET;
        }
        ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, dimId);
        MinecraftServer server = user.server;
        ServerLevel level = server.getLevel(key);
        if (level == null) {
            return FAIL_NO_TARGET;
        }
        if (!MaidGapConfig.INFINITE_RANGE.get()) {
            if (!level.dimension().location().equals(user.level().dimension().location())) {
                return FAIL_OUT_OF_RANGE;
            }
            int max = MaidGapConfig.MAX_BIND_DISTANCE.get();
            if (user.blockPosition().distSqr(b.pos()) > (double) max * max) {
                return FAIL_OUT_OF_RANGE;
            }
        }
        if (!level.isLoaded(b.pos())) {
            return FAIL_NOT_LOADED;
        }
        BlockEntity be = level.getBlockEntity(b.pos());
        if (be == null) {
            return FAIL_NO_TARGET;
        }
        String ns = namespaceOf(be.getBlockState());
        if (!MaidGapConfig.modAllowed(ns)) {
            return FAIL_NOT_ALLOWED;
        }
        if (b.isFluid()) {
            if (!MaidGapConfig.ALLOW_FLUID_TANKS.get()) {
                return FAIL_NOT_ALLOWED;
            }
            IFluidHandler fh = findFluids(be);
            return fh == null ? FAIL_NO_CAPABILITY : new Resolved(TerminalSnapshot.STATUS_OK, level, be, null, fh);
        }
        IItemHandler ih = findItems(be);
        return ih == null ? FAIL_NO_CAPABILITY : new Resolved(TerminalSnapshot.STATUS_OK, level, be, ih, null);
    }

    public static IItemHandler findItems(BlockEntity be) {
        IItemHandler h = be.getCapability(ForgeCapabilities.ITEM_HANDLER, null).resolve().orElse(null);
        if (h != null) {
            return h;
        }
        for (Direction d : Direction.values()) {
            h = be.getCapability(ForgeCapabilities.ITEM_HANDLER, d).resolve().orElse(null);
            if (h != null) {
                return h;
            }
        }
        return null;
    }

    public static IFluidHandler findFluids(BlockEntity be) {
        IFluidHandler h = be.getCapability(ForgeCapabilities.FLUID_HANDLER, null).resolve().orElse(null);
        if (h != null) {
            return h;
        }
        for (Direction d : Direction.values()) {
            h = be.getCapability(ForgeCapabilities.FLUID_HANDLER, d).resolve().orElse(null);
            if (h != null) {
                return h;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------
    // 快照
    // ------------------------------------------------------------------

    public static TerminalSnapshot build(ServerPlayer user) {
        return build(user, MAX_TOTAL_STACKS);
    }

    /**
     * @param stackBudget 这次最多列多少堆物品（发送前会按包体大小再收紧）
     */
    private static TerminalSnapshot build(ServerPlayer user, int stackBudget) {
        TerminalSnapshot s = new TerminalSnapshot();
        s.pickupAmount = 64;
        s.autoRefill = GapStore.autoRefillOn(user);
        s.maxContainers = MaidGapConfig.MAX_BOUND_CONTAINERS.get();

        GapTarget target = gapTarget(user);
        ItemStack gap = target.stack;
        s.viaTerminal = target.terminal;
        s.linkMissing = target.linkMissing;
        s.maidHasNoGap = target.maidHasNoGap;
        s.notOwner = target.notOwner;
        s.maidName = target.maidName;
        s.holdingGap = !gap.isEmpty();
        // 界面标题用：手里是哪件隙间就显示哪件，别再让「隙间·增强」的界面顶着「终端」的名字
        s.gapName = gap.isEmpty() ? "" : gap.getHoverName().getString();
        if (s.holdingGap) {
            // 服务端读名单前先把旧版脏数据洗掉并写回（R0 的「打开终端」路径，
            // 一次覆盖共用 + 全部独立名单；处理过就有标记，后续构建不再扫描）
            GapItemData.sanitizeFilters(gap);
            s.maidToChest = GapItemData.isMaidToChest(gap);
            s.blacklist = GapItemData.isBlacklist(gap);
            s.tagMatch = GapItemData.isTagMatch(gap);
            s.filters = GapItemData.filters(gap);
            s.pickupAmount = GapItemData.pickupAmount(gap);
            s.slotLocks = GapItemData.slotLockBytes(gap);
        }
        // 槽位配置界面要展示女仆背包内容（只有终端链接到在场女仆时才有）
        if (target.maid != null && tlmLoaded()) {
            s.maidSlots = cn.blockforge.generated.maidbettergap.compat.tlm.MaidLinkService
                    .maidInvDisplay(target.maid);
        }

        // 终端里存着的女仆列表（拿着终端才有；女仆不在附近也照样列出来，方便切换/删除）
        ItemStack terminal = heldTerminal(user);
        if (!terminal.isEmpty()) {
            s.maidLimit = MaidGapConfig.TERMINAL_MAID_LIMIT.get();
            java.util.UUID active = GapLinkData.activeUuid(terminal);
            s.activeMaid = active == null ? "" : active.toString();
            if (tlmLoaded()) {
                try {
                    s.maids = MaidLinkService.maidStates(user, terminal);
                } catch (LinkageError | RuntimeException err) {
                    // 车万女仆版本对不上 / 数据没就绪：列表留空，终端其余功能照常可用
                    // （maidStates 内部已逐只兜底，走到这里说明是整表级的意外）
                    s.maids = new ArrayList<>();
                }
            }
        }

        String dim = user.level().dimension().location().toString();
        int listed = 0;
        for (Binding b : s.holdingGap ? GapItemData.bindings(gap, dim) : List.<Binding>of()) {
            TerminalSnapshot.Entry e = new TerminalSnapshot.Entry(b, TerminalSnapshot.STATUS_OK);
            Resolved r = resolve(user, b);
            e.status = r.status;
            // 这个容器实际生效的名单：有自己的用自己，没有的沿用共用名单
            e.ownFilter = GapItemData.hasOwnFilter(gap, b);
            e.blacklist = GapItemData.isBlacklist(gap, b);
            e.tagMatch = GapItemData.isTagMatch(gap, b);
            e.filters = GapItemData.filters(gap, b);
            if (r.blockEntity != null) {
                ResourceLocation blockKey = ForgeRegistries.BLOCKS.getKey(r.blockEntity.getBlockState().getBlock());
                e.blockId = blockKey == null ? "" : blockKey.toString();
            }
            if (r.status == TerminalSnapshot.STATUS_OK && listed < stackBudget) {
                if (r.items != null) {
                    int limit = Math.min(r.items.getSlots(), MAX_LISTED_SLOTS);
                    for (int slot = 0; slot < limit && listed < stackBudget; slot++) {
                        ItemStack st = r.items.getStackInSlot(slot);
                        if (!st.isEmpty()) {
                            e.slots.add(slot);
                            e.stacks.add(st.copy());
                            listed++;
                        }
                    }
                } else if (r.fluids != null) {
                    for (int t = 0; t < r.fluids.getTanks(); t++) {
                        FluidStack fs = r.fluids.getFluidInTank(t);
                        if (!fs.isEmpty()) {
                            e.tankSlots.add(t);
                            e.fluids.add(fs.copy());
                            e.capacities.add(r.fluids.getTankCapacity(t));
                        }
                    }
                }
            }
            s.entries.add(e);
        }
        return s;
    }

    public static void openTerminal(Player p) {
        if (!(p instanceof ServerPlayer sp) || sp.isSpectator()) {
            return;
        }
        // 这也是一个网络入口（G 键 / 右键空气都会走到这里）：不设防的话，
        // 作弊客户端连点就能让服务端不停「构建快照 + 编码 + 发送」。
        if (!GapStore.allowAction(sp, GapStore.BUCKET_OPEN, OPEN_MIN_TICKS)) {
            return;
        }
        // 手里既没有隙间也没有终端：不构建（省掉一次全绑定扫描）
        if (heldGap(sp).isEmpty()
                && sp.getMainHandItem().getItem() != ModItems.GAP_TERMINAL.get()
                && sp.getOffhandItem().getItem() != ModItems.GAP_TERMINAL.get()) {
            return;
        }
        send(sp, true);
    }

    public static void send(ServerPlayer sp, boolean open) {
        // 超大仓库（几千堆东西 / 一堆塞满 NBT 的潜影盒）会把包撑到客户端直接掉线。
        // 做法：整个快照只构建一次，超限就在快照对象上按体积裁剪（不再反复重建、也不再反复
        // 解析全部绑定和复制女仆背包），最后给玩家一句“只显示了一部分”。
        TerminalSnapshot full = build(sp, MAX_TOTAL_STACKS);
        TerminalSnapshot snap = full;
        int size = encodedSize(full);
        boolean truncated = false;
        if (size > MAX_SNAPSHOT_BYTES) {
            truncated = true;
            int stacks = countStacks(full);
            int maidKeep = countNonEmpty(full.maidSlots);
            // ① 先砍容器物品堆：按「字节比例」估算保留几堆，每轮至少减半，两三下就收敛
            while (size > MAX_SNAPSHOT_BYTES && stacks > 0) {
                long guess = (long) stacks * MAX_SNAPSHOT_BYTES / Math.max(1, size);
                stacks = (int) Math.max(0L, Math.min(stacks - 1L, Math.max(stacks / 2L, guess)));
                snap = trimmed(full, stacks, maidKeep);
                size = encodedSize(snap);
            }
            // ② 容器物品砍光还超：女仆背包那 38 格也参与裁剪
            //   （38 个塞满 NBT 的潜影盒同样能撑爆 1 MB，只砍容器是不够的）
            while (size > MAX_SNAPSHOT_BYTES && maidKeep > 0) {
                maidKeep = Math.max(0, Math.min(maidKeep - 1, maidKeep / 2));
                snap = trimmed(full, stacks, maidKeep);
                size = encodedSize(snap);
            }
            // ③ 实在编不出来（某个物品的 NBT 写坏了）：至少发一个结构完整的空界面，
            //   并且明确告诉玩家“没显示全”，别让他以为东西丢了
            if (size > MAX_SNAPSHOT_BYTES) {
                snap = trimmed(full, 0, 0);
            }
        }
        // 发送兜底：编码整个动作都可能抛异常（个别模组物品把 NBT 写坏了、
        // 或包体触到通道硬上限）。异常绝不能顺着 GapActionPacket/OpenTerminalPacket
        // 冒进服务器线程，退一步发“只剩结构”的最小快照，界面空但不再崩。
        boolean reduced = false;
        try {
            ModNetwork.sendToPlayer(sp, new TerminalSnapshotPacket(snap, open));
        } catch (RuntimeException | LinkageError ex) {
            cn.blockforge.generated.maidbettergap.GeneratedMod.LOGGER
                    .warn("[maid_better_gap] 终端快照发送失败，改发最小快照。", ex);
            try {
                ModNetwork.sendToPlayer(sp, new TerminalSnapshotPacket(minimalSnapshot(full), open));
                reduced = true;
            } catch (RuntimeException | LinkageError ex2) {
                // 连最小快照都发不出去就不发了（几乎不可能：里面已没有任何物品数据），
                // 总之绝不让异常离开这里
                cn.blockforge.generated.maidbettergap.GeneratedMod.LOGGER
                        .warn("[maid_better_gap] 最小快照也发送失败，本次终端刷新已放弃。", ex2);
            }
        }
        // 提示最多 5 秒一次：每次点击都弹一遍会很吵（尤其是自动刷新每秒一次）
        if (truncated && GapStore.allowAction(sp, GapStore.BUCKET_HINT, 100)) {
            message(sp, "msg.maidgap.truncated", ChatFormatting.YELLOW, null);
        }
        // 走到最小快照兜底时界面是空的，玩家容易以为东西丢了——明确告诉他只是显示精简（R8）
        if (reduced && GapStore.allowAction(sp, GapStore.BUCKET_HINT, 100)) {
            message(sp, "msg.maidgap.snapshot_reduced", ChatFormatting.YELLOW, null);
        }
    }

    /**
     * 极端兜底用的最小快照：只保留结构与文本字段，物品/流体/名单全部清空。
     * 正常永远走不到这里；只有连裁剪后的快照编码都抛异常（某个物品 NBT 写坏了）才发它，
     * 保证玩家至少能打开一个“空的”终端界面，而不是收包崩客户端。
     */
    private static TerminalSnapshot minimalSnapshot(TerminalSnapshot src) {
        TerminalSnapshot s = new TerminalSnapshot();
        s.pickupAmount = src.pickupAmount;
        s.slotLocks = src.slotLocks;
        s.autoRefill = src.autoRefill;
        s.maxContainers = src.maxContainers;
        s.holdingGap = src.holdingGap;
        s.maidToChest = src.maidToChest;
        s.blacklist = src.blacklist;
        s.tagMatch = src.tagMatch;
        s.viaTerminal = src.viaTerminal;
        s.maidName = src.maidName;
        s.gapName = src.gapName;
        s.linkMissing = src.linkMissing;
        s.maidHasNoGap = src.maidHasNoGap;
        s.notOwner = src.notOwner;
        s.maids = src.maids;
        s.activeMaid = src.activeMaid;
        s.maidLimit = src.maidLimit;
        for (TerminalSnapshot.Entry e : src.entries) {
            TerminalSnapshot.Entry c = new TerminalSnapshot.Entry(e.binding, e.status);
            c.ownFilter = e.ownFilter;
            c.blacklist = e.blacklist;
            c.tagMatch = e.tagMatch;
            s.entries.add(c);
        }
        return s;
    }

    /** 快照里一共列了多少堆物品（用来估算要裁掉多少）。 */
    private static int countStacks(TerminalSnapshot snap) {
        int n = 0;
        for (TerminalSnapshot.Entry e : snap.entries) {
            n += e.stacks.size();
        }
        return n;
    }

    private static int countNonEmpty(List<ItemStack> list) {
        int n = 0;
        for (ItemStack st : list) {
            if (!st.isEmpty()) {
                n++;
            }
        }
        return n;
    }

    /**
     * 复制一份快照，只保留前 keepStacks 堆容器物品、前 keepMaid 个非空女仆背包格。
     * 只是重新引用已有的物品对象，不重新读任何容器，所以裁剪几十次也很便宜。
     */
    private static TerminalSnapshot trimmed(TerminalSnapshot src, int keepStacks, int keepMaid) {
        TerminalSnapshot s = new TerminalSnapshot();
        s.pickupAmount = src.pickupAmount;
        s.slotLocks = src.slotLocks;
        s.autoRefill = src.autoRefill;
        s.maxContainers = src.maxContainers;
        s.holdingGap = src.holdingGap;
        s.maidToChest = src.maidToChest;
        s.blacklist = src.blacklist;
        s.tagMatch = src.tagMatch;
        s.viaTerminal = src.viaTerminal;
        s.maidName = src.maidName;
        s.gapName = src.gapName;
        s.linkMissing = src.linkMissing;
        s.maidHasNoGap = src.maidHasNoGap;
        s.notOwner = src.notOwner;
        // 名单条目在 GapItemData 读取时就已净化（只剩物品 ID，没有 NBT、数量恒 1），
        // 最多 9 套 x 9 格的纯 ID 体积可以忽略，确实不需要参与裁剪；女仆列表同理。
        s.filters = src.filters;
        s.maids = src.maids;
        s.activeMaid = src.activeMaid;
        s.maidLimit = src.maidLimit;
        s.maidSlots = trimSlots(src.maidSlots, keepMaid);
        int left = Math.max(0, keepStacks);
        for (TerminalSnapshot.Entry e : src.entries) {
            TerminalSnapshot.Entry c = new TerminalSnapshot.Entry(e.binding, e.status);
            c.blockId = e.blockId;
            c.ownFilter = e.ownFilter;
            c.blacklist = e.blacklist;
            c.tagMatch = e.tagMatch;
            c.filters = e.filters;
            // 储罐数量受绑定容器个数限制，体积可控，原样保留
            c.tankSlots.addAll(e.tankSlots);
            c.fluids.addAll(e.fluids);
            c.capacities.addAll(e.capacities);
            int n = Math.min(e.stacks.size(), left);
            for (int i = 0; i < n; i++) {
                c.slots.add(e.slots.get(i));
                c.stacks.add(e.stacks.get(i));
            }
            left -= n;
            s.entries.add(c);
        }
        return s;
    }

    /** 保留前 keep 个非空格，其余格清空（格数不变，界面布局不受影响）。 */
    private static List<ItemStack> trimSlots(List<ItemStack> src, int keep) {
        if (src.isEmpty()) {
            return src;
        }
        List<ItemStack> out = new ArrayList<>(src.size());
        int kept = 0;
        for (ItemStack st : src) {
            if (!st.isEmpty()) {
                if (kept >= keep) {
                    out.add(ItemStack.EMPTY);
                    continue;
                }
                kept++;
            }
            out.add(st);
        }
        return out;
    }

    /** 这个快照编码成网络包大概多大（字节）。 */
    private static int encodedSize(TerminalSnapshot snap) {
        net.minecraft.network.FriendlyByteBuf probe =
                new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        try {
            snap.encode(probe);
            return probe.writerIndex();
        } catch (RuntimeException ex) {
            // 个别物品的 NBT 编不出来（模组自己写坏了 NBT）时，按“已经超限”处理，走精简路线
            return Integer.MAX_VALUE;
        } finally {
            probe.release();
        }
    }

    // ------------------------------------------------------------------
    // 动作
    // ------------------------------------------------------------------

    public static void handleAction(ServerPlayer sp, int type, int entryIndex, int slot) {
        // 旁观者没有背包也没有交互权，任何动作都不该生效
        if (sp.isSpectator()) {
            return;
        }
        // 只接受界面真的会发的动作：坏包/旧客户端发来的乱码类型直接丢弃。
        // 注意这里不能回包——一包回一次「build + 编码 + 发送」就等于给了作弊客户端
        // 一个免限速的放大入口（连发 type=99 就能把服务端拖垮）。
        if (type < 0 || type > MAX_ACTION) {
            return;
        }
        // 限速：普通动作与刷新各记各的（共用一张表会让「拿取后马上刷新」被误挡）
        if (type == ACT_REFRESH) {
            if (!GapStore.allowAction(sp, GapStore.BUCKET_REFRESH, REFRESH_MIN_TICKS)) {
                // 被挡掉时给一句提示，否则玩家只会觉得“刷新按钮坏了”。
                // 提示本身也要限流：否则作弊客户端狂发刷新包，每个包都能换回一个提示包。
                if (GapStore.allowAction(sp, GapStore.BUCKET_HINT, 100)) {
                    message(sp, "msg.maidgap.too_fast", ChatFormatting.GRAY, null);
                }
                return;
            }
            send(sp, false);
            return;
        }
        if (!GapStore.allowAction(sp, GapStore.BUCKET_ACTION, ACTION_MIN_TICKS)) {
            return;
        }
        if (type == ACT_CYCLE_PICKUP) {
            GapTarget target = gapTarget(sp);
            if (target.stack.isEmpty()) {
                message(sp, noTargetKey(target), ChatFormatting.RED, null);
                send(sp, false);
                return;
            }
            GapItemData.cyclePickupAmount(target.stack);
            syncGap(sp, target);
            send(sp, false);
            return;
        }
        if (type == ACT_TOGGLE_REFILL) {
            GapStore.setAutoRefill(sp, !GapStore.autoRefillOn(sp));
            send(sp, false);
            return;
        }
        if (type == ACT_SELECT_MAID || type == ACT_REMOVE_MAID) {
            handleMaidAction(sp, type, entryIndex);
            return;
        }

        GapTarget target = gapTarget(sp);
        ItemStack gap = target.stack;
        if (gap.isEmpty()) {
            message(sp, noTargetKey(target), ChatFormatting.RED, null);
            send(sp, false);
            return;
        }
        String dim = sp.level().dimension().location().toString();

        switch (type) {
            case ACT_TOGGLE_IO_MODE -> {
                GapItemData.toggleMaidToChest(gap);
                syncGap(sp, target);
                send(sp, false);
            }
            case ACT_TOGGLE_FILTER_MODE -> {
                // 容器有独立名单就只改它自己的，否则改这个容器沿用的共用设置；
                // 下标越界（快照过期/刚解绑/坏包）直接拒绝，绝不回落去动共用名单
                Binding fb = filterBinding(gap, entryIndex, dim);
                if (fb != null) {
                    GapItemData.toggleBlacklist(gap, fb);
                }
                syncGap(sp, target);
                send(sp, false);
            }
            case ACT_TOGGLE_TAG_MATCH -> {
                Binding fb = filterBinding(gap, entryIndex, dim);
                if (fb != null) {
                    GapItemData.toggleTagMatch(gap, fb);
                }
                syncGap(sp, target);
                send(sp, false);
            }
            case ACT_TOGGLE_FILTER_SCOPE -> {
                Binding fb = filterBinding(gap, entryIndex, dim);
                if (fb != null) {
                    GapItemData.setOwnFilter(gap, fb, !GapItemData.hasOwnFilter(gap, fb));
                    message(sp, GapItemData.hasOwnFilter(gap, fb)
                            ? "msg.maidgap.filter_own" : "msg.maidgap.filter_shared", ChatFormatting.LIGHT_PURPLE, null);
                }
                syncGap(sp, target);
                send(sp, false);
            }
            case ACT_ADD_FILTER_INV -> {
                ItemStack sample = slot >= 0 && slot < 36 ? sp.getInventory().getItem(slot) : ItemStack.EMPTY;
                Binding fb = filterBinding(gap, entryIndex, dim);
                if (fb != null && !sample.isEmpty() && !GapItemData.containsFilter(gap, fb, sample)
                        && !GapItemData.addFilter(gap, fb, sample)) {
                    message(sp, "msg.maidgap.filter_full", ChatFormatting.RED, null);
                }
                syncGap(sp, target);
                send(sp, false);
            }
            case ACT_CLEAR_FILTERS -> {
                Binding fb = filterBinding(gap, entryIndex, dim);
                if (fb != null) {
                    GapItemData.clearFilters(gap, fb);
                }
                syncGap(sp, target);
                send(sp, false);
            }
            case ACT_REMOVE_FILTER -> {
                Binding fb = filterBinding(gap, entryIndex, dim);
                if (fb != null) {
                    GapItemData.removeFilter(gap, fb, slot);
                }
                syncGap(sp, target);
                send(sp, false);
            }
            case ACT_ADD_FILTER -> {
                List<Binding> list = GapItemData.bindings(gap, dim);
                if (entryIndex >= 0 && entryIndex < list.size()) {
                    Binding b = list.get(entryIndex);
                    Resolved r = resolve(sp, b);
                    ItemStack sample = ItemStack.EMPTY;
                    // 客户端快照可能已经过期（容器里的槽位变了），越界就当成没点到
                    if (r.status == TerminalSnapshot.STATUS_OK && r.items != null
                            && slot >= 0 && slot < r.items.getSlots()) {
                        sample = r.items.getStackInSlot(slot);
                    }
                    if (!sample.isEmpty() && !GapItemData.containsFilter(gap, b, sample)
                            && !GapItemData.addFilter(gap, b, sample)) {
                        message(sp, "msg.maidgap.filter_full", ChatFormatting.RED, null);
                    }
                }
                syncGap(sp, target);
                send(sp, false);
            }
            default -> handleBindingAction(sp, target, gap, dim, type, entryIndex, slot);
        }
    }

    /** 终端左侧女仆列表上的操作：切换当前女仆 / 把女仆移出列表。 */
    private static void handleMaidAction(ServerPlayer sp, int type, int maidIndex) {
        ItemStack terminal = heldTerminal(sp);
        if (terminal.isEmpty()) {
            send(sp, false);
            return;
        }
        List<GapLinkData.MaidRef> list = GapLinkData.maids(terminal);
        if (maidIndex < 0 || maidIndex >= list.size()) {
            // 列表已经变了（比如两次点击之间女仆被删掉）：刷新一次界面就好，不报错
            send(sp, false);
            return;
        }
        GapLinkData.MaidRef ref = list.get(maidIndex);
        if (type == ACT_SELECT_MAID) {
            GapLinkData.setActive(terminal, ref.uuid());
        } else if (GapLinkData.removeMaid(terminal, ref.uuid())) {
            message(sp, "msg.maidgap.maid_removed", ChatFormatting.GRAY,
                    ref.name() == null || ref.name().isEmpty() ? "?" : ref.name());
        }
        // 改的是终端 NBT：推一次同步，客户端手里的提示立即更新
        syncHeld(sp);
        send(sp, false);
    }

    private static void handleBindingAction(ServerPlayer sp, GapTarget target, ItemStack gap, String dim,
                                            int type, int entryIndex, int slot) {
        List<Binding> list = GapItemData.bindings(gap, dim);
        if (entryIndex < 0 || entryIndex >= list.size()) {
            send(sp, false);
            return;
        }
        Binding b = list.get(entryIndex);
        Resolved r = resolve(sp, b);

        switch (type) {
            case ACT_UNBIND -> {
                list.remove(entryIndex);
                GapItemData.setBindings(gap, list);
                syncGap(sp, target);
                feedbackUnbind(sp);
                message(sp, "msg.maidgap.unbound", ChatFormatting.GRAY, describe(b.pos()));
                send(sp, false);
            }
            case ACT_EXTRACT_ITEM -> {
                if (r.status == TerminalSnapshot.STATUS_OK && r.items != null) {
                    if (slot < 0 || slot >= r.items.getSlots()) {
                        // 快照过期（容器被换过/槽位变少）：不报错，刷新一次界面即可
                        send(sp, false);
                        return;
                    }
                    ItemStack content = r.items.getStackInSlot(slot);
                    if (!content.isEmpty()) {
                        int amount = Math.min(GapItemData.pickupAmount(gap), content.getCount());
                        ItemStack got = r.items.extractItem(slot, amount, false);
                        if (!got.isEmpty()) {
                            ItemHandlerHelper.giveItemToPlayer(sp, got);
                            r.blockEntity.setChanged();
                            feedbackExtract(sp);
                        }
                    }
                } else {
                    statusMessage(sp, r.status);
                }
                send(sp, false);
            }
            case ACT_EXTRACT_BUCKET -> {
                if (r.status == TerminalSnapshot.STATUS_OK && r.fluids != null && slot >= 0 && slot < r.fluids.getTanks()) {
                    FluidStack tank = r.fluids.getFluidInTank(slot);
                    if (!tank.isEmpty()) {
                        // 关键：必须真的有「一整桶」才换桶。
                        // 只判断 drain 结果非空是不够的——罐里剩 1 mB 时 drain 也会成功返回，
                        // 那么每次都能换出一整桶、倒回去再换，等于无限刷液体。
                        // 这里用 < 而不是 !=：大罐子（比如 8000 mB 的储罐）本来就比一桶多，照样能换。
                        if (tank.getAmount() < BUCKET_VOLUME) {
                            sp.displayClientMessage(Component.translatable("msg.maidgap.not_enough_fluid",
                                            tank.getDisplayName(), String.valueOf(tank.getAmount()),
                                            String.valueOf(BUCKET_VOLUME))
                                    .withStyle(ChatFormatting.RED), true);
                        } else {
                            FluidStack oneBucket = new FluidStack(tank.getFluid(), BUCKET_VOLUME);
                            ItemStack bucket = FluidUtil.getFilledBucket(oneBucket);
                            if (bucket.isEmpty()) {
                                sp.displayClientMessage(Component.translatable("msg.maidgap.no_bucket",
                                                tank.getDisplayName())
                                        .withStyle(ChatFormatting.RED), true);
                            } else {
                                FluidStack drained = r.fluids.drain(oneBucket, IFluidHandler.FluidAction.EXECUTE);
                                if (drained.getAmount() != BUCKET_VOLUME) {
                                    // 储罐没给足一整桶（自定义储罐可能这样）：把抽出来的还回去，绝不多给物品。
                                    // 用 != 而不是 <：多给同样不对（否则一样能刷）。
                                    int returned = 0;
                                    if (!drained.isEmpty()) {
                                        returned = r.fluids.fill(drained, IFluidHandler.FluidAction.EXECUTE);
                                    }
                                    if (returned < drained.getAmount()) {
                                        // 回滚失败：液体丢了，但绝不能因此给物品（丢液比复制液安全）
                                        r.blockEntity.setChanged();
                                        message(sp, "msg.maidgap.rollback_failed", ChatFormatting.RED, null);
                                    } else {
                                        sp.displayClientMessage(Component.translatable(
                                                        "msg.maidgap.drain_short",
                                                        tank.getDisplayName(), String.valueOf(drained.getAmount()),
                                                        String.valueOf(BUCKET_VOLUME))
                                                .withStyle(ChatFormatting.RED), true);
                                    }
                                } else {
                                    ItemHandlerHelper.giveItemToPlayer(sp, bucket);
                                    r.blockEntity.setChanged();
                                    feedbackExtract(sp);
                                }
                            }
                        }
                    }
                } else {
                    statusMessage(sp, r.status);
                }
                send(sp, false);
            }
            default -> send(sp, false);
        }
    }

    /** 槽位配置界面点「完成」：把 38 字节锁定数组写进目标隙间（原版同格式，实时生效）。 */
    public static void applySlotLocks(ServerPlayer sp, byte[] locks) {
        GapTarget target = gapTarget(sp);
        ItemStack gap = target.stack;
        if (gap.isEmpty()) {
            message(sp, noTargetKey(target), ChatFormatting.RED, null);
            return;
        }
        if (locks == null || locks.length != GapItemData.ORIGINAL_SLOT_NUM) {
            return; // 非法载荷直接忽略：短数组会把没碰过的槽位当成“解锁”冲掉
        }
        GapItemData.setSlotLocks(gap, locks);
        syncGap(sp, target);
        int locked = 0;
        for (byte b : locks) {
            if (b != 0) {
                locked++;
            }
        }
        message(sp, "msg.maidgap.slots_saved", ChatFormatting.GREEN, String.valueOf(locked));
        send(sp, false);
    }

    // ------------------------------------------------------------------
    // 自动补货：玩家背包物品快耗尽 / 已用光时，从「绑定容器」里补充。
    // 绑定来源 = 玩家背包里的隙间 + 附近女仆饰品栏里戴着的隙间（终端不算，它没有绑定）。
    // ------------------------------------------------------------------

    public static void autoRefill(ServerPlayer sp) {
        if (!MaidGapConfig.AUTO_REFILL_ENABLED.get() || !GapStore.autoRefillOn(sp)) {
            return;
        }
        // 旁观者/已死的玩家不该被偷偷塞东西
        if (sp.isSpectator() || sp.isDeadOrDying()) {
            return;
        }
        String dim = sp.level().dimension().location().toString();
        List<ItemStack> gapStacks = new ArrayList<>();
        InvWrapper playerInv = new InvWrapper(sp.getInventory());
        for (int i = 0; i < playerInv.getSlots(); i++) {
            ItemStack st = playerInv.getStackInSlot(i);
            if (isGapItem(st)) {
                gapStacks.add(st);
            }
        }
        if (tlmLoaded()) {
            MaidLinkService.addNearbyMaidGaps(sp, gapStacks);
        }
        List<Binding> bindings = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (ItemStack gap : gapStacks) {
            for (Binding b : GapItemData.bindings(gap, dim)) {
                if (seen.add(b.key())) {
                    bindings.add(b);
                }
            }
        }
        if (bindings.isEmpty()) {
            return;
        }
        List<Resolved> sources = new ArrayList<>();
        for (Binding b : bindings) {
            Resolved r = resolve(sp, b);
            if (r.status == TerminalSnapshot.STATUS_OK && r.items != null) {
                sources.add(r);
            }
        }
        if (sources.isEmpty()) {
            return;
        }

        int threshold = MaidGapConfig.AUTO_REFILL_THRESHOLD.get();
        InvWrapper inv = new InvWrapper(sp.getInventory());
        // 补货只往主背包（0-35）塞，所以判定也只看主背包，否则副手/盔甲格里的东西永远“补不满”会反复抽箱子
        int mainSlots = Math.min(36, inv.getSlots());

        // —— 刷新「快用完 / 已用光」记忆：数量降到阈值以下就记下，用光后继续记，补满了才忘 ——
        List<ItemStack> memory = GapStore.refillMemory(sp);
        List<ItemStack> next = new ArrayList<>();
        for (int s = 0; s < mainSlots; s++) {
            ItemStack cur = inv.getStackInSlot(s);
            // 不可堆叠的东西（剑、镐、盔甲…）数量永远是 1，不该被当成“快用完”去补，
            // 否则记忆表会被这些永久的 1 占满，真正要补的消耗品反而挤不进去
            if (cur.isEmpty() || cur.getMaxStackSize() <= 1 || cur.getCount() > threshold) {
                continue;
            }
            if (!containsSame(next, cur)) {
                ItemStack proto = cur.copy();
                proto.setCount(1);
                next.add(proto);
            }
        }
        for (ItemStack old : memory) {
            if (next.size() >= 24) {
                break;
            }
            // 旧存档里记下的不可堆叠物品（剑/镐/盔甲…）不沿用：它们数量永远是 1，
            // 会一直占着 24 个记忆位，把真正要补的消耗品挤掉
            if (old.getMaxStackSize() <= 1) {
                continue;
            }
            if (containsSame(next, old)) {
                continue;
            }
            if (countOf(inv, old) == 0) {
                next.add(old);
            }
        }
        // 只在记忆表真的变了才写玩家数据：每 2 秒无脑写一次会把玩家存档一直标脏
        if (!sameStacks(next, memory)) {
            GapStore.setRefillMemory(sp, next);
        }

        int refilled = 0;
        // —— ① 背包里还剩 1~阈值 个：补满一组 ——
        for (int s = 0; s < mainSlots && refilled < 3; s++) {
            ItemStack cur = inv.getStackInSlot(s);
            if (cur.isEmpty() || cur.getMaxStackSize() <= 1 || cur.getCount() > threshold) {
                continue;
            }
            if (pullInto(sp, sources, cur, cur.getMaxStackSize() - cur.getCount())) {
                refilled++;
                feedbackRefill(sp);
            }
        }
        // —— ② 彻底用光的（背包里 0 个）：重新拉一整组回来 ——
        for (ItemStack proto : next) {
            if (refilled >= 3) {
                break;
            }
            if (proto.getMaxStackSize() <= 1) {
                continue; // 旧存档里记下的不可堆叠物品，不再补
            }
            if (countOf(inv, proto) != 0) {
                continue; // 还在背包里，上面补满那条路已经处理过
            }
            if (pullInto(sp, sources, proto, proto.getMaxStackSize())) {
                refilled++;
                feedbackRefill(sp);
            }
        }
        // —— ③ 刚刚被用光、但没赶上扫描的（吃/喝/放置/投掷）：也补一组 ——
        for (ItemStack proto : GapStore.recentlyUsed(sp, RECENT_USE_WINDOW_TICKS)) {
            if (refilled >= 3) {
                break;
            }
            if (countOf(inv, proto) != 0 || containsSame(next, proto)) {
                continue; // 还在背包里 / 记忆表上面已经处理过
            }
            if (pullInto(sp, sources, proto, proto.getMaxStackSize())) {
                refilled++;
                feedbackRefill(sp);
            }
        }
    }

    /**
     * 从绑定容器里抽取与 match 相同的物品（含 NBT）塞进玩家背包；搬到东西返回 true。
     * 先模拟一遍再真正搬运：背包塞不下的部分原路退回容器，绝不凭空消失。
     */
    private static boolean pullInto(ServerPlayer sp, List<Resolved> sources, ItemStack match, int need) {
        if (need <= 0 || match.isEmpty()) {
            return false;
        }
        net.minecraft.world.entity.player.Inventory inv = sp.getInventory();
        boolean anyMoved = false;
        for (Resolved r : sources) {
            IItemHandler h = r.items;
            boolean movedHere = false;
            for (int slot = 0; slot < h.getSlots() && need > 0; slot++) {
                ItemStack cs = h.getStackInSlot(slot);
                if (cs.isEmpty() || !ItemStack.isSameItemSameTags(cs, match)) {
                    continue;
                }
                int want = Math.min(need, cs.getCount());
                if (want <= 0) {
                    continue;
                }
                ItemStack sim = h.extractItem(slot, want, true);
                if (sim.isEmpty()) {
                    continue;
                }
                int fit = sim.getCount() - insertMainInventory(inv, sim, true).getCount();
                if (fit <= 0) {
                    continue; // 背包真塞不下，不要从容器里掏出来
                }
                ItemStack got = h.extractItem(slot, fit, false);
                if (got.isEmpty()) {
                    continue;
                }
                ItemStack left = insertMainInventory(inv, got, false);
                int put = got.getCount() - left.getCount();
                if (put > 0) {
                    need -= put;
                    movedHere = true;
                }
                if (!left.isEmpty()) {
                    // 理论上到不了这里（上面已经模拟过）；万一塞不回去，就丢在玩家脚下，不让物品消失
                    ItemHandlerHelper.giveItemToPlayer(sp, left);
                }
            }
            if (movedHere && r.blockEntity != null) {
                r.blockEntity.setChanged();
            }
            anyMoved |= movedHere;
        }
        return anyMoved;
    }

    /**
     * 往玩家主背包（0-35：快捷栏 + 三行背包）塞物品，返回没塞下的部分。
     * 刻意不碰盔甲槽和副手槽：Forge 的 InvWrapper 会把东西塞进空盔甲槽，那里既没意义又难取出。
     *
     * @param simulate true 只试算，不改动背包
     */
    private static ItemStack insertMainInventory(net.minecraft.world.entity.player.Inventory inv,
                                                 ItemStack stack, boolean simulate) {
        ItemStack rest = stack.copy();
        int slots = Math.min(36, inv.getContainerSize());
        for (int i = 0; i < slots && !rest.isEmpty(); i++) {
            ItemStack slot = inv.getItem(i);
            if (slot.isEmpty() || !ItemStack.isSameItemSameTags(slot, rest)) {
                continue;
            }
            int move = Math.min(slot.getMaxStackSize() - slot.getCount(), rest.getCount());
            if (move <= 0) {
                continue;
            }
            rest.shrink(move);
            if (!simulate) {
                slot.grow(move);
                inv.setChanged();
            }
        }
        for (int i = 0; i < slots && !rest.isEmpty(); i++) {
            if (!inv.getItem(i).isEmpty()) {
                continue;
            }
            int move = Math.min(rest.getCount(), rest.getMaxStackSize());
            if (!simulate) {
                ItemStack put = rest.copy();
                put.setCount(move);
                inv.setItem(i, put);
                inv.setChanged();
            }
            rest.shrink(move);
        }
        return rest;
    }

    /** 背包里该物品（含 NBT 一致）的总数。 */
    private static int countOf(InvWrapper inv, ItemStack proto) {
        int n = 0;
        for (int i = 0; i < inv.getSlots(); i++) {
            ItemStack st = inv.getStackInSlot(i);
            if (!st.isEmpty() && ItemStack.isSameItemSameTags(st, proto)) {
                n += st.getCount();
            }
        }
        return n;
    }

    /** 两个样本表是不是同一批物品（顺序无关，只看种类）。 */
    private static boolean sameStacks(List<ItemStack> a, List<ItemStack> b) {
        if (a.size() != b.size()) {
            return false;
        }
        for (ItemStack x : a) {
            if (!containsSame(b, x)) {
                return false;
            }
        }
        return true;
    }

    private static boolean containsSame(List<ItemStack> list, ItemStack st) {
        for (ItemStack o : list) {
            if (ItemStack.isSameItemSameTags(o, st)) {
                return true;
            }
        }
        return false;
    }

    private static void feedbackRefill(ServerPlayer sp) {
        if (MaidGapConfig.SOUND_FEEDBACK.get()) {
            sp.playSound(SoundEvents.ITEM_PICKUP, 0.4f, 1.4f);
        }
    }

    // ------------------------------------------------------------------
    // 视觉与音效反馈（沿用原版隙间的传送风格）
    // ------------------------------------------------------------------

    private static void feedbackBind(ServerPlayer player, Level target, BlockPos pos) {
        if (MaidGapConfig.PARTICLE_FEEDBACK.get()) {
            if (player.level() instanceof ServerLevel sl) {
                sl.sendParticles(ParticleTypes.PORTAL, player.getX(), player.getY() + 1.0, player.getZ(),
                        24, 0.4, 0.6, 0.4, 0.1);
            }
            if (player.level() == target && pos.closerThan(player.blockPosition(), 32)) {
                ((ServerLevel) target).sendParticles(ParticleTypes.PORTAL,
                        pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 30, 0.4, 0.4, 0.4, 0.1);
            }
        }
        if (MaidGapConfig.SOUND_FEEDBACK.get()) {
            player.playSound(SoundEvents.ENDERMAN_TELEPORT, 0.6f, 1.3f);
        }
    }

    private static void feedbackUnbind(ServerPlayer player) {
        if (MaidGapConfig.PARTICLE_FEEDBACK.get() && player.level() instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.REVERSE_PORTAL, player.getX(), player.getY() + 1.0, player.getZ(),
                    20, 0.4, 0.6, 0.4, 0.1);
        }
        if (MaidGapConfig.SOUND_FEEDBACK.get()) {
            player.playSound(SoundEvents.ENDER_CHEST_CLOSE, 0.6f, 1.1f);
        }
    }

    private static void feedbackExtract(ServerPlayer player) {
        if (MaidGapConfig.PARTICLE_FEEDBACK.get() && player.level() instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 1.2, player.getZ(),
                    6, 0.3, 0.3, 0.3, 0.02);
        }
        if (MaidGapConfig.SOUND_FEEDBACK.get()) {
            player.playSound(SoundEvents.ITEM_PICKUP, 0.5f, 1.0f);
        }
    }

    // ------------------------------------------------------------------
    // 小工具
    // ------------------------------------------------------------------

    /**
     * 物品 NBT 变更后推一次容器同步，客户端手持物品的提示等立即更新。
     * 公开：终端链接/解链（{@link cn.blockforge.generated.maidbettergap.item.GapTerminalItem}、
     * {@link cn.blockforge.generated.maidbettergap.compat.tlm.MaidLinkService}）改完 NBT 也要调它，
     * 否则 tooltip 会一直停在“未链接”，直到开一次容器或重登。
     */
    public static void syncHeld(ServerPlayer sp) {
        if (sp != null && sp.containerMenu != null) {
            sp.containerMenu.broadcastChanges();
        }
    }

    private static String describe(BlockPos pos) {
        return pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
    }

    /** 方块的命名空间（拿不到注册名时返回空串，避免 NPE）。 */
    private static String namespaceOf(BlockState state) {
        ResourceLocation key = ForgeRegistries.BLOCKS.getKey(state.getBlock());
        return key == null ? "" : key.getNamespace();
    }

    /** 终端里点箱子物品失败时，把原因（区块未加载 / 超距 / 储罐不能拿物品…）提示出来。 */
    private static void statusMessage(ServerPlayer sp, int status) {
        String key = switch (status) {
            case TerminalSnapshot.STATUS_NOT_LOADED -> "maidgap.status.not_loaded";
            case TerminalSnapshot.STATUS_OUT_OF_RANGE -> "maidgap.status.out_of_range";
            case TerminalSnapshot.STATUS_NO_TARGET -> "maidgap.status.no_target";
            case TerminalSnapshot.STATUS_NOT_ALLOWED -> "maidgap.status.not_allowed";
            case TerminalSnapshot.STATUS_NO_CAPABILITY -> "maidgap.status.no_capability";
            default -> "maidgap.status.ok";
        };
        if (key.equals("maidgap.status.ok")) {
            return;
        }
        message(sp, key, ChatFormatting.RED, null);
    }

    private static void message(ServerPlayer player, String key, ChatFormatting color, Object arg) {
        MutableComponent c = arg == null ? Component.translatable(key) : Component.translatable(key, arg);
        player.displayClientMessage(c.withStyle(color), true);
    }
}
