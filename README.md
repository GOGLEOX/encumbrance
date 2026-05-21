# Encumbrance

**Carry weight that finally matters.**

Encumbrance is a Forge 1.20.1 mod that adds a configurable inventory weight system for survival-focused servers and modpacks. Players can carry freely up to a configured threshold, then gradually receive movement penalties as their load gets heavier.

Built for server owners, pack makers, and survival communities that want logistics, preparation, and inventory choices to feel meaningful without turning gameplay into a spreadsheet.

## Highlights

- Configurable carried-weight system
- Balanced default weights for vanilla Minecraft items
- Automatic config generation for every vanilla item
- Manual support for modded item weights
- Smooth movement penalties above the configured threshold
- Optional realism mode for heavier sprint, jump, and hunger effects
- Multiplayer-safe server-side calculation
- Runtime reload command for quick tuning

## Requirements

- Minecraft `1.20.1`
- Forge `47.4.20`
- Java `17`

## Installation

1. Install Minecraft Forge `47.4.20` for Minecraft `1.20.1`.
2. Download or build the Encumbrance jar.
3. Place the jar into the `mods` folder on the server and each client.
4. Start the game or server once to generate the config.
5. Edit `config/encumbrance-common.toml` to tune weights and penalties.
6. Restart, or run `/encumbrance reload` in game.

## Player Experience

Encumbrance is designed to be felt, not fought.

Players below the configured weight threshold move normally. As they carry more, movement slows gradually until the configured maximum penalty is reached. This keeps the system readable and fair while still rewarding planning, storage, mounts, roads, and teamwork.

## Commands

`/encumbrance weight`

Shows the player's current carried weight, configured threshold, and current penalty.

Permission: all players

`/encumbrance reload`

Reloads the common config and clears cached weight totals.

Permission: operator level 2

## Configuration

The config is generated at:

```text
config/encumbrance-common.toml
```

Default general settings:

```toml
enableSystem = true
realismMode = false
maxWeightBeforePenalty = 64.0
maxMovementPenalty = 0.25
updateIntervalTicks = 20
affectCreative = false
affectSpectator = false
```

### Item Weight Scale

```text
0.0 = Weightless / ignored
0.1 = Feather, seeds, paper
0.5 = Food, sticks, leather
1.0 = Most common items
2.0 = Stone, logs, ores
3.0 = Very heavy items like anvils, obsidian, metal blocks
```

Example modded entry:

```toml
"modid:steel_ingot = 1.8"
```

Invalid entries are ignored with warnings. Missing items default to `1.0`.

## Realism Mode

When `realismMode=true`, Encumbrance can apply extra survival pressure:

- Sprinting under heavy load is penalized more aggressively.
- Jump height is reduced slightly.
- Optional hunger exhaustion can be added while sprinting encumbered.

When `realismMode=false`, only movement speed is affected.

## Building From Source

Use the included Gradle wrapper:

```powershell
.\gradlew.bat build
```

The built mod jar will be created under:

```text
build/libs/
```

## Project Status

Encumbrance is currently focused on a clean, configurable foundation:

- Common Forge config
- Vanilla and modded item weight support
- Server-side player weight calculation
- Movement speed penalties
- Optional realism effects
- Commands for inspection and reloads

Future-friendly by design, simple enough to ship today.
