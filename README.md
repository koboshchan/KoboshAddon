# KoboshAddon

## Overview

This project is a Wurst7 addon for Minecraft 26.2. It add a connection of new features to Wurst. Requires Wurst7 with addon support. Grab it from [koboshchan/Wurst7](https://github.com/koboshchan/Wurst7/tree/26.2).

## Hacks

| Hack | Category | Description |
| --- | --- | --- |
| AdvancedItemEsp | Render | Highlights configured dropped items with boxes, tracers, and optional chat alerts |
| AirWalk | Movement | Walk at a configurable Y level in mid-air |
| AntiCrash | Misc | Cancels suspicious incoming packets that can crash or freeze the client |
| AntiVanish | Misc | Highlights and tracks entities that try to vanish from view |
| AttributeSwap | Combat | Swaps items in your hotbar to activate specific attributes (such as axes/lunge weapons) on attack |
| AutoCraft | Items | Auto-crafts configured items while a crafting screen is open |
| AutoLibrarian2 | Items | Trains librarians using a refresh-trades plugin instead of breaking/replacing lecterns |
| AutoOminous | Combat | Automatically drinks Ominous Bottles when raid effects aren't active |
| AutoStaircase | Building | Automatically places blocks and climbs to build a staircase forward |
| AutoTrader | Items | Automates villager trading for selected trades |
| BedFinder | Render | Scans loaded chunks and highlights beds |
| BlockLogger | Render | Logs and highlights matching blocks; supports save/load |
| BoatNoclip | Movement | Enables noclip-style movement controls while riding a boat |
| BookDupe | Exploit | Book-based item duplication on vulnerable servers |
| BookKick | Exploit | Sends oversized book payloads to disconnect players |
| BowSpam | Combat | Charges your bow to minimum power and releases as fast as possible |
| BungeeCordSpoof | Misc | Spoof BungeeCord handshake parameters (IP, profile data) on server login |
| CordTags | Render | Shows one selected coordinate (X, Y, or Z) next to HealthTags |
| CrosshairYFly | Movement | Moves your Y level toward the player closest to your crosshair |
| DoubleDoorsInteract | Building | Interacts with the other side of double doors simultaneously |
| DragonAimBot | Combat | Aims at Ender Dragons with smoothing and movement prediction |
| EventReactor | Misc | Reacts to selected game events by chatting, running commands, or changing hack states |
| Exploration | Movement | Automates movement for systematic exploration routes |
| FastExcavator | Player | Breaks blocks faster by breaking multiple blocks per tick |
| Filler | Building | Places blocks to fill nearby empty spaces |
| GamemodeNotifier | Misc | Notifies when your gamemode changes |
| GhostMode | Misc | Closes the death screen and keeps client-side control after death |
| Grid | Building | Places blocks automatically on a grid centered on a target block |
| GunAimBot | Combat | Aims ranged attacks at nearby entities |
| HackList2 | Render | Shows a customizable list of active hacks on the screen with per-hack visibility toggles |
| InfiniteExplorer | Movement | Continuously extends exploration goals |
| InvisReminder | Misc | Warns when your invisibility state needs attention |
| ItemSearch | Items | Searches for configured items in containers |
| ItemTp | Exploit | Teleports dropped items to you on supported servers |
| ItemTractorBeam | Exploit | Sends movement packet bursts to pull dropped items toward you on vulnerable servers |
| LoginCommand | Misc | Sends configured chat commands automatically when you join a server |
| NBTViewer | Misc | Shows NBT data for the block or entity you look at |
| OpSign | Exploit | Creates pre-filled operator signs for quick placement |
| OreSim | Render | Scans nearby chunks and renders ore blocks, with optional exposed-face filtering |
| SpawnerPlayerEsp | Render | Highlights player-type mob spawner activity |
| StripAura | Combat | Automatically strips logs in range |
| Teleport | Movement | Teleports you to the block you are currently looking at |
| TrialSpawnerEsp | Render | Highlights nearby trial spawners with configurable cooldown filtering |
| Use | Misc | Automatically uses items or blocks |
| VehicleOneHit | Combat | Destroys boats and minecarts with duplicated attack packets |
| XPAura | Combat | Automatically teleports to experience orbs within range to pick them up |

## Commands

| Command | Description |
| --- | --- |
| `.autocraft add/remove/list/clear` | Manage the AutoCraft item list |
| `.explore pause/resume/toggle` | Control the Exploration hack while it is active |
| `.flyto <x> <y> <z>` / `.flyto stop` | Walk/fly directly to coordinates |
| `.viewlogs list/load/clear` | View and load BlockLogger JSON log files |

## Build Requirements

1. Build Wurst first, from either:

   - `wurst7-base` (submodule): `./gradlew :wurst7-base:build --no-daemon`
   - `../Wurst7` (sibling checkout): `./gradlew build` in that directory

   Requires [koboshchan/Wurst7](https://github.com/koboshchan/Wurst7).
   Supported branches: `26.2`.

2. Build this addon:

   ```shell
   ./gradlew build --no-daemon
   ```

   `build.gradle` automatically resolves the newest Wurst jar from either
   location above.
