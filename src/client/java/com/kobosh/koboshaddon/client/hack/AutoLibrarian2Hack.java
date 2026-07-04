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

import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.protocol.game.ServerboundSelectTradePacket;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.Holder;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.InteractionHand;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.settings.TextFieldSetting;
import net.wurstclient.hacks.autolibrarian.BookOffer;
import net.wurstclient.settings.BookOffersSetting;
import net.wurstclient.util.ChatUtils;
import net.wurstclient.util.InventoryUtils;
import net.wurstclient.util.RotationUtils;

@SearchTags({"auto librarian 2", "librarian plugin", "refresh trades",
	"villager reroll", "librarian trainer"})
public final class AutoLibrarian2Hack extends Hack implements UpdateListener
{
	private final BookOffersSetting wantedBooks = new BookOffersSetting(
		"Wanted books",
		"A list of enchanted books that you want your librarians to sell.\n\n"
			+ "AutoLibrarian2 will stop training the current villager"
			+ " once it has learned to sell one of these books.\n\n"
			+ "You can also set a maximum price for each book.",
		"minecraft:depth_strider;3", "minecraft:efficiency;5",
		"minecraft:feather_falling;4", "minecraft:fortune;3",
		"minecraft:looting;3", "minecraft:mending;1", "minecraft:protection;4",
		"minecraft:respiration;3", "minecraft:sharpness;5",
		"minecraft:silk_touch;1", "minecraft:unbreaking;3");
	
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
		"Delay between actions in milliseconds.", 50, 1, 100, 1,
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
	private Villager targetVillager;
	private final HashSet<Villager> experiencedVillagers = new HashSet<>();
	private int rerollCount;
	private long lastActionTime;
	
	public AutoLibrarian2Hack()
	{
		super("AutoLibrarian2");
		setCategory(Category.OTHER);
		addSetting(wantedBooks);
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
		
		if(MC.screen instanceof MerchantScreen)
			MC.player.closeContainer();
		
		targetVillager = null;
		experiencedVillagers.clear();
	}
	
	@Override
	public void onUpdate()
	{
		if(MC.player == null || MC.level == null)
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
			.stream(MC.level.entitiesForRendering().spliterator(), false)
			.filter(e -> e instanceof Villager)
			.map(e -> (Villager)e)
			.filter(e -> !e.isRemoved() && e.getHealth() > 0)
			.filter(e -> MC.player.distanceToSqr(e) <= rangeSq)
			.filter(e -> e.getVillagerData().profession()
				.is(VillagerProfession.LIBRARIAN))
			.filter(e -> e.getVillagerData().level() == 1)
			.filter(e -> !experiencedVillagers.contains(e))
			.min(Comparator.comparingDouble(
				e -> MC.player.distanceToSqr(e)))
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
		if(MC.screen instanceof MerchantScreen)
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
		
		if(MC.player.distanceToSqr(targetVillager) > range.getValueSq())
		{
			ChatUtils.error(
				"AutoLibrarian2: Villager moved out of range. Retrying...");
			state = State.FIND_VILLAGER;
			targetVillager = null;
			return;
		}
		
		// Build hit result pointing at villager's bounding box centre
		AABB box = targetVillager.getBoundingBox();
		Vec3 eyesPos = RotationUtils.getEyesPos();
		Vec3 center = box.getCenter();
		Vec3 hitVec = box.clip(eyesPos, center).orElse(center);
		EntityHitResult hitResult =
			new EntityHitResult(targetVillager, hitVec);
		
		// Interact with the villager
		MC.gameMode.interactAt(MC.player, targetVillager,
			hitResult, InteractionHand.MAIN_HAND);
		
		lastActionTime = System.currentTimeMillis();
	}
	
	private void checkTrades()
	{
		if(!(MC.screen instanceof MerchantScreen tradeScreen))
		{
			// Screen closed unexpectedly, re-open
			state = State.OPEN_TRADE_SCREEN;
			lastActionTime = System.currentTimeMillis();
			return;
		}
		
		// Check villager experience — if > 0 we can't retrain it
		int xp = tradeScreen.getMenu().getTraderXp();
		if(xp > 0)
		{
			ChatUtils.warning("AutoLibrarian2: Villager is already"
				+ " experienced, skipping.");
			experiencedVillagers.add(targetVillager);
			MC.player.closeContainer();
			targetVillager = null;
			state = State.FIND_VILLAGER;
			return;
		}
		
		MerchantOffers offers = tradeScreen.getMenu().getOffers();
		
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
			
			MC.player.closeContainer();
			state = State.DONE;
			return;
		}
		
