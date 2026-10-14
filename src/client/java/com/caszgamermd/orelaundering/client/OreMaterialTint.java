package com.caszgamermd.orelaundering.client;

import com.caszgamermd.orelaundering.OreItems;
import com.mojang.serialization.MapCodec;
import net.minecraft.client.color.item.ItemTintSource;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Per-stack tint from the matching material's ingot or block TEXTURE, not
 * hardcoded per-ore color presets. Works with modded ores and resource packs.
 */
public final class OreMaterialTint implements ItemTintSource {
    public static final OreMaterialTint INSTANCE = new OreMaterialTint();
    public static final MapCodec<OreMaterialTint> MAP_CODEC = MapCodec.unit(INSTANCE);
    private OreMaterialTint() {}

    @Override public int calculate(ItemStack stack,@Nullable ClientLevel level,@Nullable LivingEntity owner) {
        return OreFluidTint.fromMaterial(OreItems.oreOf(stack));
    }
    @Override public MapCodec<OreMaterialTint> type() { return MAP_CODEC; }
}
