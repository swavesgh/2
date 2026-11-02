package com.snipe;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.*;

public class SnipeScreen extends Screen {
    static final int W = 440, H = 254;
    // palette
    static final int BG = 0xFF0E1220, BAR = 0xFF0A0E1A, CARD = 0xFF121828, ROW = 0xFF151B2C, TILE = 0xFF161C2E, BORDER = 0xFF252D47;
    static final int ACCENT = 0xFF4C8DFF, GREEN = 0xFF4ADE80, RED = 0xFFF87171, AMBER = 0xFFFBBF24, TEXT = 0xFFE6EAF5, MUTED = 0xFF7C86A2, PINK = 0xFFE9A8FF;

    static int tab = 0, listPage = 0, window = 1, sort = 0, selPurchase = -1, filter = 0;
    static String selKey = null, search = "";
    static boolean eachFlag = true;
    static final Set<String> checked = new LinkedHashSet<>();
    static String toast = ""; static long toastUntil = 0;
    static final long[] WINDOWS = {3600_000L, 86400_000L, 7 * 86400_000L};
    static final String[] WNAMES = {"Past hour", "Past 24h", "Past 7 days"};
    static final String[] SORTS = {"Priciest", "Cheapest", "A-Z", "Listings"};
    static final String[] FILTERS = {"All", "Custom", "Listed"};
    static final String[] MODES = {"LOWEST", "AVERAGE", "MEDIAN"};

    // button looks
    static final int K_NORMAL = 0, K_TAB = 1, K_START = 2, K_SEG = 3, K_TOGGLE = 4, K_ACCENT = 5, K_DANGER = 6;
    static class Skin { Button b; int kind; BooleanSupplier active; Skin(Button b, int kind, BooleanSupplier a) { this.b = b; this.kind = kind; this.active = a; } }
    final List<Skin> skins = new ArrayList<>();
    final List<EditBox> boxes = new ArrayList<>();

    int x0, y0;
    List<Catalog.Row> allRows = List.of(), view = List.of();
    Map<String, Integer> nameCount = new HashMap<>();
    Button[] rows = new Button[8], chks = new Button[8], winBtns = new Button[3];
    String[] rName = new String[8], rRight = new String[8];
    boolean[] rOn = new boolean[8], rChk = new boolean[8], rSel = new boolean[8];
    int[] rCol = new int[8];
    Button sortBtn, addBtn, delBtn, eachBtn, filterBtn;
    EditBox searchBox, priceBox;
    String pageLabel = "1/1";
    List<Object[]> labels = new ArrayList<>();

    public SnipeScreen() { super(Component.literal("AH Sniper")); }

    // ---------------------------------------------------------------- layout helpers
    Button raw(Component c, int x, int y, int w, int h, Runnable r) {
        Button b = Button.builder(c, bb -> r.run()).bounds(x, y, w, h).build();
        addRenderableWidget(b);
        return b;
    }
    Button btn(Component c, int x, int y, int w, int h, Runnable r) { return btn(c, x, y, w, h, K_NORMAL, null, r); }
    Button btn(Component c, int x, int y, int w, int h, int kind, BooleanSupplier active, Runnable r) {
        Button b = raw(c, x, y, w, h, r);
        skins.add(new Skin(b, kind, active));
        return b;
    }
    EditBox box(int x, int y, int w, int h, String name) {
        EditBox e = new EditBox(font, x, y, w, h, txt(name));
        addRenderableWidget(e); boxes.add(e);
        return e;
    }
    Component txt(String s) { return Component.literal(s); }
    void toast(String s) { toast = s; toastUntil = System.currentTimeMillis() + 6000; }

    @Override protected void init() {
        x0 = (width - W) / 2; y0 = (height - H) / 2; labels.clear(); skins.clear(); boxes.clear();
        String[] tn = {"Snipe List", "Economy", "Purchases", "Settings"};
        int[] tw = {72, 60, 66, 58};
        int tx = x0 + 6;
        for (int i = 0; i < 4; i++) {
            final int idx = i;
            btn(txt(tn[i]), tx, y0 + 5, tw[i], 18, K_TAB, () -> tab == idx, () -> { tab = idx; listPage = 0; rebuildWidgets(); });
            tx += tw[i] + 4;
        }
        btn(txt("Analyze"), x0 + 280, y0 + 5, 58, 18, () -> { Sniper.analyze(); rebuildWidgets(); });
        btn(txt(Sniper.running ? "Stop" : "Start sniping"), x0 + W - 96, y0 + 5, 90, 18, K_START, null,
            () -> { if (Sniper.running) Sniper.stop(); else Sniper.start(); rebuildWidgets(); });

        if (tab <= 1) initItems(); else if (tab == 2) initPurchases(); else initSettings();
    }

