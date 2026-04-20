/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package com.kobosh.wurstaddon.client.hack;

import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.network.packet.s2c.play.PlayerListS2CPacket;
import net.minecraft.world.GameMode;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.PacketInputListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.util.ChatUtils;

@SearchTags({"gamemode notifier", "gm notifier", "gamemode detection"})
public final class GamemodeNotifierHack extends Hack
	implements PacketInputListener
{
	private final CheckboxSetting survival = new CheckboxSetting("Survival",
		"Notify when a player changes to Survival mode.", true);
	
	private final CheckboxSetting creative = new CheckboxSetting("Creative",
		"Notify when a player changes to Creative mode.", true);
	
	private final CheckboxSetting adventure = new CheckboxSetting("Adventure",
		"Notify when a player changes to Adventure mode.", true);
	
	private final CheckboxSetting spectator = new CheckboxSetting("Spectator",
		"Notify when a player changes to Spectator mode.", true);
	
	public GamemodeNotifierHack()
	{
		super("GamemodeNotifier");
		setCategory(Category.CHAT);
		addSetting(survival);
		addSetting(creative);
		addSetting(adventure);
		addSetting(spectator);
	}
	
	@Override
	protected void onEnable()
	{
		EVENTS.add(PacketInputListener.class, this);
	}
	
	@Override
	protected void onDisable()
	{
		EVENTS.remove(PacketInputListener.class, this);
	}
	
	@Override
	public void onReceivedPacket(PacketInputEvent event)
	{
		if(!(event.getPacket() instanceof PlayerListS2CPacket packet))
			return;
		
		// Check if network handler is available
		if(MC.getNetworkHandler() == null)
			return;
		
		for(PlayerListS2CPacket.Entry entry : packet.getEntries())
		{
			if(!packet.getActions()
				.contains(PlayerListS2CPacket.Action.UPDATE_GAME_MODE))
				continue;
			
			PlayerListEntry playerEntry =
				MC.getNetworkHandler().getPlayerListEntry(entry.profileId());
			if(playerEntry == null)
				continue;
			
			GameMode newGameMode = entry.gameMode();
			if(playerEntry.getGameMode() == newGameMode)
				continue;
			
			if(!shouldNotify(newGameMode))
				continue;
			
			String playerName = playerEntry.getProfile().getName();
			String gameModeName = getGameModeName(newGameMode);
			ChatUtils.message("Player " + playerName + " changed gamemode to "
				+ gameModeName);
		}
	}
	
	private boolean shouldNotify(GameMode gameMode)
	{
		return switch(gameMode)
		{
			case SURVIVAL -> survival.isChecked();
			case CREATIVE -> creative.isChecked();
			case ADVENTURE -> adventure.isChecked();
			case SPECTATOR -> spectator.isChecked();
		};
	}
	
	private String getGameModeName(GameMode gameMode)
	{
		return switch(gameMode)
		{
			case SURVIVAL -> "Survival";
			case CREATIVE -> "Creative";
			case ADVENTURE -> "Adventure";
			case SPECTATOR -> "Spectator";
		};
	}
}
