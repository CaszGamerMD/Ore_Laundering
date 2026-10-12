package com.caszgamermd.orelaundering;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.Nullable;

public final class OreMachineEntity extends BlockEntity implements Container, MenuProvider {
    private static final int BASE_GROWTH_TICKS = 6000;
    private static final int GRINDER_TICKS = 40;
    private static final int VAT_CAPACITY = 144;
    private static final int TANK_CAPACITY = 576;
    private static final int LAVA_CAPACITY = 16000;
    private static final int LAVA_PER_DUST = 50;
    private static final int[] STAGE_MARKS = {5, 25, 66, 100};

    // 0: player-accessible input, 1: collected output, 2: non-extractable planted ore preview
    private final NonNullList<ItemStack> items = NonNullList.withSize(3, ItemStack.EMPTY);
    private String ore = "";
    private int progress, speed, density, grindingBits, queued, molten, lavaMb;
    private boolean auto;

    public OreMachineEntity(BlockPos pos, BlockState state) {
        super(OreMachines.BLOCK_ENTITY, pos, state);
    }

    public OreMachines.Kind kind() {
        return ((OreMachineBlock) getBlockState().getBlock()).kind();
    }

    public boolean grows() {
        return ((OreMachineBlock) getBlockState().getBlock()).grows();
    }

    public String plantedOre() {
        return ore;
    }

    public BlockState plantedOreBlock() {
        Identifier id = Identifier.tryParse(ore);
        if (id != null && BuiltInRegistries.ITEM.containsKey(id)) {
            Item item = BuiltInRegistries.ITEM.getValue(id);
            if (item instanceof BlockItem blockItem) return blockItem.getBlock().defaultBlockState();
        }
        return Blocks.AIR.defaultBlockState();
    }

    public int growthPercent() {
        return ore.isEmpty() ? 0 : Math.min(100, progress * 100 / growthTime());
    }

    public int growthStage() {
        int pct = growthPercent(), stage = 0;
        for (int threshold : STAGE_MARKS) if (pct >= threshold) stage++;
        return stage;
    }

    private int growthTime() {
        return Math.max(1, BASE_GROWTH_TICKS / (speed + 1));
    }

    private void refreshGrowthStage() {
        if (level == null || !grows()) return;
        BlockState s = getBlockState();
        int stage = growthStage();
        if (s.getValue(OreMachineBlock.GROWTH) != stage)
            level.setBlock(worldPosition, s.setValue(OreMachineBlock.GROWTH, stage), Block.UPDATE_ALL);
    }

    private void refreshPreview() {
        ItemStack sample = ItemStack.EMPTY;
        Identifier id = Identifier.tryParse(ore);
        if (!ore.isEmpty() && id != null && BuiltInRegistries.ITEM.containsKey(id)) {
            sample = new ItemStack(BuiltInRegistries.ITEM.getValue(id));
        }
        items.set(2, sample);
    }

    private void changed() {
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
    }

    public boolean recognizes(ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (grows()) return stack.is(OreItems.GROWTH_ACCELERATOR) ||
            stack.is(OreItems.DENSITY_MATRIX) || stack.is(OreItems.AUTO_HARVESTER) ||
            !OreCompatibility.fromOre(stack).isEmpty();
        return switch (kind()) {
            case GRINDER -> stack.is(OreItems.GRINDING_BIT) || stack.is(OreItems.COUNTERFEIT_ORE);
            case VAT, TANK -> stack.is(OreItems.ORE_DUST) || moldUnits(stack) > 0;
            case LAVA_TANK -> stack.is(Items.LAVA_BUCKET);
            default -> false;
        };
    }

    private static int moldUnits(ItemStack s) {
        return s.is(OreItems.NUGGET_MOLD) ? 1 : s.is(OreItems.INGOT_MOLD) ? 9
            : s.is(OreItems.BLOCK_MOLD) ? 81 : 0;
    }

    private static void notice(Player p, String message) {
        p.sendOverlayMessage(Component.literal(message));
    }

    private static void takeHand(Player p, ItemStack stack) {
        if (!p.getAbilities().instabuild) stack.shrink(1);
    }

