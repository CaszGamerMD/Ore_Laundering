package com.caszgamermd.orelaundering;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;
import snownee.jade.api.config.IPluginConfig;

/** Optional Jade client overlay integration. No Jade classes load unless Jade is installed. */
@WailaPlugin
public final class OreJadePlugin implements IWailaPlugin {
    @Override public void registerClient(IWailaClientRegistration registry) {
        registry.registerBlockComponent(Overlay.INSTANCE, OreMachineBlock.class);
    }
    private enum Overlay implements IBlockComponentProvider {
        INSTANCE;
        @Override public Identifier getUid() { return OreLaundering.id("machine_status"); }

        @Override public void appendTooltip(ITooltip tooltip, BlockAccessor target, IPluginConfig cfg) {
            if (!(target.getBlockEntity() instanceof OreMachineEntity machine)) return;
            if (machine.grows()) {
                if (machine.plantedOre().isEmpty()) {
                    tooltip.add(Component.literal("Planted ore: None"));
                } else {
                    tooltip.add(Component.literal("Planted ore: " +
                            OreCompatibility.displayName(machine.plantedOre())));
                    tooltip.add(Component.literal("Growth: " + machine.growthPercent() + "%"));
                }
            } else {
                int amount = machine.menuData.get(6);
                switch (machine.kind()) {
                    case GRINDER -> tooltip.add(Component.literal("Queued ore: " + machine.menuData.get(5)));
                    case VAT -> {
                        tooltip.add(Component.literal("Molten ore: " + amount + " / 144"));
                        tooltip.add(Component.literal("Lava below: " + machine.menuData.get(10) + " mB"));
                    }
                    case TANK -> tooltip.add(Component.literal("Stored molten ore: " + amount + " / 576"));
                    case LAVA_TANK -> tooltip.add(Component.literal("Stored lava: " +
                        machine.menuData.get(7) + " / 16000 mB"));
                    default -> {}
                }
            }
        }
    }
}
