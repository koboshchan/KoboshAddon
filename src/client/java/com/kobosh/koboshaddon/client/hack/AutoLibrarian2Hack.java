/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package com.kobosh.koboshaddon.client.hack;

import java.util.Comparator;
import java.util.HashSet;
import java.util.stream.StreamSupport;

import net.minecraft.client.gui.screen.ingame.MerchantScreen;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.SelectMerchantTradeC2SPacket;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.village.TradeOffer;
import net.minecraft.village.TradeOfferList;
import net.minecraft.village.VillagerProfession;
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
import net.wurstclient.util.RotationUtils;

@SearchTags({"auto librarian 2", "librarian plugin", "refresh trades",
	"villager reroll", "librarian trainer"})
public final class AutoLibrarian2Hack extends Hack implements UpdateListener
{
	private final TextFieldSetting enchantment = new TextFieldSetting(
		"Enchantment",
		"The enchantment ID to look for (e.g. 'minecraft:mending').",
		"minecraft:mending");
	
	private final SliderSetting level = new SliderSetting("Level",
		"The required enchantment level.", 1, 1, 5, 1, ValueDisplay.INTEGER);
	
	private final SliderSetting maxPrice = new SliderSetting("Max price",
		"Maximum emerald cost to accept for the enchanted book.", 64, 1, 64, 1,
		ValueDisplay.INTEGER);
	
	private final TextFieldSetting rerollItem = new TextFieldSetting(
		"Reroll item",
		"Item to buy from the villager to trigger a plugin trade refresh"
			+ " (e.g. 'minecraft:nether_star').",
		"minecraft:nether_star");
	
	private final SliderSetting maxRerolls = new SliderSetting("Max rerolls",
		"Maximum number of reroll attempts before giving up.", 100, 1, 500, 1,
		ValueDisplay.INTEGER);
	
	private final CheckboxSetting lockInTrade = new CheckboxSetting(
		"Lock in trade",
		"Buys the first trade offer once the wanted book is found, locking in"
			+ " the villager's trades.",
		false);
	
	private final SliderSetting delay = new SliderSetting("Delay",
		"Delay between actions in milliseconds.", 500, 50, 5000, 50,
		ValueDisplay.INTEGER.withSuffix("ms"));
	
	private final SliderSetting range = new SliderSetting("Range",
		"Search radius for villagers.", 5, 1, 10, 0.5, ValueDisplay.DECIMAL);
	
	private final CheckboxSetting muteChatLogs = new CheckboxSetting(
		"Mute chat logs", "Suppresses informational chat messages.", false);
	
	private enum State
	{
		FIND_VILLAGER,
		OPEN_TRADE_SCREEN,
		CHECK_TRADES,
		BUY_REROLL,
		WAIT_FOR_REOPEN,
		DONE
	}
	
	private State state = State.FIND_VILLAGER;
	private VillagerEntity targetVillager;
	private final HashSet<VillagerEntity> experiencedVillagers = new HashSet<>();
	private int rerollCount;
	private long lastActionTime;
	
	public AutoLibrarian2Hack()
	{
		super("AutoLibrarian2");
		setCategory(Category.OTHER);
		addSetting(enchantment);
		addSetting(level);
		addSetting(maxPrice);
		addSetting(rerollItem);
		addSetting(maxRerolls);
		addSetting(lockInTrade);
		addSetting(delay);
		addSetting(range);
		addSetting(muteChatLogs);
	}
	
	@Override
	protected void onEnable()
	{
		EVENTS.add(UpdateListener.class, this);
		state = State.FIND_VILLAGER;
		targetVillager = null;
		rerollCount = 0;
		lastActionTime = 0;
		experiencedVillagers.clear();
	}
	
	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		
		if(MC.currentScreen instanceof MerchantScreen)
			MC.player.closeHandledScreen();
		