    private void output(ItemStack stack, boolean worldOnly) {
        if (level == null || stack.isEmpty()) return;
        if (!worldOnly) {
            ItemStack existing = items.get(1);
            if (existing.isEmpty()) {
                items.set(1, stack.copy());
                changed();
                return;
            }
            if (ItemStack.isSameItemSameComponents(existing, stack) &&
                    existing.getCount() + stack.getCount() <= existing.getMaxStackSize()) {
                existing.grow(stack.getCount());
                changed();
                return;
            }
        }
        Block.popResourceFromFace(level, worldPosition, Direction.UP, stack);
    }

    private int lavaUnder() {
        if (level == null || kind() != OreMachines.Kind.VAT) return 0;
        if (level.getBlockEntity(worldPosition.below()) instanceof OreMachineEntity tank &&
                tank.kind() == OreMachines.Kind.LAVA_TANK) return tank.lavaMb;
        return 0;
    }

    private boolean consumeLava(int mb) {
        if (level == null || kind() != OreMachines.Kind.VAT) return false;
        if (!(level.getBlockEntity(worldPosition.below()) instanceof OreMachineEntity tank) ||
            tank.kind() != OreMachines.Kind.LAVA_TANK || tank.lavaMb < mb) return false;
        tank.lavaMb -= mb;
        tank.changed();
        return true;
    }

    private void seedOre(String src) {
        ore = src;
        progress = 0;
        refreshPreview();
        refreshGrowthStage();
        changed();
    }

    private boolean applyUpgrade(ItemStack stack) {
        if (grows()) {
            if (stack.is(OreItems.GROWTH_ACCELERATOR) && speed < 3) { speed++; changed(); return true; }
            if (stack.is(OreItems.DENSITY_MATRIX) && density < 3) { density++; changed(); return true; }
            if (stack.is(OreItems.AUTO_HARVESTER) && !auto) { auto = true; changed(); return true; }
        } else if (kind() == OreMachines.Kind.GRINDER &&
                    stack.is(OreItems.GRINDING_BIT) && grindingBits < 3) {
            grindingBits++;
            changed();
            return true;
        }
        return false;
    }

