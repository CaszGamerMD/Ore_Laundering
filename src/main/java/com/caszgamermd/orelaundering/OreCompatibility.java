package com.caszgamermd.orelaundering;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class OreCompatibility {
    private OreCompatibility() {}

    public static final TagKey<Item> COMMON_ORES =
        TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("c", "ores"));

    public static String fromOre(ItemStack stack) {
        if (stack.isEmpty()) return "";
        Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        String path = id.getPath();
        if (stack.is(COMMON_ORES) || path.endsWith("_ore") ||
                path.endsWith("_ores") || path.equals("ancient_debris")) {
            return id.toString();
        }
        return "";
    }

    public static String displayName(String ore) {
        if (ore.isEmpty()) return "Unknown";
        String path = ore.substring(ore.indexOf(':') + 1)
            .replace("deepslate_", "").replace("nether_", "")
            .replace("end_", "").replace("_ore", "").replace('_', ' ');
        StringBuilder result = new StringBuilder();
        for (String word : path.split(" ")) {
            if (!word.isBlank()) result.append(Character.toUpperCase(word.charAt(0)))
                .append(word.substring(1)).append(' ');
        }
        return result.toString().trim();
    }

    // One molten unit makes a nugget; 9 make an ingot; 81 make a block.
    // Output lookup is registry-based, not a list of vanilla-only ores.
    public static Item castingOutput(String ore, int units) {
        if (ore.isEmpty()) return Items.AIR;
        Identifier origin = Identifier.tryParse(ore);
        if (origin == null) return Items.AIR;
        String path = origin.getPath().replaceFirst("^(deepslate_|nether_|end_)", "");
        // Support host-rock variants such as granite_diamond_ore and tuff_iron_ore.
        // Explicit loot-table analysis will handle ambiguous modded names separately.
        path = path.replaceFirst("^(granite|diorite|andesite|tuff|stone|blackstone|basalt|netherrack|calcite)_", "");
        path = path.replaceFirst("_(granite|diorite|andesite|tuff|stone|blackstone|basalt|netherrack|calcite)_ore$", "_ore");
        if (path.endsWith("_ore")) path = path.substring(0, path.length() - 4);
        if (path.equals("ancient_debris")) path = "netherite_scrap";
        String suffix = units == 100 ? "_nugget" : units == 900 ? "_ingot" : "_block";
        List<String> candidatePaths = new ArrayList<>();
        candidatePaths.add(path + suffix);
        if (units == 900) {
            candidatePaths.add(path); // gemstones, e.g. minecraft:diamond
            if (path.equals("lapis")) candidatePaths.add("lapis_lazuli");
        }
        for (String idPath : candidatePaths) {
            for (String ns : new String[] {origin.getNamespace(), "minecraft"}) {
                Identifier target = Identifier.tryParse(ns + ":" + idPath);
                if (target != null && BuiltInRegistries.ITEM.containsKey(target)) {
                    Item result = BuiltInRegistries.ITEM.getValue(target);
                    if (result != Items.AIR) return result;
                }
            }
        }
        return Items.AIR;
    }
}
