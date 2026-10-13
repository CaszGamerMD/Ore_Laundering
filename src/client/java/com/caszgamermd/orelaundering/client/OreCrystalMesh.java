package com.caszgamermd.orelaundering.client;

import com.caszgamermd.orelaundering.OreMachines;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;

/**
 * Draws solid, faceted ore-textured crystals, never cuboids.
 *
 * The roots are embedded a fraction of a pixel into their supporting surface:
 * - Wall rack: from the front face of the steel backplate, pointing outward.
 * - Ceiling rack: from the UNDERSIDE of the iron growth plate, pointing down.
 * - Trough: from the soil surface, pointing up.
 *
 * All geometry is client-only and submitted with the ore/material's actual
 * block-atlas sprite. No generic crystal texture or mini ore cubes are used.
 */
final class OreCrystalMesh {
    private OreCrystalMesh() {}

    // Two-dimensional root positions, intentionally staggered; no random flicker.
    private static final float[][] ROOTS = {
        {.49f,.49f}, {.31f,.33f}, {.68f,.65f}, {.66f,.29f},
        {.29f,.70f}, {.47f,.20f}, {.78f,.47f}, {.43f,.78f},
        {.20f,.47f}, {.76f,.79f}, {.56f,.52f}, {.24f,.18f},
        {.60f,.85f}
    };
    private static final int FACETS = 6;
    private static final float TWO_PI = (float)(Math.PI * 2.0);
    private static final int[] COUNTS = {0,2,5,9,13};
    private static final float[] GROWTH = {0f,.22f,.52f,.77f,1f};

    private record Vertex(float x, float y, float z) {}

    static void render(PoseStack.Pose pose, VertexConsumer buffer, TextureAtlasSprite sprite,
                       int light, OreMachines.Kind kind, int stage) {
        if (stage <= 0 || stage > 4 || sprite == null) return;
        float growth = GROWTH[stage];
        for (int index = 0; index < COUNTS[stage]; index++) {
            float[] spot = ROOTS[index];
            float length = (0.23f + (index % 5) * .043f) * growth;
            float radius = (.033f + (index % 4) * .008f) * (.69f + .31f * growth);
            float leanX = ((index % 5) - 2) * .009f * growth;
            float leanZ = ((index % 3) - 1) * .017f * growth;
            // An asymmetric taper and slight per-shard twist creates mineral
            // facets with recognizable pointed tips, not six-sided cylinders.
            float twist = index * .71f;
            for (int side = 0; side < FACETS; side++) {
                float alpha = twist + side * TWO_PI / FACETS;
                float beta = twist + (side + 1) * TWO_PI / FACETS;
                Vertex bottomA = ring(kind, spot, .00f, radius*.75f, alpha, leanX, leanZ);
                Vertex bottomB = ring(kind, spot, .00f, radius*.75f, beta, leanX, leanZ);
                Vertex shoulderA = ring(kind, spot, length*.16f, radius, alpha, leanX, leanZ);
                Vertex shoulderB = ring(kind, spot, length*.16f, radius, beta, leanX, leanZ);
                Vertex neckA = ring(kind, spot, length*.68f, radius*.78f, alpha, leanX, leanZ);
                Vertex neckB = ring(kind, spot, length*.68f, radius*.78f, beta, leanX, leanZ);
                Vertex point = ring(kind, spot, length, 0f, alpha, leanX, leanZ);
                float t0 = .08f + (index % 4) * .12f;
                float t1 = t0 + .26f;
                float brightness = 0; // shading is computed by the selected facet index
                int shade = 217 + ((side + index) % 4) * 12;
                // Each face occupies a different strip of the source ore sprite,
                // giving visual variation without requiring unique texture assets.
                quad(pose,buffer,sprite,light,bottomA,bottomB,shoulderB,shoulderA,
                    t0,t1,.91f,.75f,shade,kind,alpha,beta);
                quad(pose,buffer,sprite,light,shoulderA,shoulderB,neckB,neckA,
                    t0,t1,.75f,.29f,shade,kind,alpha,beta);
                quad(pose,buffer,sprite,light,neckA,neckB,point,point,
                    t0,t1,.29f,.07f,Math.min(255,shade+12),kind,alpha,beta);
            }
        }
    }

