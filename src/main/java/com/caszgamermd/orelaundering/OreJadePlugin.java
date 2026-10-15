package com.caszgamermd.orelaundering;

import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;

/** Common entrypoint is safe even when Jade is installed on dedicated servers.
 * The actual overlay implementation is loaded only on the client. */
@WailaPlugin
public final class OreJadePlugin implements IWailaPlugin {
    @Override public void registerClient(IWailaClientRegistration registry) {
        try {
            Class<?> bridge=Class.forName(
                    "com.caszgamermd.orelaundering.client.OreJadeClientBridge");
            bridge.getMethod("register",IWailaClientRegistration.class)
                    .invoke(null,registry);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("Could not register Ore Laundering Jade overlay",ex);
        }
    }
}
