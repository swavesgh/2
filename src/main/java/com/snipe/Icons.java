package com.snipe;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import java.util.*;

/** Maps an item id (e.g. "diamond_sword") to a stack we can draw as an icon. */
public class Icons {
    static Map<String, ItemStack> cache;

    public static ItemStack get(String id) {
        if (id == null || id.isEmpty()) return ItemStack.EMPTY;
        if (cache == null) {
            cache = new HashMap<>();
            for (Item item : BuiltInRegistries.ITEM) {
                if (item == Items.AIR) continue;
                cache.put(BuiltInRegistries.ITEM.getKey(item).getPath(), item.getDefaultInstance());
            }
        }
        return cache.getOrDefault(id, ItemStack.EMPTY);
    }

    /** Item id out of a "name|id" key. */
    public static String idOfKey(String key) {
        if (key == null) return "";
        String[] p = key.split("\\|", 3);
        return p.length >= 2 ? p[1] : "";
    }
}
