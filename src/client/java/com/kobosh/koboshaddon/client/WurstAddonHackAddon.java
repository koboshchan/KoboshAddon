package com.kobosh.koboshaddon.client;

import com.kobosh.koboshaddon.client.command.AutoCraftCmd;
import com.kobosh.koboshaddon.client.command.ExploreCmd;
import com.kobosh.koboshaddon.client.command.FlyToCmd;
import com.kobosh.koboshaddon.client.command.ViewLogsCmd;
import com.kobosh.koboshaddon.client.hack.AirWalkHack;
import com.kobosh.koboshaddon.client.hack.AntiVanishHack;
import com.kobosh.koboshaddon.client.hack.AutoCraftHack;
import com.kobosh.koboshaddon.client.hack.AutoLibrarian2Hack;
import com.kobosh.koboshaddon.client.hack.AutoTraderHack;
import com.kobosh.koboshaddon.client.hack.BedFinderHack;
import com.kobosh.koboshaddon.client.hack.BlockLoggerHack;
import com.kobosh.koboshaddon.client.hack.BookDupeHack;
import com.kobosh.koboshaddon.client.hack.BookKickHack;
import com.kobosh.koboshaddon.client.hack.DragonAimBotHack;
import com.kobosh.koboshaddon.client.hack.ExplorationHack;
import com.kobosh.koboshaddon.client.hack.EventReactorHack;
import com.kobosh.koboshaddon.client.hack.FillerHack;
import com.kobosh.koboshaddon.client.hack.GamemodeNotifierHack;
import com.kobosh.koboshaddon.client.hack.GunAimBotHack;
import com.kobosh.koboshaddon.client.hack.InfiniteExplorerHack;
import com.kobosh.koboshaddon.client.hack.InvisReminderHack;
import com.kobosh.koboshaddon.client.hack.ItemSearchHack;
import com.kobosh.koboshaddon.client.hack.ItemTpHack;
import com.kobosh.koboshaddon.client.hack.NBTViewerHack;
import com.kobosh.koboshaddon.client.hack.OpSignHack;
import com.kobosh.koboshaddon.client.hack.SpawnerPlayerEspHack;
import com.kobosh.koboshaddon.client.hack.VehicleOneHitHack;
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
            new AutoLibrarian2Hack(),
            new AutoTraderHack(),
            new BedFinderHack(),
            blockLoggerHack,
            new BookDupeHack(),
            new BookKickHack(),
            new DragonAimBotHack(),
            explorationHack,
            new EventReactorHack(),
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
