# mystaddon — MystPages (Ore, Fluid & Structure Pages for Mystcraft Reborn)

A NeoForge 1.21.1 addon for [**Mystcraft Reborn**](https://github.com/StudioRewired/mystcraft-reborn)
(mod id `mystcraft_reborn`) that automatically detects **modded ores**, **modded fluids** and
**modded structures** and adds them as **pages** on Descriptive Books for those generation types.

## How it works

Mystcraft Reborn stores a Descriptive Book's biome pages as a list of registry ids in its
`biome_symbols` data component. MystPages follows the exact same pattern with its own
components:

- `mystpages:ore_pages` — list of ore block ids
- `mystpages:fluid_pages` — list of fluid ids
- `mystpages:structure_pages` — list of structure ids

### Detection (`page/PageCatalog` + `detect/`)

- **Ores**: non-vanilla blocks whose registry name contains `ore`, plus any non-vanilla block
  in the common `c:ores` tag (second pass on `TagsUpdatedEvent`).
- **Fluids**: fluids in the fluid registry that have a real block form and positive density
  (water/lava/empty skipped; still+flowing deduplicated by block).
- **Structures**: non-vanilla entries in the structure registry (`Registries.STRUCTURE`).

Blacklists for each type live in `config/mystpages-common.toml`.

### Using pages

Hold a Descriptive Book and run:

```
/mystpages catalog ore|fluids|structures   # list detected pages
/mystpages add ore somemod:tin_ore         # add a page to the held book
/mystpages remove fluid somemod:crude_oil
/mystpages list structure
```

Pages on the book also show in its tooltip.

### Generation (`worldgen/AgeGenerationBridge`)

1. When you right-click with a book that has pages, the pages are captured (the host mod
   creates the Age dimension synchronously inside that same use call).
2. When the new `mystcraft_reborn:age_*` dimension loads, the captured pages are bound to
   it in a saved data map (`mystpages_age_pages`).
3. As chunks in that Age generate:
   - each **ore page** places configurable veins of that ore (replacing stone/deepslate);
   - the first **fluid page** replaces ocean/lake water below sea level with that fluid;
   - **structure pages** are recorded on the Age — placing arbitrary mod structures inside
     an Age requires the host mod to expose structure-set injection, so the pages are
     captured and logged for now.

## Sable compatibility (sub-level portals)

When the [**Sable**](https://modrinth.com/mod/sable) mod (sub-levels = moving block grids) is
installed, MystPages additionally makes Mystcraft Reborn link portals Sable-compatible
(`compat/sable/`):

- **Portals on sub-levels** — if an entity overlaps a `mystcraft_reborn:link_portal` block
  that lives inside a Sable sub-level, it is teleported through it. The destination is
  resolved exactly like the host block does (portal block entity → receptacle → linking book →
  `LinkingBookItem.getDestination`), via reflection so there is no compile-time dependency.
- **Players on sub-levels through world portals** — Sable can suppress vanilla's
  `entityInside` trigger for entities standing on a sub-level, so entities with a tracking
  sub-level get the vanilla portal flow (`Entity.setAsInsidePortal`) re-triggered explicitly
  when a world portal block overlaps them.

All Sable access goes through `SableBridge`, a defensive reflection facade over
`Sable.HELPER` (`getAllIntersecting`, `getTrackingOrVehicleSubLevel`, `SubLevel.logicalPose`,
local `getBlockState`/`getBlockEntity`). The compat is inert when Sable is absent.

## Project layout

- `src/main/java/com/mystpages/`
  - `MystPages` — mod entry, event wiring
  - `MystPagesCommands` — `/mystpages` command + book tooltips
  - `AddonConfig` — detection blacklists + generation tuning
  - `detect/` — `OreScanner`, `FluidScanner`, `StructureScanner`
  - `page/` — `PageType`, `PageCatalog`, `PageComponents`, `BookPages`
  - `worldgen/` — `AgePagesData`, `AgeGenerationBridge`
  - `compat/sable/` — `SableBridge` (reflection facade), `SablePortalCompat` (portal teleporting)
- `src/main/templates/META-INF/neoforge.mods.toml` — mod metadata (requires `mystcraft_reborn`, optional `sable`)

The addon intentionally references the host mod only through registry ids
(`mystcraft_reborn`, `mystcraft_reborn:descriptive_book`, `mystcraft_reborn:age_*`), so it
compiles without the host jar on the classpath.

## Building

```
./gradlew build
```

Requires network access to `maven.neoforged.net` and the Gradle plugin portal.
Targets Minecraft 1.21.1, NeoForge 21.1.x (same line as mystcraft-reborn), Java 21.
Drop the jar next to the Mystcraft Reborn jar.
