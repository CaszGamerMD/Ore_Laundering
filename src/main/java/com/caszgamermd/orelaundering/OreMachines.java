package com.caszgamermd.orelaundering;

import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.references.BlockItemId;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;

public final class OreMachines {
    private OreMachines() {}

    public enum Kind { WALL, CEILING, TROUGH, GRINDER, VAT, TANK, LAVA_TANK, SPOUT, CASTING }

    private static Block register(String name, Kind kind) {
        ResourceKey<Block> blockKey = ResourceKey.create(Registries.BLOCK, OreLaundering.id(name));
        ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, OreLaundering.id(name));
        Block block = Registry.register(BuiltInRegistries.BLOCK, blockKey,
            new OreMachineBlock(kind, BlockBehaviour.Properties.of()
                .setId(blockKey).strength(3.0F, 6.0F)
                .sound(SoundType.METAL).noOcclusion()));
        Registry.register(BuiltInRegistries.ITEM, itemKey,
            new BlockItem(block, new Item.Properties().setId(itemKey).useBlockDescriptionPrefix()));
        return block;
    }

    public static final Block WALL_RACK = register("wall_growth_rack", Kind.WALL);
    public static final Block CEILING_RACK = register("ceiling_growth_rack", Kind.CEILING);
    public static final Block GROW_TROUGH = register("grow_trough", Kind.TROUGH);
    public static final Block GRINDER = register("grinder", Kind.GRINDER);
    public static final Block ORE_VAT = register("ore_vat", Kind.VAT);
    public static final Block ORE_TANK = register("ore_tank", Kind.TANK);
    public static final Block LAVA_TANK = register("lava_tank", Kind.LAVA_TANK);
    public static final Block VAT_SPOUT = register("vat_spout", Kind.SPOUT);
    public static final Block CASTING_TABLE = register("casting_table", Kind.CASTING);

    public static BlockEntityType<OreMachineEntity> BLOCK_ENTITY;

    public static void initialize() {
        BLOCK_ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,
            OreLaundering.id("machine"),
            FabricBlockEntityTypeBuilder.create(OreMachineEntity::new,
                WALL_RACK, CEILING_RACK, GROW_TROUGH, GRINDER, ORE_VAT, ORE_TANK, LAVA_TANK, VAT_SPOUT, CASTING_TABLE).build());
    }
}
