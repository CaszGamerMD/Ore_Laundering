package com.caszgamermd.orelaundering.client;

import com.caszgamermd.orelaundering.OreLaundering;
import com.caszgamermd.orelaundering.OreMachineBlock;
import com.caszgamermd.orelaundering.OreMachineEntity;
import com.caszgamermd.orelaundering.OreMachineStatus;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.config.IPluginConfig;

/** Jade tooltip methods depend on Minecraft client GUI types. */
public final class OreJadeClientBridge {
    private OreJadeClientBridge() {}
    public static void register(IWailaClientRegistration registration) {
        registration.registerBlockComponent(Overlay.INSTANCE,OreMachineBlock.class);
    }
    private enum Overlay implements IBlockComponentProvider {
        INSTANCE;
        @Override public Identifier getUid(){
            return OreLaundering.id("machine_status");
        }
        @Override public void appendTooltip(ITooltip tooltip,BlockAccessor accessor,
                                             IPluginConfig config) {
            if(!(accessor.getBlockEntity() instanceof OreMachineEntity machine))return;
            for(String line:OreMachineStatus.lines(machine))
                tooltip.add(Component.literal(line));
        }
    }
}
