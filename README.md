# Craftwright

FE-powered autocrafting for **Minecraft Java 26.1.2 / NeoForge**.

| | Crafter | Advanced Crafter |
|---|---:|---:|
| Starting patterns | 1 | 6 |
| Maximum patterns | 3 | 12 |
| Base crafting interval | 40 ticks | 10 ticks |
| Maximum speed | 1 craft/tick | 1 craft/tick |
| Base working power | 4 FE/t | 16 FE/t |
| Energy storage | 40,000 FE | 400,000 FE |

Both tiers have 18 ingredient slots, 9 output slots, and separate speed, pattern, pull, and push upgrade slots. The advanced recipe requires **four Craftwright Crafters**, two diamonds, two quartz blocks, and a blaze rod. The regular recipe uses iron, copper, redstone, and a crafting table.

## Using a crafter

1. Place it and supply FE through any face.
2. Open **Patterns**. Select a pattern output or click **Add** to select an empty unlocked pattern.
3. Pick up an item from your inventory and click a ghost ingredient cell to copy one item into the 3×3 grid. Right-click clears a cell; your actual items stay on the cursor.
4. **With JEI installed:** while the crafter menu is open, view a crafting recipe and click JEI’s **+** button. This copies the displayed recipe into the selected pattern’s ghost grid, even if you have none of its ingredients. The server confirms the import, switches back to Patterns, and fills the normal output. Click **Save** to keep it. Shaped recipes retain their layout; shapeless recipes are supported too.
5. The normal crafting output is filled automatically. You can copy an item stack into the output to override it; **Save** is available only when the exact output, count, and components are produced by a real recipe for that grid. **Auto** restores the normal output. Hover over the output to inspect returned containers.
6. Save the pattern. Supply ingredients on **Inventory**, or use hoppers/pipes. Saved, unlocked patterns rotate fairly whenever their ingredients and output space are available.
7. Configure the six world-facing directions on **Sided I/O**. Click each face to cycle **Input → Output → Input & Output → Disabled**. The bottom defaults to output; other faces default to input.

Recipe editing uses ghost items exclusively. Machines revalidate recipes on the server and after recipe reloads. Outputs and crafting remainders must all fit before ingredients are consumed. Pausing, losing power, or running out of output space preserves ingredients and completed crafting progress. Breaking a crafter drops its real inventory and upgrades, clears its saved patterns, and loses stored FE.

## Upgrades and balance

- **Pattern:** each upgrade unlocks one additional pattern. Regular accepts 2; advanced accepts 6. Removing upgrades locks excess patterns without deleting them.
- **Speed:** accepts up to 8. Each upgrade reduces the interval by approximately an equal number of ticks, reaching 1 tick at eight upgrades. Regular intervals: **40, 36, 31, 26, 21, 16, 11, 6, 1**. Advanced: **10, 9, 8, 7, 6, 5, 4, 3, 1**.
- **Pull / Push:** separate tiered items; each slot accepts one. Higher tiers replace the previous one and use it in their recipe. Tier 1 uses copper, redstone, and a hopper/dropper; later tiers add iron, gold, then diamonds.

| Transfer tier | Items per operation | Default interval | Cost per moved item |
|---|---:|---:|---:|
| 1 | 1 | 20 ticks | 2 FE |
| 2 | 8 | 20 ticks | 4 FE |
| 3 | 32 | 20 ticks | 6 FE |
| 4 | 64 | 20 ticks | 8 FE |

Each pull/push budget is shared across all eligible faces. Faces rotate in priority. Pulling filters to exact ingredients used by unlocked patterns; pushing exports only the output inventory. Passive automation needs no upgrades. External extraction never removes ingredients or upgrades.

While crafting, FE/t = `base × (1 + speed upgrades)² + 2 × pattern upgrades + pull tier + push tier`. Active transfers additionally charge the table's per-item cost. Idle and blocked machines consume no crafting power. Each upgrade raises working power consumption; faster crafting also increases energy per craft at the top end.

The world-specific `serverconfig/craftwright-server.toml` configures base working FE, active transfer interval, and base transfer FE per item. Recipes can be replaced through datapacks or compatible pack scripting tools. Machine ingredient recipes use NeoForge common material tags.

## Pack compatibility

Craftwright uses standard NeoForge item and energy capabilities, standard crafting recipes, and common material tags. This provides the integration surface for compatible pipes, FE networks, storage systems, and recipe viewers without requiring a particular FTB pack or automation mod.

Current patterns match **exact item types and components**. Recipes using tags work when a valid concrete ingredient is programmed; automatic substitution across all members of a tag is not implemented. JEI crafting-recipe transfer is supported through its **+** button; JEI is optional and does not need to be installed on dedicated servers. Machine/processing recipes and EMI-specific integration are not implemented. No named third-party pack has been certified yet; it must target the same Minecraft/NeoForge version.

## Development

Requires **JDK 25**. Versions are pinned in `gradle.properties`; the official NeoForge ModDevGradle wrapper is included.

```sh
./gradlew build
./gradlew runClient
./gradlew runServer
./gradlew runGameTestServer -PgameTests
# Verify that dedicated servers also work without JEI:
./gradlew runGameTestServer -PgameTests -PwithoutJei
```

The first run downloads Minecraft, NeoForge, and development assets. The built mod is `build/libs/craftwright-0.1.2.jar`; install it on both clients and servers. A local JDK, if provisioned by the workspace, lives under ignored `.tooling/`; it is not required at that location on other machines.

GameTests live in a separate source set and are excluded from the mod JAR. They exercise crafting timing, speed and pattern upgrades, output blockage, container returns, energy loss, invalid output overrides, all side modes and cached capabilities, fair recipe scheduling, save/reload, ghost inventory safety, powered transfers into/out of vanilla inventories, and validated recipe imports (including packet encoding and rejected stale/invalid requests). `build` compiles these tests; `runGameTestServer -PgameTests` executes them.

The initial block/item models reference vanilla textures as placeholders. Custom artwork and additional recipe-viewer integrations can be added independently.

License: All rights reserved, pending the author's license choice. The original NeoForge template license is retained in `TEMPLATE_LICENSE.txt`.
