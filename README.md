# Ore Laundering

**Caszual Suite · Minecraft 26.2 · Fabric · Java 25** — currently experimental.

Ore Laundering is a standalone member of the **Caszual Suite**. With Caszual Additions installed, its machines, upgrades, molds, and materials join the shared Caszual creative collection and the **Barrel of Caszual Fun**. Caszual Additions is optional.

Grow counterfeit copies of ore, grind them into dust, melt the dust with lava, and cast ingots, nuggets, and blocks with a physical spout and table.

## Machines

| Machine | Function |
|---|---|
| Wall Growth Rack | Grows counterfeit ore from a permanent consumed ore seed |
| Ceiling Growth Rack | Grows ore clusters **downward** from the ceiling |
| Grow Trough | Floor-grown ore, base yield of two counterfeit ores per cycle |
| Grinder | Turns counterfeit ore into dust, with bonus yield from Grinding Bit upgrades |
| Ore Vat | Smelts each dust into a random **300–600 mL** of molten material; needs a lava tank **directly underneath** |
| Ore Tank | Holds up to **57,600 mL** of one material at a time, with eight visibly changing molten levels |
| Lava Tank | Holds up to **16,000 mB** lava, with eight visible lava heights; supply with lava buckets or the GUI |
| Vat Spout | Compact faucet that automatically faces and visibly connects into an adjacent Ore Vat or Ore Tank, pouring into the casting table immediately below; empty-hand click toggles the stream |
| Casting Table / Basin | Casts ingots or nuggets (table) or blocks (basin). Empty-hand click switches between the shapes **only when entirely empty** |

## Growth

Seed with a vanilla/modded ore using right-click or the machine's input slot. Growth appears at **5%, 25%, 66%, and 100%** completion. The displayed ore is rendered as **faceted, pointed crystal shards** using the material block's texture where available (otherwise the source ore texture), not miniature full-size cubes. Ceiling-rack shards begin embedded in the underside of the iron plate and grow down; wall racks grow outward; troughs grow upward.

**Manual harvesting:** At 100% growth, **right-click with an empty hand** to collect Counterfeit Ore directly into your inventory. Any overflow falls into the world. **Sneak-right-click** to open the rack's GUI instead. Auto-Harvester harvests still fall into the world (below a ceiling tray, outward from a wall rack). Once seeded, a rack keeps reproducing the same ore after each harvest.

- **Growth Accelerator:** up to three speed levels; grows in `6000 / (speed level + 1)` ticks.
- **Density Matrix:** up to three density levels; adds one counterfeit ore per harvest per level.
- **Auto-Harvester:** drops harvests into the world as loose items.
- **Grinding Bit:** up to three bits; each provides an independent 25% chance of additional dust.

## Molten quantities and casting

| Result | Molten material |
|---|---:|
| Nugget | **100 mL** |
| Ingot | **900 mL** |
| Block | **8,100 mL** |

The Ore Vat now visibly fills with molten fluid as it processes material, and holds **14,400 mL**. Each dust consumes **50 mB** of lava from the tank below and yields a random **300–600 mL**. Different ores cannot mix in the same vat or molten tank.

To cast, place a **Vat Spout adjacent to the Ore Vat/Ore Tank** and a **Casting Table directly below the spout**. Empty-hand right-click the spout to enable pouring; it fills at **30 mL/tick** and stops when the recipe's capacity is reached. Cooling then takes **30 ticks for nuggets, 80 for ingots, and 160 for blocks**. Collect the finished result from the output slot in the casting table GUI.

The casting table defaults to an ingot recipe (900 mL). Insert a **Nugget Mold** to cast nuggets (100 mL), or an **Ingot Mold** for ingots. Empty-hand right-click the casting table to change to **Basin mode** for 8,100 mL blocks.

### Empty-only transformation rule

Switching modes is **blocked** until ALL of the following are true:

- The table/basin contains **0 mL** of molten metal.
- No mold is filling or cooling.
- Its finished output has been collected.
- Its input mold slot is empty.
- No material remains queued in the casting block.

An inserted mold is locked during filling/cooling and cannot be swapped mid-cast. **Sneak + empty-hand right-click** opens the GUI without changing shape. The table's mode persists across saving and loading.

## Machine GUIs and overlay

Every block opens a GUI for inventory/contents/status. The Jade tooltip integration is optional and displays a planted ore's name and growth progress when Jade is installed.

The previous direct-cast interface on the vat/tank has been replaced by casting through the spout and physical casting table.

### Machine fluid visuals

- Lava Tanks and Ore Tanks have eight discrete visually rising fill levels; an empty tank displays no artificial fluid cube.
- Vat interiors also show eight inset molten levels, while casting table/basin contents fill smoothly and cool in place.
- Molten pools and poured streams use a grayscale ripple texture shaded toward the **actual ingot/block/gem texture's average color** (including compatible modded materials). Fallback colors cover unusual materials.
- Lava uses the vanilla animated lava sprite. All liquid surfaces are set back from the shell and rim to prevent overlapping faces/z-fighting.
- Spouts orient toward the adjacent vat/tank automatically, including previously placed ones after a server tick, and render a stream only when a valid transfer can occur.

## Ore compatibility

Ore identifiers are preserved on counterfeit ore and dust item components. Known host-rock prefixes (granite, diorite, andesite, tuff, etc.) are stripped during material output lookup, so many alternate host-rock ores can produce the same ingot/gem/block.

Casting outputs are resolved using registered item names in the source mod's namespace and vanilla Minecraft; not every mod uses conventional names, so some ores may require mappings. **Dynamic examination of mining loot-table drops is not implemented yet.** The mod avoids consuming liquid for unknown casting outputs.

## Development

Build with Java 25 and Gradle 9.7:

```bash
gradle build
```

GitHub Actions [Build Fabric jar](https://github.com/CaszGamerMD/Ore_Laundering/actions/workflows/build.yml) provides the runtime JAR and source JAR after successful compilation.

This is an early mod release. Please test in a disposable world before using it in an important save.
