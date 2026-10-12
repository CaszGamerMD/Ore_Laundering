package com.caszgamermd.orelaundering;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

public final class OreMachineEntity extends BlockEntity {
    private static final int BASE_GROWTH_TICKS = 6000; // 5 minutes
    private static final int GRINDER_TICKS = 40;
    private static final int VAT_CAPACITY = 16 * 9;    // 16 ingots
    private static final int TANK_CAPACITY = 64 * 9;   // 64 ingots

    private String ore = "";
    private int progress;
    private int speed;
    private int density;
    private boolean auto;
    private int grindingBits;
    private int queued;
    private int molten;
    private int lavaHeat;

    public OreMachineEntity(BlockPos pos, BlockState state) {
        super(OreMachines.BLOCK_ENTITY, pos, state);
    }

    private OreMachines.Kind kind() {
        return ((OreMachineBlock) getBlockState().getBlock()).kind();
    }

    private boolean grows() {
        return ((OreMachineBlock) getBlockState().getBlock()).grows();
    }

    public boolean recognizes(ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (grows()) {
            return stack.is(OreItems.GROWTH_ACCELERATOR) ||
                stack.is(OreItems.DENSITY_MATRIX) ||
                stack.is(OreItems.AUTO_HARVESTER) ||
                !OreCompatibility.fromOre(stack).isEmpty();
        }
        return switch (kind()) {
            case GRINDER -> stack.is(OreItems.GRINDING_BIT) ||
                stack.is(OreItems.COUNTERFEIT_ORE);
            case VAT -> stack.is(Items.LAVA_BUCKET) ||
                stack.is(OreItems.ORE_DUST) || isMold(stack);
            case TANK -> isMold(stack);
            default -> false;
        };
    }

    private static boolean isMold(ItemStack stack) {
        return stack.is(OreItems.NUGGET_MOLD) ||
            stack.is(OreItems.INGOT_MOLD) || stack.is(OreItems.BLOCK_MOLD);
    }

    private static void consume(Player player, ItemStack stack) {
        if (!player.getAbilities().instabuild) stack.shrink(1);
    }

    private static void notice(Player player, String message) {
        player.sendOverlayMessage(Component.literal(message));
    }

    private void drop(ItemStack stack) {
        if (level != null && !stack.isEmpty())
            Block.popResourceFromFace(level, worldPosition, Direction.UP, stack);
    }

    public void interactWithItem(ItemStack stack, Player player) {
        if (level == null) return;
        if (grows()) {
            if (stack.is(OreItems.GROWTH_ACCELERATOR)) {
                if (speed >= 3) { notice(player, "Growth speed: MAX"); return; }
                speed++;
                consume(player, stack);
                setChanged();
                notice(player, "Growth speed level " + speed + "/3");
            } else if (stack.is(OreItems.DENSITY_MATRIX)) {
                if (density >= 3) { notice(player, "Growth density: MAX"); return; }
                density++;
                consume(player, stack);
                setChanged();
                notice(player, "Growth density level " + density + "/3");
            } else if (stack.is(OreItems.AUTO_HARVESTER)) {
                if (auto) { notice(player, "Auto-harvester already installed"); return; }
                auto = true;
                consume(player, stack);
                setChanged();
                notice(player, "Auto-harvesting: ON (drops items on the ground)");
            } else {
                String source = OreCompatibility.fromOre(stack);
                if (source.isEmpty()) return;
                if (!ore.isEmpty()) {
                    notice(player, "Already growing " + OreCompatibility.displayName(ore));
                    return;
                }
                ore = source;
                progress = 0;
                consume(player, stack);
                setChanged();
                notice(player, "Planted " + OreCompatibility.displayName(ore) + " ore");
            }
            return;
        }

        if (kind() == OreMachines.Kind.GRINDER) {
            if (stack.is(OreItems.GRINDING_BIT)) {
                if (grindingBits >= 3) { notice(player, "Grinding bits: MAX"); return; }
                grindingBits++;
                consume(player, stack);
                setChanged();
                notice(player, "Precision grinder level " + grindingBits + "/3");
                return;
            }
            String source = OreItems.oreOf(stack);
            if (source.isEmpty() || (queued > 0 && !ore.equals(source)) || queued >= 64) {
                notice(player, "Grinder full, or wrong counterfeit ore");
                return;
            }
            ore = source;
            queued++;
            consume(player, stack);
            setChanged();
            notice(player, "Grinding " + queued + " counterfeit ore");
            return;
        }

        if (kind() == OreMachines.Kind.VAT) {
            if (stack.is(Items.LAVA_BUCKET)) {
                if (lavaHeat > 6000) { notice(player, "Vat already has enough lava heat"); return; }
                lavaHeat += 2000; // 20 dust smelts per bucket of lava
                if (!player.getAbilities().instabuild) {
                    stack.shrink(1);
                    if (!player.addItem(new ItemStack(Items.BUCKET)))
                        drop(new ItemStack(Items.BUCKET));
                }
                setChanged();
                notice(player, "Lava heat: " + lavaHeat + " / 8000");
                return;
            }
            if (stack.is(OreItems.ORE_DUST)) {
                String source = OreItems.oreOf(stack);
                if (source.isEmpty() || (!ore.isEmpty() && !ore.equals(source))) {
                    notice(player, "Cannot mix different molten ores");
                    return;
                }
                if (molten + 9 > VAT_CAPACITY || lavaHeat < 100) {
                    notice(player, "Vat full, or not enough lava heat");
                    return;
                }
                ore = source;
                molten += 9;
                lavaHeat -= 100;
                consume(player, stack);
                setChanged();
                notice(player, "Molten " + OreCompatibility.displayName(ore) + ": " + molten + " units");
                return;
            }
            if (isMold(stack)) cast(stack, player);
            return;
        }

        if (kind() == OreMachines.Kind.TANK && isMold(stack)) cast(stack, player);
    }

