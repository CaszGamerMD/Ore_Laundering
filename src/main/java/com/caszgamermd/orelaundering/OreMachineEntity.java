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
    // All molten inventories are measured in millilitres: nugget 100, ingot 900, block 8100.
    private static final int VAT_CAPACITY = 14400;
    private static final int TANK_CAPACITY = 57600;
    private static final int POUR_RATE = 30; // mL per tick
    private static final int[] COOLING_TICKS = {30, 80, 160};
    private static final int LAVA_CAPACITY = 16000;
    private static final int LAVA_PER_DUST = 50;
    private static final int[] STAGE_MARKS = {5, 25, 66, 100};

    // 0: player-accessible input, 1: collected output, 2: non-extractable planted ore preview
    private final NonNullList<ItemStack> items = NonNullList.withSize(3, ItemStack.EMPTY);
    private String ore = "";
    private int progress, speed, density, grindingBits, queued, molten, lavaMb;
    private int cooling, castingRecipe; // poured volume is held in molten until output is collected
    private boolean auto;

    public OreMachineEntity(BlockPos pos, BlockState state) {
        super(OreMachines.BLOCK_ENTITY, pos, state);
        // A newly placed spout starts open; an empty vat still won't pour.
        this.auto = kind() == OreMachines.Kind.SPOUT;
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

    public int moltenMl() { return molten; }

    public int lavaMilliBuckets() { return lavaMb; }

    /**
     * A spout can empty into either a casting table or an open Ore Tank.
     * Receiver is straight below the nozzle (one block or two with air between).
     * Lava tanks are deliberately excluded: they are fuel, not molten storage.
     */
    private @Nullable OreMachineEntity pouringReceiver() {
        if (level == null || kind() != OreMachines.Kind.SPOUT) return null;
        for (int distance = 1; distance <= 2; distance++) {
            if (!(level.getBlockEntity(worldPosition.below(distance)) instanceof OreMachineEntity receiver))
                continue;
            OreMachines.Kind receiverKind = receiver.kind();
            if (receiverKind == OreMachines.Kind.CASTING || receiverKind == OreMachines.Kind.TANK) {
                if (distance == 1 || level.getBlockState(worldPosition.below()).isAir())
                    return receiver;
            }
        }
        return null;
    }

    /** Take molten material from the connected vat or storage tank. */
    private @Nullable OreMachineEntity moltenSource() {
        if (level == null || kind() != OreMachines.Kind.SPOUT) return null;
        Direction back = getBlockState().getValue(OreMachineBlock.FACING).getOpposite();
        if (level.getBlockEntity(worldPosition.relative(back)) instanceof OreMachineEntity source
                && (source.kind() == OreMachines.Kind.VAT || source.kind() == OreMachines.Kind.TANK)
                && source.molten > 0 && !source.ore.isEmpty()) return source;
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            if (direction == back) continue;
            if (level.getBlockEntity(worldPosition.relative(direction)) instanceof OreMachineEntity source
                    && (source.kind() == OreMachines.Kind.VAT || source.kind() == OreMachines.Kind.TANK)
                    && source.molten > 0 && !source.ore.isEmpty()) return source;
        }
        return null;
    }

    public int streamDropDistance() {
        OreMachineEntity receiver = pouringReceiver();
        return receiver == null ? 1 : Math.max(1, worldPosition.getY() - receiver.worldPosition.getY());
    }

    /**
     * A non-mutating test for both casting and storage. This mirrors receiveMolten
     * so the stream is only drawn while molten liquid can actually move.
     */
    private boolean canReceiveMolten(String sourceOre) {
        String material = OreCompatibility.canonical(sourceOre);
        if (material.isEmpty()) return false;
        if (kind() == OreMachines.Kind.TANK) {
            return molten < TANK_CAPACITY
                && (molten == 0 || OreCompatibility.canonical(ore).equals(material));
        }
        if (kind() == OreMachines.Kind.CASTING) {
            return cooling == 0 && items.get(1).isEmpty()
                && molten < castingTarget()
                && (molten == 0 || OreCompatibility.canonical(ore).equals(material))
                && OreCompatibility.castingOutput(material, castingTarget()) != Items.AIR;
        }
        return false;
    }

    /** True when the spout has a valid source and a receiver with room. */
    public String flowingOre() {
        if (kind() != OreMachines.Kind.SPOUT || !auto) return "";
        OreMachineEntity source = moltenSource();
        OreMachineEntity receiver = pouringReceiver();
        if (source == null || receiver == null || source == receiver) return "";
        String material = OreCompatibility.canonical(source.ore);
        return receiver.canReceiveMolten(material) ? material : "";
    }

    /** Synced display status: closed, no source, no receiver, mismatch, full/blocked, pouring. */
    private int spoutStatus() {
        if (kind() != OreMachines.Kind.SPOUT || !auto) return 0;
        OreMachineEntity source = moltenSource(), receiver = pouringReceiver();
        if (source == null) return 1;
        if (receiver == null || source == receiver) return 2;
        if (receiver.molten > 0 && !OreCompatibility.canonical(receiver.ore).equals(
                OreCompatibility.canonical(source.ore))) return 3;
        if (!receiver.canReceiveMolten(source.ore)) return 4;
        return 5;
    }

    public boolean castingBasin() {
        return kind() == OreMachines.Kind.CASTING && getBlockState().getValue(OreMachineBlock.GROWTH) == 1;
    }

    private int castingTarget() {
        if (castingBasin()) return 8100;
        return items.get(0).is(OreItems.NUGGET_MOLD) ? 100 : 900;
    }

    public boolean canChangeCastingMold() {
        return kind() != OreMachines.Kind.CASTING ||
            (molten == 0 && cooling == 0 && items.get(1).isEmpty());
    }

    private boolean castingEmpty() {
        // All five checks matter: no molten fluid, no cooling, no stored material,
        // no input mold, and no finished-but-uncollected product.
        return molten == 0 && cooling == 0 && ore.isEmpty()
            && items.get(0).isEmpty() && items.get(1).isEmpty();
    }

    public void toggleCastingMode(Player player) {
        if (kind() != OreMachines.Kind.CASTING || level == null) return;
        if (!castingEmpty()) {
            notice(player, "Empty the casting table and collect its output before switching");
            player.openMenu(this);
            return;
        }
        BlockState state = getBlockState();
        int newMode = castingBasin() ? 0 : 1;
        level.setBlock(worldPosition, state.setValue(OreMachineBlock.GROWTH, newMode), Block.UPDATE_ALL);
        changed();
        notice(player, newMode == 1 ? "Casting Basin: 8100 mL blocks" : "Casting Table: 900 mL ingots / 100 mL nuggets");
    }

    public void toggleSpout(Player player) {
        if (kind() != OreMachines.Kind.SPOUT) return;
        auto = !auto;
        changed();
        if (!auto) { notice(player,"Spout closed"); return; }
        OreMachineEntity source = moltenSource();
        OreMachineEntity receiver = pouringReceiver();
        if (source == null) notice(player, "Spout open: connect an Ore Vat or Ore Tank containing molten ore");
        else if (receiver == null) notice(player, "Spout open: place a Casting Table or Ore Tank 1-2 blocks below");
        else if (source == receiver) notice(player, "Spout cannot pour back into its source tank");
        else if (receiver.kind() == OreMachines.Kind.TANK && receiver.molten >= TANK_CAPACITY)
            notice(player, "Spout waiting: Ore Tank is full");
        else if (receiver.kind() == OreMachines.Kind.TANK && !receiver.canReceiveMolten(source.ore))
            notice(player, "Spout blocked: Ore Tank contains a different material");
        else if (receiver.kind() == OreMachines.Kind.CASTING && !receiver.canReceiveMolten(source.ore))
            notice(player, "Spout waiting: incompatible mold, cooling or uncollected output");
        else notice(player, receiver.kind() == OreMachines.Kind.TANK
            ? "Spout pouring into Ore Tank" : "Spout pouring into Casting Table");
    }

    /**
     * Move up to maxAmount of molten material into a storage tank or casting
     * mold. Never consumes source material here; the spout commits both sides
     * only after the destination has accepted the exact amount.
     */
    private int receiveMolten(String sourceOre, int maxAmount) {
        if (maxAmount <= 0 || !canReceiveMolten(sourceOre)) return 0;
        String material = OreCompatibility.canonical(sourceOre);
        if (kind() == OreMachines.Kind.TANK) {
            int accepted = Math.min(maxAmount, TANK_CAPACITY - molten);
            if (accepted <= 0) return 0;
            ore = material;
            molten += accepted;
            refreshPreview();
            changed();
            return accepted;
        }
        if (kind() == OreMachines.Kind.CASTING) {
            int target = castingTarget();
            int accepted = Math.min(maxAmount, target - molten);
            if (accepted <= 0) return 0;
            ore = material;
            molten += accepted;
            castingRecipe = target;
            refreshPreview();
            if (molten >= target)
                cooling = target == 8100 ? COOLING_TICKS[2]
                    : target == 900 ? COOLING_TICKS[1] : COOLING_TICKS[0];
            changed();
            return accepted;
        }
        return 0;
    }

    private void tickSpout() {
        if (level == null) return;
        // Re-orient old spouts and newly connected sources.
        if (level.getGameTime() % 20 == 0) {
            Direction back = getBlockState().getValue(OreMachineBlock.FACING).getOpposite();
            Direction supply = null;
            if (level.getBlockEntity(worldPosition.relative(back)) instanceof OreMachineEntity test
                    && (test.kind() == OreMachines.Kind.VAT || test.kind() == OreMachines.Kind.TANK)) {
                supply=back;
            } else {
                for (Direction direction:Direction.Plane.HORIZONTAL) {
                    if (level.getBlockEntity(worldPosition.relative(direction)) instanceof OreMachineEntity test
                            && (test.kind() == OreMachines.Kind.VAT || test.kind() == OreMachines.Kind.TANK)) {
                        supply=direction; break;
                    }
                }
            }
            if (supply != null && back != supply) {
                level.setBlock(worldPosition,getBlockState().setValue(OreMachineBlock.FACING,
                    supply.getOpposite()),Block.UPDATE_ALL);
            }
        }
        if (!auto) return;
        OreMachineEntity source = moltenSource();
        OreMachineEntity receiver = pouringReceiver();
        if (source == null || receiver == null || source == receiver) return;
        // Transfer the exact accepted mL; leftovers stay in the vat/tank.
        // An Ore Tank can both receive from one spout and supply a second.
        String material = OreCompatibility.canonical(source.ore);
        int moved = receiver.receiveMolten(material, Math.min(POUR_RATE, source.molten));
        if (moved <= 0) return;
        source.molten -= moved;
        if (source.molten == 0) source.ore = "";
        source.refreshPreview();
        source.changed();
        // A full tank pauses; it never spills or mixes materials.
        // When room is freed, the still-open spout can continue pouring.
    }

    private void tickCasting() {
        if (kind() != OreMachines.Kind.CASTING || cooling <= 0 || level == null) return;
        cooling--;
        if (cooling == 0) {
            Item result = OreCompatibility.castingOutput(ore, castingRecipe);
            if (result != Items.AIR && items.get(1).isEmpty()) {
                items.set(1, new ItemStack(result));
                changed();
            }
        } else if (cooling % 10 == 0) changed();
    }

    public BlockState crystalBlock() {
        if (!grows() || ore.isEmpty()) return Blocks.AIR.defaultBlockState();
        Item item = OreCompatibility.castingOutput(ore, 8100);
        if (item instanceof BlockItem blockItem) return blockItem.getBlock().defaultBlockState();
        return plantedOreBlock();
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
            case VAT -> stack.is(OreItems.ORE_DUST);
            case TANK -> false;
            case CASTING -> stack.is(OreItems.NUGGET_MOLD) || stack.is(OreItems.INGOT_MOLD);
            case SPOUT -> false;
            case LAVA_TANK -> stack.is(Items.LAVA_BUCKET);
            default -> false;
        };
    }

    private static int moldUnits(ItemStack s) {
        return s.is(OreItems.NUGGET_MOLD) ? 100 : s.is(OreItems.INGOT_MOLD) ? 900
            : s.is(OreItems.BLOCK_MOLD) ? 8100 : 0;
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
        Direction dropFace = kind() == OreMachines.Kind.CEILING ? Direction.DOWN
            : kind() == OreMachines.Kind.WALL ? getBlockState().getValue(OreMachineBlock.FACING)
            : Direction.UP;
        Block.popResourceFromFace(level, worldPosition, dropFace, stack);
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
        ore = OreCompatibility.canonical(src);
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
            String src = OreCompatibility.fromOre(stack, level, worldPosition);
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
            if (!src.isEmpty() && (ore.isEmpty() || ore.equals(src)) && molten <= VAT_CAPACITY-600
                    && consumeLava(LAVA_PER_DUST)) {
                int yieldMl = 300 + level.getRandom().nextInt(301);
                ore = src;
                molten += yieldMl;
                takeHand(player, stack);
                refreshPreview();
                changed();
                notice(player, "Smelted " + yieldMl + " mL of " + OreCompatibility.displayName(ore));
            } else notice(player, "Needs matching ore, room and a fueled Lava Tank below");
            return;
        }
        if (kind() == OreMachines.Kind.CASTING && (stack.is(OreItems.NUGGET_MOLD) || stack.is(OreItems.INGOT_MOLD))) {
            notice(player, "Put the mold in the input slot (only before filling)");
        }
    }

    public void interactEmpty(Player player) {
        if (level == null) return;
        if (kind() == OreMachines.Kind.CASTING) {
            if (player.isShiftKeyDown() || !player.getMainHandItem().isEmpty()) player.openMenu(this);
            else toggleCastingMode(player);
            return;
        }
        if (kind() == OreMachines.Kind.SPOUT) {
            if (player.isShiftKeyDown()) player.openMenu(this);
            else toggleSpout(player);
            return;
        }
        // Mature crystals harvest with a regular empty-hand right click.
        // Sneak-right-click is reserved for opening the machine GUI.
        if (grows() && !player.isShiftKeyDown() && !ore.isEmpty() && growthPercent() >= 100) {
            String harvestedOre = ore;
            harvest(player);
            notice(player, "Harvested " + OreCompatibility.displayName(harvestedOre));
            return;
        }
        if (player.isShiftKeyDown() && kind() == OreMachines.Kind.VAT) {
            pourTank(player);
            return;
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

    private void harvest(@Nullable Player harvester) {
        ItemStack reward = OreItems.materialStack(OreItems.COUNTERFEIT_ORE, ore,
            (kind() == OreMachines.Kind.TROUGH ? 2 : 1) + density);
        if (harvester != null) {
            // Manual harvesting gives the counterfeit ore straight to the player.
            // Only overflow falls into the world.
            harvester.addItem(reward);
        }
        if (!reward.isEmpty()) output(reward, true);
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
                String source = OreCompatibility.fromOre(input, level, worldPosition);
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
                       molten <= VAT_CAPACITY-600 && consumeLava(LAVA_PER_DUST)) {
                    ore = source; molten += 300 + level.getRandom().nextInt(301);
                    input.shrink(1); refreshPreview(); changed();
                }
            }
            case CASTING, TANK, SPOUT -> { }
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
        if (be.kind() == OreMachines.Kind.SPOUT) be.tickSpout();
        if (be.kind() == OreMachines.Kind.CASTING) be.tickCasting();
        if (be.grows() && !be.ore.isEmpty()) {
            if (be.progress < be.growthTime()) {
                be.progress++;
                if (be.progress % 20 == 0 || be.progress == be.growthTime()) {
                    be.refreshGrowthStage();
                    be.changed();
                }
            }
            if (be.progress >= be.growthTime() && be.auto) be.harvest(null);
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
            case 12 -> cooling;
            case 13 -> castingRecipe;
            case 14 -> castingBasin() ? 1 : 0;
            case 15 -> kind() == OreMachines.Kind.SPOUT && auto ? 1 : 0;
            case 16 -> kind() == OreMachines.Kind.SPOUT && moltenSource() != null
                ? moltenSource().molten : 0;
            case 17 -> kind() == OreMachines.Kind.SPOUT && pouringReceiver() != null
                ? pouringReceiver().molten : 0;
            case 18 -> kind() != OreMachines.Kind.SPOUT || pouringReceiver() == null ? 0
                : pouringReceiver().kind() == OreMachines.Kind.TANK ? TANK_CAPACITY
                : pouringReceiver().castingTarget();
            case 19 -> kind() != OreMachines.Kind.SPOUT || pouringReceiver() == null ? 0
                : pouringReceiver().kind() == OreMachines.Kind.TANK ? 1 : 2;
            case 20 -> spoutStatus();
            default -> 0;
        }; }
        @Override public void set(int i, int value) {}
        @Override public int getCount() { return 21; }
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
        if (slot == 2 || (slot == 0 && !canChangeCastingMold())) return ItemStack.EMPTY;
        ItemStack out = ContainerHelper.removeItem(items, slot, amount);
        if (!out.isEmpty()) {
            finishCastingIfCollected(slot);
            changed();
        }
        return out;
    }
    private void finishCastingIfCollected(int slot) {
        if (kind() == OreMachines.Kind.CASTING && slot == 1 && items.get(1).isEmpty()
                && molten > 0 && cooling == 0) {
            molten = 0;
            castingRecipe = 0;
            ore = "";
            refreshPreview();
        }
    }
    @Override public ItemStack removeItemNoUpdate(int slot) {
        if (slot == 2 || (slot == 0 && !canChangeCastingMold())) return ItemStack.EMPTY;
        ItemStack out = ContainerHelper.takeItem(items, slot);
        if (!out.isEmpty()) { finishCastingIfCollected(slot); changed(); }
        return out;
    }
    @Override public void setItem(int slot, ItemStack stack) {
        if (slot == 2) return;
        if (slot == 0 && !canChangeCastingMold()) return;
        items.set(slot, stack);
        if (stack.getCount() > getMaxStackSize()) stack.setCount(getMaxStackSize());
        finishCastingIfCollected(slot);
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
        return slot == 0 && recognizes(stack) &&
            canChangeCastingMold();
    }

    @Override protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, items);
        output.putString("ore", ore);
        output.putInt("progress", progress);
        output.putInt("speed", speed);
        output.putInt("density", density);
        output.putBoolean("auto", auto);
        if (kind() == OreMachines.Kind.SPOUT) output.putBoolean("spout_v2", true);
        output.putInt("grinding_bits", grindingBits);
        output.putInt("queued", queued);
        output.putInt("molten", molten);
        output.putInt("lava_mb", lavaMb);
        output.putInt("cooling", cooling);
        output.putInt("casting_recipe", castingRecipe);
    }
    @Override protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        ContainerHelper.loadAllItems(input, items);
        ore = OreCompatibility.canonical(input.getStringOr("ore", ""));
        progress = input.getIntOr("progress", 0);
        speed = Math.clamp(input.getIntOr("speed", 0), 0, 3);
        density = Math.clamp(input.getIntOr("density", 0), 0, 3);
        auto = input.getBooleanOr("auto", kind() == OreMachines.Kind.SPOUT);
        // Previous versions forcibly closed the spout after a single cast.
        // Re-open older spouts once when migrating their persistent data.
        if (kind() == OreMachines.Kind.SPOUT && !input.getBooleanOr("spout_v2",false)) auto=true;
        grindingBits = Math.clamp(input.getIntOr("grinding_bits", 0), 0, 3);
        queued = Math.max(0, input.getIntOr("queued", 0));
        molten = Math.max(0, input.getIntOr("molten", 0));
        lavaMb = Math.max(0, input.getIntOr("lava_mb", 0));
        cooling = Math.max(0, input.getIntOr("cooling", 0));
        castingRecipe = Math.max(0, input.getIntOr("casting_recipe", 0));
        refreshPreview();
    }
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider lookup) {
        return saveWithoutMetadata(lookup);
    }
    @Override public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
