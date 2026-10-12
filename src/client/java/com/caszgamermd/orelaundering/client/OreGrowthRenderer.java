package com.caszgamermd.orelaundering.client;

import com.caszgamermd.orelaundering.OreMachineBlock;
import com.caszgamermd.orelaundering.OreMachineEntity;
import com.caszgamermd.orelaundering.OreMachines;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.BlockModelRenderState;
import net.minecraft.client.renderer.block.BlockModelResolver;
import net.minecraft.client.renderer.block.model.BlockDisplayContext;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public final class OreGrowthRenderer implements BlockEntityRenderer<OreMachineEntity, OreGrowthRenderer.State> {
    private final BlockModelResolver resolver;
    public OreGrowthRenderer(BlockEntityRendererProvider.Context context) {
        this.resolver = context.blockModelResolver();
    }
    @Override public State createRenderState() { return new State(); }

    @Override public void extractRenderState(OreMachineEntity be, State state, float tickDelta,
            Vec3 cameraPos, ModelFeatureRenderer.@Nullable CrumblingOverlay crumbling) {
        BlockEntityRenderer.super.extractRenderState(be, state, tickDelta, cameraPos, crumbling);
        state.kind = be.kind();
        state.stage = be.growthStage();
        state.facing = be.getBlockState().getValue(OreMachineBlock.FACING);
        BlockState ore = be.grows() ? be.plantedOreBlock() : Blocks.AIR.defaultBlockState();
        if (ore != state.cachedOre) {
            state.material.clear();
            if (!ore.isAir()) resolver.update(state.material, ore, BlockDisplayContext.create());
            state.cachedOre = ore;
        }
    }

    @Override public void submit(State state, PoseStack pose, SubmitNodeCollector collector,
            CameraRenderState cameraState) {
        if (state.stage <= 0 || state.material.isEmpty() || state.kind == null ||
                state.kind.ordinal() > OreMachines.Kind.TROUGH.ordinal()) return;
        pose.pushPose();
        if (state.kind == OreMachines.Kind.WALL) {
            pose.translate(.5,0,.5);
            pose.mulPose(Axis.YP.rotationDegrees(switch (state.facing) {
                case NORTH -> 180; case EAST -> 270; case SOUTH -> 0; default -> 90;
            }));
            pose.translate(-.5,0,-.5);
        }
        // Each discrete stage increases coverage of clusters, with no intersecting
        // geometry or coplanar surfaces. The actual modded/vanilla block model is used.
        int count = switch (state.stage) { case 1 -> 1; case 2 -> 3; case 3 -> 6; default -> 9; };
        float size = switch (state.stage) { case 1 -> .13f; case 2 -> .19f; case 3 -> .22f; default -> .23f; };
        for (int i=0;i<count;i++) {
            int x = i % 3, row = i / 3;
            float cx=.20f+x*.30f;
            float cy, cz;
            if (state.kind == OreMachines.Kind.WALL) {
                cy=.20f+row*.28f; cz=.47f;
            } else if (state.kind == OreMachines.Kind.CEILING) {
                cy=.59f+Math.min(row,2)*.055f; cz=.20f+row*.30f;
            } else {
                cy=.28f+Math.min(row,2)*.05f; cz=.20f+row*.30f;
            }
            pose.pushPose();
            pose.translate(cx-size/2f,cy,cz-size/2f);
            pose.scale(size,size,size);
            state.material.submit(pose,collector,state.lightCoords,OverlayTexture.NO_OVERLAY,
                EntityRenderState.NO_OUTLINE);
            pose.popPose();
        }
        pose.popPose();
    }

    public static final class State extends BlockEntityRenderState {
        public final BlockModelRenderState material = new BlockModelRenderState();
        public BlockState cachedOre = Blocks.AIR.defaultBlockState();
        public OreMachines.Kind kind;
        public int stage;
        public Direction facing = Direction.NORTH;
    }
}