    /**
     * In local crystal coordinates the primary axis is Y+.
     * Ceiling maps Y+ to world Y-; wall maps it to world Z+.
     * Faces and normals are built in block-local positions so they work with
     * the wall rack's existing FACING rotation.
     */
    private static Vertex ring(OreMachines.Kind kind, float[] spot,
                               float length, float radius, float angle,
                               float leanX, float leanZ) {
        float a = radius * (float)Math.cos(angle);
        float b = radius * (float)Math.sin(angle);
        float incline = length * 1.8f;
        float x = .16f + spot[0]*.68f + a + leanX*incline;
        return switch(kind) {
            case CEILING -> new Vertex(x, .442f - length, .14f + spot[1]*.72f + b + leanZ*incline);
            case WALL -> new Vertex(x, .16f + spot[1]*.69f + b + leanZ*incline, .129f + length);
            default -> new Vertex(x, .247f + length, .14f + spot[1]*.72f + b + leanZ*incline);
        };
    }

    private static void quad(PoseStack.Pose pose, VertexConsumer out, TextureAtlasSprite sprite,
                             int light, Vertex a, Vertex b, Vertex c, Vertex d,
                             float u0, float u1, float v0, float v1, int shade,
                             OreMachines.Kind kind, float angA, float angB) {
        float ux=b.x-a.x, uy=b.y-a.y, uz=b.z-a.z;
        float vx=c.x-a.x, vy=c.y-a.y, vz=c.z-a.z;
        float nx=uy*vz-uz*vy, ny=uz*vx-ux*vz, nz=ux*vy-uy*vx;
        float outwardX=(float)Math.cos((angA+angB)*.5);
        float outwardZ=(float)Math.sin((angA+angB)*.5);
        float dot = kind == OreMachines.Kind.WALL ?
            nx*outwardX + ny*outwardZ : nx*outwardX + nz*outwardZ;
        // The winding must be outward for entityCutout's culling.
        if (dot < 0f) {
            nx=-nx; ny=-ny; nz=-nz;
            add(pose,out,sprite,light,a,u0,v0,shade,nx,ny,nz);
            add(pose,out,sprite,light,d,u0,v1,shade,nx,ny,nz);
            add(pose,out,sprite,light,c,u1,v1,shade,nx,ny,nz);
            add(pose,out,sprite,light,b,u1,v0,shade,nx,ny,nz);
        } else {
            add(pose,out,sprite,light,a,u0,v0,shade,nx,ny,nz);
            add(pose,out,sprite,light,b,u1,v0,shade,nx,ny,nz);
            add(pose,out,sprite,light,c,u1,v1,shade,nx,ny,nz);
            add(pose,out,sprite,light,d,u0,v1,shade,nx,ny,nz);
        }
    }

    private static void add(PoseStack.Pose pose, VertexConsumer out, TextureAtlasSprite sprite,
                            int light, Vertex vertex, float u, float v,
                            int shade, float normalX, float normalY, float normalZ) {
        float normalLength = (float)Math.sqrt(normalX*normalX+normalY*normalY+normalZ*normalZ);
        if (normalLength < .000001f) normalLength=1f;
        float atlasU = sprite.getU0() + (sprite.getU1()-sprite.getU0()) * u;
        float atlasV = sprite.getV0() + (sprite.getV1()-sprite.getV0()) * v;
        out.addVertex(pose.pose(),vertex.x,vertex.y,vertex.z)
            .setColor(shade,shade,shade,255)
            .setUv(atlasU,atlasV)
            .setOverlay(OverlayTexture.NO_OVERLAY)
            .setLight(light)
            .setNormal(pose,normalX/normalLength,normalY/normalLength,normalZ/normalLength);
    }
}
