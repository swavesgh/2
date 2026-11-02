package com.snipe;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/** Builds the coloured, tiered deal messages. */
public class Chat {
    /** minPct = discount under average needed to reach this tier. sound: 0 none, 1 ping, 2 level-up, 3 fanfare. */
    public record Tier(double minPct, String label, ChatFormatting color, int stars, int sound) {}

    static final Tier[] TIERS = {
        new Tier(70, "LEGENDARY DEAL", ChatFormatting.LIGHT_PURPLE, 5, 3),
        new Tier(55, "INSANE DEAL",    ChatFormatting.RED,          4, 2),
        new Tier(40, "HUGE DEAL",      ChatFormatting.GOLD,         3, 2),
        new Tier(25, "GREAT DEAL",     ChatFormatting.GREEN,        2, 1),
        new Tier(0,  "Decent deal",    ChatFormatting.AQUA,         1, 0),
    };

    public static Tier tier(double pct) {
        for (Tier t : TIERS) if (pct >= t.minPct()) return t;
        return TIERS[TIERS.length - 1];
    }

    static MutableComponent t(String s, ChatFormatting... f) { return Component.literal(s).withStyle(f); }

    /** Multi-line, colour-coded message. buying = the bot is about to buy it (no page hint). */
    public static MutableComponent deal(Market.Listing l, Market.Snap s, int pageNo, boolean buying) {
        double unit = l.unit();
        int pct = (int) Math.round((1 - unit / s.avg) * 100);
        Tier ti = tier(pct);
        double resale = s.avg * l.count() * (1 - Config.I.ahTaxPercent / 100);
        double profit = resale - l.total();
        String stars = "\u2605".repeat(ti.stars());
        ChatFormatting c = ti.color();

        MutableComponent m = Component.empty();
        m.append(t("\n"));
        m.append(t("[Sniper] ", ChatFormatting.DARK_GRAY));
        m.append(t(stars + " " + (buying ? "SNIPING \u00BB " : "") + ti.label() + " " + stars + "\n", c, ChatFormatting.BOLD));

        m.append(t("  " + l.name(), ChatFormatting.WHITE, ChatFormatting.BOLD));
        if (!l.variant().isEmpty()) m.append(t(" [" + Market.shortVariant(l.variant()) + "]", ChatFormatting.LIGHT_PURPLE));
        m.append(t(" x" + l.count() + "\n", ChatFormatting.GRAY));

        m.append(t("  Price ", ChatFormatting.GRAY));
        m.append(t(Config.fmt(l.total()), c, ChatFormatting.BOLD));
        if (l.count() > 1) m.append(t(" (" + Config.fmt(unit) + " each)", ChatFormatting.GRAY));
        m.append(t("   Avg ", ChatFormatting.GRAY));
        m.append(t(Config.fmt(s.avg) + "\n", ChatFormatting.YELLOW));

        int filled = Math.max(0, Math.min(10, (int) Math.round(pct / 10.0)));
        m.append(t("  ", ChatFormatting.GRAY));
        m.append(t("\u25A0".repeat(filled), c));
        m.append(t("\u25A0".repeat(10 - filled), ChatFormatting.DARK_GRAY));
        m.append(t("  -" + pct + "% ", c, ChatFormatting.BOLD));
        m.append(t("under average\n", ChatFormatting.GRAY));

        m.append(t("  Profit if resold at avg: ", ChatFormatting.GRAY));
        m.append(t((profit < 0 ? "-" : "+") + Config.fmt(Math.abs(profit)), profit >= 0 ? ChatFormatting.GREEN : ChatFormatting.RED, ChatFormatting.BOLD));

        if (!buying) {
            m.append(t("\n  Seller ", ChatFormatting.GRAY));
            m.append(t(l.seller(), ChatFormatting.AQUA));
            m.append(t("   /" + Config.I.command + " page " + (pageNo + 1), ChatFormatting.DARK_GRAY));
        }
        return m;
    }
}