    private void cast(ItemStack mold, Player player) {
        int units = mold.is(OreItems.NUGGET_MOLD) ? 1
            : mold.is(OreItems.INGOT_MOLD) ? 9 : 81;
        if (molten < units) {
            notice(player, "Not enough molten ore (" + molten + " / " + units + " units)");
            return;
        }
        Item result = OreCompatibility.castingOutput(ore, units);
        if (result == Items.AIR) {
            notice(player, "No compatible cast item exists for this ore and mold");
            return;
        }
        molten -= units;
        drop(new ItemStack(result));
        if (molten == 0) ore = "";
        setChanged();
        notice(player, "Cast " + new ItemStack(result).getHoverName().getString());
    }

    public void interactEmpty(Player player) {
        if (level == null) return;
        if (grows()) {
            if (ore.isEmpty()) {
                notice(player, "Insert an ore block to plant a new culture");
                return;
            }
            if (progress >= growthTime()) harvest();
            else notice(player, OreCompatibility.displayName(ore) + ": " +
                (progress * 100 / growthTime()) + "% mature");
            return;
        }
        if (kind() == OreMachines.Kind.VAT && player.isShiftKeyDown()) {
            if (transferToTank(player)) return;
        }
        String description = switch (kind()) {
            case GRINDER -> "Grinder: " + queued + " ore queued; bits " + grindingBits + "/3";
            case VAT -> "Vat: " + molten + "/144 molten units; heat " + lavaHeat +
                ". Sneak-use to pour into adjacent tank";
            case TANK -> "Ore tank: " + molten + "/576 molten units";
            default -> "Ready";
        };
        notice(player, description);
    }

    private boolean transferToTank(Player player) {
        if (molten <= 0 || level == null) return false;
        for (Direction dir : Direction.values()) {
            if (!(level.getBlockEntity(worldPosition.relative(dir)) instanceof OreMachineEntity target)
                    || target.kind() != OreMachines.Kind.TANK) continue;
            if (!target.ore.isEmpty() && !target.ore.equals(ore)) {
                notice(player, "Tank contains a different metal");
                return true;
            }
            int moved = Math.min(molten, TANK_CAPACITY - target.molten);
            if (moved <= 0) { notice(player, "Tank is full"); return true; }
            target.ore = ore;
            target.molten += moved;
            molten -= moved;
            if (molten == 0) ore = "";
            target.setChanged();
            setChanged();
            notice(player, "Poured " + moved + " molten units into ore tank");
            return true;
        }
        notice(player, "Place an ore tank next to the vat to pour into it");
        return true;
    }

    private int growthTime() {
        return BASE_GROWTH_TICKS / (speed + 1);
    }

    private void harvest() {
        int count = (kind() == OreMachines.Kind.TROUGH ? 2 : 1) + density;
        drop(OreItems.materialStack(OreItems.COUNTERFEIT_ORE, ore, count));
        progress = 0;
        updateStage();
        setChanged();
    }

    private void updateStage() {
        if (level == null) return;
        int next = Math.min(3, progress * 4 / growthTime());
        BlockState state = getBlockState();
        if (state.getValue(OreMachineBlock.GROWTH) != next) {
            level.setBlock(worldPosition, state.setValue(OreMachineBlock.GROWTH, next), Block.UPDATE_ALL);
        }
    }

    public static void tick(Level level, BlockPos pos, BlockState state, OreMachineEntity machine) {
        if (level.isClientSide()) return;
        if (machine.grows() && !machine.ore.isEmpty()) {
            if (machine.progress < machine.growthTime()) {
                machine.progress++;
                if (machine.progress % 20 == 0) {
                    machine.updateStage();
                    machine.setChanged();
                }
            }
            if (machine.progress >= machine.growthTime() && machine.auto)
                machine.harvest(); // always drops on the ground
        } else if (machine.kind() == OreMachines.Kind.GRINDER && machine.queued > 0) {
            if (++machine.progress >= GRINDER_TICKS) {
                int dust = 1;
                for (int i = 0; i < machine.grindingBits; i++)
                    if (level.getRandom().nextFloat() < 0.25F) dust++;
                machine.drop(OreItems.materialStack(OreItems.ORE_DUST, machine.ore, dust));
                machine.progress = 0;
                if (--machine.queued == 0) machine.ore = "";
                machine.setChanged();
            }
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putString("ore", ore);
        output.putInt("progress", progress);
        output.putInt("speed", speed);
        output.putInt("density", density);
        output.putBoolean("auto", auto);
        output.putInt("grinding_bits", grindingBits);
        output.putInt("queued", queued);
        output.putInt("molten", molten);
        output.putInt("lava_heat", lavaHeat);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        ore = input.getStringOr("ore", "");
        progress = input.getIntOr("progress", 0);
        speed = Math.clamp(input.getIntOr("speed", 0), 0, 3);
        density = Math.clamp(input.getIntOr("density", 0), 0, 3);
        auto = input.getBooleanOr("auto", false);
        grindingBits = Math.clamp(input.getIntOr("grinding_bits", 0), 0, 3);
        queued = Math.max(0, input.getIntOr("queued", 0));
        molten = Math.max(0, input.getIntOr("molten", 0));
        lavaHeat = Math.max(0, input.getIntOr("lava_heat", 0));
    }
}
