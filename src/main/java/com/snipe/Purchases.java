package com.snipe;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;
import java.nio.file.*;
import java.util.*;

public class Purchases {
    public static class P {
        public String name, label, key, status = "bought";
        public int count, retries; public double total, listedFor; public long time, lastTry;
    }
    public static List<P> list = new ArrayList<>(); // newest first
    static Path path() { return FabricLoader.getInstance().getConfigDir().resolve("snipe_purchases.json"); }

    public static void load() {
        try { if (Files.exists(path())) list = new Gson().fromJson(Files.readString(path()), new TypeToken<List<P>>() {}.getType()); } catch (Exception e) { e.printStackTrace(); }
        if (list == null) list = new ArrayList<>();
    }
    public static void save() { try { Files.writeString(path(), new Gson().toJson(list)); } catch (Exception e) { e.printStackTrace(); } }
    public static void add(P p) { list.add(0, p); save(); }
}