		targetVillager = null;
		experiencedVillagers.clear();
	}
	
	@Override
	public void onUpdate()
	{
		if(MC.player == null || MC.world == null)
			return;
		
		switch(state)
		{
			case FIND_VILLAGER -> findVillager();
			case OPEN_TRADE_SCREEN -> openTradeScreen();
			case CHECK_TRADES -> checkTrades();
			case BUY_REROLL -> buyReroll();
			case WAIT_FOR_REOPEN -> waitForReopen();
			case DONE -> setEnabled(false);
		}
	}
	
	private void findVillager()
	{
		double rangeSq = range.getValueSq();
		
		targetVillager = StreamSupport
			.stream(MC.world.getEntities().spliterator(), false)
			.filter(e -> e instanceof VillagerEntity)
			.map(e -> (VillagerEntity)e)
			.filter(e -> !e.isRemoved() && e.getHealth() > 0)
			.filter(e -> MC.player.squaredDistanceTo(e) <= rangeSq)
			.filter(e -> e.getVillagerData().profession()
				.matchesKey(VillagerProfession.LIBRARIAN))
			.filter(e -> e.getVillagerData().level() == 1)
			.filter(e -> !experiencedVillagers.contains(e))
			.min(Comparator.comparingDouble(
				e -> MC.player.squaredDistanceTo(e)))
			.orElse(null);
		
		if(targetVillager == null)
		{
			ChatUtils.error("AutoLibrarian2: No level-1 librarian found"
				+ " within range.");
			setEnabled(false);
			return;
		}
		
		if(!muteChatLogs.isChecked())
			ChatUtils.message("AutoLibrarian2: Found librarian, opening"
				+ " trade screen...");
		
		state = State.OPEN_TRADE_SCREEN;
		lastActionTime = System.currentTimeMillis();
	}
	
	private void openTradeScreen()
	{
		// Already open
		if(MC.currentScreen instanceof MerchantScreen)
		{
			state = State.CHECK_TRADES;
			return;
		}
		
		// Wait a bit between attempts
		if(System.currentTimeMillis() - lastActionTime < delay.getValue())
			return;
		
		if(targetVillager == null || targetVillager.isRemoved())
		{
			state = State.FIND_VILLAGER;
			targetVillager = null;
			return;
		}
		
		if(MC.player.squaredDistanceTo(targetVillager) > range.getValueSq())
		{
			ChatUtils.error(
				"AutoLibrarian2: Villager moved out of range. Retrying...");
			state = State.FIND_VILLAGER;
			targetVillager = null;
			return;
		}
		
		// Build hit result pointing at villager's bounding box centre
		Box box = targetVillager.getBoundingBox();
		Vec3d eyesPos = RotationUtils.getEyesPos();
		Vec3d center = box.getCenter();
		Vec3d hitVec = box.raycast(eyesPos, center).orElse(center);
		EntityHitResult hitResult =
			new EntityHitResult(targetVillager, hitVec);
		
		// Interact with the villager
		MC.interactionManager.interactEntityAtLocation(MC.player, targetVillager,
			hitResult, Hand.MAIN_HAND);
		
		lastActionTime = System.currentTimeMillis();
	}
	
	private void checkTrades()
	{
		if(!(MC.currentScreen instanceof MerchantScreen tradeScreen))
		{
			// Screen closed unexpectedly, re-open
			state = State.OPEN_TRADE_SCREEN;
			lastActionTime = System.currentTimeMillis();
			return;
		}
		
		// Check villager experience — if > 0 we can't retrain it
		int xp = tradeScreen.getScreenHandler().getExperience();
		if(xp > 0)
		{
			ChatUtils.warning("AutoLibrarian2: Villager is already"
				+ " experienced, skipping.");
			experiencedVillagers.add(targetVillager);
			MC.player.closeHandledScreen();
			targetVillager = null;
			state = State.FIND_VILLAGER;
			return;
		}
		
		TradeOfferList offers = tradeScreen.getScreenHandler().getRecipes();
		
		// Check if we have the wanted book
		int bookIndex = findWantedBookIndex(offers);
		if(bookIndex >= 0)
		{
			if(!muteChatLogs.isChecked())
				ChatUtils.message("AutoLibrarian2: Found wanted book after "
					+ rerollCount + " reroll(s)!");
			
			if(lockInTrade.isChecked())
			{
				// Lock in by buying first trade offer
				lockInTrade(tradeScreen, offers);
			}
			
			MC.player.closeHandledScreen();
			state = State.DONE;
			return;
		}
		
		// Check reroll limit
		if(rerollCount >= maxRerolls.getValueI())
		{
			ChatUtils.warning("AutoLibrarian2: Max rerolls (" + maxRerolls.getValueI()
				+ ") reached without finding wanted book.");
			MC.player.closeHandledScreen();
			setEnabled(false);
			return;
		}
		
		// Start buying the reroll item
		state = State.BUY_REROLL;
	}
	
	private void buyReroll()
	{
		if(!(MC.currentScreen instanceof MerchantScreen tradeScreen))
		{
			state = State.OPEN_TRADE_SCREEN;
			lastActionTime = System.currentTimeMillis();
			return;
		}
		
		TradeOfferList offers = tradeScreen.getScreenHandler().getRecipes();
		int rerollIndex = findRerollItemIndex(offers);
		
		if(rerollIndex < 0)
		{
			ChatUtils.error("AutoLibrarian2: Could not find reroll item '"
				+ rerollItem.getValue() + "' in villager's trades.");
			MC.player.closeHandledScreen();
			setEnabled(false);
			return;
		}
		
		TradeOffer offer = offers.get(rerollIndex);
		if(!hasEnoughItems(offer))
		{
			ChatUtils.error("AutoLibrarian2: Not enough items to buy reroll"
				+ " item.");
			MC.player.closeHandledScreen();
			setEnabled(false);
			return;
		}
		
		// Select and execute the reroll trade
		tradeScreen.getScreenHandler().setRecipeIndex(rerollIndex);
		tradeScreen.getScreenHandler().switchTo(rerollIndex);
		MC.getNetworkHandler()
			.sendPacket(new SelectMerchantTradeC2SPacket(rerollIndex));
		MC.interactionManager.clickSlot(tradeScreen.getScreenHandler().syncId,
			2, 0, SlotActionType.PICKUP, MC.player);
		
		rerollCount++;
		
		if(!muteChatLogs.isChecked())
			ChatUtils.message("AutoLibrarian2: Bought reroll item (attempt "
				+ rerollCount + "/" + maxRerolls.getValueI() + ").");
		
		// Close and wait for plugin to reset trades
		MC.player.closeHandledScreen();
		state = State.WAIT_FOR_REOPEN;
		lastActionTime = System.currentTimeMillis();
	}
	
	private void waitForReopen()
	{
		// Wait the configured delay before re-opening to let the plugin reset
		if(System.currentTimeMillis() - lastActionTime < delay.getValue())
			return;
		
		// Re-open the trade screen to check new offers
		state = State.OPEN_TRADE_SCREEN;
		lastActionTime = System.currentTimeMillis();
	}
	
	// ─────────────────── helpers ───────────────────
	
	private int findWantedBookIndex(TradeOfferList offers)
	{
		String wantedEnchant = enchantment.getValue().trim();
		int wantedLevel = level.getValueI();
		int wantedMaxPrice = maxPrice.getValueI();
		
		for(int i = 0; i < offers.size(); i++)
		{
			TradeOffer offer = offers.get(i);
			if(offer.isDisabled())
				continue;
			
			ItemStack stack = offer.getSellItem();
			if(!stack.isOf(Items.ENCHANTED_BOOK))
				continue;
			
			ItemEnchantmentsComponent stored = stack.getOrDefault(
				DataComponentTypes.STORED_ENCHANTMENTS,
				ItemEnchantmentsComponent.DEFAULT);
			
			for(RegistryEntry<?> entry : stored.getEnchantments())
			{
				String key = entry.getKey()
					.map(k -> k.getValue().toString()).orElse("");
				int lvl = stored.getLevel(
					(RegistryEntry<net.minecraft.enchantment.Enchantment>)entry);
				
				if(key.equals(wantedEnchant) && lvl == wantedLevel)
				{
					int price = offer.getOriginalFirstBuyItem().getCount();
					if(price <= wantedMaxPrice)
						return i;
				}
			}
		}
		return -1;
	}
	
	private int findRerollItemIndex(TradeOfferList offers)
	{
		Item rerollItemType = getItemFromString(rerollItem.getValue());
		if(rerollItemType == null)
			return -1;
		
		for(int i = 0; i < offers.size(); i++)
		{
			if(offers.get(i).getSellItem().isOf(rerollItemType))
				return i;
		}
		return -1;
	}
	
	private void lockInTrade(MerchantScreen tradeScreen, TradeOfferList offers)
	{
		if(offers.isEmpty())
			return;
		
		tradeScreen.getScreenHandler().setRecipeIndex(0);
		tradeScreen.getScreenHandler().switchTo(0);
		MC.getNetworkHandler()
			.sendPacket(new SelectMerchantTradeC2SPacket(0));
		MC.interactionManager.clickSlot(tradeScreen.getScreenHandler().syncId,
			2, 0, SlotActionType.PICKUP, MC.player);
		
		if(!muteChatLogs.isChecked())
			ChatUtils.message("AutoLibrarian2: Locked in trade.");
	}
	
	private boolean hasEnoughItems(TradeOffer offer)
	{
		ItemStack first = offer.getOriginalFirstBuyItem();
		if(!first.isEmpty())
		{
			if(InventoryUtils.count(first.getItem()) < first.getCount())
				return false;
		}
		
		var secondOpt = offer.getSecondBuyItem();
		if(secondOpt.isPresent())
		{
			ItemStack second = secondOpt.get().itemStack();
			if(!second.isEmpty()
				&& InventoryUtils.count(second.getItem()) < second.getCount())
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
			return Registries.ITEM.get(id);
		}catch(Exception e)
		{
			return null;
		}
	}
}
