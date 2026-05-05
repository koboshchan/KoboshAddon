/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package com.kobosh.koboshaddon.client.hack;

import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.util.math.MathHelper;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.PacketOutputListener;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.EnumSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.settings.TextFieldSetting;
import net.wurstclient.util.ChatUtils;

@SearchTags({"event", "reactor", "trigger", "automation"})
public final class EventReactorHack extends Hack
	implements UpdateListener, PacketOutputListener
{
	private final EnumSetting<TriggerEvent> triggerEvent =
		new EnumSetting<>("Event",
			"Which in-game event should trigger the reaction.",
			TriggerEvent.values(), TriggerEvent.ITEM_PICKUP);

	private final EnumSetting<Reaction> reaction =
		new EnumSetting<>("Reaction",
			"What to do when the selected event happens.", Reaction.values(),
			Reaction.SAY_IN_CHAT);

	private final TextFieldSetting chatMessage = new TextFieldSetting("Chat",
		"Message sent when reaction is set to 'Say in chat'.",
		"EventReactor triggered!");

	private final TextFieldSetting command = new TextFieldSetting("Command",
		"Command to run when reaction is set to 'Run command'.",
		"spawn");

	private final TextFieldSetting targetHack =
		new TextFieldSetting("Target hack",
			"Hack name used by toggle/enable/disable reactions.", "AutoSprint");

	private final SliderSetting healthThreshold = new SliderSetting(
		"Low health", "Health threshold used by the Low health event.", 6, 1,
		20, 1, ValueDisplay.INTEGER);

	private final SliderSetting cooldownTicks = new SliderSetting("Cooldown",
		"Minimum ticks between triggers (20 ticks = 1 second).", 20, 0, 200,
		1, ValueDisplay.INTEGER);

	private int lastInventoryAmount;
	private boolean wasInventoryFull;
	private boolean wasChestOpen;
	private boolean wasLowHealth;
	private boolean dropTriggered;
	private long lastTriggerMillis;

	public EventReactorHack()
	{
		super("EventReactor");
		setCategory(Category.OTHER);

		addSetting(triggerEvent);
		addSetting(reaction);
		addSetting(chatMessage);
		addSetting(command);
		addSetting(targetHack);
		addSetting(healthThreshold);
		addSetting(cooldownTicks);
	}

	@Override
	protected void onEnable()
	{
		lastInventoryAmount = getInventoryAmount();
		wasInventoryFull = isInventoryFull();
		wasChestOpen = isChestOpen();
		wasLowHealth = isLowHealth();
		dropTriggered = false;
		lastTriggerMillis = 0;

		EVENTS.add(UpdateListener.class, this);
		EVENTS.add(PacketOutputListener.class, this);
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		EVENTS.remove(PacketOutputListener.class, this);
	}

	@Override
	public void onUpdate()
	{
		if(MC.player == null || MC.world == null)
			return;

		int inventoryAmount = getInventoryAmount();
		boolean inventoryFull = isInventoryFull();
		boolean chestOpen = isChestOpen();
		boolean lowHealth = isLowHealth();

		switch(triggerEvent.getSelected())
		{
			case ITEM_PICKUP -> {
				if(inventoryAmount > lastInventoryAmount)
					trigger("item pickup");
			}
			case ITEM_DROP -> {
				if(dropTriggered)
					trigger("item drop");
				dropTriggered = false;
			}
			case INVENTORY_FULL -> {
				if(inventoryFull && !wasInventoryFull)
					trigger("inventory full");
			}
			case OPEN_CHEST -> {
				if(chestOpen && !wasChestOpen)
					trigger("chest opened");
			}
			case LOW_HEALTH -> {
				if(lowHealth && !wasLowHealth)
					trigger("low health");
			}
		}

		lastInventoryAmount = inventoryAmount;
		wasInventoryFull = inventoryFull;
		wasChestOpen = chestOpen;
		wasLowHealth = lowHealth;
	}

	@Override
	public void onSentPacket(PacketOutputEvent event)
	{
		if(!(event.getPacket() instanceof PlayerActionC2SPacket packet))
			return;

		PlayerActionC2SPacket.Action action = packet.getAction();
		if(action == PlayerActionC2SPacket.Action.DROP_ITEM
			|| action == PlayerActionC2SPacket.Action.DROP_ALL_ITEMS)
			dropTriggered = true;
	}

	private void trigger(String eventName)
	{
		if(!isOffCooldown())
			return;

		switch(reaction.getSelected())
		{
			case SAY_IN_CHAT -> doSayInChat();
			case RUN_COMMAND -> doRunCommand();
			case TOGGLE_HACK -> doToggleHack();
			case ENABLE_HACK -> doEnableHack();
			case DISABLE_HACK -> doDisableHack();
			case SHOW_NOTIFICATION ->
				ChatUtils.message("EventReactor: triggered by " + eventName + ".");
		}

		lastTriggerMillis = System.currentTimeMillis();
	}

	private void doSayInChat()
	{
		if(MC.player == null || MC.player.networkHandler == null)
			return;

		String message = chatMessage.getValue().trim();
		if(message.isEmpty())
			return;

		MC.player.networkHandler.sendChatMessage(message);
	}

	private void doRunCommand()
	{
		if(MC.player == null || MC.player.networkHandler == null)
			return;

		String cmd = command.getValue().trim();
		if(cmd.isEmpty())
			return;

		if(cmd.startsWith("/"))
			cmd = cmd.substring(1);

		if(cmd.isEmpty())
			return;

		MC.player.networkHandler.sendChatCommand(cmd);
	}

	private void doToggleHack()
	{
		Hack hack = getTargetHack();
		if(hack == null)
			return;

		hack.setEnabled(!hack.isEnabled());
	}

	private void doEnableHack()
	{
		Hack hack = getTargetHack();
		if(hack == null)
			return;

		hack.setEnabled(true);
	}

	private void doDisableHack()
	{
		Hack hack = getTargetHack();
		if(hack == null)
			return;

		hack.setEnabled(false);
	}

	private Hack getTargetHack()
	{
		String hackName = targetHack.getValue().trim();
		if(hackName.isEmpty())
		{
			ChatUtils.error("EventReactor target hack is empty.");
			return null;
		}

		Hack hack = WURST.getHax().getHackByName(hackName);
		if(hack == null)
		{
			ChatUtils.error("EventReactor target hack not found: " + hackName);
			return null;
		}

		return hack;
	}

	private boolean isOffCooldown()
	{
		int ticks = MathHelper.clamp(cooldownTicks.getValueI(), 0, 200);
		if(ticks <= 0)
			return true;

		long elapsed = System.currentTimeMillis() - lastTriggerMillis;
		return elapsed >= ticks * 50L;
	}

	private int getInventoryAmount()
	{
		if(MC.player == null)
			return 0;

		int total = 0;
		for(int i = 0; i < 41; i++)
			total += MC.player.getInventory().getStack(i).getCount();

		return total;
	}

	private boolean isInventoryFull()
	{
		if(MC.player == null)
			return false;

		return MC.player.getInventory().getEmptySlot() == -1;
	}

	private boolean isChestOpen()
	{
		if(MC.player == null)
			return false;

		if(!(MC.currentScreen instanceof HandledScreen<?>))
			return false;

		return MC.player.currentScreenHandler instanceof GenericContainerScreenHandler
			&& !(MC.player.currentScreenHandler instanceof PlayerScreenHandler);
	}

	private boolean isLowHealth()
	{
		if(MC.player == null)
			return false;

		return MC.player.getHealth() <= healthThreshold.getValue();
	}

	private enum TriggerEvent
	{
		ITEM_PICKUP("Item pickup"),
		ITEM_DROP("Item drop"),
		INVENTORY_FULL("Inventory full"),
		OPEN_CHEST("Open chest"),
		LOW_HEALTH("Low health");

		private final String displayName;

		private TriggerEvent(String displayName)
		{
			this.displayName = displayName;
		}

		@Override
		public String toString()
		{
			return displayName;
		}
	}

	private enum Reaction
	{
		SAY_IN_CHAT("Say in chat"),
		RUN_COMMAND("Run command"),
		TOGGLE_HACK("Toggle hack"),
		ENABLE_HACK("Enable hack"),
		DISABLE_HACK("Disable hack"),
		SHOW_NOTIFICATION("Show notification");

		private final String displayName;

		private Reaction(String displayName)
		{
			this.displayName = displayName;
		}

		@Override
		public String toString()
		{
			return displayName;
		}
	}
}