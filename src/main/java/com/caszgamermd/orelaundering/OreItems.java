package com.caszgamermd.orelaundering;

import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public final class OreItems {
    private OreItems() {}

    // Per-stack original ore ID (including a modded namespace).
    public static final DataComponentType<String> SOURCE_ORE = Registry.register(
        BuiltInRegistries.DATA_COMPONENT_TYPE, OreLaundering.id("source_ore"),
        DataComponentType.<String>builder()
            .persistent(Codec.STRING)
            .networkSynchronized(ByteBufCodecs.STRING_UTF8)
            .build()
    );

    private static Item register(String name) {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, OreLaundering.id(name));
        return Registry.register(BuiltInRegistries.ITEM, key,
            new Item(new Item.Properties().setId(key)));
    }

    public static final Item COUNTERFEIT_ORE = register("counterfeit_ore");
    public static final Item ORE_DUST = register("ore_dust");
    public static final Item GROWTH_ACCELERATOR = register("growth_accelerator");
    public static final Item DENSITY_MATRIX = register("density_matrix");
    public static final Item AUTO_HARVESTER = register("auto_harvester");
    public static final Item GRINDING_BIT = register("grinding_bit");
    public static final Item NUGGET_MOLD = register("nugget_mold");
    public static final Item INGOT_MOLD = register("ingot_mold");
    public static final Item BLOCK_MOLD = register("block_mold");

    public static ItemStack materialStack(Item item, String ore, int count) {
        ItemStack stack = new ItemStack(item, count);
        stack.set(SOURCE_ORE, ore);
        stack.set(DataComponents.ITEM_NAME, Component.literal(
            (item == COUNTERFEIT_ORE ? "Counterfeit " : "") +
            OreCompatibility.displayName(ore) +
            (item == ORE_DUST ? " Dust" : " Ore")));
        return stack;
    }

    public static String oreOf(ItemStack stack) {
        return stack.getOrDefault(SOURCE_ORE, "");
    }

    private static final ResourceKey<CreativeModeTab> TAB_KEY =
        ResourceKey.create(Registries.CREATIVE_MODE_TAB, OreLaundering.id("creative_tab"));

    public static void initialize() {
        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, TAB_KEY,
            FabricCreativeModeTab.builder()
                .title(Component.literal("Ore Laundering"))
                .icon(() -> new ItemStack(GROWTH_ACCELERATOR))
                .displayItems((params, output) -> {
                    output.accept(OreMachines.WALL_RACK);
                    output.accept(OreMachines.CEILING_RACK);
                    output.accept(OreMachines.GROW_TROUGH);
                    output.accept(OreMachines.GRINDER);
                    output.accept(OreMachines.ORE_VAT);
                    output.accept(OreMachines.ORE_TANK);
                    output.accept(OreMachines.LAVA_TANK);
                    output.accept(GROWTH_ACCELERATOR);
                    output.accept(DENSITY_MATRIX);
                    output.accept(AUTO_HARVESTER);
                    output.accept(GRINDING_BIT);
                    output.accept(NUGGET_MOLD);
                    output.accept(INGOT_MOLD);
                    output.accept(BLOCK_MOLD);
                }).build());
    }
}