    void makeRows(boolean checks) {
        for (int i = 0; i < 8; i++) {
            final int idx = i;
            if (checks) {
                chks[i] = raw(Component.empty(), x0 + 6, y0 + 50 + i * 20, 16, 18, () -> toggleCheck(idx));
                rows[i] = raw(Component.empty(), x0 + 22, y0 + 50 + i * 20, 160, 18, () -> selectRow(idx));
            } else {
                chks[i] = null;
                rows[i] = raw(Component.empty(), x0 + 6, y0 + 50 + i * 20, 176, 18, () -> selectRow(idx));
            }
        }
        btn(txt("<"), x0 + 6, y0 + 212, 20, 16, () -> { listPage--; refreshList(); });
        btn(txt(">"), x0 + 28, y0 + 212, 20, 16, () -> { listPage++; refreshList(); });
    }

    void initItems() {
        allRows = Catalog.all();
        nameCount.clear();
        for (Catalog.Row r : allRows) nameCount.merge(Config.key(r.name()), 1, Integer::sum);
        searchBox = box(x0 + 6, y0 + 30, 62, 16, "Search");
        searchBox.setHint(txt("Search..."));
        searchBox.setValue(search);
        searchBox.setResponder(s -> { if (!s.equals(search)) { search = s; listPage = 0; refreshList(); } });
        filterBtn = btn(txt(FILTERS[filter]), x0 + 72, y0 + 30, 50, 16, () -> {
            filter = (filter + 1) % FILTERS.length; filterBtn.setMessage(txt(FILTERS[filter])); listPage = 0; refreshList(); });
        sortBtn = btn(txt(SORTS[sort]), x0 + 126, y0 + 30, 56, 16, () -> {
            sort = (sort + 1) % SORTS.length; sortBtn.setMessage(txt(SORTS[sort])); listPage = 0; refreshList(); });
        makeRows(true);
        btn(txt("All"), x0 + 94, y0 + 212, 40, 16, () -> {
            for (Catalog.Row r : view) checked.add(r.key());
            toast("Ticked " + String.format("%,d", view.size()) + " items. Type an amount and press Add.");
            refreshList(); refreshDetail(); });
        btn(txt("None"), x0 + 138, y0 + 212, 44, 16, () -> { checked.clear(); refreshList(); refreshDetail(); });
        for (int i = 0; i < 3; i++) {
            final int idx = i;
            winBtns[i] = btn(txt(WNAMES[i]), x0 + 192 + i * 82, y0 + 30, 78, 16, K_SEG, () -> window == idx, () -> { window = idx; });
        }

        priceBox = box(x0 + 238, y0 + 210, 74, 18, "Price");
        priceBox.setHint(txt("50k or 80%"));
        eachBtn = btn(txt("each"), x0 + 316, y0 + 210, 38, 18, () -> { eachFlag = !eachFlag; eachBtn.setMessage(txt(eachFlag ? "each" : "total")); });
        addBtn = btn(txt("Add"), x0 + 358, y0 + 210, 44, 18, K_ACCENT, null, this::addSnipe);
        delBtn = btn(txt("Off"), x0 + 406, y0 + 210, 28, 18, K_DANGER, null, this::removeSnipe);
        refreshList(); suggestPrice(); refreshDetail();
    }

    void initPurchases() {
        makeRows(false); refreshList();
        btn(txt("Sell selected"), x0 + 192, y0 + 210, 90, 18, K_ACCENT, null, () -> {
            if (selPurchase < 0 || selPurchase >= Purchases.list.size()) return;
            Purchases.P p = Purchases.list.get(selPurchase);
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            Compat.open(mc, null); Seller.sellPurchase(mc, p);
        });
        btn(txt("Sell hand"), x0 + 286, y0 + 210, 66, 18, () -> {
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            Compat.open(mc, null); Seller.sellHeld(mc);
        });
    }

