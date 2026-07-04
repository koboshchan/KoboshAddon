/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package com.kobosh.koboshaddon.client.hack;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.util.Mth;
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

	private static final int CHECK_INTERVAL_TICKS = 200;

	private int lastInventoryAmount;
	private boolean pickupTriggered;
	private boolean dropTriggered;
	private int ticksUntilCheck;
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
		pickupTriggered = false;
		dropTriggered = false;
		ticksUntilCheck = CHECK_INTERVAL_TICKS;
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
		if(MC.player == null || MC.level == null)
			return;

		int inventoryAmount = getInventoryAmount();
		if(inventoryAmount > lastInventoryAmount)
			pickupTriggered = true;

		lastInventoryAmount = inventoryAmount;

		ticksUntilCheck--;
		if(ticksUntilCheck > 0)
			return;

		ticksUntilCheck = CHECK_INTERVAL_TICKS;
		if(!isConditionTrue(triggerEvent.getSelected()))
			return;

		trigger(getEventName(triggerEvent.getSelected()));
		pickupTriggered = false;
		dropTriggered = false;
	}

	@Override
	public void onSentPacket(PacketOutputEvent event)
	{
		if(!(event.getPacket() instanceof ServerboundPlayerActionPacket packet))
			return;

		ServerboundPlayerActionPacket.Action action = packet.getAction();
		if(action == ServerboundPlayerActionPacket.Action.DROP_ITEM
			|| action == ServerboundPlayerActionPacket.Action.DROP_ALL_ITEMS)
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

	private boolean isConditionTrue(TriggerEvent event)
	{
		return switch(event)
		{
			case ITEM_PICKUP -> pickupTriggered;
			case ITEM_DROP -> dropTriggered;
			case INVENTORY_FULL -> isInventoryFull();
			case OPEN_CHEST -> isChestOpen();
			case LOW_HEALTH -> isLowHealth();
		};
	}

	private String getEventName(TriggerEvent event)
	{
		return switch(event)
		{
			case ITEM_PICKUP -> "item pickup";
			case ITEM_DROP -> "item drop";
			case INVENTORY_FULL -> "inventory full";
			case OPEN_CHEST -> "chest opened";
			case LOW_HEALTH -> "low health";
		};
	}

	private void doSayInChat()
	{
		if(MC.player == null || MC.player.connection == null)
			return;

		String message = chatMessage.getValue().trim();
		if(message.isEmpty())
			return;

		MC.player.connection.sendChat(message);
	}

	private void doRunCommand()
	{
		if(MC.player == null || MC.player.connection == null)
			return;

		String cmd = command.getValue().trim();
		if(cmd.isEmpty())
			return;

		if(cmd.startsWith("/"))
			cmd = cmd.substring(1);

		if(cmd.isEmpty())
			return;

		MC.player.connection.sendCommand(cmd);
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
		int ticks = Mth.clamp(cooldownTicks.getValueI(), 0, 200);
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
			total += MC.player.getInventory().getItem(i).getCount();

		return total;
	}

	private boolean isInventoryFull()
	{
		if(MC.player == null)
			return false;

		return MC.player.getInventory().getFreeSlot() == -1;
	}

	private boolean isChestOpen()
	{
		if(MC.player == null)
			return false;

		if(!(MC.screen instanceof AbstractContainerScreen<?>))
			return false;

		return MC.player.containerMenu instanceof ChestMenu
			&& !(MC.player.containerMenu instanceof InventoryMenu);
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