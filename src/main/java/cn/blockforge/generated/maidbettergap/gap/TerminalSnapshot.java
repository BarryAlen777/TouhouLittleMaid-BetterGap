package cn.blockforge.generated.maidbettergap.gap;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import java.util.ArrayList;
import java.util.List;

/**
 * 终端界面快照：服务端一次性打包所有绑定容器的状态与内容，发给客户端展示。
 */
public final class TerminalSnapshot {

    public static final int STATUS_OK = 0;
    public static final int STATUS_NOT_LOADED = 1;
    public static final int STATUS_OUT_OF_RANGE = 2;
    public static final int STATUS_NO_TARGET = 3;
    public static final int STATUS_NOT_ALLOWED = 4;
    public static final int STATUS_NO_CAPABILITY = 5;

    public static final class Entry {
        public final Binding binding;
        public int status;
        public String blockId = "";
        /** 这个容器是不是用了自己的独立过滤名单（false = 沿用隙间的共用名单）。 */
        public boolean ownFilter;
        /** 这个容器实际生效的名单模式：true = 黑名单。 */
        public boolean blacklist;
        /** 这个容器实际生效的类别匹配开关。 */
        public boolean tagMatch;
        /** 这个容器实际生效的 9 格名单（可能含空堆）。 */
        public List<ItemStack> filters = new ArrayList<>();
        /** 非空物品的真实槽位号与内容（稀疏，节约流量）。 */
        public final List<Integer> slots = new ArrayList<>();
        public final List<ItemStack> stacks = new ArrayList<>();
        /** 储罐内容（只放非空罐，tankSlots 记录真实罐号）。 */
        public final List<Integer> tankSlots = new ArrayList<>();
        public final List<FluidStack> fluids = new ArrayList<>();
        public final List<Integer> capacities = new ArrayList<>();

        public Entry(Binding binding, int status) {
            this.binding = binding;
            this.status = status;
        }
    }

    /** 终端列表里的一只女仆。 */
    public static final class MaidEntry {
        public final String uuid;
        public String name = "";
        /** false = 不在已加载区块 / 已不是本人（界面显示“离线”）。 */
        public boolean online;
        /** 在线但饰品栏里没有隙间（界面显示“无隙间”）。 */
        public boolean hasGap;
        /** 女仆找到了、也活着，但已经不是这位玩家的（界面显示“非本人”，不显示她的任何物品）。 */
        public boolean notOwner;

        public MaidEntry(String uuid) {
            this.uuid = uuid == null ? "" : uuid;
        }
    }

    public final List<Entry> entries = new ArrayList<>();
    public int pickupAmount;
    /** 女仆槽位锁定原始数组（38 字节，原版格式），给槽位配置界面用。 */
    public byte[] slotLocks = new byte[GapItemData.ORIGINAL_SLOT_NUM];
    /** 槽位配置界面里展示的女仆背包内容（38 格：0-35 背包、36 主手、37 副手；女仆不在时全空）。 */
    public List<ItemStack> maidSlots = new ArrayList<>();
    public boolean autoRefill;
    public int maxContainers = 4;
    /** 手里是否拿着隙间物品（绑定/白名单都存在物品 NBT 上）。 */
    public boolean holdingGap;
    /** 女仆传输方向：true = 女仆->箱子。 */
    public boolean maidToChest;
    /** true = 黑名单，false = 白名单（共用名单的设置，容器有独立名单时看 Entry 里的）。 */
    public boolean blacklist;
    /** true = 名单条目按类别（物品标签）匹配（共用名单的设置）。 */
    public boolean tagMatch;
    /** 共用名单的 9 格内容。 */
    public List<ItemStack> filters = new ArrayList<>();
    /** true = 经由「隙间终端」远程设置女仆饰品栏里的隙间。 */
    public boolean viaTerminal;
    /** 链接的女仆展示名（女仆不在时用终端里存的名字）。 */
    public String maidName = "";
    /**
     * 这次正在设置的隙间物品的显示名（如「隙间·增强」）。
     * 拿着隙间本身打开界面时，标题就显示它——别再写死成「终端」（这是被玩家点出来的小毛病）。
     */
    public String gapName = "";
    /** 终端还没链接女仆 / 女仆不在已加载区块。 */
    public boolean linkMissing;
    /** 女仆找到了，但饰品栏里没有隙间。 */
    public boolean maidHasNoGap;
    /** 终端链接的女仆不是这位玩家的（不显示她的任何物品）。 */
    public boolean notOwner;
    /** 终端里存着的女仆列表（只有手里拿着终端时有内容）。 */
    public List<MaidEntry> maids = new ArrayList<>();
    /** 当前正在管理的那只女仆的 UUID 字符串（空 = 没有）。 */
    public String activeMaid = "";
    /** 终端最多能存多少只女仆（配置值，界面拿它显示上限）。 */
    public int maidLimit = 16;

