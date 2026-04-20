# KoboshAddon

## Overview

This project is a Wurst7 addon for Minecraft 1.21.1.

It uses Java ServiceLoader to register an addon provider that contributes hacks
to Wurst at startup.

## Current Hack

`ExampleHack` is a one-shot hack that reports the final net-added item set from
`../Wurst7-1.21.1` in the commit range:

- `9459157791e06b93730e68a11fe37e31daea3133..HEAD` (inclusive)

Net result in this range:

- `minecraft:writable_book`

Behavior:

- Prints the count of net-added items.
- Prints each item id in chat.
- Disables itself immediately after running.

## How Addon Registration Works

1. Provider class: `WurstAddonHackAddon` implements `net.wurstclient.addon.Addon`.
2. Service file: `src/client/resources/META-INF/services/net.wurstclient.addon.Addon`.
3. Service file content points to the provider class.

No manual registration in `WurstaddonClient` is required.

## Build Requirements

1. Build Wurst first, either in:

   - `wurst7-base`, or
   - `../Wurst7`

requires koboshchan/Wurst7 for add on support

current supported branches are `master` and `1.21.1`

1. Build this addon:

   - `./gradlew build`

`build.gradle` automatically resolves the newest matching Wurst jar from those
two locations.

## Validation

When Wurst starts, verify a log line similar to:

- `[Wurst] Loaded addon: KoboshAddon (...)`

Then confirm `Example Hack` appears in the Wurst hack list, can be toggled,
prints `minecraft:writable_book`, and auto-disables.
