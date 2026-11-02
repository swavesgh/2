# AH Sniper (Fabric, Minecraft 26.2)

## Get the jar with GitHub
1. Upload ALL files and folders (including the hidden `.github` folder) to your repo, replacing the old ones.
2. Open the **Actions** tab and wait for the green tick (or press "Run workflow").
3. Open the run, scroll to **Artifacts**, download `ahsniper-jar`, unzip, use `ahsniper-1.6.0.jar` (not -sources).
Requires Java 25, Fabric Loader 0.19+, Fabric API for 26.2.

## New in 1.6.0
- Deal alerts and snipe messages are multi-line, colour-coded by how far under average the listing is
  (Decent aqua, Great green, Huge gold, Insane red, Legendary purple), with a bar, profit estimate and a sound.
  Turn the sound off with `"dealSound": false` in config/snipe.json.
- Selling: hold an item and type `/ah hand` (no price), press the Sell key (default `\`), or use the
  Purchases tab buttons. The mod holds the item and runs `ah hand <price>` using Settings > "Sell/resell price"
  (Lowest BIN / Average / Median) and Undercut %. Sell command default is now `ah hand {price}`.

## AFK mode (1.6.0) - Settings tab > "AFK mode" ON
- Forces auto-resell, and only buys a listing if relisting it (per your Sell/resell price, undercut, tax and min profit) would be profitable.
- Every buy is verified (item must actually arrive) and every relist is verified (item must actually leave your inventory).
- Items it could not sell are retried every `sweepMinutes` (default 5, config/snipe.json), up to 6 times.
- Failed listings are blocked for 5 minutes; after 4 failed buys or 3 failed relists in a row it STOPS with a sound and a red message.
- Stops if your inventory is full. "Max unsold" pauses buying when unsold stock exceeds that value (0 = off).
- Resumes by itself after a disconnect/reconnect. Prints a session profit summary when it stops.
- Tip: press F3+P to turn off "pause on lost focus" so alt-tabbing does not open the pause menu.

## 1.6.0
- Item textures next to every row in the Snipe List / Economy / Purchases tabs and in the detail panel.

## 1.6.0
- Prices are now tracked per exact variant: the same item with different enchants or custom lore (e.g. Demolitionist) is a separate
  item with its own average, alerts and snipe entry. Plain "Diamond Chestplate" is no longer compared to an enchanted one.
  Delete config/snipe_market3.json once and press Analyze to clear the old mixed-up averages.
- Faster sniping: in config/snipe.json set `snipePages` (e.g. 2) so each cycle only checks the first pages; a full scan for price stats
  still runs every `fullScanMinutes`. Use `sortName`/`sortClicks` to press the AH sort button after opening (to reach newest / lowest price first).

## 1.6.0
- Custom-enchant lore (e.g. Demolitionist) on an un-renamed item is priced by the enchant itself: a Demolitionist diamond chestplate,
  iron sword or book share one price group ("Demolitionist (any item)"). Items on a base listed in `premiumBases`
  (default "netherite") get their own group per item, since the base is worth a lot. Plain/vanilla-enchant items are unchanged.

## 1.6.0
- `minSellers` (default 3): prices only count as data when at least that many different sellers have the item listed.

## 1.6.0 - mistake sniping
- The "looks fake" guard no longer applies to un-renamed items (they cannot be faked), so a 1-coin or missing-zero listing is bought.
  Renamed/custom-named items keep `scamGuardPercent`.
- `autoSnipe` (config/snipe.json, default false): buys ANY un-renamed item listed `autoSnipePercent`+ under its average
  (avg >= `autoSnipeMinAvg`, max `autoSnipeMaxSpend` per listing) without adding it to the Snipe List.

## 1.6.0
- Renamed gear (swords, armour, tools, books) is priced by its enchants, not its name: a renamed netherite sword with "Unbroken Chain" is priced
  with every other "Unbroken Chain" netherite sword. Custom-enchant titles are detected as bold lore lines.
- Only renamed non-gear custom items (scrolls, ranks...) still keep the name-based identity and the `scamGuardPercent` check.

## 1.6.0 - new GUI + bulk sniping
- Flat, modern look: cards, stat tiles, toggle colours, status bar at the bottom.
- Economy / Snipe List: tick the boxes next to items (or press **All** to tick everything shown, **None** to clear). Filter button cycles All / Custom / Listed (only items with price data).
- Type an amount in the price box and press **Add N**:  `50k`, `1.5m` or `80%`. A % means "that % of each item's average" (items without price data are skipped).
  **Off** switches off every ticked item. With nothing ticked, Add / Off act on the selected item like before.
