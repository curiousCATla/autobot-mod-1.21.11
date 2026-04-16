# AutoBot — Minecraft Fabric Automation Mod

A client-side Fabric mod for Minecraft 1.21.11 that automates player movement, rotation, fishing, and complex action sequences through a scriptable macro system. Built with Java 21 and the Fabric API.

---

## Features

- **Precise Movement Control** — Walk, strafe, and climb by exact block distances with automatic block-centering and stuck recovery
- **Smooth Rotation** — Instantly snap or smoothly turn to cardinal directions and pitch angles; manual 15° yaw adjustments via keybinds
- **Auto-Fishing** — Detects fish bites via hook velocity, auto-reels, recasts with randomized delays, and periodically side-steps to simulate natural behavior
- **Scriptable Path Macros** — Write plain-text macro files to sequence any combination of moves, climbs, waits, rotations, and pitch changes; hot-swap between multiple scripts in-game
- **In-Game UI** — Paginated GUI screen for browsing, selecting, and refreshing macro configs without leaving the game

---

## Architecture

All automation subsystems run on the client tick loop (~20 ticks/sec) and are coordinated by `TrainerBotClient`. Each controller is independently stateful, exposing an `isBusy()` guard so the macro engine waits for one action to finish before starting the next.

```
TrainerBotClient  (tick loop / keybind dispatch)
├── RotationController     smooth & instant yaw control
├── MovementController     block-precise walk & climb
├── AutoFishController     fishing automation
└── PathMacroController    script parsing & step execution
        ├── PathMacroStorage   file I/O & macro discovery
        └── MacroStep / MacroActionType   data model
```

### Component Breakdown

| Component | File | Responsibility |
|---|---|---|
| Client Entry Point | `TrainerBotClient.java` | Registers tick events, wires all controllers |
| Keybindings | `ModKeybindings.java` | 8 keybinds (rotate, face, fish, macro, UI) |
| Rotation | `RotationController.java` | 15° instant snaps; 4°/tick smooth yaw & pitch facing |
| Movement | `MovementController.java` | Walk/strafe/climb with centering, stuck detection & mouse actions |
| Mouse Action | `MouseAction.java` | Enum for per-move mouse button behavior (NONE / ATTACK / USE) |
| Fishing | `AutoFishController.java` | Bite detection, recast cooldown, anti-AFK drift |
| Macro Engine | `PathMacroController.java` | Parses `.txt` scripts, sequences steps with busy-waiting |
| Macro Storage | `PathMacroStorage.java` | Reads `config/trainer-bot/*.txt`, tracks selection |
| Config UI | `PathConfigSelectionScreen.java` | In-game paginated macro picker |

---

## Macro Scripting

Macros are plain `.txt` files placed in `.minecraft/config/trainer-bot/`. Lines beginning with `#` are comments.

### Syntax

```
FACE  <NORTH|SOUTH|EAST|WEST>
PITCH <angle>                        # -90 (up) to 90 (down)
MOVE  <UP|DOWN|LEFT|RIGHT>  <blocks>  <NONE|ATTACK|USE>
CLIMB <UP>                  <blocks>
WAIT  <ticks>
LOOP                                 # jump back to step 1 and repeat forever
```

**Mouse action options for MOVE:**
| Value | Behaviour |
|---|---|
| `NONE` | Walk without pressing any mouse button |
| `ATTACK` | Hold left click — breaks blocks or attacks entities in crosshair |
| `USE` | Hold right click — places blocks or interacts with entities in crosshair |

### Example

```
# Harvest loop
FACE NORTH
PITCH 0          # look straight ahead
MOVE UP 3 NONE
WAIT 10
PITCH -45        # look up 45 degrees
MOVE RIGHT 5 USE
CLIMB UP 2
WAIT 20
FACE SOUTH
PITCH 90         # look straight down
MOVE UP 5 ATTACK
LOOP             # restart from the top indefinitely
```

Steps execute sequentially; each step waits for the previous movement or rotation to finish before proceeding. Yaw and pitch turns started together resolve in parallel. The macro controller pauses automatically between steps (4-tick gap) to keep actions deterministic.

`LOOP` resets the step counter back to the first step and replays the entire script from the beginning. It runs indefinitely until you press `H` to stop the macro. Place it at the end of any script you want to repeat continuously.

---

## Keybindings

| Key | Action |
|---|---|
| `R` | Rotate camera right 15° |
| `L` | Rotate camera left 15° |
| `↑ ↓ ← →` | Snap to face North / South / West / East |
| `G` | Toggle auto-fish |
| `H` | Toggle path macro execution |
| `P` | Open macro selection screen |

---

## Technical Highlights

- **Tick-based state machines** — Movement and rotation use internal progress tracking across multiple ticks, avoiding frame-locked delays
- **Collision-aware climbing** — Detects a solid block 0.6 blocks ahead with clear headspace before jumping; recovers if stuck for 10+ ticks
- **Hook velocity bite detection** — AutoFish reads the bobber's Y velocity each tick; a threshold of `-0.04` reliably signals a catch without relying on sound or visual events
- **World-space direction vectors** — `MovementController` converts logical directions (UP/DOWN/LEFT/RIGHT) to world-space movement using the player's current yaw, so macros stay correct regardless of facing
- **Direct game API input** — Mouse actions (attack, use) call `gameMode` methods directly rather than simulating key state, so automation remains fully functional even when the game window is out of focus
- **Parallel yaw + pitch resolution** — Both axes animate simultaneously inside the same tick loop; `isBusy()` clears only when both finish, so the macro never advances prematurely
- **File-based macro hot-swap** — Macros reload from disk each time they are activated; no restart required to test script changes

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Java 21 |
| Minecraft Version | 1.21.11 |
| Mod Loader | Fabric Loader 0.18.4 |
| API | Fabric API 0.141.3+1.21.11 |
| Build | Gradle + Fabric Loom 1.15 |
| Bytecode | Mixin framework |

---

## Building

Requires JDK 21 and an internet connection for Gradle dependency resolution.

```bash
./gradlew build
```

The compiled `.jar` will be at `build/libs/`. Drop it into your `.minecraft/mods/` folder alongside Fabric Loader and Fabric API.

---

## Project Structure

```
src/
├── main/                        # Shared (server-compatible) entry point
│   └── java/trainer/autobot/
│       └── TrainerBot.java
└── client/                      # Client-only logic
    └── java/trainer/autobot/
        ├── TrainerBotClient.java
        ├── keybind/
        ├── rotation/
        ├── movement/
        ├── fishing/
        ├── macro/
        └── ui/
```
