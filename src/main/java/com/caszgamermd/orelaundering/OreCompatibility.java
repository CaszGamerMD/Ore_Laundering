package com.caszgamermd.orelaundering;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

/**
 * Material identity is based on what an ore produces, not its host stone.
 *
 * e.g. mod:granite_diamond_ore -> minecraft:diamond_ore
 * and mod:tuff_silver_ore -> mod:silver_ore (when its own ingot exists).
 */
public final class OreCompatibility {
    private OreCompatibility() {}

    public static final TagKey<Item> COMMON_ORES =
        TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("c", "ores"));

    public static boolean isOre(ItemStack stack) {
        if (stack.isEmpty()) return false;
        String path = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
        return stack.is(COMMON_ORES) || path.endsWith("_ore") ||
            path.endsWith("_ores") || path.equals("ancient_debris");
    }

    public static String fromOre(ItemStack stack) {
        if (!isOre(stack)) return "";
        return canonical(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
    }

    /** Inspect real no-Silk-Touch mine drops on the server, falling back to IDs. */
    public static String fromOre(ItemStack stack, Level level, BlockPos pos) {
        String byName = fromOre(stack);
        if (byName.isEmpty()) return "";
        if (level instanceof ServerLevel server && stack.getItem() instanceof BlockItem source) {
            try {
                // Pass a real iron pickaxe, otherwise diamond ore loot tables
                // may produce nothing and return a misleading empty result.
                List<ItemStack> drops = Block.getDrops(source.getBlock().defaultBlockState(),
                    server, pos, null, null, new ItemStack(Items.IRON_PICKAXE));
                for (ItemStack drop : drops) {
                    if (drop.isEmpty() || drop.is(stack.getItem())) continue;
                    String derived = fromDrop(drop.getItem());
                    if (!derived.isEmpty()) return canonical(derived);
                }
            } catch (RuntimeException ignored) {
                // Mods can have dynamic or unusual loot conditions. Do not
                // require a particular tool, mod or loot table for planting.
            }
        }
        return byName;
    }

    private static String fromDrop(Item item) {
        if (item == Items.AIR) return "";
        Identifier id = BuiltInRegistries.ITEM.getKey(item);
        String path = id.getPath();
        String raw = path;
        if (raw.startsWith("raw_")) raw = raw.substring(4);
        if (raw.endsWith("_raw")) raw = raw.substring(0,raw.length()-4);
        if (raw.endsWith("_ingot")) raw = raw.substring(0,raw.length()-6);
        if (raw.endsWith("_nugget")) raw = raw.substring(0,raw.length()-7);
        if (raw.endsWith("_gem")) raw = raw.substring(0,raw.length()-4);
        if (raw.equals("lapis_lazuli")) raw="lapis";
        if (raw.equals("quartz")) raw="quartz";
        if (raw.equals("netherite_scrap")) raw="ancient_debris";
        // A modded ore block can drop its own special block. It is not a
        // material indicator unless a corresponding output can be cast.
        if (raw.endsWith("_ore") || raw.endsWith("_block")) return "";
        if (!isMaterial(raw,id.getNamespace())) return "";
        return id.getNamespace()+":"+(raw.equals("ancient_debris") ? raw : raw+"_ore");
    }

    private static boolean isMaterial(String stem, String namespace) {
        if (stem.equals("ancient_debris")) return true;
        for (String candidate : new String[]{stem+"_ingot",stem+"_block",stem,stem+"_nugget"}) {
            Identifier a=Identifier.fromNamespaceAndPath(namespace,candidate);
            if(BuiltInRegistries.ITEM.containsKey(a)) return true;
        }
        return false;
    }

    public static String canonical(String ore) {
        if (ore == null || ore.isEmpty()) return "";
        Identifier id=Identifier.tryParse(ore);
        if (id==null) return ore;
        String path=id.getPath();
        if(path.equals("ancient_debris")) return "minecraft:ancient_debris";
        // Alternate-stone variants keep the metal but not the stone. Strip
        // *multiple* prefixes so deepslate_granite_iron_ore also works.
        String old;
        do {
            old=path;
            path=path.replaceFirst("^(deepslate|granite|diorite|andesite|tuff|stone|blackstone|basalt|netherrack|calcite|end|nether|cobbled_deepslate)_","");
        } while(!path.equals(old));
        path=path.replaceFirst("_(granite|diorite|andesite|tuff|stone|blackstone|basalt|netherrack|calcite)_ore$","_ore");
        if(path.equals("lapis_lazuli_ore"))path="lapis_ore";
        if(path.equals("nether_quartz_ore"))path="quartz_ore";
        String material=path.endsWith("_ore")?path.substring(0,path.length()-4):path;
        // Vanilla results always become their one canonical family, even if
        // the rock variant is registered by another mod.
        String vanilla=material+"_ore";
        if (isMaterial(material,"minecraft") && (
             BuiltInRegistries.ITEM.containsKey(Identifier.fromNamespaceAndPath("minecraft",vanilla))
             || switch(material){
                 case "lapis","redstone","quartz","diamond","emerald","coal","copper","iron","gold" -> true;
                 default -> false;
             })) return "minecraft:"+vanilla;
        return id.getNamespace()+":"+path;
    }

    public static String displayName(String ore) {
        if (ore == null || ore.isEmpty()) return "Unknown";
        String path=canonical(ore);
        path=path.substring(path.indexOf(':')+1)
            .replaceFirst("_ore$","").replace('_',' ');
        StringBuilder title=new StringBuilder();
        for (String part:path.split(" ")) {
            if (!part.isBlank()) title.append(Character.toUpperCase(part.charAt(0)))
                .append(part.substring(1)).append(' ');
        }
        return title.toString().trim();
    }

    /** mL per item: 100 nugget, 900 ingot/gem, 8100 storage block. */
    public static Item castingOutput(String ore,int millilitres) {
        if(ore==null || ore.isEmpty()) return Items.AIR;
        Identifier origin=Identifier.tryParse(canonical(ore));
        if(origin==null)return Items.AIR;
        String path=origin.getPath();
        if(path.endsWith("_ore"))path=path.substring(0,path.length()-4);
        if(path.equals("ancient_debris"))path="netherite_scrap";
        if(path.equals("nether_quartz"))path="quartz";
        List<String> candidates=new ArrayList<>();
        if(millilitres==100) {
            candidates.add(path+"_nugget");
        } else if(millilitres==900) {
            candidates.add(path+"_ingot");
            candidates.add(path);
            if(path.equals("lapis")) candidates.add("lapis_lazuli");
        } else if(millilitres==8100) {
            candidates.add(path+"_block");
            if(path.equals("lapis"))candidates.add("lapis_block");
            if(path.equals("netherite_scrap"))candidates.add("netherite_block");
        } else return Items.AIR;
        for (String idPath:candidates) {
            for (String ns:new String[]{origin.getNamespace(),"minecraft"}) {
                Identifier target=Identifier.fromNamespaceAndPath(ns,idPath);
                if(BuiltInRegistries.ITEM.containsKey(target)){
                    Item result=BuiltInRegistries.ITEM.getValue(target);
                    if(result!=Items.AIR)return result;
                }
            }
        }
        return Items.AIR;
    }
}