    // ------------------------------------------------------------ settings tab
    void toggle(String label, int x, int y, Supplier<String> text, Runnable act) {
        Button b = Button.builder(txt(text.get()), bb -> { act.run(); bb.setMessage(txt(text.get())); Config.save(); }).bounds(x + 112, y, 92, 18).build();
        addRenderableWidget(b); skins.add(new Skin(b, K_TOGGLE, null)); labels.add(new Object[]{label, x, y + 5});
    }
    void field(String label, int x, int y, String val, Consumer<String> on) {
        EditBox e = box(x + 112, y, 92, 18, label);
        e.setMaxLength(64); e.setValue(val); e.setResponder(on);
        labels.add(new Object[]{label, x, y + 5});
    }
    void num(String label, int x, int y, double val, DoubleConsumer on) {
        field(label, x, y, val == Math.floor(val) ? String.valueOf((long) val) : String.valueOf(val), s -> { try { on.accept(Double.parseDouble(s.trim())); } catch (Exception ignored) {} });
    }
    void inum(String label, int x, int y, int val, IntConsumer on) {
        field(label, x, y, String.valueOf(val), s -> { try { on.accept(Integer.parseInt(s.trim())); } catch (Exception ignored) {} });
    }

    void initSettings() {
        int ax = x0 + 8, bx = x0 + 226, y = y0 + 32, st = 20;
        Config c = Config.I;
        toggle("Buy mode", ax, y, () -> c.buyEnabled ? "ON (buys)" : "OFF (scan)", () -> c.buyEnabled = !c.buyEnabled);
        toggle("Auto resell", ax, y + st, () -> c.resell ? "ON" : "OFF", () -> c.resell = !c.resell);
        toggle("Sell/resell price", ax, y + st * 2, () -> switch (c.resellMode) { case "AVERAGE" -> "Average"; case "MEDIAN" -> "Median"; default -> "Lowest BIN"; },
            () -> c.resellMode = MODES[(Arrays.asList(MODES).indexOf(c.resellMode) + 1) % MODES.length]);
        num("Undercut %", ax, y + st * 3, c.undercutPercent, v -> c.undercutPercent = v);
        num("Min profit %", ax, y + st * 4, c.minProfitPercent, v -> c.minProfitPercent = v);
        num("AH tax %", ax, y + st * 5, c.ahTaxPercent, v -> c.ahTaxPercent = v);
        toggle("Analyze first", ax, y + st * 6, () -> c.analyzeFirst ? "ON" : "OFF", () -> c.analyzeFirst = !c.analyzeFirst);
        toggle("Deal alerts", ax, y + st * 7, () -> c.alerts ? "ON" : "OFF", () -> c.alerts = !c.alerts);
        toggle("Need price data", ax, y + st * 8, () -> c.requireData ? "ON" : "OFF", () -> c.requireData = !c.requireData);
        toggle("AFK mode", ax, y + st * 9, () -> c.afk ? "ON" : "OFF", () -> c.afk = !c.afk);
        inum("Max pages", bx, y, c.maxPages, v -> c.maxPages = v);
        inum("Click delay", bx, y + st, c.delayTicks, v -> c.delayTicks = v);
        inum("Refresh wait", bx, y + st * 2, c.refreshTicks, v -> c.refreshTicks = v);
        inum("Resell wait", bx, y + st * 3, c.resellWaitTicks, v -> c.resellWaitTicks = v);
        field("AH command", bx, y + st * 4, c.command, s -> c.command = s.trim());
        field("Sell command", bx, y + st * 5, c.sellCommand, s -> c.sellCommand = s);
        num("Alert % under", bx, y + st * 6, c.alertPercent, v -> c.alertPercent = v);
        num("Alert min avg", bx, y + st * 7, c.alertMinPrice, v -> c.alertMinPrice = v);
        num("Scam guard %", bx, y + st * 8, c.scamGuardPercent, v -> c.scamGuardPercent = v);
        num("Max unsold", bx, y + st * 9, c.maxUnsold, v -> c.maxUnsold = v);
    }

    // ------------------------------------------------------------ list logic
    boolean enabled(String key) { Config.Entry e = Config.I.entries.get(key); return e != null && e.enabled; }
    double avg(Catalog.Row r) { Market.Snap s = Market.latest(r.key()); return s == null ? 0 : s.avg; }
    int listings(Catalog.Row r) { Market.Snap s = Market.latest(r.key()); return s == null ? 0 : s.listings; }

