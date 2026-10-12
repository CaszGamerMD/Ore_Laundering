# Ore Laundering

**Minecraft 26.2 · Fabric · Java 25**

Grow counterfeit copies of vanilla and modded ores, grind the counterfeit material into dust, and use lava to melt dust into molten metal. Cast it directly or store it for later in an ore tank.

## Machines

| Machine | How it works |
|---|---|
| Wall Growth Rack | Wall-style model, grows one counterfeit ore per cycle before upgrades |
| Ceiling Growth Rack | Hanging model, grows one counterfeit ore per cycle before upgrades |
| Grow Trough | Floor model, grows two counterfeit ore per cycle before upgrades |
| Grinder | One counterfeit ore converts to one dust after two seconds, with possible bonus dust |
| Ore Vat | Lava-powered furnace: converts one dust into nine molten units |
| Ore Tank | Stores up to 576 molten units (64 ingots) without consuming fuel |

All three growth blocks show four visual maturity stages. Growth is permanent after seeding (the seed ore is consumed), and only mature growth can be harvested.

## Upgrades

| Upgrade | Installed on | Effect |
|---|---|---|
| Growth Accelerator | Any growth block | Up to level 3; maturation time is 6000 / (1 + level) ticks |
| Density Matrix | Any growth block | Up to level 3; +1 counterfeit ore per harvest per level |
| Auto-Harvester | Any growth block | Automatically harvests mature ore **as item drops on the ground** |
| Grinding Bit | Grinder | Up to level 3; each bit has an independent 25% chance to produce +1 bonus dust |

Upgrades are installed by right-clicking the matching station with the upgrade item.

## Player interactions

1. Craft or place one of the growth structures.
2. **Right-click with an ore block** (vanilla or from another mod). This consumes one ore and starts the renewable culture.
3. Wait for its visual growth stages to reach maturity; **right-click with an empty hand** to manually harvest. Install an Auto-Harvester if you'd prefer automatic ground drops.
4. **Right-click the Grinder with a Counterfeit Ore** to queue grinding. Finished dust drops from the top.
5. **Right-click the Ore Vat with a lava bucket** to add 2,000 heat units (20 dust smelts). An empty bucket is returned. Each dust smelt uses 100 heat units and adds 9 molten units to the vat.
6. Insert ore dust into the vat with right-click. Different ore types **cannot mix**.
7. **Right-click the vat with a Nugget, Ingot, or Block Mold** to consume 1, 9, or 81 molten units respectively and produce the appropriate resource where one exists in the registries.
8. Place an Ore Tank directly adjacent to a vat, then **sneak + empty-hand right-click** on the vat to pour into the tank. The tank accepts only one material at a time and can itself be right-clicked with molds for later casting.

Right-click with an empty hand to view the current machine status on the action bar.

## Compatibility

- A seed ore item is accepted when its registry path ends in `_ore` or `_ores`, or it belongs to the common item tag `c:ores`. Ancient Debris is also recognized.
- Counterfeit Ore and Ore Dust retain the source ore registry identifier as a persistent item component. This avoids registering a separate item for every ore added by other mods.
- Casting currently resolves outputs by conventional IDs, attempting the source mod namespace and then `minecraft`. For example, `copper_ore` casts `copper_nugget`/`copper_ingot`/`copper_block` when available, while `diamond_ore` can cast diamonds and diamond blocks.
- Mods with unconventional ore names or processing systems may need dedicated mappings. The casting mold reports when an output does not exist rather than consuming the stored material.
- The initial implementation uses direct player interaction and world drops; machine inventories, hopper transfer, fluid piping, and multi-material tank networks are not yet implemented.

## Building

Build using **Java 25** and **Gradle 9.7**:

```bash
gradle build
```

The GitHub Actions [Build Fabric jar](https://github.com/CaszGamerMD/Ore_Laundering/actions/workflows/build.yml) workflow runs the build and provides the compiled mod jar as an artifact when successful.

Currently under active development. Test in a disposable world; this is not yet a production-stable release.
