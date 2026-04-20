package com.kobosh.koboshaddon.client;

import com.kobosh.koboshaddon.client.hack.AutoCraftHack;
import com.kobosh.koboshaddon.client.hack.BlockLoggerHack;
import com.kobosh.koboshaddon.client.hack.ExplorationHack;

public final class AddonFeatureRegistry {
    private AddonFeatureRegistry() {
    }

    public static AutoCraftHack autoCraftHack;
    public static ExplorationHack explorationHack;
    public static BlockLoggerHack blockLoggerHack;
}
