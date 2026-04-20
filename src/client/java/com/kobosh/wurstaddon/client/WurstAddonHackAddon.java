package com.kobosh.wurstaddon.client;

import com.kobosh.wurstaddon.client.command.AutoCraftCmd;
import com.kobosh.wurstaddon.client.command.ExploreCmd;
import com.kobosh.wurstaddon.client.command.FlyToCmd;
import com.kobosh.wurstaddon.client.command.ViewLogsCmd;
import com.kobosh.wurstaddon.client.hack.AirWalkHack;
import com.kobosh.wurstaddon.client.hack.AntiVanishHack;
import com.kobosh.wurstaddon.client.hack.AutoCraftHack;
import com.kobosh.wurstaddon.client.hack.AutoTraderHack;
import com.kobosh.wurstaddon.client.hack.BedFinderHack;
import com.kobosh.wurstaddon.client.hack.BlockLoggerHack;
import com.kobosh.wurstaddon.client.hack.BookDupeHack;
import com.kobosh.wurstaddon.client.hack.BookKickHack;
import com.kobosh.wurstaddon.client.hack.DragonAimBotHack;
import com.kobosh.wurstaddon.client.hack.ExplorationHack;
import com.kobosh.wurstaddon.client.hack.FillerHack;
import com.kobosh.wurstaddon.client.hack.GamemodeNotifierHack;
import com.kobosh.wurstaddon.client.hack.GunAimBotHack;
import com.kobosh.wurstaddon.client.hack.InfiniteExplorerHack;
import com.kobosh.wurstaddon.client.hack.InvisReminderHack;
import com.kobosh.wurstaddon.client.hack.ItemSearchHack;
import com.kobosh.wurstaddon.client.hack.ItemTpHack;
import com.kobosh.wurstaddon.client.hack.NBTViewerHack;
import com.kobosh.wurstaddon.client.hack.OpSignHack;
import com.kobosh.wurstaddon.client.hack.SpawnerPlayerEspHack;
import com.kobosh.wurstaddon.client.hack.VehicleOneHitHack;
import net.wurstclient.addon.Addon;
import net.wurstclient.command.Command;
import net.wurstclient.hack.Hack;

public final class WurstAddonHackAddon implements Addon {

    private final AutoCraftHack autoCraftHack = new AutoCraftHack();
    private final ExplorationHack explorationHack = new ExplorationHack();
    private final BlockLoggerHack blockLoggerHack = new BlockLoggerHack();

    private final Hack[] hacks = {
            new AirWalkHack(),
            new AntiVanishHack(),
            autoCraftHack,
            new AutoTraderHack(),
            new BedFinderHack(),
            blockLoggerHack,
            new BookDupeHack(),
            new BookKickHack(),
            new DragonAimBotHack(),
            explorationHack,
            new FillerHack(),
            new GamemodeNotifierHack(),
            new GunAimBotHack(),
            new InfiniteExplorerHack(),
            new InvisReminderHack(),
            new ItemSearchHack(),
            new ItemTpHack(),
            new NBTViewerHack(),
            new OpSignHack(),
            new SpawnerPlayerEspHack(),
            new VehicleOneHitHack()
    };

    private final Command[] commands = {
            new AutoCraftCmd(),
            new ExploreCmd(),
            new FlyToCmd(),
            new ViewLogsCmd()
    };

    public WurstAddonHackAddon() {
        AddonFeatureRegistry.autoCraftHack = autoCraftHack;
        AddonFeatureRegistry.explorationHack = explorationHack;
        AddonFeatureRegistry.blockLoggerHack = blockLoggerHack;
    }

    @Override
    public String getAddonName() {
        return "KoboshAddon";
    }

    @Override
    public Hack[] getHacks() {
        return hacks;
    }

    @Override
    public Command[] getCommands() {
        return commands;
    }
}