    Comparator<Catalog.Row> comparator() {
        return switch (sort) {
            case 0 -> Comparator.comparingDouble((Catalog.Row r) -> -avg(r));
            case 1 -> Comparator.comparingDouble((Catalog.Row r) -> avg(r) <= 0 ? Double.MAX_VALUE : avg(r));
            case 2 -> Comparator.comparing((Catalog.Row r) -> r.name().toLowerCase());
            default -> Comparator.comparingInt((Catalog.Row r) -> -listings(r));
        };
    }

    String cut(String s, int n) { return s.length() > n ? s.substring(0, n - 1) + "..." : s; }
    /** Shorten s with "..." until it fits in px pixels. */
    String fit(String s, int px) {
        if (font.width(s) <= px) return s;
        while (s.length() > 1 && font.width(s + "...") > px) s = s.substring(0, s.length() - 1);
        return s + "...";
    }

    void refreshList() {
        if (tab <= 1) {
            String q = search.toLowerCase();
            List<Catalog.Row> v = new ArrayList<>();
            for (Catalog.Row r : allRows) {
                if (tab == 0 && !enabled(r.key())) continue;
                if (filter == 1 && !r.custom()) continue;
                if (filter == 2 && Market.latest(r.key()) == null) continue;
                if (!q.isEmpty() && !r.name().toLowerCase().contains(q)) continue;
                v.add(r);
            }
            v.sort(comparator());
            view = v;
        }
        int n = tab <= 1 ? view.size() : Purchases.list.size();
        int pages = Math.max(1, (n + 7) / 8);
        listPage = Math.max(0, Math.min(listPage, pages - 1));
        for (int i = 0; i < 8; i++) {
            int idx = listPage * 8 + i;
            Button b = rows[i];
            if (b == null) continue;
            boolean vis = idx < n;
            b.visible = vis;
            if (chks[i] != null) chks[i].visible = vis;
            if (!vis) continue;
            if (tab <= 1) {
                Catalog.Row r = view.get(idx);
                Market.Snap s = Market.latest(r.key());
                boolean dup = nameCount.getOrDefault(Config.key(r.name()), 1) > 1 && !r.id().isEmpty();
                rName[i] = dup ? r.name() + " (" + r.id() + ")" : r.name();
                rRight[i] = s == null ? "-" : Config.fmt(s.avg);
                rOn[i] = enabled(r.key()); rChk[i] = checked.contains(r.key()); rSel[i] = r.key().equals(selKey); rCol[i] = TEXT;
            } else {
                Purchases.P p = Purchases.list.get(idx);
                rName[i] = (p.label != null ? p.label : p.name) + " x" + p.count;
                rRight[i] = Config.fmt(p.total);
                rOn[i] = p.status.startsWith("listed"); rChk[i] = false; rSel[i] = idx == selPurchase;
                rCol[i] = p.status.startsWith("listed") ? GREEN : p.status.startsWith("held") ? AMBER : TEXT;
            }
            b.setMessage(txt(rName[i]));
        }
        pageLabel = (listPage + 1) + "/" + pages;
    }

    void selectRow(int i) {
        int idx = listPage * 8 + i;
        if (tab <= 1) { if (idx < view.size()) { selKey = view.get(idx).key(); suggestPrice(); refreshList(); refreshDetail(); } }
        else { selPurchase = idx; refreshList(); }
    }
    void toggleCheck(int i) {
        int idx = listPage * 8 + i;
        if (idx >= view.size()) return;
        String k = view.get(idx).key();
        if (!checked.remove(k)) checked.add(k);
        refreshList(); refreshDetail();
    }

    void refreshDetail() {
        if (priceBox == null) return;
        Config.Entry e = selKey == null ? null : Config.I.entries.get(selKey);
        boolean on = e != null && e.enabled;
        eachBtn.setMessage(txt(eachFlag ? "each" : "total"));
        int n = checked.size();
        addBtn.setMessage(txt(n > 0 ? "Add " + n : on ? "Save" : "Add"));
        delBtn.visible = n > 0 || on;
    }

    /** Fill the price box with a suggestion for the selected item (80% of avg) unless the user already typed. */
    void suggestPrice() {
        if (priceBox == null || selKey == null) return;
        Config.Entry e = Config.I.entries.get(selKey);
        Market.Snap s = Market.latest(selKey);
        if (e != null && e.maxPrice > 0) { priceBox.setValue(String.valueOf((long) e.maxPrice)); eachFlag = e.each; }
        else priceBox.setValue(s != null ? String.valueOf((long) (s.avg * 0.8)) : "");
    }

