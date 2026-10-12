package com.caszgamermd.orelaundering;

import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class OreLaundering implements ModInitializer {
    public static final String ID = "ore_laundering";
    public static final Logger LOGGER = LoggerFactory.getLogger(ID);

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(ID, path);
    }

    @Override
    public void onInitialize() {
        OreMachines.initialize();
        OreItems.initialize();
        LOGGER.info("Ore Laundering initialized for Minecraft 26.2");
    }
}
