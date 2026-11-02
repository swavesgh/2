package com.snipe;

import com.google.gson.*;
import net.fabricmc.loader.api.FabricLoader;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

public class Config {
    /** One snipe target. Key = lowercase item name. */
    public static class Entry { public boolean enabled; public double maxPrice; public boolean each = true; }

    public Map<String, Entry> entries = new LinkedHashMap<>();

    // --- behaviour (all editable in the Settings tab) ---
    public boolean buyEnabled = true;        // false = only scan/track prices
    public boolean resell = false;           // relist bought items on the AH
    public boolean analyzeFirst = true;      // scan the whole AH once (no buying) before sniping
    public boolean requireData = true;       // only buy items that the analysis scan has seen (>= 3 listings)
    public double scamGuardPercent = 80;     // never buy a listing more than this % under the average (likely a fake)
    public boolean alerts = true;            // chat alert for great deals on items you are NOT sniping
    public double alertPercent = 40;         // alert if a listing is at least this % under the average
    public double alertMinPrice = 100000;    // ignore items whose average is below this
    public String resellMode = "LOWEST";     // LOWEST | AVERAGE | MEDIAN
    public double undercutPercent = 1;       // list this % under the reference price
    public double minProfitPercent = 5;      // skip resell if profit (after tax) is below this
    public double ahTaxPercent = 0;          // server's AH listing tax / fee
    public int maxPages = 100, delayTicks = 6, refreshTicks = 40, resellWaitTicks = 30;
    public String command = "ah";                    // opens the auction house
    public String sellCommand = "ah hand {price}";   // {price} = total price for the stack in your hand
    public boolean afk = false;              // AFK mode: forces resell, only buys items it can resell at a profit, retries stuck items, stops safely on trouble
    public double maxUnsold = 0;             // pause buying while unsold (held) stock cost exceeds this. 0 = no limit
    public int sweepMinutes = 5;             // AFK: retry selling held items every this many minutes
    public int snipePages = 100;             // pages looked at per sniping cycle. Set to 1-3 if your AH is sorted newest/lowest first = much faster
    public int fullScanMinutes = 10;         // with snipePages below maxPages: how often a full scan refreshes the price stats
    public String sortName = "sort";         // name of the AH sort button
    public int sortClicks = 0;               // press the sort button this many times after opening the AH (to reach "newest" / "lowest price")
    public String premiumBases = "netherite";  // base items (id contains one of these, comma separated) whose custom-enchant items are priced on their own, not with the cheap-item group
    public int minSellers = 3;               // a price only counts as real data if at least this many different sellers have it listed (stops one player faking an item or dragging the average)
    public double scamGuardPlainPercent = 100; // same guard but for un-renamed items (they cannot be faked). 100 = off, so 1-coin / missing-zero mistakes are NOT skipped
    public boolean autoSnipe = false;          // buy ANY un-renamed item listed far under its average, without setting it up in the Snipe List
    public double autoSnipePercent = 90;       // ...at least this % under the average
    public double autoSnipeMinAvg = 100000;    // ...and only if the item's average is at least this
    public double autoSnipeMaxSpend = 5000000; // ...and never spend more than this on one listing (0 = no limit)
    public boolean dealSound = true;                 // play a sound for good deals

    // --- GUI parsing (edit in config/snipe.json if your server's AH looks different) ---
    public String priceRegex = "(?i)price\\D*([0-9][0-9,]*\\.?[0-9]*)\\s*([kmbt]?)";
    public String sellerRegex = "(?i)(?:seller|sold by|owner|listed by)\\W+([A-Za-z0-9_]{3,16})";
    public String nextName = "next";
    public String confirmName = "confirm";

    public boolean resellOn() { return resell || afk; }

    public static Config I = new Config();
    static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    static Path path() { return FabricLoader.getInstance().getConfigDir().resolve("snipe.json"); }

    public static void load() {
        try { if (Files.exists(path())) I = GSON.fromJson(Files.readString(path()), Config.class); } catch (Exception e) { e.printStackTrace(); }
        if (I == null) I = new Config();
        if (I.entries == null) I.entries = new LinkedHashMap<>();
        if (I.sellCommand == null || I.sellCommand.trim().equals("ah sell {price}")) I.sellCommand = "ah hand {price}"; // old default
    }
    public static void save() { try { Files.writeString(path(), GSON.toJson(I)); } catch (Exception e) { e.printStackTrace(); } }

    public static String key(String name) { return name.toLowerCase().trim(); }
    /** Item identity = display name + base item type, so a renamed sword can never pass as a scroll. */
    public static String vkey(String name, String id) { return key(name) + "|" + id; }
    public static Entry entryOrNew(String key) { return I.entries.computeIfAbsent(key, k -> new Entry()); }

    public static double suffix(String s) {
        return switch (s.toLowerCase()) { case "k" -> 1e3; case "m" -> 1e6; case "b" -> 1e9; case "t" -> 1e12; default -> 1; };
    }
    /** Parses 16799111, 16,799,111, 16.8m, 2k. Returns -1 on failure. */
    public static double parseUser(String s) {
        Matcher m = Pattern.compile("([0-9][0-9,]*\\.?[0-9]*)\\s*([kKmMbBtT]?)").matcher(s.trim());
        if (!m.find()) return -1;
        try { return Double.parseDouble(m.group(1).replace(",", "")) * suffix(m.group(2)); } catch (Exception e) { return -1; }
    }
    public static String fmt(double v) {
        if (v >= 1e9) return String.format("%.2fB", v / 1e9);
        if (v >= 1e6) return String.format("%.2fM", v / 1e6);
        if (v >= 1e3) return String.format("%.1fK", v / 1e3);
        return String.format("%.0f", v);
    }
}
