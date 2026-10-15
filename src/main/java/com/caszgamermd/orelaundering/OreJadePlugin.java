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

/** Optional Jade WAILA integration. Entry point is registered in fabric.mod.json. */
@WailaPlugin
public final class OreJadePlugin implements IWailaPlugin {
    @Override public void registerClient(IWailaClientRegistration registry) {
        registry.registerBlockComponent(Overlay.INSTANCE, OreMachineBlock.class);
    }

    private enum Overlay implements IBlockComponentProvider {
        INSTANCE;
        @Override public Identifier getUid() {
            return OreLaundering.id("machine_status");
        }
        @Override public void appendTooltip(ITooltip tooltip, BlockAccessor target,
                IPluginConfig config) {
            if (!(target.getBlockEntity() instanceof OreMachineEntity machine)) return;
            for (String line:OreMachineStatus.lines(machine)) {
                tooltip.add(Component.literal(line));
            }
        }
    }
}
