package com.caszgamermd.orelaundering.client;

import com.caszgamermd.orelaundering.OreLaundering;
import com.caszgamermd.orelaundering.OreMachineBlock;
import com.caszgamermd.orelaundering.OreMachineEntity;
import com.caszgamermd.orelaundering.OreMachines;
import com.caszgamermd.orelaundering.OreItems;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.data.AtlasIds;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Client-only machine rendering: faceted ore crystals, inset translucent tank
 * liquids, molten vat/casting pools and a real pouring stream.
 */
public final class OreGrowthRenderer implements BlockEntityRenderer<OreMachineEntity,OreGrowthRenderer.State> {
    private static final Identifier MOLTEN_SPRITE = OreLaundering.id("block/molten_surface");
    private static final Identifier LAVA_SPRITE = Identifier.withDefaultNamespace("block/lava_still");

    public OreGrowthRenderer(BlockEntityRendererProvider.Context context) { }

    @Override public State createRenderState() { return new State(); }

    @Override public void extractRenderState(OreMachineEntity be,State state,float tickDelta,
            Vec3 cameraPos,ModelFeatureRenderer.@Nullable CrumblingOverlay crumbling) {
        BlockEntityRenderer.super.extractRenderState(be,state,tickDelta,cameraPos,crumbling);
        state.kind=be.kind();
        state.stage=be.growthStage();
        state.facing=be.getBlockState().getValue(OreMachineBlock.FACING);
        state.basin=be.castingBasin();
        state.fill=be.kind()==OreMachines.Kind.LAVA_TANK ? be.lavaMilliBuckets() : be.moltenMl();
        state.capacity=switch(be.kind()) {
            case LAVA_TANK -> 16000;
            case TANK -> 57600;
            case VAT -> 14400;
            case CASTING -> state.basin ? 8100 :
                be.getItem(0).is(OreItems.NUGGET_MOLD) || be.menuData.get(13)==100 ? 100 : 900;
            default -> 0;
        };
        state.age=be.getLevel()==null ? 0 : be.getLevel().getGameTime()+tickDelta;
        TextureAtlas blockAtlas=Minecraft.getInstance().getAtlasManager().getAtlasOrThrow(AtlasIds.BLOCKS);
        if(be.grows()) {
            BlockState ore=be.crystalBlock();
            state.crystalSprite=ore.isAir() ? null :
                Minecraft.getInstance().getModelManager().getBlockStateModelSet().getParticleMaterial(ore).sprite();
        } else {
            state.crystalSprite=null;
        }
        if (be.kind()==OreMachines.Kind.SPOUT) {
            state.streamDistance=be.streamDropDistance();
            state.streamOre=be.flowingOre();
        } else {
            state.streamOre="";
        }
        boolean lava=be.kind()==OreMachines.Kind.LAVA_TANK;
        state.fluidSprite=blockAtlas.getSprite(lava ? LAVA_SPRITE : MOLTEN_SPRITE);
        // Resource packs may remove a custom atlas entry: degrade to a known
        // vanilla fluid sprite rather than exposing a magenta checkerboard.
        if(state.fluidSprite==null || !(lava?LAVA_SPRITE:MOLTEN_SPRITE).equals(state.fluidSprite.contents().name()))
            // Lava as a molten fallback overwrites the real material color.
            // Use neutral white instead so every metal retains its sampled tint.
            state.fluidSprite=blockAtlas.getSprite(lava ? LAVA_SPRITE :
                Identifier.withDefaultNamespace("block/white_concrete"));
        state.tint=lava ? 0xffffffff : OreFluidTint.fromMaterial(
            be.kind()==OreMachines.Kind.SPOUT ? state.streamOre : be.plantedOre());
    }

    @Override public void submit(State state,PoseStack pose,SubmitNodeCollector collector,
            CameraRenderState cameraState) {
        if(state.kind==null)return;
        if(state.kind==OreMachines.Kind.SPOUT) {
            if(state.streamOre.isEmpty()) return;
            final TextureAtlasSprite sprite=state.fluidSprite;
            final int tint=state.tint,light=state.lightCoords,drop=state.streamDistance;
            final float age=state.age;
            collector.submitCustomGeometry(pose,RenderTypes.entityTranslucent(TextureAtlas.LOCATION_BLOCKS),
                (snap,out)->OreFluidMesh.drawStream(snap,out,sprite,light,tint,age,drop));
            return;
        }
        if(state.kind==OreMachines.Kind.LAVA_TANK || state.kind==OreMachines.Kind.TANK
                || state.kind==OreMachines.Kind.VAT || state.kind==OreMachines.Kind.CASTING) {
            if(state.fill<=0)return;
            final TextureAtlasSprite sprite=state.fluidSprite;
            final OreMachines.Kind kind=state.kind;
            final int light=state.lightCoords,fill=state.fill,capacity=state.capacity,tint=state.tint;
            final boolean basin=state.basin;
            final float age=state.age;
            collector.submitCustomGeometry(pose,RenderTypes.entityTranslucent(TextureAtlas.LOCATION_BLOCKS),
                (snap,out)->OreFluidMesh.drawPool(snap,out,sprite,light,tint,kind,basin,fill,capacity,age));
            return;
        }
        // Crystal geometry has its own pointed faces and uses only the ore's
        // sprite, never the source block's cubic geometry.
        if(state.stage<=0 || state.crystalSprite==null
                || state.kind.ordinal()>OreMachines.Kind.TROUGH.ordinal()) return;
        final OreMachines.Kind growthKind=state.kind;
        final TextureAtlasSprite crystal=state.crystalSprite;
        final int stage=state.stage,light=state.lightCoords;
        pose.pushPose();
        if(growthKind==OreMachines.Kind.WALL) {
            pose.translate(.5,0,.5);
            pose.mulPose(Axis.YP.rotationDegrees(switch(state.facing) {
                case NORTH -> 180; case EAST -> 270; case SOUTH -> 0; default -> 90;
            }));
            pose.translate(-.5,0,-.5);
        }
        collector.submitCustomGeometry(pose,RenderTypes.entityCutout(TextureAtlas.LOCATION_BLOCKS),
            (snap,out)->OreCrystalMesh.render(snap,out,crystal,light,growthKind,stage));
        pose.popPose();
    }

    public static final class State extends BlockEntityRenderState {
        OreMachines.Kind kind;
        Direction facing=Direction.NORTH;
        TextureAtlasSprite fluidSprite,crystalSprite;
        String streamOre="";
        int stage,fill,capacity,tint,streamDistance;
        boolean basin;
        float age;
    }
}
