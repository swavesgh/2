package com.snipe;

import com.google.gson.Gson;
import net.fabricmc.loader.api.FabricLoader;
import java.nio.file.*;
import java.util.*;

/** Everything learned from scanning the auction house. */
public class Market {
    public static class Info { public String name, id; public boolean custom; }
    public static class Snap {
        public long t; public double low, avg, median, high; public String lowSeller = "?";
        public int used, total, listings, sellers, items;
    }
    public static class Data {
        public Map<String, Info> info = new HashMap<>();
        public Map<String, Snap> latest = new HashMap<>();
        public Map<String, List<double[]>> history = new HashMap<>(); // [time, low, avg, median, high]
    }
    public record Listing(String key, String name, String id, boolean custom, double total, int count, String seller, String variant, String gname) {
        public double unit() { return total / Math.max(1, count); }
        /** Name plus a short tag for enchants / custom lore, e.g. "Diamond Chestplate [prot4 unbr3 mend1 Demolitionist]". */
        public String label() { return variant.isEmpty() ? name : name + " [" + shortVariant(variant) + "]"; }
        /** Name of the price group this listing belongs to (a custom enchant covers every cheap base item). */
        public String groupLabel() {
            if (variant.startsWith("ce:")) return shortVariant(variant) + (variant.contains("@") ? "" : " (any item)");
            return variant.isEmpty() ? gname : gname + " [" + shortVariant(variant) + "]";
        }
        public String iconId() { return variant.startsWith("ce:") && !variant.contains("@") ? "enchanted_book" : id; }
    }

    /** Compact version of a variant string (enchants abbreviated, first custom lore line kept). */
    public static String shortVariant(String v) {
        if (v.startsWith("ce:")) { String t = v.substring(3); int at = t.indexOf('@'); return at >= 0 ? t.substring(0, at) + " on " + t.substring(at + 1) : t; }
        StringBuilder sb = new StringBuilder(); boolean lore = false;
        for (String part : v.split(";")) {
            if (part.isBlank()) continue;
            if (part.matches("[a-z_:]+ \\d+")) {
                String[] a = part.split(" ");
                String n = a[0].substring(a[0].indexOf(':') + 1);
                sb.append(n.length() > 4 ? n.substring(0, 4) : n).append(a[1]).append(' ');
            } else if (!lore) { sb.append(part).append(' '); lore = true; }
        }
        return sb.toString().trim();
    }

    public static Data d = new Data();
    static int commits = 0;
    public static long lastCommit = 0;
    static Path path() { return FabricLoader.getInstance().getConfigDir().resolve("snipe_market3.json"); }

    public static void load() {
        try { if (Files.exists(path())) d = new Gson().fromJson(Files.readString(path()), Data.class); } catch (Exception e) { e.printStackTrace(); }
        if (d == null) d = new Data();
        if (d.info == null) d.info = new HashMap<>();
        if (d.latest == null) d.latest = new HashMap<>();
        if (d.history == null) d.history = new HashMap<>();
    }
    public static void save() { try { Files.writeString(path(), new Gson().toJson(d)); } catch (Exception e) { e.printStackTrace(); } }

    /** Turn a full scan's listings into per-item stats + history. */
    public static void commit(Collection<Listing> all) {
        Map<String, List<Listing>> by = new HashMap<>();
        for (Listing l : all) by.computeIfAbsent(l.key(), k -> new ArrayList<>()).add(l);
        lastCommit = System.currentTimeMillis();
        long now = System.currentTimeMillis(), cutoff = now - 7L * 86400_000L;
        for (var e : by.entrySet()) {
            List<Listing> ls = e.getValue();
            Listing f = ls.get(0);
            Info inf = d.info.computeIfAbsent(e.getKey(), k -> new Info());
            inf.name = f.groupLabel(); inf.id = f.iconId(); inf.custom |= f.custom();

            List<Double> u = new ArrayList<>();
            for (Listing l : ls) u.add(l.unit());
            Collections.sort(u);
            Listing lowL = Collections.min(ls, Comparator.comparingDouble(Listing::unit));

            Snap s = new Snap();
            s.t = now; s.low = lowL.unit(); s.lowSeller = lowL.seller();
            s.median = u.get(u.size() / 2); s.high = u.get(u.size() - 1);
            double q1 = u.get(u.size() / 4), q3 = u.get(u.size() * 3 / 4), iqr = q3 - q1;
            double lo = q1 - 1.5 * iqr, hi = q3 + 1.5 * iqr, sum = 0; int used = 0;
            for (double x : u) if (u.size() < 4 || (x >= lo && x <= hi)) { sum += x; used++; }
            s.avg = used > 0 ? sum / used : s.median; s.used = used; s.total = u.size();
            s.listings = ls.size();
            Set<String> sellers = new HashSet<>(); int items = 0;
            for (Listing l : ls) { sellers.add(l.seller()); items += l.count(); }
            s.sellers = sellers.size(); s.items = items;
            d.latest.put(e.getKey(), s);

            List<double[]> h = d.history.computeIfAbsent(e.getKey(), k -> new ArrayList<>());
            h.add(new double[]{now, s.low, s.avg, s.median, s.high});
            h.removeIf(x -> x[0] < cutoff);
            while (h.size() > 1500) h.remove(0);
        }
        if (++commits % 5 == 0) save();
    }

    public static Snap latest(String key) { return d.latest.get(key); }

    public static List<double[]> window(String key, long ms) {
        List<double[]> out = new ArrayList<>();
        long cutoff = System.currentTimeMillis() - ms;
        for (double[] s : d.history.getOrDefault(key, List.of())) if (s[0] >= cutoff) out.add(s);
        return out;
    }
}