		// Check reroll limit
		if(rerollCount >= maxRerolls.getValueI())
		{
			ChatUtils.warning("AutoLibrarian2: Max rerolls (" + maxRerolls.getValueI()
				+ ") reached without finding wanted book.");
			MC.player.closeContainer();
			setEnabled(false);
			return;
		}
		
		// Start buying the reroll item
		state = State.BUY_REROLL;
	}
	
	private void buyReroll()
	{
		if(!(MC.screen instanceof MerchantScreen tradeScreen))
		{
			state = State.OPEN_TRADE_SCREEN;
			lastActionTime = System.currentTimeMillis();
			return;
		}
		
		MerchantOffers offers = tradeScreen.getMenu().getOffers();
		int rerollIndex = findRerollItemIndex(offers);
		
		if(rerollIndex < 0)
		{
			ChatUtils.error("AutoLibrarian2: Could not find reroll item '"
				+ rerollItem.getValue() + "' in villager's trades.");
			MC.player.closeContainer();
			setEnabled(false);
			return;
		}
		
		MerchantOffer offer = offers.get(rerollIndex);
		if(!hasEnoughItems(offer))
		{
			ChatUtils.error("AutoLibrarian2: Not enough items to buy reroll"
				+ " item.");
			MC.player.closeContainer();
			setEnabled(false);
			return;
		}
		
		// Select and execute the reroll trade
		tradeScreen.getMenu().setSelectionHint(rerollIndex);
		tradeScreen.getMenu().tryMoveItems(rerollIndex);
		MC.getConnection()
			.send(new ServerboundSelectTradePacket(rerollIndex));
		MC.gameMode.handleInventoryMouseClick(tradeScreen.getMenu().containerId,
			2, 0, ClickType.PICKUP, MC.player);
		
		rerollCount++;
		
		if(!muteChatLogs.isChecked())
			ChatUtils.message("AutoLibrarian2: Bought reroll item (attempt "
				+ rerollCount + "/" + maxRerolls.getValueI() + ").");
		
		// Close and wait for plugin to reset trades
		MC.player.closeContainer();
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
	
	private int findWantedBookIndex(MerchantOffers offers)
	{
		for(int i = 0; i < offers.size(); i++)
		{
			MerchantOffer offer = offers.get(i);
			if(offer.isOutOfStock())
				continue;
			
			ItemStack stack = offer.getResult();
			if(!stack.is(Items.ENCHANTED_BOOK))
				continue;
			
			ItemEnchantments stored = stack.getOrDefault(
				DataComponents.STORED_ENCHANTMENTS,
				ItemEnchantments.EMPTY);
			
			for(Holder<?> entry : stored.keySet())
			{
				String key = entry.unwrapKey()
					.map(k -> k.identifier().toString()).orElse("");
				int lvl = stored.getLevel(
					(Holder<net.minecraft.world.item.enchantment.Enchantment>)entry);
				int price = offer.getBaseCostA().getCount();
				
				BookOffer bookOffer = new BookOffer(key, lvl, price);
				if(bookOffer.isFullyValid() && wantedBooks.isWanted(bookOffer))
					return i;
			}
		}
		return -1;
	}
	
	private int findRerollItemIndex(MerchantOffers offers)
	{
		Item rerollItemType = getItemFromString(rerollItem.getValue());
		if(rerollItemType == null)
			return -1;
		
		for(int i = 0; i < offers.size(); i++)
		{
			if(offers.get(i).getResult().is(rerollItemType))
				return i;
		}
		return -1;
	}
	
	private void lockInTrade(MerchantScreen tradeScreen, MerchantOffers offers)
	{
		if(offers.isEmpty())
			return;
		
		tradeScreen.getMenu().setSelectionHint(0);
		tradeScreen.getMenu().tryMoveItems(0);
		MC.getConnection()
			.send(new ServerboundSelectTradePacket(0));
		MC.gameMode.handleInventoryMouseClick(tradeScreen.getMenu().containerId,
			2, 0, ClickType.PICKUP, MC.player);
		
		if(!muteChatLogs.isChecked())
			ChatUtils.message("AutoLibrarian2: Locked in trade.");
	}
	
	private boolean hasEnoughItems(MerchantOffer offer)
	{
		ItemStack first = offer.getBaseCostA();
		if(!first.isEmpty())
		{
			if(InventoryUtils.count(first.getItem()) < first.getCount())
				return false;
		}
		
		var secondOpt = offer.getItemCostB();
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
			return BuiltInRegistries.ITEM.get(id).map(Holder::value).orElse(null);
		}catch(Exception e)
		{
			return null;
		}
	}
}
