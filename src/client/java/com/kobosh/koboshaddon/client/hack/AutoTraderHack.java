/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package com.kobosh.koboshaddon.client.hack;

import net.minecraft.core.Holder;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.protocol.game.ServerboundSelectTradePacket;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.settings.TextFieldSetting;
import net.wurstclient.util.ChatUtils;
import net.wurstclient.util.InventoryUtils;

@SearchTags({"auto trader", "villager trader", "merchant bot", "trade bot",
	"auto trade"})
public final class AutoTraderHack extends Hack implements UpdateListener
{
	private final TextFieldSetting targetItem = new TextFieldSetting(
		"Target item",
		"The item you want to get from villagers (e.g., 'minecraft:emerald', 'minecraft:enchanted_book')",
		"minecraft:emerald");
	
	private final TextFieldSetting paymentItem = new TextFieldSetting(
		"Payment item",
		"The item you want to trade for the target item (e.g., 'minecraft:wheat', 'minecraft:paper')",
		"minecraft:wheat");
	
	private final SliderSetting maxPrice = new SliderSetting("Max price",
		"Maximum amount of payment items to spend for one target item", 32, 1,
		64, 1, ValueDisplay.INTEGER);
	
	private final CheckboxSetting autoTrade = new CheckboxSetting("Auto trade",
		"Automatically execute trades when the target item is found", true);
	
	private final SliderSetting maxTrades = new SliderSetting("Max trades",
		"Maximum number of trades to execute per villager", 1, 1, 100, 1,
		ValueDisplay.INTEGER);
	
	private final SliderSetting delay =
		new SliderSetting("Delay", "Delay between trades in milliseconds", 10.0,
			0, 100, 0.01, ValueDisplay.DECIMAL.withSuffix("ms"));
	
	private final CheckboxSetting requireExactItem = new CheckboxSetting(
		"Require exact item",
		"Only trade for the exact item specified (ignores enchantments, etc.)",
		false);
	
	private final CheckboxSetting onlyUnlockedTrades =
		new CheckboxSetting("Only unlocked trades",
			"Only execute trades that are currently available (not disabled)",
			true);
	
	private final CheckboxSetting containsTradeRerollPlugin =
		new CheckboxSetting("Contains trade reroll plugin",
			"Attempts to buy the reroll target to trigger a reroll when exhausted",
			false);
	
	private final TextFieldSetting rerollTarget = new TextFieldSetting(
		"Reroll target",
		"The item to purchase to trigger a reroll (e.g., 'minecraft:nether_star')",
		"minecraft:nether_star");
	
	private final CheckboxSetting muteChatLogs = new CheckboxSetting(
		"Mute chat logs", "Mutes chat logs produced by this hack.", false);
	
	private int tradesExecuted;
	
	private boolean tradingInProgress;
	private long lastTradeTime;
	private long lastServerSyncTime;
	
	public AutoTraderHack()
	{
		super("AutoTrader");
		setCategory(Category.OTHER);
		
		addSetting(targetItem);
		addSetting(paymentItem);
		addSetting(maxPrice);
		addSetting(autoTrade);
		addSetting(maxTrades);
		addSetting(delay);
		addSetting(requireExactItem);
		addSetting(onlyUnlockedTrades);
		addSetting(containsTradeRerollPlugin);
		addSetting(rerollTarget);
		addSetting(muteChatLogs);
	}
	
	@Override
	protected void onEnable()
	{
		EVENTS.add(UpdateListener.class, this);
		tradesExecuted = 0;
		tradingInProgress = false;
		lastTradeTime = 0;
		lastServerSyncTime = 0;
	}
	
	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		
		if(MC.gui.screen() instanceof MerchantScreen)
			MC.player.closeContainer();
		
