# Ore Laundering

A Minecraft **26.2 / Fabric** mod about growing counterfeit ore, processing it into dust, and casting usable resources.

## Production loop

1. Put an eligible vanilla or modded **ore block** onto a growth station to establish a renewable culture.
2. Let the culture mature. Interact with a ripe station to harvest **Counterfeit <Ore>**, or install an auto-harvester to drop harvests into the world automatically.
3. Feed counterfeit ore into the **Grinder**, which yields material-tagged dust. Grinding upgrades give independent chances for bonus dust.
4. Feed dust into the **Ore Vat** to cast nuggets, ingots, or blocks (where the loaded game's item registry provides corresponding items).

## Growth stations

- **Wall Growth Rack** — mounted on a wall.
- **Ceiling Growth Rack** — suspended from a ceiling.
- **Grow Trough** — floor-mounted.

Each accepts independent upgrades:
- **Growth Accelerator**: faster maturation (up to level 3).
- **Density Matrix**: more counterfeit ore per harvest (up to level 3).
- **Auto-Harvester**: one installed unit toggles automatic collection; harvests are **dropped on the ground**, so they can be gathered with item collectors.

## Grinder upgrades

**Precision Grinding Bit** (up to level 3) gives each installed bit an independent 25% chance to make an extra unit of dust per counterfeit ore ground.

## Ore compatibility

Counterfeit ore and dust use **one item type each with a per-stack source ore identifier** so third-party ore IDs don't need to be hard-coded into the mod. Ores are detected by conventional ore IDs or the `c:ores` item tag. Casting resolves products by conventional registered item IDs (e.g. `tin_ore` → `tin_ingot`, `tin_nugget`, `tin_block`); gem-like ores can yield their gem instead of ingots. Some unconventional mods may require mapping support.

## Build

Java 25 required. Using Gradle 9.3+, execute `gradle build` with the dependencies specified in `gradle.properties`. The GitHub Actions workflow also builds and uploads the jar on pushes.

This project is under active development; test gameplay in a disposable world while mechanics are being refined.
