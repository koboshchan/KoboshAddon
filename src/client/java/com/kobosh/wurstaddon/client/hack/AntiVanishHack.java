/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package com.kobosh.wurstaddon.client.hack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import com.mojang.brigadier.suggestion.Suggestion;

import net.minecraft.network.packet.c2s.play.RequestCommandCompletionsC2SPacket;
import net.minecraft.network.packet.s2c.play.CommandSuggestionsS2CPacket;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.ChatInputListener;
import net.wurstclient.events.PacketInputListener;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.EnumSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.TextFieldSetting;
import net.wurstclient.util.ChatUtils;

@SearchTags({"anti vanish", "vanish detection", "admin detection"})
public final class AntiVanishHack extends Hack
	implements UpdateListener, PacketInputListener, ChatInputListener
{
	private final SliderSetting interval =
		new SliderSetting("Interval", "Vanish check interval in ticks.", 100, 1,
			300, 1, SliderSetting.ValueDisplay.INTEGER);
	
	private final EnumSetting<Mode> mode = new EnumSetting<>("Mode",
		"Detection mode:\n"
			+ "\u00a7lLeave Message\u00a7r - Detects when a player disconnects without sending a leave message.\n"
			+ "\u00a7lReal Join Message\u00a7r - Uses command completion to verify if players actually left.",
		Mode.values(), Mode.LEAVE_MESSAGE);
	
	private final TextFieldSetting command = new TextFieldSetting("Command",
		"The completion command to detect player names.", "minecraft:msg");
	
	private Map<UUID, String> playerCache = new HashMap<>();
	private final List<String> messageCache = new ArrayList<>();
	
	private final Random random = new Random();
	private final List<Integer> completionIDs = new ArrayList<>();
	private List<String> completionPlayerCache = new ArrayList<>();
	
	private int timer = 0;
	
	public AntiVanishHack()
	{
		super("AntiVanish");
		setCategory(Category.CHAT);
		addSetting(interval);
		addSetting(mode);
		addSetting(command);
	}
	
	@Override
	protected void onEnable()
	{
		timer = 0;
		completionIDs.clear();
		messageCache.clear();
		EVENTS.add(UpdateListener.class, this);
		EVENTS.add(PacketInputListener.class, this);
		EVENTS.add(ChatInputListener.class, this);
	}
	
	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		EVENTS.remove(PacketInputListener.class, this);
		EVENTS.remove(ChatInputListener.class, this);
	}
	
	@Override
	public void onUpdate()
	{
		timer++;
		if(timer < interval.getValueI())
			return;
		
		switch(mode.getSelected())
		{
			case LEAVE_MESSAGE -> checkLeaveMessages();
			case REAL_JOIN_MESSAGE -> checkWithCompletion();
		}
		
		timer = 0;
		messageCache.clear();
	}
	
	private void checkLeaveMessages()
	{
		Map<UUID, String> oldPlayers = Map.copyOf(playerCache);
		playerCache = MC.getNetworkHandler().getPlayerList().stream()
			.collect(Collectors.toMap(e -> e.getProfile().getId(),
				e -> e.getProfile().getName()));
		
		for(UUID uuid : oldPlayers.keySet())
		{
			if(playerCache.containsKey(uuid))
				continue;
			
			String name = oldPlayers.get(uuid);
			if(name.contains(" ") || name.length() < 3 || name.length() > 16)
				continue;
			
			if(messageCache.stream().noneMatch(s -> s.contains(name)))
				ChatUtils.warning(name + " has gone into vanish.");
		}
	}
	
	private void checkWithCompletion()
	{
		int id = random.nextInt(200);
		completionIDs.add(id);
		MC.getNetworkHandler()
			.sendPacket(new RequestCommandCompletionsC2SPacket(id,
				command.getValue() + " "));
	}
	
	@Override
	public void onReceivedPacket(PacketInputEvent event)
	{
		if(mode.getSelected() != Mode.REAL_JOIN_MESSAGE)
			return;
		
		if(!(event.getPacket() instanceof CommandSuggestionsS2CPacket packet))
			return;
		
		if(!completionIDs.contains(packet.id()))
			return;
		
		var lastUsernames = completionPlayerCache.stream().toList();
		
		completionPlayerCache = packet.getSuggestions().getList().stream()
			.map(Suggestion::getText).toList();
		
		if(lastUsernames.isEmpty())
			return;
		
		Predicate<String> joinedOrQuit = playerName -> lastUsernames
			.contains(playerName) != completionPlayerCache.contains(playerName);
		
		for(String playerName : completionPlayerCache)
		{
			if(Objects.equals(playerName, MC.player.getName().getString()))
				continue;
			if(playerName.contains(" ") || playerName.length() < 3
				|| playerName.length() > 16)
				continue;
			if(joinedOrQuit.test(playerName))
				ChatUtils.message("Player joined: " + playerName);
		}
		
		for(String playerName : lastUsernames)
		{
			if(Objects.equals(playerName, MC.player.getName().getString()))
				continue;
			if(playerName.contains(" ") || playerName.length() < 3
				|| playerName.length() > 16)
				continue;
			if(joinedOrQuit.test(playerName))
				ChatUtils.message("Player left: " + playerName);
		}
		
		completionIDs.remove(Integer.valueOf(packet.id()));
		event.cancel();
	}
	
	@Override
	public void onReceivedMessage(ChatInputEvent event)
	{
		messageCache.add(event.getComponent().getString());
	}
	
	public enum Mode
	{
		LEAVE_MESSAGE("Leave Message"),
		REAL_JOIN_MESSAGE("Real Join Message");
		
		private final String name;
		
		private Mode(String name)
		{
			this.name = name;
		}
		
		@Override
		public String toString()
		{
			return name;
		}
	}
}
