# KoboshAddon

## Overview

This project is a Wurst7 addon for Minecraft 1.21.1. It ports all features
added to [koboshchan/Wurst7](https://github.com/koboshchan/Wurst7) between
commit `9459157791e06b93730e68a11fe37e31daea3133` and HEAD (inclusive) as a
standalone addon — no modifications to Wurst core required.

## Hacks

| Hack | Category | Description |
| --- | --- | --- |
| AirWalk | Movement | Walk at a configurable Y level in mid-air |
| AntiVanish | Misc | Highlights entities that try to vanish from view |
| AutoCraft | Items | Auto-crafts configured items while a crafting screen is open |
| AutoTrader | Items | Automates villager trading for selected trades |
| BedFinder | Render | Scans loaded chunks and highlights nearby beds |
| BlockLogger | Render | Logs and highlights matching blocks; supports save/load |
| BookDupe | Exploit | Book-based item duplication on vulnerable servers |
| BookKick | Exploit | Sends oversized book payloads to disconnect players |
| DragonAimBot | Combat | Aims at Ender Dragons with smoothing and movement prediction |
| Exploration | Movement | Automates movement for systematic exploration routes |
| Filler | Building | Places blocks to fill nearby empty spaces |
| GamemodeNotifier | Misc | Notifies when your gamemode changes |
| GunAimBot | Combat | Aims ranged attacks at nearby entities |
| InfiniteExplorer | Movement | Continuously extends exploration goals |
| InvisReminder | Misc | Warns when your invisibility state needs attention |
| ItemSearch | Items | Searches for configured items in containers |
| ItemTp | Exploit | Teleports dropped items to you on supported servers |
| NBTViewer | Misc | Shows NBT data for the block or entity you look at |
| OpSign | Exploit | Creates pre-filled operator signs for quick placement |
| SpawnerPlayerEsp | Render | Highlights player-type mob spawner activity |
| VehicleOneHit | Combat | Destroys boats and minecarts with duplicated attack packets |

## Commands

| Command | Description |
| --- | --- |
| `.autocraft add/remove/list/clear` | Manage the AutoCraft item list |
| `.explore pause/resume/toggle` | Control the Exploration hack while it is active |
| `.flyto <x> <y> <z>` / `.flyto stop` | Walk/fly directly to coordinates |
| `.viewlogs list/load/clear` | View and load BlockLogger JSON log files |

## How Addon Registration Works

1. Provider class: `WurstAddonHackAddon` implements `net.wurstclient.addon.Addon`.
2. Service file: `src/client/resources/META-INF/services/net.wurstclient.addon.Addon`.
3. The service file points to `com.kobosh.wurstaddon.client.WurstAddonHackAddon`.

No manual registration in `WurstaddonClient` is required.

## Build Requirements

1. Build Wurst first, from either:

   - `wurst7-base` (submodule): `./gradlew :wurst7-base:build --no-daemon`
   - `../Wurst7` (sibling checkout): `./gradlew build` in that directory

   Requires [koboshchan/Wurst7](https://github.com/koboshchan/Wurst7).
   Supported branches: `master`, `1.21.1`.

2. Build this addon:

   ```
   ./gradlew build --no-daemon
   ```

   `build.gradle` automatically resolves the newest Wurst jar from either
   location above.

## Validation

When Wurst starts, check for a log line like:

```
[Wurst] Loaded addon: KoboshAddon (...)
```

Then confirm the ported hacks appear in the Wurst hack list and can be toggled.