    public void interactWithItem(ItemStack stack, Player player) {
        if (level == null || stack.isEmpty()) return;
        if (applyUpgrade(stack)) {
            takeHand(player, stack);
            notice(player, "Upgrade installed");
            return;
        }
        if (grows() && ore.isEmpty()) {
            String src = OreCompatibility.fromOre(stack);
            if (!src.isEmpty()) {
                seedOre(src);
                takeHand(player, stack);
                notice(player, "Growing " + OreCompatibility.displayName(ore));
                return;
            }
        }
        if (kind() == OreMachines.Kind.GRINDER && stack.is(OreItems.COUNTERFEIT_ORE)) {
            String src = OreItems.oreOf(stack);
            if (!src.isEmpty() && queued < 64 && (ore.isEmpty() || ore.equals(src))) {
                ore = src;
                queued++;
                takeHand(player, stack);
                changed();
            }
            return;
        }
        if (kind() == OreMachines.Kind.LAVA_TANK && stack.is(Items.LAVA_BUCKET)) {
            if (lavaMb + 1000 > LAVA_CAPACITY) { notice(player, "Lava tank full"); return; }
            lavaMb += 1000;
            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
                if (!player.addItem(new ItemStack(Items.BUCKET))) output(new ItemStack(Items.BUCKET), true);
            }
            changed();
            notice(player, "Lava " + lavaMb + " / " + LAVA_CAPACITY + " mB");
            return;
        }
        if (kind() == OreMachines.Kind.VAT && stack.is(Items.LAVA_BUCKET)) {
            notice(player, "Install a Lava Tank directly underneath the vat");
            return;
        }
        if (kind() == OreMachines.Kind.VAT && stack.is(OreItems.ORE_DUST)) {
            String src = OreItems.oreOf(stack);
            if (!src.isEmpty() && (ore.isEmpty() || ore.equals(src))
                    && molten + 9 <= VAT_CAPACITY && consumeLava(LAVA_PER_DUST)) {
                ore = src; molten += 9;
                takeHand(player, stack); changed();
                notice(player, "Smelted " + OreCompatibility.displayName(ore) + " (molten " + molten + ")");
            } else notice(player, "Needs matching ore, room and a fueled Lava Tank below");
            return;
        }
        if (moldUnits(stack) > 0 && (kind() == OreMachines.Kind.VAT || kind() == OreMachines.Kind.TANK))
            cast(stack, player);
    }

    private boolean cast(ItemStack mold, @Nullable Player player) {
        int units = moldUnits(mold);
        if (units == 0 || molten < units || ore.isEmpty()) return false;
        Item result = OreCompatibility.castingOutput(ore, units);
        if (result == Items.AIR) {
            if (player != null) notice(player, "No compatible casting output");
            return false;
        }
        molten -= units;
        output(new ItemStack(result), false);
        if (molten == 0) ore = "";
        refreshPreview();
        changed();
        return true;
    }

    public void interactEmpty(Player player) {
        if (level == null) return;
        if (player.isShiftKeyDown()) {
            if (grows() && !ore.isEmpty() && growthPercent() >= 100) {
                harvest();
                notice(player, "Harvested " + OreCompatibility.displayName(ore));
                return;
            }
            if (kind() == OreMachines.Kind.VAT) {
                pourTank(player);
                return;
            }
        }
        player.openMenu(this);
    }

    private void pourTank(Player player) {
        if (level == null || molten <= 0) { notice(player, "Vat is empty"); return; }
        for (Direction direction : Direction.values()) {
            if (direction == Direction.DOWN) continue; // reserved for lava power
            if (!(level.getBlockEntity(worldPosition.relative(direction)) instanceof OreMachineEntity tank)
                    || tank.kind() != OreMachines.Kind.TANK) continue;
            if (!tank.ore.isEmpty() && !tank.ore.equals(ore)) {
                notice(player, "Ore tank contains a different material"); return;
            }
            int moved = Math.min(molten, TANK_CAPACITY - tank.molten);
            if (moved <= 0) { notice(player, "Ore tank full"); return; }
            tank.ore = ore;
            tank.molten += moved;
            molten -= moved;
            if (molten == 0) ore = "";
            tank.refreshPreview();
            refreshPreview();
            tank.changed();
            changed();
            notice(player, "Poured " + moved + " molten units"); return;
        }
        notice(player, "Put an Ore Tank beside the vat");
    }

    private void harvest() {
        output(OreItems.materialStack(OreItems.COUNTERFEIT_ORE, ore,
            (kind() == OreMachines.Kind.TROUGH ? 2 : 1) + density), true);
        progress = 0;
        refreshGrowthStage();
        changed();
    }

    private void processInput() {
        ItemStack input = items.get(0);
        if (input.isEmpty()) return;
        if (applyUpgrade(input)) { input.shrink(1); changed(); return; }
        if (grows()) {
            if (ore.isEmpty()) {
                String source = OreCompatibility.fromOre(input);
                if (!source.isEmpty()) { seedOre(source); input.shrink(1); changed(); }
            }
            return;
        }
        switch (kind()) {
            case GRINDER -> {
                String source = OreItems.oreOf(input);
                if (input.is(OreItems.COUNTERFEIT_ORE) && !source.isEmpty()
                        && queued < 64 && (ore.isEmpty() || ore.equals(source))) {
                    ore = source; queued++; input.shrink(1); changed();
                }
            }
            case VAT -> {
                String source = OreItems.oreOf(input);
                if (input.is(OreItems.ORE_DUST) && !source.isEmpty() &&
                       (ore.isEmpty() || ore.equals(source)) &&
                       molten + 9 <= VAT_CAPACITY && consumeLava(LAVA_PER_DUST)) {
                    ore = source; molten += 9; input.shrink(1); changed();
                } else if (moldUnits(input) > 0) cast(input, null);
            }
            case TANK -> { if (moldUnits(input) > 0) cast(input, null); }
            case LAVA_TANK -> {
                if (input.is(Items.LAVA_BUCKET) && lavaMb + 1000 <= LAVA_CAPACITY
                        && (items.get(1).isEmpty() ||
                        (items.get(1).is(Items.BUCKET) && items.get(1).getCount() < 64))) {
                    lavaMb += 1000;
                    input.shrink(1);
                    output(new ItemStack(Items.BUCKET), false);
                    changed();
                }
            }
            default -> {}
        }
    }

    public static void tick(Level level, BlockPos pos, BlockState state, OreMachineEntity be) {
        if (level.isClientSide()) return;
        if (level.getGameTime() % 10 == 0) be.processInput();
        if (be.grows() && !be.ore.isEmpty()) {
            if (be.progress < be.growthTime()) {
                be.progress++;
                if (be.progress % 20 == 0 || be.progress == be.growthTime()) {
                    be.refreshGrowthStage();
                    be.changed();
                }
            }
            if (be.progress >= be.growthTime() && be.auto) be.harvest();
        } else if (be.kind() == OreMachines.Kind.GRINDER && be.queued > 0) {
            if (++be.progress >= GRINDER_TICKS) {
                int count = 1;
                for (int i=0; i<be.grindingBits; i++)
                    if (level.getRandom().nextFloat() < 0.25f) count++;
                be.output(OreItems.materialStack(OreItems.ORE_DUST, be.ore, count), false);
                be.progress = 0;
                if (--be.queued == 0) be.ore = "";
                be.changed();
            }
        }
    }

    // The machine menu synchronizes all progress/capacity values through the vanilla data slot protocol.
    public final ContainerData menuData = new ContainerData() {
        @Override public int get(int i) { return switch (i) {
            case 0 -> growthPercent();
            case 1 -> speed;
            case 2 -> density;
            case 3 -> auto ? 1 : 0;
            case 4 -> grindingBits;
            case 5 -> queued;
            case 6 -> molten;
            case 7 -> lavaMb;
            case 8 -> kind().ordinal();
            case 9 -> kind() == OreMachines.Kind.LAVA_TANK ? LAVA_CAPACITY :
                        kind() == OreMachines.Kind.TANK ? TANK_CAPACITY : VAT_CAPACITY;
            case 10 -> lavaUnder();
            case 11 -> grows() && !ore.isEmpty() ? 1 : 0;
            default -> 0;
        }; }
        @Override public void set(int i, int value) {}
        @Override public int getCount() { return 12; }
    };

    @Override public Component getDisplayName() {
        return getBlockState().getBlock().getName();
    }

    @Override public @Nullable AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new OreMachineMenu(id, inventory, this, menuData);
    }

    @Override public int getContainerSize() { return 3; }
    @Override public boolean isEmpty() { return items.get(0).isEmpty() && items.get(1).isEmpty(); }
    @Override public ItemStack getItem(int index) { return items.get(index); }
    @Override public ItemStack removeItem(int slot, int amount) {
        if (slot == 2) return ItemStack.EMPTY;
        ItemStack out = ContainerHelper.removeItem(items, slot, amount);
        if (!out.isEmpty()) changed();
        return out;
    }
    @Override public ItemStack removeItemNoUpdate(int slot) {
        if (slot == 2) return ItemStack.EMPTY;
        return ContainerHelper.takeItem(items, slot);
    }
    @Override public void setItem(int slot, ItemStack stack) {
        if (slot == 2) return;
        items.set(slot, stack);
        if (stack.getCount() > getMaxStackSize()) stack.setCount(getMaxStackSize());
        changed();
    }
    @Override public boolean stillValid(Player player) {
        return Container.stillValidBlockEntity(this, player);
    }
    @Override public void clearContent() {
        items.set(0, ItemStack.EMPTY); items.set(1, ItemStack.EMPTY);
        changed();
    }
    @Override public boolean canPlaceItem(int slot, ItemStack stack) {
        return slot == 0 && recognizes(stack);
    }

    @Override protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, items);
        output.putString("ore", ore);
        output.putInt("progress", progress);
        output.putInt("speed", speed);
        output.putInt("density", density);
        output.putBoolean("auto", auto);
        output.putInt("grinding_bits", grindingBits);
        output.putInt("queued", queued);
        output.putInt("molten", molten);
        output.putInt("lava_mb", lavaMb);
    }
    @Override protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        ContainerHelper.loadAllItems(input, items);
        ore = input.getStringOr("ore", "");
        progress = input.getIntOr("progress", 0);
        speed = Math.clamp(input.getIntOr("speed", 0), 0, 3);
        density = Math.clamp(input.getIntOr("density", 0), 0, 3);
        auto = input.getBooleanOr("auto", false);
        grindingBits = Math.clamp(input.getIntOr("grinding_bits", 0), 0, 3);
        queued = Math.max(0, input.getIntOr("queued", 0));
        molten = Math.max(0, input.getIntOr("molten", 0));
        lavaMb = Math.max(0, input.getIntOr("lava_mb", 0));
        refreshPreview();
    }
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider lookup) {
        return saveWithoutMetadata(lookup);
    }
    @Override public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
