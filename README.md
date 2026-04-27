# AutoBot — Minecraft Fabric Automation Mod

A client-side Fabric mod for Minecraft 1.21.11 that automates player movement, rotation, fishing, tree-felling, and complex action sequences through a scriptable macro system. Built with Java 21 and the Fabric API.

---

## Features

- **Precise Movement Control** — Walk, strafe, and climb by exact block distances with automatic block-centering and stuck recovery
- **Smooth Rotation** — Instantly snap or smoothly turn to cardinal directions and pitch angles; manual 15° yaw adjustments via keybinds
- **Auto-Fishing** — Detects fish bites via hook velocity, auto-reels, recasts with randomized delays, and periodically side-steps to simulate natural behavior
- **Auto Tree Cutting** — Scans a 16-block radius for the nearest tree, navigates to it, and fells every log column by column from the ground up; flight-capable variant ascends and descends dynamically, clearing leaves and logs in its path
- **Scriptable Path Macros** — Write plain-text macro files to sequence any combination of moves, climbs, waits, rotations, and pitch changes; hot-swap between multiple scripts in-game
- **In-Game UI** — Paginated GUI screen for browsing, selecting, and refreshing macro configs without leaving the game

---

## Architecture
sti 
All automation subsystems run on the client tick loop (~20 ticks/sec) and are coordinated by `TrainerBotClient`. Each controller is independently stateful, exposing an `isBusy()` guard so the macro engine waits for one action to finish before starting the next.

