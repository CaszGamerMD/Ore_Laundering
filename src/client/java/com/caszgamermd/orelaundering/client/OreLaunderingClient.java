package com.caszgamermd.orelaundering.client;

import com.caszgamermd.orelaundering.OreMachines;
import com.caszgamermd.orelaundering.OreMenus;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry;
import net.minecraft.client.gui.screens.MenuScreens;

public final class OreLaunderingClient implements ClientModInitializer {
    @Override public void onInitializeClient() {
        BlockEntityRendererRegistry.register(OreMachines.BLOCK_ENTITY, OreGrowthRenderer::new);
        MenuScreens.register(OreMenus.MACHINE, OreMachineScreen::new);
    }
}
