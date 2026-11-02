package com.snipe;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * Manual selling: holds an item, works out the price from your Settings
 * (Resell price mode + Undercut %), then runs the sell command ("ah hand {price}").
 */
public class Seller {
    enum S { IDLE, SEND, CONFIRM }
    static S state = S.IDLE;
    static int cooldown, wait;
    static double price;
    static String name = "";
    static Purchases.P ref;

    static boolean busy() {
        if (Sniper.running) { Sniper.warn("Stop the bot first, then sell."); return true; }
        if (state != S.IDLE) { Sniper.warn("Already selling something, wait a moment."); return true; }
        return false;
    }

    /** Total price for the whole stack, or 0 if there is no usable data. */
    static double quote(ItemStack st) {
        Market.Snap s = Market.latest(Sniper.keyOf(st));
        String nm = st.getHoverName().getString();
        String vr = Sniper.variantOf(st);
        if (!vr.isEmpty()) nm += " [" + Market.shortVariant(vr) + "]";
        if (s == null || s.total < 1 || s.avg <= 0) { Sniper.warn("No price data for " + nm + " yet (needs 1+ listing of this exact enchant/lore combo). Press Analyze first."); return 0; }
        Config c = Config.I;
        double refUnit; String basis;
        switch (c.resellMode) {
            case "AVERAGE" -> { refUnit = s.avg; basis = "average"; }
            case "MEDIAN" -> { refUnit = s.median; basis = "median"; }
            default -> {
                refUnit = s.low; basis = "lowest BIN";
                if (s.total >= 3 && (1 - s.low / s.avg) * 100 > c.scamGuardPercent) { refUnit = s.median; basis = "median (lowest looked fake)"; }
            }
        }
        double total = Math.max(1, Math.floor(refUnit * (1 - c.undercutPercent / 100) * st.getCount()));
        long ageMin = Math.max(0, (System.currentTimeMillis() - s.t) / 60_000L);
        Sniper.say(Component.literal("[Sniper] ").withStyle(ChatFormatting.DARK_GRAY)
            .append(Component.literal("Selling ").withStyle(ChatFormatting.GRAY))
            .append(Component.literal(nm + " x" + st.getCount()).withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD))
            .append(Component.literal(" for ").withStyle(ChatFormatting.GRAY))
            .append(Component.literal(Config.fmt(total)).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD))
            .append(Component.literal("  (" + basis + " " + Config.fmt(refUnit) + " each, -" + c.undercutPercent + "%, data " + ageMin + "m old)").withStyle(ChatFormatting.DARK_GRAY)));
        return total;
    }

    /** Sell whatever is in your main hand. */
    public static void sellHeld(Minecraft mc) {
        if (mc.player == null || busy()) return;
        ItemStack st = mc.player.getMainHandItem();
        if (st.isEmpty()) { Sniper.warn("Hold the item you want to sell first."); return; }
        double p = quote(st);
        if (p <= 0) return;
        price = p; name = st.getHoverName().getString(); ref = null;
        state = S.SEND; cooldown = 1;
    }

    /** Find a bought item in your inventory, hold it, and sell it. */
    public static void sellPurchase(Minecraft mc, Purchases.P p) {
        if (mc.player == null || busy()) return;
        int found = -1;
        for (int i = 0; i < 36 && found < 0; i++) {
            ItemStack s = Compat.invItem(mc, i);
            if (!s.isEmpty() && s.getHoverName().getString().equalsIgnoreCase(p.name)) found = i;
        }
        if (found < 0) { Sniper.warn(p.name + " is not in your inventory."); return; }
        double pr = quote(Compat.invItem(mc, found));
        if (pr <= 0) return;
        Compat.holdSlot(mc, found);
        price = pr; name = p.name; ref = p;
        state = S.SEND; cooldown = 6;
    }

    public static void tick(Minecraft mc) {
        if (state == S.IDLE || mc.player == null || mc.getConnection() == null) return;
        if (cooldown > 0) { cooldown--; return; }
        switch (state) {
            case SEND -> {
                ItemStack st = mc.player.getMainHandItem();
                if (st.isEmpty() || !st.getHoverName().getString().equalsIgnoreCase(name)) {
                    Sniper.warn("Item left your hand, sell cancelled.");
                    state = S.IDLE; return;
                }
                Compat.command(mc, Config.I.sellCommand.replace("{price}", String.valueOf((long) price)));
                if (ref != null) { ref.status = "listed"; ref.listedFor = price; Purchases.save(); }
                state = S.CONFIRM; wait = 0; cooldown = Config.I.delayTicks * 3;
            }
            case CONFIRM -> {
                if (Compat.screen(mc) instanceof ContainerScreen gs) {
                    int c = Sniper.findButton(gs.getMenu(), Config.I.confirmName);
                    if (c >= 0) { Compat.click(mc, gs.getMenu(), c); state = S.IDLE; return; }
                }
                if (++wait > 20) state = S.IDLE;
            }
            default -> state = S.IDLE;
        }
    }
}
