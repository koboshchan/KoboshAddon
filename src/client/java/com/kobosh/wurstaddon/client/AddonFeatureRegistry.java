package com.kobosh.wurstaddon.client;

import com.kobosh.wurstaddon.client.hack.AutoCraftHack;
import com.kobosh.wurstaddon.client.hack.BlockLoggerHack;
import com.kobosh.wurstaddon.client.hack.ExplorationHack;

public final class AddonFeatureRegistry {
    private AddonFeatureRegistry() {
    }

    public static AutoCraftHack autoCraftHack;
    public static ExplorationHack explorationHack;
    public static BlockLoggerHack blockLoggerHack;
}
