package com.caszgamermd.orelaundering.client;

import com.caszgamermd.orelaundering.OreCompatibility;
import com.caszgamermd.orelaundering.client.mixin.SpriteContentsAccessor;
import com.mojang.blaze3d.platform.NativeImage;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.AtlasIds;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/** A true material color, taken from the block/ingot/gem texture whenever possible. */
final class OreFluidTint {
    private OreFluidTint() {}
    private static final Map<TextureAtlasSprite,Integer> SPRITE_COLORS = new WeakHashMap<>();

    static int fromMaterial(String ore) {
        if (ore == null || ore.isEmpty()) return 0xffaaaaaa;
        ore=OreCompatibility.canonical(ore);

        // The ingot/gem sprite is the primary reference; the storage block is
        // secondary. Resource packs and compatible modded ingots work too.
        int itemColor=-1,blockColor=-1;
        Item ingot=OreCompatibility.castingOutput(ore,900);
        if (ingot!=Items.AIR) {
            Identifier id=BuiltInRegistries.ITEM.getKey(ingot);
            Identifier icon=Identifier.fromNamespaceAndPath(id.getNamespace(),"item/"+id.getPath());
            TextureAtlasSprite sprite=Minecraft.getInstance().getAtlasManager()
                .getAtlasOrThrow(AtlasIds.ITEMS).getSprite(icon);
            if (sprite!=null && icon.equals(sprite.contents().name())) {
                itemColor=sample(sprite);
            }
        }
        Item block=OreCompatibility.castingOutput(ore,8100);
        if(block instanceof BlockItem b) {
            TextureAtlasSprite sprite=Minecraft.getInstance().getModelManager()
                .getBlockStateModelSet().getParticleMaterial(b.getBlock().defaultBlockState()).sprite();
            blockColor=sample(sprite);
        }
        if(itemColor!=-1 && blockColor!=-1) {
            int r=(((itemColor>>16)&255)*2+((blockColor>>16)&255))/3;
            int g=(((itemColor>>8)&255)*2+((blockColor>>8)&255))/3;
            int b=((itemColor&255)*2+(blockColor&255))/3;
            return 0xff000000|(r<<16)|(g<<8)|b;
        }
        if(itemColor!=-1)return itemColor;
        if(blockColor!=-1)return blockColor;

        // Common fallback ores and modded materials whose icon does not use
        // conventional paths still get recognizable melt colors.
        String text=ore.toLowerCase(java.util.Locale.ROOT);
        if(text.contains("copper"))return 0xffad6f45;
        if(text.contains("diamond"))return 0xff5de2d8;
        if(text.contains("emerald"))return 0xff4acc83;
        if(text.contains("gold"))return 0xffefd55b;
        if(text.contains("iron"))return 0xffc5c3bb;
        if(text.contains("redstone"))return 0xffdd5544;
        if(text.contains("lapis"))return 0xff456cdf;
        if(text.contains("quartz"))return 0xffe7e4db;
        if(text.contains("netherite"))return 0xff76666b;
        if(text.contains("coal"))return 0xff55555b;
        if(text.contains("amethyst"))return 0xffb588d6;
        if(text.contains("silver")||text.contains("aluminum"))return 0xffd7d8de;
        if(text.contains("tin")||text.contains("zinc"))return 0xffa4c2cb;
        if(text.contains("lead"))return 0xff857fa6;
        if(text.contains("uranium"))return 0xff7abd51;
        if(text.contains("cobalt"))return 0xff537fc9;
        if(text.contains("nickel"))return 0xffb7ab83;
        return 0xffbaaca0;
    }

    private static int sample(TextureAtlasSprite sprite) {
        if (sprite==null) return -1;
        Integer cached=SPRITE_COLORS.get(sprite);
        if (cached!=null) return cached;
        int color=samplePixels(sprite);
        SPRITE_COLORS.put(sprite,color);
        return color;
    }

    private static int samplePixels(TextureAtlasSprite sprite) {
        try {
            NativeImage image=((SpriteContentsAccessor)(Object)sprite.contents()).oreLaundering$originalImage();
            int w=Math.min(Math.min(image.getWidth(),sprite.contents().width()),64);
            int h=Math.min(Math.min(image.getHeight(),sprite.contents().height()),64);
            if(w==0||h==0)return -1;
            long sr=0,sg=0,sb=0,n=0;
            int step=Math.max(1,Math.min(w,h)/24);
            for(int y=0;y<h;y+=step) for(int x=0;x<w;x+=step) {
                int abgr=image.getPixel(x,y);
                int alpha=(abgr>>>24)&255;
                if(alpha<128)continue;
                int r=abgr&255;
                int g=(abgr>>>8)&255;
                int b=(abgr>>>16)&255;
                if(r+g+b<35)continue;
                sr+=r;sg+=g;sb+=b;n++;
            }
            if(n==0)return -1;
            // A little extra light makes it visibly molten but preserves the
            // hue of the source material, even if a resource pack replaces it.
            int r=Math.min(255,(int)(sr/n*0.85+255*0.15));
            int g=Math.min(255,(int)(sg/n*0.85+255*0.15));
            int b=Math.min(255,(int)(sb/n*0.85+255*0.15));
            return (255<<24)|(r<<16)|(g<<8)|b;
        } catch (RuntimeException exception) {
            return -1;
        }
    }
}
