package com.caszgamermd.orelaundering.client;

import com.caszgamermd.orelaundering.OreMachines;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;

/**
 * Only draws the dynamic fluid itself. All machine shells remain baked block models.
 * Eight discrete tank/vat heights and a continuously filling casting table are drawn
 * clear of their frames, eliminating solid-liquid coplanar z-fighting.
 */
final class OreFluidMesh {
    private OreFluidMesh() {}
    private record Point(float x,float y,float z) {}

    static void drawPool(PoseStack.Pose pose, VertexConsumer output, TextureAtlasSprite sprite,
                         int light, int tint, OreMachines.Kind kind, boolean basin,
                         int amount, int capacity, float age) {
        if (sprite == null || amount <= 0 || capacity <= 0) return;
        float fraction = Math.min(1f,(float)amount / capacity);
        // Steps are intentional, so lava/ore tanks have eight easily recognized
        // liquid heights; the table/basin fills smoothly while the faucet is on.
        if (kind == OreMachines.Kind.LAVA_TANK || kind == OreMachines.Kind.TANK
             || kind == OreMachines.Kind.VAT) {
            fraction = (float)Math.ceil(fraction * 8f) / 8f;
        }
        float x0,x1,z0,z1,y0,maxY;
        if (kind == OreMachines.Kind.VAT) {
            x0=3.18f/16f; x1=12.82f/16f;
            z0=3.18f/16f; z1=12.82f/16f;
            y0=5.08f/16f; maxY=10.65f/16f;
        } else if (kind == OreMachines.Kind.CASTING) {
            if (basin) {
                x0=2.20f/16f; x1=13.80f/16f;
                z0=2.20f/16f; z1=13.80f/16f;
                y0=5.08f/16f; maxY=10.65f/16f;
            } else {
                x0=3.23f/16f; x1=12.77f/16f;
                z0=3.23f/16f; z1=12.77f/16f;
                y0=7.08f/16f; maxY=8.70f/16f;
            }
        } else {
            x0=2.34f/16f; x1=13.66f/16f;
            z0=2.34f/16f; z1=13.66f/16f;
            y0=2.69f/16f; maxY=13.64f/16f;
        }

        float height = Math.max(.009f, fraction * (maxY-y0));
        float top = y0 + height;
        // Small repeating waves only on upper faces. They never reach the metal
        // lip and share their upper edge vertices with the four liquid sides.
        float wiggle = Math.min(.003f, height*.08f);
        float waveA=(float)Math.sin(age*.10f)*wiggle;
        float waveB=(float)Math.sin(age*.10f+1.7f)*wiggle;
        float waveC=(float)Math.sin(age*.10f+3.1f)*wiggle;
        float waveD=(float)Math.sin(age*.10f+4.8f)*wiggle;
        Point a = new Point(x0,top+waveA,z0);
        Point b = new Point(x1,top+waveB,z0);
        Point c = new Point(x1,top+waveC,z1);
        Point d = new Point(x0,top+waveD,z1);
        int bright=light | 0x00B000B0;
        // Top upward normal, standard atlas UVs with a moving internal offset.
        float offset = .035f*(float)Math.sin(age*.035f);
        quad(pose,output,sprite,bright,tint,1.08f,238,
            a,d,c,b,offset,offset,.91f+offset,.91f+offset,0,1,0);
        // Front / back / left / right fill walls; no external face aligns with metal.
        quad(pose,output,sprite,bright,tint,.77f,246,
            new Point(x0,y0,z0),a,b,new Point(x1,y0,z0),
            .04f,1f-fraction,.96f,1f,0,0,-1);
        quad(pose,output,sprite,bright,tint,.91f,246,
            new Point(x1,y0,z1),c,d,new Point(x0,y0,z1),
            .04f,1f-fraction,.96f,1f,0,0,1);
        quad(pose,output,sprite,bright,tint,.82f,246,
            new Point(x0,y0,z1),d,a,new Point(x0,y0,z0),
            .04f,1f-fraction,.96f,1f,-1,0,0);
        quad(pose,output,sprite,bright,tint,.94f,246,
            new Point(x1,y0,z0),b,c,new Point(x1,y0,z1),
            .04f,1f-fraction,.96f,1f,1,0,0);
    }