		tradesExecuted = 0;
		tradingInProgress = false;
	}
	
	@Override
	public void onUpdate()
	{
		// Only handle trading if a trade screen is manually opened
		if(MC.gui.screen() instanceof MerchantScreen tradeScreen)
		{
			handleTrading(tradeScreen);
			return;
		}
		
		// Reset trading state when not in trade screen
		if(tradingInProgress)
		{
			tradingInProgress = false;
			tradesExecuted = 0;
		}
	}
	
	private void handleTrading(MerchantScreen tradeScreen)
	{
		if(!autoTrade.isChecked())
			return;
		
		if(tradesExecuted >= maxTrades.getValueI())
		{
			if(!muteChatLogs.isChecked())
				ChatUtils.message(
					"Completed " + tradesExecuted + " trades with villager.");
			MC.player.closeContainer();
			tradesExecuted = 0;
			return;
		}
		
		MerchantOffers offers = tradeScreen.getMenu().getOffers();
		MerchantOffer targetOffer = findTargetTrade(offers, true);
		
		if(targetOffer == null || targetOffer.isOutOfStock())
		{
			if(containsTradeRerollPlugin.isChecked())
			{
				MerchantOffer rerollOffer = findRerollTargetTrade(offers);
				if(rerollOffer != null && !rerollOffer.isOutOfStock()
					&& hasEnoughItems(rerollOffer))
				{
					executeTrade(tradeScreen, rerollOffer, offers);
					return;
				}
			}
			
			ChatUtils.warning("No suitable trades found with this villager.");
			MC.player.closeContainer();
			return;
		}
		
		// Check if we have enough items to trade
		if(!hasEnoughItems(targetOffer))
		{
			ChatUtils.warning("Not enough items to complete trade.");
			MC.player.closeContainer();
			return;
		}
		
		// Check delay before executing trade
		if(System.currentTimeMillis() - lastTradeTime < delay.getValue())
			return;
		
		// Execute the trade
		executeTrade(tradeScreen, targetOffer, offers);
	}
	
	private MerchantOffer findTargetTrade(MerchantOffers offers,
                                          boolean filterDisabled)
	{
		Item targetItemType = getItemFromString(targetItem.getValue());
		Item paymentItemType = getItemFromString(paymentItem.getValue());
		
		if(targetItemType == null || paymentItemType == null)
			return null;
		
		for(int i = 0; i < offers.size(); i++)
		{
			MerchantOffer offer = offers.get(i);
			
			// Skip disabled trades if setting is enabled
			if(filterDisabled && onlyUnlockedTrades.isChecked()
				&& offer.isOutOfStock())
				continue;
			
			// Check if this trade gives us our target item
			ItemStack sellItem = offer.getResult();
			boolean matchesTarget;
			
			if(requireExactItem.isChecked())
				matchesTarget = sellItem.getItem() == targetItemType;
			else
				matchesTarget = sellItem.is(targetItemType);
			
			if(!matchesTarget)
				continue;
			
			// Check if we can pay for it with our payment item
			ItemStack firstBuyItem = offer.getBaseCostA();
			
			boolean canAfford = false;
			
			// Check first buy item
			if(firstBuyItem.is(paymentItemType)
				&& firstBuyItem.getCount() <= maxPrice.getValueI())
			{
				canAfford = true;
			}
			
			if(canAfford)
				return offer;
		}
		
		return null;
	}
	
	private MerchantOffer findRerollTargetTrade(MerchantOffers offers)
	{
		Item rerollItemType = getItemFromString(rerollTarget.getValue());
		if(rerollItemType == null)
			return null;
		
		for(MerchantOffer offer : offers)
		{
			if(offer.getResult().is(rerollItemType))
				return offer;
		}
		
		return null;
	}
	
	private void executeTrade(MerchantScreen tradeScreen,
                              MerchantOffer targetOffer, MerchantOffers offers)
	{
		// Find the index of our target offer
		int tradeIndex = -1;
		for(int i = 0; i < offers.size(); i++)
		{
			if(offers.get(i) == targetOffer)
			{
				tradeIndex = i;
				break;
			}
		}
		
		if(tradeIndex == -1)
			return;
		
		// Select the trade
		tradeScreen.getMenu().setSelectionHint(tradeIndex);
		tradeScreen.getMenu().tryMoveItems(tradeIndex);
		MC.getConnection()
			.send(new ServerboundSelectTradePacket(tradeIndex));
		
		// Update server sync timer after selection
		if(lastServerSyncTime == 0)
			lastServerSyncTime = System.currentTimeMillis();
		
		// Wait for configured delay before server processing
		if(System.currentTimeMillis() - lastServerSyncTime < delay.getValue())
			return;
		
		// Execute the trade by clicking the result slot
		MC.gameMode.handleContainerInput(tradeScreen.getMenu().containerId,
			2, 0, ContainerInput.PICKUP, MC.player);
		
		tradesExecuted++;
		tradingInProgress = true;
		lastTradeTime = System.currentTimeMillis();
		lastServerSyncTime = 0; // Reset for next trade
		
		if(!muteChatLogs.isChecked())
			ChatUtils.message("Executed trade " + tradesExecuted + "/"
				+ maxTrades.getValueI() + " - Got "
				+ targetOffer.getResult().getHoverName().getString());
	}
	
	private boolean hasEnoughItems(MerchantOffer offer)
	{
		ItemStack firstBuyItem = offer.getBaseCostA();
		
		// Check first item
		if(!firstBuyItem.isEmpty())
		{
			int needed = firstBuyItem.getCount();
			int available = InventoryUtils.count(firstBuyItem.getItem());
			if(available < needed)
				return false;
		}
		
		return true;
	}
	
	private Item getItemFromString(String itemId)
	{
		try
		{
			Identifier id = Identifier.tryParse(itemId);
			if(id == null)
				return null;
			return BuiltInRegistries.ITEM.get(id).map(Holder::value).orElse(null);
		}catch(Exception e)
		{
			return null;
		}
	}
}
