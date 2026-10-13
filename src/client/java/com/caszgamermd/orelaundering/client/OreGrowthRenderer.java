package com.caszgamermd.orelaundering.client;

import com.caszgamermd.orelaundering.OreMachineBlock;
import com.caszgamermd.orelaundering.OreMachineEntity;
import com.caszgamermd.orelaundering.OreMachines;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
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
        state.molten = be.moltenMl();
        state.target = be.menuData.get(13);
        state.basin = be.castingBasin();
        state.spoutOpen = be.menuData.get(15) == 1;
        state.facing = be.getBlockState().getValue(OreMachineBlock.FACING);
        BlockState ore = be.grows() ? be.crystalBlock()
            : (be.kind() == OreMachines.Kind.CASTING || be.kind() == OreMachines.Kind.SPOUT
                ? Blocks.MAGMA_BLOCK.defaultBlockState() : Blocks.AIR.defaultBlockState());
        state.crystalSprite = be.grows() && !ore.isAir()
            ? Minecraft.getInstance().getModelManager().getBlockStateModelSet().getParticleMaterial(ore).sprite()
            : null;
        if (ore != state.cachedOre) {
            state.material.clear();
            if (!ore.isAir()) resolver.update(state.material, ore, BlockDisplayContext.create());
            state.cachedOre = ore;
        }
    }

    @Override public void submit(State state, PoseStack pose, SubmitNodeCollector collector,
            CameraRenderState cameraState) {
        if (state.kind == null) return;
        if (state.kind == OreMachines.Kind.CASTING) {
            if (state.molten == 0 || state.material.isEmpty()) return;
            pose.pushPose();
            float ratio = Math.min(1, (float)state.molten / (float)Math.max(100, state.basin ? 8100 : (state.target == 100 ? 100 : 900)));
            float width = state.basin ? .69f : .56f;
            float height = state.basin ? (.04f + ratio * .38f) : (.03f + ratio * .06f);
            pose.translate((1-width)/2f, state.basin ? .32f : .45f, (1-width)/2f);
            pose.scale(width,height,width);
            state.material.submit(pose,collector,state.lightCoords,OverlayTexture.NO_OVERLAY,
                EntityRenderState.NO_OUTLINE);
            pose.popPose();
            return;
        }
        if (state.kind == OreMachines.Kind.SPOUT) {
            if (!state.spoutOpen || state.material.isEmpty()) return;
            pose.pushPose();
            pose.translate(.46f,-.54f,.56f);
            pose.scale(.08f,.78f,.08f);
            state.material.submit(pose,collector,state.lightCoords,OverlayTexture.NO_OVERLAY,
                EntityRenderState.NO_OUTLINE);
            pose.popPose();
            return;
        }
        // Crystal meshes are NOT scaled ore cubes. Only the selected ore's
        // material sprite is used; the pointed faceted shard mesh is our own.
        if (state.stage <= 0 || state.crystalSprite == null
                || state.kind.ordinal() > OreMachines.Kind.TROUGH.ordinal()) return;
        final OreMachines.Kind growthKind = state.kind;
        final int growthStage = state.stage;
        final int light = state.lightCoords;
        final TextureAtlasSprite sprite = state.crystalSprite;
        pose.pushPose();
        if (growthKind == OreMachines.Kind.WALL) {
            // Align wall-crystal roots with the rotated backplate.
            pose.translate(.5,0,.5);
            pose.mulPose(Axis.YP.rotationDegrees(switch (state.facing) {
                case NORTH -> 180; case EAST -> 270; case SOUTH -> 0; default -> 90;
            }));
            pose.translate(-.5,0,-.5);
        }
        // submitCustomGeometry executes after submit() returns: capture immutable
        // stage/sprite/kind/light values and use only the supplied Pose snapshot.
        collector.submitCustomGeometry(pose,
            RenderTypes.entityCutout(TextureAtlas.LOCATION_BLOCKS),
            (snapshot, vertices) -> OreCrystalMesh.render(
                snapshot, vertices, sprite, light, growthKind, growthStage));
        pose.popPose();
    }

    public static final class State extends BlockEntityRenderState {
        public final BlockModelRenderState material = new BlockModelRenderState();
        public BlockState cachedOre = Blocks.AIR.defaultBlockState();
        public OreMachines.Kind kind;
        public int stage, molten, target;
        public boolean basin, spoutOpen;
        public Direction facing = Direction.NORTH;
        public TextureAtlasSprite crystalSprite;
    }
}