    static void drawStream(PoseStack.Pose pose, VertexConsumer output,
                           TextureAtlasSprite sprite, int light, int tint, float age, int dropDistance) {
        if (sprite==null) return;
        // Top meets the end of the compact spout nozzle (1.55px), bottom sits
        // inside the casting table; no two surfaces share the exact same plane.
        float lo=dropDistance >= 2 ? -1.48f : -.48f, hi=.128f;
        float cx=.5f, cz=.515f;
        float radius=.039f+ .004f*(float)Math.sin(age*.14f);
        float x0=cx-radius,x1=cx+radius,z0=cz-radius,z1=cz+radius;
        int bright=light|0x00D000D0;
        quad(pose,output,sprite,bright,tint,.85f,255,
            new Point(x0,lo,z0),new Point(x0,hi,z0),new Point(x1,hi,z0),new Point(x1,lo,z0),
            .1f,.02f,.8f,.96f,0,0,-1);
        quad(pose,output,sprite,bright,tint,1f,255,
            new Point(x1,lo,z1),new Point(x1,hi,z1),new Point(x0,hi,z1),new Point(x0,lo,z1),
            .1f,.02f,.8f,.96f,0,0,1);
        quad(pose,output,sprite,bright,tint,.82f,255,
            new Point(x0,lo,z1),new Point(x0,hi,z1),new Point(x0,hi,z0),new Point(x0,lo,z0),
            .1f,.02f,.8f,.96f,-1,0,0);
        quad(pose,output,sprite,bright,tint,.97f,255,
            new Point(x1,lo,z0),new Point(x1,hi,z0),new Point(x1,hi,z1),new Point(x1,lo,z1),
            .1f,.02f,.8f,.96f,1,0,0);
    }

    private static void quad(PoseStack.Pose pose,VertexConsumer output,TextureAtlasSprite sprite,
                             int light,int tint,float shade,int alpha,
                             Point a,Point b,Point c,Point d,
                             float u0,float v0,float u1,float v1,
                             float nx,float ny,float nz) {
        int r=Math.min(255,Math.max(0,Math.round(((tint>>>16)&255)*shade)));
        int g=Math.min(255,Math.max(0,Math.round(((tint>>>8)&255)*shade)));
        int bl=Math.min(255,Math.max(0,Math.round((tint&255)*shade)));
        add(pose,output,sprite,light,a,u0,v1,r,g,bl,alpha,nx,ny,nz);
        add(pose,output,sprite,light,b,u0,v0,r,g,bl,alpha,nx,ny,nz);
        add(pose,output,sprite,light,c,u1,v0,r,g,bl,alpha,nx,ny,nz);
        add(pose,output,sprite,light,d,u1,v1,r,g,bl,alpha,nx,ny,nz);
    }

    private static void add(PoseStack.Pose pose, VertexConsumer out, TextureAtlasSprite sprite,
                            int light, Point p,float u,float v,int r,int g,int b,int a,
                            float nx,float ny,float nz) {
        // Never sample an atlas neighbor, including at animated texture boundaries.
        float uu=Math.max(.025f,Math.min(.975f,u));
        float vv=Math.max(.025f,Math.min(.975f,v));
        out.addVertex(pose.pose(),p.x,p.y,p.z)
            .setColor(r,g,b,a)
            .setUv(sprite.getU0()+(sprite.getU1()-sprite.getU0())*uu,
                   sprite.getV0()+(sprite.getV1()-sprite.getV0())*vv)
            .setOverlay(OverlayTexture.NO_OVERLAY)
            .setLight(light)
            .setNormal(pose,nx,ny,nz);
    }
}