    /** Add / Save: applies to every ticked item, or to the selected one when nothing is ticked. */
    void addSnipe() {
        String t = priceBox.getValue().trim();
        boolean pct = t.endsWith("%");
        double v = Config.parseUser(t.replace("%", ""));
        if (v <= 0) { toast("Type an amount first, e.g. 50k, 1.5m or 80% (of each item's average)."); return; }
        List<String> keys = checked.isEmpty() ? (selKey == null ? List.of() : List.of(selKey)) : new ArrayList<>(checked);
        if (keys.isEmpty()) { toast("Select an item, or tick some boxes first."); return; }
        int n = 0, skipped = 0;
        for (String k : keys) {
            double p = v; boolean each = eachFlag;
            if (pct) {
                Market.Snap s = Market.latest(k);
                if (s == null || s.avg <= 0) { skipped++; continue; }
                p = Math.floor(s.avg * v / 100); each = true;
            }
            Config.Entry e = Config.entryOrNew(k);
            e.enabled = true; e.maxPrice = p; e.each = each; n++;
        }
        Config.save();
        toast("Sniping " + String.format("%,d", n) + " item" + (n == 1 ? "" : "s") + (pct ? " at " + Math.round(v) + "% of average" : " at " + Config.fmt(v) + (eachFlag ? " each" : " total"))
            + (skipped > 0 ? " (" + skipped + " skipped: no price data)" : ""));
        checked.clear();
        refreshList(); refreshDetail();
    }
    void removeSnipe() {
        List<String> keys = checked.isEmpty() ? (selKey == null ? List.of() : List.of(selKey)) : new ArrayList<>(checked);
        int n = 0;
        for (String k : keys) { Config.Entry e = Config.I.entries.get(k); if (e != null && e.enabled) { e.enabled = false; n++; } }
        Config.save();
        toast("Stopped sniping " + n + " item" + (n == 1 ? "" : "s"));
        checked.clear();
        refreshList(); refreshDetail();
    }

    @Override public void removed() { Config.save(); }
    @Override public boolean isPauseScreen() { return false; }

    // ---------------------------------------------------------------- drawing helpers
    static void rr(GuiGraphicsExtractor g, int x, int y, int w, int h, int c) {
        g.fill(x + 1, y, x + w - 1, y + h, c);
        g.fill(x, y + 1, x + w, y + h - 1, c);
    }
    static void outline(GuiGraphicsExtractor g, int x, int y, int w, int h, int c) {
        g.fill(x, y, x + w, y + 1, c); g.fill(x, y + h - 1, x + w, y + h, c);
        g.fill(x, y, x + 1, y + h, c); g.fill(x + w - 1, y, x + w, y + h, c);
    }
    void center(GuiGraphicsExtractor g, String s, int x, int y, int w, int h, int c) {
        g.text(font, s, x + (w - font.width(s)) / 2, y + (h - 8) / 2, c);
    }
    void tile(GuiGraphicsExtractor g, int x, int y, int w, String label, String value, int vc) {
        rr(g, x, y, w, 28, TILE);
        g.text(font, label, x + 5, y + 4, MUTED);
        g.text(font, fit(value, w - 8), x + 5, y + 15, vc);
    }

