package com.snipe;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Every item you can snipe: all vanilla items + built-in Lifesteal.net custom items + everything the AH scan has seen. */
public class Catalog {
    public record Row(String key, String name, String id, boolean custom) {}
    static List<Row> vanilla, seeds;

    static void parse(String text, List<Row> out) {
        for (String line : text.split("\\R")) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            String[] p = line.split("\\|", 2);
            String id = p.length > 1 ? p[1].trim() : "";
            out.add(new Row(id.isEmpty() ? Config.key(p[0]) : Config.vkey(p[0], id), p[0].trim(), id, true));
        }
    }

    static void loadSeeds() {
        seeds = new ArrayList<>();
        try (var in = Catalog.class.getResourceAsStream("/assets/snipe/seed_items.txt")) {
            if (in != null) parse(new String(in.readAllBytes(), StandardCharsets.UTF_8), seeds);
        } catch (Exception e) { e.printStackTrace(); }
        Path extra = FabricLoader.getInstance().getConfigDir().resolve("snipe_extra_items.txt");
        try {
            if (!Files.exists(extra)) Files.writeString(extra, "# Add your own custom items here, one exact AH name per line\n");
            else parse(Files.readString(extra), seeds);
        } catch (Exception e) { e.printStackTrace(); }
    }

    public static List<Row> all() {
        if (vanilla == null) {
            vanilla = new ArrayList<>();
            for (Item item : BuiltInRegistries.ITEM) {
                if (item == Items.AIR) continue;
                String n = item.getDefaultInstance().getHoverName().getString();
                String vid = BuiltInRegistries.ITEM.getKey(item).getPath();
                vanilla.add(new Row(Config.vkey(n, vid), n, vid, false));
            }
        }
        if (seeds == null) loadSeeds();
        Map<String, Row> m = new LinkedHashMap<>();
        for (Row r : vanilla) m.putIfAbsent(r.key(), r);
        for (Row r : seeds) m.put(r.key(), r);
        for (var e : Market.d.info.entrySet()) {
            Market.Info i = e.getValue();
            m.put(e.getKey(), new Row(e.getKey(), i.name == null ? e.getKey() : i.name, i.id == null ? "" : i.id, i.custom));
        }
        for (String k : Config.I.entries.keySet()) m.putIfAbsent(k, new Row(k, k, "", true));
        // hide built-in guesses once the scan has found the real item with that name (unless you enabled the guess)
        Set<String> found = new HashSet<>();
        for (Market.Info i : Market.d.info.values()) if (i.name != null) found.add(Config.key(i.name));
        Set<String> seedKeys = new HashSet<>();
        for (Row r : seeds) seedKeys.add(r.key());
        m.values().removeIf(r -> seedKeys.contains(r.key()) && !Market.d.info.containsKey(r.key())
            && found.contains(Config.key(r.name())) && !(Config.I.entries.containsKey(r.key()) && Config.I.entries.get(r.key()).enabled));
        return new ArrayList<>(m.values());
    }
}
