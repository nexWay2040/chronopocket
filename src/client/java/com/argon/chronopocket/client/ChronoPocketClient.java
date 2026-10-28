package com.argon.chronopocket.client;

import com.argon.chronopocket.ChronoPocketMod;
import net.fabricmc.api.ClientModInitializer;

public class ChronoPocketClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        ChronoPocketMod.LOGGER.info("ChronoPocket Client initialized!");
    }
}
