package com.caszgamermd.orelaundering;

import com.mojang.serialization.MapCodec;
import org.jetbrains.annotations.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class OreMachineBlock extends BaseEntityBlock {
    public static final Property<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final IntegerProperty GROWTH = IntegerProperty.create("growth", 0, 3);

    private final OreMachines.Kind kind;

    public OreMachineBlock(OreMachines.Kind kind, Properties properties) {
        super(properties);
        this.kind = kind;
        registerDefaultState(stateDefinition.any()
            .setValue(FACING, Direction.NORTH).setValue(GROWTH, 0));
    }

    public OreMachines.Kind kind() { return kind; }
    public boolean grows() {
        return kind == OreMachines.Kind.WALL || kind == OreMachines.Kind.CEILING ||
               kind == OreMachines.Kind.TROUGH;
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return simpleCodec(properties -> new OreMachineBlock(kind, properties));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        builder.add(FACING, GROWTH);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction face = context.getClickedFace();
        if (kind == OreMachines.Kind.WALL && face.getAxis().isHorizontal()) {
            return defaultBlockState().setValue(FACING, face);
        }
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        if (kind == OreMachines.Kind.CEILING) {
            BlockPos above = pos.above();
            return level.getBlockState(above).isFaceSturdy(level, above, Direction.DOWN);
        }
        if (kind == OreMachines.Kind.WALL) {
            Direction back = state.getValue(FACING).getOpposite();
            BlockPos support = pos.relative(back);
            return level.getBlockState(support).isFaceSturdy(level, support, back.getOpposite());
        }
        return true;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        if (kind == OreMachines.Kind.CEILING) return box(1, 7, 1, 15, 16, 15);
        if (kind == OreMachines.Kind.TROUGH) return box(0, 0, 0, 16, 7, 16);
        if (kind == OreMachines.Kind.WALL) {
            return switch (state.getValue(FACING)) {
                case NORTH -> box(1, 1, 11, 15, 15, 16);
                case SOUTH -> box(1, 1, 0, 15, 15, 5);
                case WEST -> box(11, 1, 1, 16, 15, 15);
                default -> box(0, 1, 1, 5, 15, 15);
            };
        }
        return box(0, 0, 0, 16, 16, 16);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new OreMachineEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) return null;
        return createTickerHelper(type, OreMachines.BLOCK_ENTITY, OreMachineEntity::tick);
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level,
            BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof OreMachineEntity machine)) return InteractionResult.PASS;
        if (!machine.recognizes(stack)) return InteractionResult.TRY_WITH_EMPTY_HAND;
        if (!level.isClientSide()) machine.interactWithItem(stack, player);
        return InteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level,
            BlockPos pos, Player player, BlockHitResult hit) {
        if (level.getBlockEntity(pos) instanceof OreMachineEntity machine) {
            if (!level.isClientSide()) machine.interactEmpty(player);
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }
}