```
TrainerBotClient  (tick loop / keybind dispatch)
├── RotationController     smooth & instant yaw/pitch control
├── MovementController     block-precise walk & climb
├── AutoFishController     fishing automation
├── AutoTreeController     autonomous tree-felling state machine
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
| Tree Cutting | `AutoTreeController.java` | Multi-state tree-felling with flight, tunnelling, and column ordering |
| Macro Engine | `PathMacroController.java` | Parses `.txt` scripts, sequences steps with busy-waiting |
| Macro Storage | `PathMacroStorage.java` | Reads `config/trainer-bot/*.txt`, tracks selection |
| Config UI | `PathConfigSelectionScreen.java` | In-game paginated macro picker |

---

## Auto Tree Cutting

`AutoTreeController` is the most complex subsystem in the mod. It navigates the player to the nearest tree within a configurable radius and fells every log autonomously — including logs hidden behind leaves, logs on diagonal branches, and logs high above the ground that require flight to reach. After each tree is fully cut, the bot searches for the next one and repeats until toggled off.

### State Machine

The controller is implemented as a finite-state machine. Each tick, exactly one state is active and performs one atomic action. The machine advances only when that action is confirmed complete.

```
[T pressed]
    │
    ▼
SEARCHING ──── no trees within 16 blocks ──────────────────── IDLE
    │ tree found, all logs collected & sorted
    ▼
FACING_TREE ── rotate toward trunk (8°/tick)
    │ rotation done
    ▼
WALKING_TO_TREE ── walk forward (dist − 1 block) using MovementController
    │ arrived
    ▼
FACING_LOG ── rotate to aim crosshair at current target log
    │ rotation done
    ▼
BREAKING_LOG ── hold attack each tick until log disappears
    │
    ├── log broken, next log reachable from ground ──────────► FACING_LOG
    │
    ├── log broken, next log too high, flight available ──────► FLYING_UP
    │
    ├── MISS hit + out of 4.5-block reach, can fly,
    │   already at right height, too far horizontally ─────► FLYING_TO_LOG
    │
    ├── MISS hit + out of reach, flight available ─────────────► FLYING_UP
    │
    ├── MISS hit + out of reach, no flight (≤3 attempts) ────► REPOSITIONING
    │
    └── all logs cut
            │
            ├── was flying ──────────────────────────────────── FLYING_DOWN
            └── on ground ───────────────────────────────────── SEARCHING

FLYING_UP / descent ── look toward travel direction, clear path, hold Jump or Shift
    │ reached target height, OR Y unchanged for 20 ticks (stuck above solid log)
    ▼
FACING_LOG ── re-aim at log, then resume BREAKING_LOG

FLYING_TO_LOG ── rotate to face log horizontally, fly forward, clear path
    │ within reach
    ▼
FACING_LOG

REPOSITIONING ── rotate to face log, walk 2 blocks toward it
    │ done
    ▼
FACING_LOG

FLYING_DOWN ── hold Shift; exit when player.getY() ≤ groundY+0.5
    │           or solid block below after 20 ticks, disable flight
    ▼
SEARCHING
```

A **safety loop** in `tick()` lets states that complete instantly (e.g. WALKING_TO_TREE when already adjacent) cascade to their successor within the same tick, so the bot never stalls for a full tick on a no-op.

### Tree Discovery

**Radius scan** — `findNearestTreeBase()` iterates every (X, Z) offset within a 16-block horizontal circle. For each column it scans upward until it finds the first log with no log directly beneath it — that is the trunk base. The closest such base by horizontal distance becomes the target.

**26-connectivity flood-fill** — `findConnectedLogs()` runs a BFS from the trunk base. At each position it checks all **26 neighbors** in the surrounding 3×3×3 cube (not just the 6 cardinal directions). This is necessary because acacia and jungle trees have diagonal branch connections that a 6-connected BFS would miss entirely.

```java
for (int ddx = -1; ddx <= 1; ddx++)
    for (int ddy = -1; ddy <= 1; ddy++)
        for (int ddz = -1; ddz <= 1; ddz++) {
            if (ddx == 0 && ddy == 0 && ddz == 0) continue;
            // enqueue neighbor if it is a log and not yet visited
        }
```

### Cut Order — Column-Based Sorting

After collection, logs are grouped into **(X, Z) columns**. Columns are sorted so the one with the **lowest base log** comes first — ensuring the bot always starts at ground level. Within each column, logs are ordered **Y ascending** (bottom to top). The result is a flat list that the bot works through sequentially: complete one column root-to-tip, then move to the next.

```
Column A (trunk, base Y=64):  64, 65, 66, 67
Column B (branch, base Y=67): 67, 68
Column C (branch, base Y=67): 67, 68, 69

Cut order: A64 → A65 → A66 → A67 → B67 → B68 → C67 → C68 → C69
```

### Breaking Logic

Every tick in `BREAKING_LOG`, the controller reads `client.hitResult` — Minecraft's built-in crosshair raycast:

| Crosshair result | Action |
|---|---|
| Target log | `continueDestroyBlock()` — chip away at the log |
| Any log, leaf, or vine | `continueDestroyBlock()` — obstacle in LOS; clear it |
| MISS + within 4.5 blocks | Re-aim (`FACING_LOG`) — camera drifted |
| MISS + beyond 4.5 blocks | Reposition (see below) |
| Unrelated block | Re-aim (`FACING_LOG`) |

### Out-of-Reach Recovery

When the 3D distance `dist3DToBlock()` exceeds Minecraft's 4.5-block reach, the bot cannot break the log from its current position. It chooses a corrective state based on what is available:

```
canFly AND already at correct height AND too far horizontally → FLYING_TO_LOG
canFly (any other case)                                       → FLYING_UP / descent
no flight, attempts remaining                                 → REPOSITIONING (walk 2 blocks closer)
no flight, max attempts reached                               → skip log, advance to next
```

### Flight Mechanics

When flight is enabled (`player.getAbilities().mayfly`), the bot activates it programmatically and manages vertical movement with the game's Jump and Sneak keys.

**Before lifting off** — The player's XZ position is snapped to the centre of their current block. This ensures the vertical travel is a straight line aligned with the block grid rather than drifting from an off-centre position.

**Ascending or descending** — `FLYING_UP` determines direction by comparing `targetFlyY` (log Y − 1) to `player.getY()`. It presses Jump to ascend or Sneak to descend. During travel it sets pitch to **−90° (up)** or **+90° (down)** so the crosshair points in the direction of travel, and breaks any leaves, logs, or vines it encounters — effectively drilling a clear shaft through the canopy. When descending, a Y-delta stuck detector watches for the player's Y stopping above the target (blocked by a solid log below); after 20 ticks of no movement it transitions anyway and re-aims from the current height.

**Horizontal tunnelling** — `FLYING_TO_LOG` handles branch logs that are out of horizontal reach while at the correct height. The bot faces the log at zero pitch, holds the forward key, and breaks any leaf, log, or vine block the crosshair touches until it is within reach distance.

**Landing** — `FLYING_DOWN` holds Sneak and exits when `player.getY() ≤ groundY + 0.5` (primary) or when a solid non-obstacle block has been directly below the player for 20 consecutive ticks (fallback for uneven terrain). It then disables flight and resets fall distance before the next search begins. `player.onGround()` is not used because Minecraft's flight physics can leave that flag false even when the player is physically resting on a block.

### Key Helper Functions

| Function | Where used | What it does |
|---|---|---|
| `findNearestTreeBase()` | SEARCHING | Grid scan → per-column upward scan to find the lowest trunk log |
| `findConnectedLogs()` | SEARCHING | 26-neighbor BFS from trunk base; returns logs sorted by column then Y |
| `yawToBlock()` | FACING_TREE, FACING_LOG, FLYING_TO_LOG | `atan2(−dx, dz)` → Minecraft yaw (South = 0°, East = −90°) |
| `pitchToBlock()` | FACING_LOG | `−atan2(dy, horizDist)` → Minecraft pitch (up = −90°, down = +90°) |
| `dist3DToBlock()` | BREAKING_LOG | True 3D Euclidean distance from player eye to log centre; drives reach detection |
| `horizontalDistToBlock()` | WALKING_TO_TREE, FLYING_TO_LOG | XZ-only distance for navigation stop conditions |
| `isReachableFromGround()` | BREAKING_LOG | Compares log Y against `groundY + 4`; decides whether flight is needed |
| `isLogBlock()` | everywhere | `blockState.is(BlockTags.LOGS)` — matches all wood types |
| `isBreakableObstacle()` | all movement & breaking states | Returns true for logs, leaves, and vines (`BlockTags.CLIMBABLE`) — single source of truth for what to clear |

### Design Decisions and Problem-Solving

The tree cutter was built incrementally, with each test revealing a new edge case that required a deliberate design response:

**Leaf canopy blocking the crosshair** — The crosshair raycast hits the nearest block in the line of sight, not the log behind it. Rather than trying to find a clear angle, the bot simply breaks whatever leaf or intervening log is in the way and the next tick the raycast reaches deeper.

**Diagonal branches missed by BFS** — The initial 6-connected flood-fill correctly captured straight trunks but silently skipped acacia branches. Switching to 26-connectivity (full 3×3×3 cube) resolved this without any other changes to the rest of the pipeline.

**Bot freezing when log is out of reach** — The first version returned silently when `hitResult` was `MISS`, stalling the state machine indefinitely. The fix was to compute the actual 3D distance each tick and route to a corrective state instead of doing nothing.

**Overshooting during flight** — Enabling flight and immediately pressing Jump without centring first caused the player to drift diagonally. Snapping XZ to the block centre before lift-off produced a clean, repeatable vertical path.

**Branch logs horizontally unreachable from the trunk** — Flying to the correct height still left some branch logs out of the 4.5-block reach because horizontal distance was too large. Adding the `FLYING_TO_LOG` state — which flies forward while tunnelling through obstacles — addressed this class of failure cleanly.

**Vines blocking movement and breaking** — Vines grow on tree trunks and between branches and can block the crosshair raycast in the same way leaves do. Rather than duplicating separate leaf and log checks in every state, a single `isBreakableObstacle(BlockState)` helper was introduced. It returns true for `BlockTags.LOGS`, `BlockTags.LEAVES`, and `BlockTags.CLIMBABLE` (which covers all vine variants). All five crosshair-breaking sites — walking to tree, breaking logs, flying up/down, and horizontal tunnelling — call this one method.

**`FLYING_DOWN` freezing with Shift held indefinitely** — `player.onGround()` is unreliable while `abilities.flying` is true; Minecraft's flight physics can leave the flag false even when the player is physically resting on a block. The fix uses a Y-position check as the primary exit condition (`player.getY() ≤ groundY + 0.5`) with a secondary stuck detector (solid non-obstacle block below for 20 consecutive ticks) as a fallback for uneven terrain.

**`FLYING_UP` freezing when descending toward a solid log** — When the target log is directly below the player and still intact, the block stops the player's descent at `log.getY() + 1.0` — one full block above `targetFlyY = log.getY() − 1`. The original tolerance of 0.3 blocks can never be satisfied. A Y-delta stuck detector now tracks whether the player's Y changes each tick; after 20 ticks of no vertical movement (<0.05 blocks/tick) the state transitions to `FACING_LOG`, where the bot re-aims at the log from its current height and resumes breaking.

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
| `T` | Toggle auto tree cutting |

---

## Technical Highlights

- **Tick-based state machines** — Movement and rotation use internal progress tracking across multiple ticks, avoiding frame-locked delays
- **Collision-aware climbing** — Detects a solid block 0.6 blocks ahead with clear headspace before jumping; recovers if stuck for 10+ ticks
- **Hook velocity bite detection** — AutoFish reads the bobber's Y velocity each tick; a threshold of `-0.04` reliably signals a catch without relying on sound or visual events
- **26-connectivity flood-fill** — Tree log collection uses a 3×3×3 BFS neighborhood rather than 6-connected cardinal directions, correctly capturing diagonal branch structures in acacia and jungle trees
- **3D reach detection** — Out-of-reach logs are identified by true Euclidean distance from the player's eye (not just horizontal distance), so diagonal logs at height are correctly flagged for flight correction
- **Column-based cut order** — Logs are sorted by (X, Z) column then Y, ensuring each trunk or branch is felled root-to-tip before the bot moves on — mirroring natural chopping behaviour
- **Bidirectional vertical flight** — A single `FLYING_UP` state handles both ascent and descent by comparing target height to current position; pitch is set toward the direction of travel so the crosshair clears obstacles in the path; a Y-delta stuck detector prevents freezing when descending toward a log that blocks movement
- **Horizontal tunnelling in flight** — `FLYING_TO_LOG` flies the player forward toward an out-of-reach branch log, breaking any leaves, logs, or vines the crosshair touches along the way
- **Obstacle abstraction** — `isBreakableObstacle()` centralises all five crosshair-clearing checks (walking, breaking, flying up/down, tunnelling) into a single predicate covering logs, leaves, and vines
- **Y-position landing detection** — `FLYING_DOWN` exits on `player.getY() ≤ groundY + 0.5` rather than `player.onGround()`, which Minecraft's flight physics can leave false even when the player is standing on a block
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
        ├── tree/
        ├── macro/
        └── ui/
```