    public TerminalSnapshot() {}

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(pickupAmount);
        buf.writeByteArray(slotLocks);
        buf.writeVarInt(maidSlots.size());
        for (ItemStack ms : maidSlots) {
            buf.writeItemStack(ms, false);
        }
        buf.writeBoolean(autoRefill);
        buf.writeVarInt(maxContainers);
        buf.writeBoolean(holdingGap);
        buf.writeBoolean(maidToChest);
        buf.writeBoolean(blacklist);
        buf.writeBoolean(tagMatch);
        buf.writeVarInt(filters.size());
        for (ItemStack f : filters) {
            buf.writeItemStack(f, false);
        }
        buf.writeVarInt(entries.size());
        for (Entry e : entries) {
            buf.writeUtf(e.binding.dimension());
            buf.writeInt(e.binding.pos().getX());
            buf.writeInt(e.binding.pos().getY());
            buf.writeInt(e.binding.pos().getZ());
            buf.writeByte(e.binding.kind());
            buf.writeVarInt(e.status);
            buf.writeUtf(e.blockId);
            buf.writeBoolean(e.ownFilter);
            buf.writeBoolean(e.blacklist);
            buf.writeBoolean(e.tagMatch);
            buf.writeVarInt(e.filters.size());
            for (ItemStack f : e.filters) {
                buf.writeItemStack(f, false);
            }
            buf.writeVarInt(e.stacks.size());
            for (int i = 0; i < e.stacks.size(); i++) {
                buf.writeVarInt(e.slots.get(i));
                buf.writeItemStack(e.stacks.get(i), false);
            }
            buf.writeVarInt(e.fluids.size());
            for (int i = 0; i < e.fluids.size(); i++) {
                buf.writeVarInt(e.tankSlots.get(i));
                e.fluids.get(i).writeToPacket(buf);
                buf.writeVarInt(e.capacities.get(i));
            }
        }
        buf.writeBoolean(viaTerminal);
        buf.writeUtf(maidName);
        buf.writeUtf(gapName);
        buf.writeBoolean(linkMissing);
        buf.writeBoolean(maidHasNoGap);
        buf.writeBoolean(notOwner);
        buf.writeVarInt(maids.size());
        for (MaidEntry m : maids) {
            buf.writeUtf(m.uuid);
            buf.writeUtf(m.name);
            buf.writeBoolean(m.online);
            buf.writeBoolean(m.hasGap);
            buf.writeBoolean(m.notOwner);
        }
        buf.writeUtf(activeMaid);
        buf.writeVarInt(maidLimit);
    }

    public static TerminalSnapshot decode(FriendlyByteBuf buf) {
        TerminalSnapshot s = new TerminalSnapshot();
        s.pickupAmount = buf.readVarInt();
        s.slotLocks = buf.readByteArray();
        int maidSlotCount = buf.readVarInt();
        for (int i = 0; i < maidSlotCount; i++) {
            s.maidSlots.add(buf.readItem());
        }
        s.autoRefill = buf.readBoolean();
        s.maxContainers = buf.readVarInt();
        s.holdingGap = buf.readBoolean();
        s.maidToChest = buf.readBoolean();
        s.blacklist = buf.readBoolean();
        s.tagMatch = buf.readBoolean();
        int filterCount = buf.readVarInt();
        for (int i = 0; i < filterCount; i++) {
            s.filters.add(buf.readItem());
        }
        int entryCount = buf.readVarInt();
        for (int i = 0; i < entryCount; i++) {
            String dim = buf.readUtf();
            int x = buf.readInt();
            int y = buf.readInt();
            int z = buf.readInt();
            byte kind = buf.readByte();
            int status = buf.readVarInt();
            Entry e = new Entry(new Binding(dim, new net.minecraft.core.BlockPos(x, y, z), kind), status);
            e.blockId = buf.readUtf();
            e.ownFilter = buf.readBoolean();
            e.blacklist = buf.readBoolean();
            e.tagMatch = buf.readBoolean();
            int ownFilterCount = buf.readVarInt();
            for (int j = 0; j < ownFilterCount; j++) {
                e.filters.add(buf.readItem());
            }
            int stackCount = buf.readVarInt();
            for (int j = 0; j < stackCount; j++) {
                e.slots.add(buf.readVarInt());
                e.stacks.add(buf.readItem());
            }
            int tankCount = buf.readVarInt();
            for (int j = 0; j < tankCount; j++) {
                e.tankSlots.add(buf.readVarInt());
                e.fluids.add(FluidStack.readFromPacket(buf));
                e.capacities.add(buf.readVarInt());
            }
            s.entries.add(e);
        }
        s.viaTerminal = buf.readBoolean();
        s.maidName = buf.readUtf();
        s.gapName = buf.readUtf();
        s.linkMissing = buf.readBoolean();
        s.maidHasNoGap = buf.readBoolean();
        s.notOwner = buf.readBoolean();
        int maidCount = buf.readVarInt();
        for (int i = 0; i < maidCount; i++) {
            MaidEntry m = new MaidEntry(buf.readUtf());
            m.name = buf.readUtf();
            m.online = buf.readBoolean();
            m.hasGap = buf.readBoolean();
            m.notOwner = buf.readBoolean();
            s.maids.add(m);
        }
        s.activeMaid = buf.readUtf();
        s.maidLimit = buf.readVarInt();
        return s;
    }
}
