package com.caszgamermd.orelaundering.client.mixin;

import com.mojang.serialization.MapCodec;
import net.minecraft.client.color.item.ItemTintSource;
import net.minecraft.client.color.item.ItemTintSources;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ExtraCodecs;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Fabric 26.2 keeps tint-source mapper private. */
@Mixin(ItemTintSources.class)
public interface ItemTintSourcesAccessor {
    @Accessor("ID_MAPPER")
    static ExtraCodecs.LateBoundIdMapper<Identifier,MapCodec<? extends ItemTintSource>>
    oreLaundering$getTintMapper() {
        throw new AssertionError("Mixin ItemTintSourcesAccessor not applied");
    }
}