    void drawSkins(GuiGraphicsExtractor g, int mx, int my) {
        for (Skin s : skins) {
            Button b = s.b;
            if (!b.visible) continue;
            int x = b.getX(), y = b.getY(), w = b.getWidth(), h = b.getHeight();
            boolean hov = mx >= x && mx < x + w && my >= y && my < y + h;
            String t = b.getMessage().getString();
            boolean act = s.active != null && s.active.getAsBoolean();
            switch (s.kind) {
                case K_TAB -> {
                    if (act) rr(g, x, y, w, h, 0xFF1A2340); else if (hov) rr(g, x, y, w, h, 0xFF141B30);
                    if (act) g.fill(x + 4, y + h - 2, x + w - 4, y + h, ACCENT);
                    center(g, t, x, y, w, h - 1, act ? 0xFF9CC0FF : hov ? TEXT : MUTED);
                }
                case K_START -> {
                    boolean run = Sniper.running;
                    rr(g, x, y, w, h, run ? (hov ? 0xFFEF4444 : 0xFFDC2626) : (hov ? 0xFF22C55E : 0xFF16A34A));
                    center(g, t, x, y, w, h, 0xFFFFFFFF);
                }
                case K_SEG -> {
                    rr(g, x, y, w, h, act ? 0xFF2B4B8F : hov ? 0xFF222B45 : 0xFF181F33);
                    center(g, t, x, y, w, h, act ? 0xFFFFFFFF : MUTED);
                }
                case K_TOGGLE -> {
                    boolean on = t.startsWith("ON"), off = t.startsWith("OFF");
                    rr(g, x, y, w, h, on ? (hov ? 0xFF166534 : 0xFF14532D) : off ? (hov ? 0xFF2F3650 : 0xFF272D42) : (hov ? 0xFF2A3454 : 0xFF202843));
                    center(g, t, x, y, w, h, on ? 0xFF86EFAC : off ? 0xFF8A93AD : TEXT);
                }
                case K_ACCENT -> {
                    rr(g, x, y, w, h, hov ? 0xFF3B82F6 : 0xFF2563EB);
                    center(g, t, x, y, w, h, 0xFFFFFFFF);
                }
                case K_DANGER -> {
                    rr(g, x, y, w, h, hov ? 0xFF7F2323 : 0xFF5B1A1A);
                    center(g, t, x, y, w, h, 0xFFFCA5A5);
                }
                default -> {
                    rr(g, x, y, w, h, hov ? 0xFF2A3454 : 0xFF1C2338);
                    center(g, t, x, y, w, h, TEXT);
                }
            }
        }
        for (EditBox e : boxes) {
            if (!e.visible) continue;
            outline(g, e.getX(), e.getY(), e.getWidth(), e.getHeight(), e.isFocused() ? ACCENT : BORDER);
        }
    }

    void drawRows(GuiGraphicsExtractor g, int mx, int my) {
        boolean checks = tab <= 1;
        int n = checks ? view.size() : Purchases.list.size();
        for (int i = 0; i < 8; i++) {
            int idx = listPage * 8 + i;
            if (idx >= n) break;
            int rx = x0 + 6, ry = y0 + 50 + i * 20, rw = 176;
            boolean hov = mx >= rx && mx < rx + rw && my >= ry && my < ry + 18;
            rr(g, rx, ry, rw, 18, rSel[i] ? 0xFF1F2D52 : hov ? 0xFF1B2338 : ROW);
            if (rSel[i]) g.fill(rx, ry + 2, rx + 2, ry + 16, ACCENT);
            else if (rOn[i]) g.fill(rx, ry + 2, rx + 2, ry + 16, GREEN);
            int ix = rx + 3;
            if (checks) {
                int cx = rx + 5, cy = ry + 4;
                outline(g, cx, cy, 10, 10, rChk[i] ? ACCENT : 0xFF3A4466);
                if (rChk[i]) g.fill(cx + 2, cy + 2, cx + 8, cy + 8, ACCENT);
                ix = rx + 19;
            }
            String id = checks ? view.get(idx).id() : Icons.idOfKey(Purchases.list.get(idx).key);
            ItemStack st = Icons.get(id);
            if (!st.isEmpty()) Compat.drawItem(g, st, ix, ry + 1);
            int nx = ix + 20;
            int rw2 = font.width(rRight[i]);
            g.text(font, rRight[i], rx + rw - 5 - rw2, ry + 5, rOn[i] && checks ? GREEN : MUTED);
            int maxName = rx + rw - 5 - rw2 - 6 - nx;
            String name = rName[i];
            int vb = name.indexOf(" [");
            if (vb > 0) {
                String base = fit(name.substring(0, vb), Math.max(20, maxName * 2 / 3));
                g.text(font, base, nx, ry + 5, rCol[i]);
                int bw = font.width(base) + 4;
                g.text(font, fit(name.substring(vb + 1), Math.max(0, maxName - bw)), nx + bw, ry + 5, PINK);
            } else g.text(font, fit(name, maxName), nx, ry + 5, rCol[i]);
        }
    }

