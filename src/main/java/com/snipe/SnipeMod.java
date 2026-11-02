package com.snipe;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

public class SnipeMod implements ClientModInitializer {
    public static KeyMapping openKey, sellKey;

    @Override public void onInitializeClient() {
        Config.load(); Market.load(); Purchases.load();
        KeyMapping.Category cat = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("snipe", "main"));
        openKey = KeyMappingHelper.registerKeyMapping(
            new KeyMapping("key.snipe.open", InputConstants.Type.KEYSYM, InputConstants.KEY_RSHIFT, cat));
        sellKey = KeyMappingHelper.registerKeyMapping(
            new KeyMapping("key.snipe.sell", InputConstants.Type.KEYSYM, InputConstants.KEY_BACKSLASH, cat));
        // typing a bare "/ah hand" sells the held item at your auto price
        ClientSendMessageEvents.ALLOW_COMMAND.register(cmd -> {
            if (cmd.trim().equalsIgnoreCase(Config.I.command.trim() + " hand")) { Seller.sellHeld(Minecraft.getInstance()); return false; }
            return true;
        });
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            while (openKey.consumeClick()) Compat.open(mc, new SnipeScreen());
            while (sellKey.consumeClick()) Seller.sellHeld(mc);
            Sniper.tick(mc);
            Seller.tick(mc);
        });
        ClientLifecycleEvents.CLIENT_STOPPING.register(c -> { Config.save(); Market.save(); Purchases.save(); });
    }
}
