package com.caszgamermd.orelaundering;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.inventory.MenuType;

public final class OreMenus {
    private OreMenus() {}
    public static final MenuType<OreMachineMenu> MACHINE = Registry.register(
        BuiltInRegistries.MENU, OreLaundering.id("machine_menu"),
        new MenuType<>(OreMachineMenu::new, FeatureFlagSet.of()));
    public static void initialize() {}
}
