package com.kobosh.koboshaddon.client;

import com.kobosh.koboshaddon.client.command.AutoCraftCmd;
import com.kobosh.koboshaddon.client.command.ExploreCmd;
import com.kobosh.koboshaddon.client.command.FlyToCmd;
import com.kobosh.koboshaddon.client.command.NbtCmd;
import com.kobosh.koboshaddon.client.command.ViewLogsCmd;
import com.kobosh.koboshaddon.client.command.AliasCmd;
import com.kobosh.koboshaddon.client.command.ACmd;
import com.kobosh.koboshaddon.client.hack.AdvancedItemEspHack;
import com.kobosh.koboshaddon.client.hack.AirWalkHack;
import com.kobosh.koboshaddon.client.hack.AntiCrashHack;
import com.kobosh.koboshaddon.client.hack.AntiVanishHack;
import com.kobosh.koboshaddon.client.hack.AutoCraftHack;
import com.kobosh.koboshaddon.client.hack.AutoLibrarian2Hack;
import com.kobosh.koboshaddon.client.hack.AutoStaircaseHack;
import com.kobosh.koboshaddon.client.hack.AutoTraderHack;
import com.kobosh.koboshaddon.client.hack.BedFinderHack;
import com.kobosh.koboshaddon.client.hack.BlockLoggerHack;
import com.kobosh.koboshaddon.client.hack.BoatNoclipHack;
import com.kobosh.koboshaddon.client.hack.BowSpamHack;
import com.kobosh.koboshaddon.client.hack.BookDupeHack;
import com.kobosh.koboshaddon.client.hack.BookKickHack;
import com.kobosh.koboshaddon.client.hack.CordTagsHack;
import com.kobosh.koboshaddon.client.hack.CrosshairYFlyHack;
import com.kobosh.koboshaddon.client.hack.DragonAimBotHack;
import com.kobosh.koboshaddon.client.hack.ExplorationHack;
import com.kobosh.koboshaddon.client.hack.FastExcavatorHack;
import com.kobosh.koboshaddon.client.hack.FastTunnellerHack;
import com.kobosh.koboshaddon.client.hack.EventReactorHack;
import com.kobosh.koboshaddon.client.hack.FillerHack;
import com.kobosh.koboshaddon.client.hack.GamemodeNotifierHack;
import com.kobosh.koboshaddon.client.hack.GhostModeHack;
import com.kobosh.koboshaddon.client.hack.GunAimBotHack;
import com.kobosh.koboshaddon.client.hack.HackList2Hack;
import com.kobosh.koboshaddon.client.hack.InfiniteExplorerHack;
import com.kobosh.koboshaddon.client.hack.InvisReminderHack;
import com.kobosh.koboshaddon.client.hack.ItemSearchHack;
import com.kobosh.koboshaddon.client.hack.ItemTractorBeamHack;
import com.kobosh.koboshaddon.client.hack.ItemTpHack;
import com.kobosh.koboshaddon.client.hack.NBTViewerHack;
import com.kobosh.koboshaddon.client.hack.OpSignHack;
import com.kobosh.koboshaddon.client.hack.OreSimHack;
import com.kobosh.koboshaddon.client.hack.SpawnerPlayerEspHack;
import com.kobosh.koboshaddon.client.hack.StripAuraHack;
import com.kobosh.koboshaddon.client.hack.TeleportHack;
import com.kobosh.koboshaddon.client.hack.TrialSpawnerEspHack;
import com.kobosh.koboshaddon.client.hack.UseHack;
import com.kobosh.koboshaddon.client.hack.VehicleOneHitHack;
import com.kobosh.koboshaddon.client.hack.AutoOminousHack;
import com.kobosh.koboshaddon.client.hack.LoginCommandHack;
import com.kobosh.koboshaddon.client.hack.XPAuraHack;
import com.kobosh.koboshaddon.client.hack.BungeeCordSpoofHack;
import com.kobosh.koboshaddon.client.hack.AttributeSwapHack;
import com.kobosh.koboshaddon.client.hack.GridHack;
import com.kobosh.koboshaddon.client.hack.DoubleDoorsInteractHack;
import net.wurstclient.addon.Addon;
import net.wurstclient.command.Command;
import net.wurstclient.hack.Hack;

public final class WurstAddonHackAddon implements Addon {

    private final AutoCraftHack autoCraftHack = new AutoCraftHack();
    private final ExplorationHack explorationHack = new ExplorationHack();
    private final BlockLoggerHack blockLoggerHack = new BlockLoggerHack();

    private final Hack[] hacks = {
            new AdvancedItemEspHack(),
            new AirWalkHack(),
            new AntiCrashHack(),
            new AntiVanishHack(),
            autoCraftHack,
            new AutoLibrarian2Hack(),
            new AutoStaircaseHack(),
            new AutoTraderHack(),
            new BedFinderHack(),
            blockLoggerHack,
            new BoatNoclipHack(),
            new BowSpamHack(),
            new BookDupeHack(),
            new BookKickHack(),
            new CordTagsHack(),
            new CrosshairYFlyHack(),
            new DragonAimBotHack(),
            explorationHack,
            new EventReactorHack(),
            new FastExcavatorHack(),
            new FastTunnellerHack(),
            new FillerHack(),
            new GamemodeNotifierHack(),
            new GhostModeHack(),
            new GunAimBotHack(),
            new HackList2Hack(),
            new InfiniteExplorerHack(),
            new InvisReminderHack(),
            new ItemSearchHack(),
            new ItemTractorBeamHack(),
            new ItemTpHack(),
            new NBTViewerHack(),
            new OpSignHack(),
            new OreSimHack(),
            new SpawnerPlayerEspHack(),
            new StripAuraHack(),
            new TeleportHack(),
            new TrialSpawnerEspHack(),
            new UseHack(),
            new VehicleOneHitHack(),
            new AutoOminousHack(),
            new LoginCommandHack(),
            new XPAuraHack(),
            new BungeeCordSpoofHack(),
            new AttributeSwapHack(),
            new GridHack(),
            new DoubleDoorsInteractHack()
    };

    private final Command[] commands = {
            new AutoCraftCmd(),
            new ExploreCmd(),
            new FlyToCmd(),
            new NbtCmd(),
            new ViewLogsCmd(),
            new AliasCmd(),
            new ACmd()
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