    // ---------------------------------------------------------------- drawing
    @Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float dt) {
        // window
        g.fill(x0 - 1, y0 - 1, x0 + W + 1, y0 + H + 1, 0xFF05070D);
        rr(g, x0, y0, W, H, BG);
        g.fill(x0, y0, x0 + W, y0 + 28, BAR);
        g.fill(x0, y0 + 28, x0 + W, y0 + 29, BORDER);
        g.fill(x0, y0 + H - 18, x0 + W, y0 + H, BAR);
        g.fill(x0, y0 + H - 18, x0 + W, y0 + H - 17, BORDER);
        if (tab <= 2) {
            rr(g, x0 + 4, y0 + 30 + 0, 182, H - 52, CARD);
            rr(g, x0 + 188, y0 + 30, W - 192, H - 52, CARD);
        } else {
            rr(g, x0 + 4, y0 + 30, 216, H - 52, CARD);
            rr(g, x0 + 222, y0 + 30, W - 226, H - 52, CARD);
            for (int i = 0; i < 10; i++) if (i % 2 == 0) {
                g.fill(x0 + 6, y0 + 31 + i * 20, x0 + 218, y0 + 51 + i * 20, 0x0CFFFFFF);
                g.fill(x0 + 224, y0 + 31 + i * 20, x0 + W - 6, y0 + 51 + i * 20, 0x0CFFFFFF);
            }
        }
        super.extractRenderState(g, mx, my, dt);
        drawSkins(g, mx, my);
        if (tab <= 2) drawRows(g, mx, my);

        if (tab <= 1) { drawItem(g); g.text(font, pageLabel, x0 + 54, y0 + 217, MUTED); }
        else if (tab == 2) { drawPurchases(g); g.text(font, pageLabel, x0 + 54, y0 + 217, MUTED); }
        else for (Object[] l : labels) g.text(font, (String) l[0], (Integer) l[1], (Integer) l[2], 0xFFC9D1E6);

        // footer
        boolean stopped = Sniper.status.startsWith("STOPPED");
        g.fill(x0 + 8, y0 + H - 12, x0 + 14, y0 + H - 6, stopped ? RED : Sniper.running ? GREEN : 0xFF5B647D);
        String foot = System.currentTimeMillis() < toastUntil ? toast : Sniper.status + (Config.I.buyEnabled ? "" : "   [buy mode off]");
        g.text(font, fit(foot, W - 40), x0 + 20, y0 + H - 13, System.currentTimeMillis() < toastUntil ? 0xFF9CC0FF : MUTED);
    }

    Catalog.Row findRow(String key) { for (Catalog.Row r : allRows) if (r.key().equals(key)) return r; return null; }

    void drawItem(GuiGraphicsExtractor g) {
        int rx = x0 + 194, y = y0 + 52;
        if (selKey == null) {
            g.text(font, "Select an item on the left", rx, y, TEXT);
            g.text(font, tab == 1 ? "Tick boxes to bulk-add. Type an amount (50k) or a % of average (80%), then Add." : "Your enabled snipes show here.", rx, y + 12, MUTED);
            if (tab == 1) g.text(font, fit("Tip: set the filter to Listed to tick only items with price data.", 236), rx, y + 24, MUTED);
            g.text(font, "Snipe at", rx, y0 + 215, MUTED);
            return;
        }
        Catalog.Row r = findRow(selKey);
        ItemStack ic = r != null ? Icons.get(r.id()) : ItemStack.EMPTY;
        int tx = ic.isEmpty() ? rx : rx + 22;
        if (!ic.isEmpty()) Compat.drawItem(g, ic, rx, y - 2);
        String full = r != null ? r.name() : selKey;
        int vb = full.indexOf(" [");
        g.text(font, fit(vb > 0 ? full.substring(0, vb) : full, 214), tx, y, 0xFF67E8F9);
        g.text(font, fit(vb > 0 ? full.substring(vb + 2).replace("]", "")
            : (r != null && !r.id().isEmpty() ? r.id() + "  -  " : "") + (r != null && r.custom() ? "custom item" : "vanilla item"), 214), tx, y + 11, vb > 0 ? PINK : MUTED);
        Market.Snap s = Market.latest(selKey);
        if (s == null) {
            g.text(font, "No market data yet.", rx, y + 34, AMBER);
            g.text(font, "Press Analyze (or Start with buy mode off) to scan the AH.", rx, y + 46, MUTED);
            g.text(font, "Snipe at", rx, y0 + 215, MUTED);
            return;
        }
        int ty = y0 + 74;
        tile(g, rx, ty, 56, "LOWEST", Config.fmt(s.low), GREEN);
        tile(g, rx + 59, ty, 56, "AVERAGE", Config.fmt(s.avg), AMBER);
        tile(g, rx + 118, ty, 56, "MEDIAN", Config.fmt(s.median), TEXT);
        tile(g, rx + 177, ty, 56, "HIGHEST", Config.fmt(s.high), RED);
        g.text(font, fit("Listings " + s.listings + "   Sellers " + s.sellers + "   Items " + s.items + "   Lowest by " + s.lowSeller, 234), rx, y0 + 108, MUTED);
        drawChart(g, rx, y0 + 121, 234, 74);
        g.text(font, "bars = lowest price, yellow line = average", rx, y0 + 198, 0xFF55607C);
        g.text(font, "Snipe at", rx, y0 + 215, MUTED);
    }

    void drawChart(GuiGraphicsExtractor g, int cx, int cy, int cw, int ch) {
        rr(g, cx, cy, cw, ch, 0xFF0A0E1A);
        List<double[]> h = Market.window(selKey, WINDOWS[window]);
        if (h.isEmpty()) { g.text(font, "no samples in this window yet", cx + 6, cy + 6, MUTED); return; }
        int n = Math.min(h.size(), 40);
        List<double[]> w = h.subList(h.size() - n, h.size());
        double wavg = 0, max = 0;
        for (double[] x : w) { wavg += x[2]; max = Math.max(max, x[1]); }
        wavg /= n; max = Math.max(max, wavg) * 1.05;
        int bw = Math.max(2, Math.min(12, (cw - 8) / n - 1));
        for (int i = 0; i < n; i++) {
            int bx = cx + cw - 4 - (n - i) * (bw + 1);
            int hh = Math.max(1, (int) (w.get(i)[1] / max * (ch - 6)));
            g.fill(bx, cy + ch - 3 - hh, bx + bw, cy + ch - 3, w.get(i)[1] <= wavg ? 0xFF4ADE80 : 0xFFF59E0B);
        }
        int ly = cy + ch - 3 - (int) (wavg / max * (ch - 6));
        g.fill(cx + 2, ly, cx + cw - 2, ly + 1, 0xFFFDE047);
    }

    void drawPurchases(GuiGraphicsExtractor g) {
        double spent = 0, exp = 0; int listed = 0;
        for (Purchases.P p : Purchases.list) {
            spent += p.total;
            if (p.listedFor > 0) { listed++; exp += p.listedFor * (1 - Config.I.ahTaxPercent / 100) - p.total; }
        }
        g.text(font, Purchases.list.size() + " purchases (newest first)", x0 + 8, y0 + 34, MUTED);
        int rx = x0 + 194;
        tile(g, rx, y0 + 34, 76, "SPENT", Config.fmt(spent), TEXT);
        tile(g, rx + 79, y0 + 34, 76, "RELISTED", listed + " / " + Purchases.list.size(), AMBER);
        tile(g, rx + 158, y0 + 34, 76, "PROFIT IF SOLD", (exp < 0 ? "-" : "+") + Config.fmt(Math.abs(exp)), exp >= 0 ? GREEN : RED);
        g.text(font, fit("Or hold any item and type /" + Config.I.command + " hand (auto price)", 236), rx, y0 + 196, 0xFF55607C);
        g.fill(rx, y0 + 68, x0 + W - 8, y0 + 69, BORDER);
        if (selPurchase < 0 || selPurchase >= Purchases.list.size()) { g.text(font, "Select a purchase on the left.", rx, y0 + 80, MUTED); return; }
        Purchases.P p = Purchases.list.get(selPurchase);
        int py = y0 + 78;
        ItemStack pic = Icons.get(Icons.idOfKey(p.key));
        int ptx = pic.isEmpty() ? rx : rx + 22;
        if (!pic.isEmpty()) Compat.drawItem(g, pic, rx, py - 2);
        g.text(font, fit(p.name, 212), ptx, py, 0xFF67E8F9);
        g.text(font, LocalDateTime.ofInstant(Instant.ofEpochMilli(p.time), ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("dd MMM HH:mm")), ptx, py + 11, MUTED);
        g.text(font, "Bought x" + p.count + " for " + Config.fmt(p.total) + " (" + Config.fmt(p.total / Math.max(1, p.count)) + " each)", rx, py + 28, TEXT);
        g.text(font, "Status: " + p.status, rx, py + 40, AMBER);
        if (p.listedFor > 0) {
            double net = p.listedFor * (1 - Config.I.ahTaxPercent / 100) - p.total;
            g.text(font, "Listed for " + Config.fmt(p.listedFor), rx, py + 52, GREEN);
            g.text(font, "Profit if sold: " + (net < 0 ? "-" : "") + Config.fmt(Math.abs(net)), rx, py + 64, net >= 0 ? GREEN : RED);
        }
    }
}
