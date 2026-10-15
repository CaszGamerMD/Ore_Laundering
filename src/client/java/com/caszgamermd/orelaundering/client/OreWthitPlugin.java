package com.caszgamermd.orelaundering.client;

import com.caszgamermd.orelaundering.OreMachineBlock;
import com.caszgamermd.orelaundering.OreMachineEntity;
import com.caszgamermd.orelaundering.OreMachineStatus;
import mcp.mobius.waila.api.IBlockAccessor;
import mcp.mobius.waila.api.IBlockComponentProvider;
import mcp.mobius.waila.api.IClientRegistrar;
import mcp.mobius.waila.api.IPluginConfig;
import mcp.mobius.waila.api.ITooltip;
import mcp.mobius.waila.api.IWailaClientPlugin;
import net.minecraft.network.chat.Component;

/**
 * Optional WTHIT / WAILA integration, loaded only by WTHIT.
 * No WTHIT classes are referenced by the base mod entrypoint.
 */
public final class OreWthitPlugin implements IWailaClientPlugin {
    @Override public void register(IClientRegistrar registry) {
        registry.body(Provider.INSTANCE, OreMachineBlock.class);
    }

    private enum Provider implements IBlockComponentProvider {
        INSTANCE;
        @Override public void appendBody(ITooltip tooltip, IBlockAccessor target,
                                         IPluginConfig config) {
            if (!(target.getBlockEntity() instanceof OreMachineEntity machine)) return;
            for (String line:OreMachineStatus.lines(machine)) {
                tooltip.addLine(Component.literal(line));
            }
        }
    }
}
