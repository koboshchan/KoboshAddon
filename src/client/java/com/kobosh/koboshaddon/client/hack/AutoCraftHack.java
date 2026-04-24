/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package com.kobosh.koboshaddon.client.hack;

import java.util.List;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.RecipeDisplayEntry;
import net.minecraft.recipe.display.SlotDisplayContexts;
import net.minecraft.registry.Registries;
import net.minecraft.screen.CraftingScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Identifier;
import net.minecraft.util.context.ContextParameterMap;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.ItemListSetting;
import net.wurstclient.util.ChatUtils;
@SearchTags({"auto craft", "auto crafting", "crafting"})
public final class AutoCraftHack extends Hack implements UpdateListener
{
	private final ItemListSetting items = new ItemListSetting("Items",
		"Items that AutoCraft should craft automatically.", "minecraft:stick",
		"minecraft:torch", "minecraft:arrow");
	
	private final CheckboxSetting antiDesync = new CheckboxSetting(
		"Anti desync", "Tries to prevent inventory desynchronization.", false);
	
	private final CheckboxSetting craftAll = new CheckboxSetting("Craft all",
		"Crafts the maximum possible amount per craft (shift-clicking).",
		false);
	
	private final CheckboxSetting drop = new CheckboxSetting("Drop",
		"Automatically drops crafted items (useful when inventory is full).",
		false);
	
	private final CheckboxSetting debugMode = new CheckboxSetting("Debug mode",
		"Shows debug messages about crafting attempts.", false);

	private int craftCooldownTicks;
	
	public AutoCraftHack()
	{
		super("AutoCraft");
		setCategory(Category.ITEMS);
		
		addSetting(items);
		addSetting(antiDesync);
		addSetting(craftAll);
		addSetting(drop);
		addSetting(debugMode);
	}
	
	@Override
	public String getRenderName()
	{
		return getName() + " [" + items.getItemNames().size() + "]";
	}
	
	@Override
	protected void onEnable()
	{
		EVENTS.add(UpdateListener.class, this);
		craftCooldownTicks = 0;
		
		if(items.getItemNames().isEmpty())
		{
			ChatUtils.warning(
				"AutoCraft item list is empty! Add some items to craft.");
			return;
		}
		
		if(debugMode.isChecked())
			ChatUtils.message(
				"AutoCraft enabled. Monitoring for crafting opportunities...");
	}
	
	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		craftCooldownTicks = 0;
		
		if(debugMode.isChecked())
			ChatUtils.message("AutoCraft disabled.");
	}
	
	@Override
	public void onUpdate()
	{
		// Basic safety checks
		if(MC.player == null || MC.interactionManager == null
			|| MC.world == null)
			return;
		
		if(items.getItemNames().isEmpty())
			return;
		
		// Check if we're in a crafting screen
		if(!(MC.player.currentScreenHandler instanceof CraftingScreenHandler))
			return;
		
		// Anti-desync: update inventory
		if(antiDesync.isChecked())
			MC.player.getInventory().updateItems();
		
		attemptCrafting();
	}
	
	private void attemptCrafting()
	{
		if(craftCooldownTicks > 0)
		{
			craftCooldownTicks--;
			return;
		}

		CraftingScreenHandler craftingHandler =
			(CraftingScreenHandler)MC.player.currentScreenHandler;
		List<String> itemNames = items.getItemNames();
		ContextParameterMap slotContext =
			SlotDisplayContexts.createParameters(MC.world);

		for(var collection : MC.player.getRecipeBook().getOrderedResults())
		{
			List<RecipeDisplayEntry> craftableRecipes = collection.filter(
				net.minecraft.client.gui.screen.recipebook.RecipeResultCollection.RecipeFilterMode.CRAFTABLE);

			for(RecipeDisplayEntry recipe : craftableRecipes)
			{
				List<ItemStack> resultStacks =
					recipe.display().result().getStacks(slotContext);

				for(ItemStack resultStack : resultStacks)
				{
					if(resultStack.isEmpty())
						continue;

					String itemName =
						Registries.ITEM.getId(resultStack.getItem()).toString();
					if(!itemNames.contains(itemName))
						continue;

					MC.interactionManager.clickRecipe(craftingHandler.syncId,
						recipe.id(), craftAll.isChecked());

					SlotActionType actionType = drop.isChecked()
						? SlotActionType.THROW
						: SlotActionType.QUICK_MOVE;
					int button = drop.isChecked() ? 1 : 0;

					MC.interactionManager.clickSlot(craftingHandler.syncId, 0,
						button, actionType, MC.player);

					if(debugMode.isChecked())
						ChatUtils.message("AutoCraft crafted "
							+ resultStack.getCount() + "x " + itemName);

					craftCooldownTicks = craftAll.isChecked() ? 1 : 4;
					return;
				}
			}
		}
	}
	
	public boolean isValidCraftingItem(Item item)
	{
		String itemName = Registries.ITEM.getId(item).toString();
		return items.getItemNames().contains(itemName);
	}
	
	public void addItem(String itemName)
	{
		// Convert string to Item
		Identifier itemId = Identifier.tryParse(itemName);
		if(itemId == null)
		{
			if(debugMode.isChecked())
				ChatUtils.error("Invalid item name: " + itemName);
			return;
		}
		
		Item item = Registries.ITEM.get(itemId);
		if(item == null)
		{
			if(debugMode.isChecked())
				ChatUtils.error("Item not found: " + itemName);
			return;
		}
		
		items.add(item);
		if(debugMode.isChecked())
			ChatUtils.message("Added " + itemName + " to AutoCraft list.");
	}
	
	public void removeItem(String itemName)
	{
		List<String> itemNames = items.getItemNames();
		int index = itemNames.indexOf(itemName);
		if(index >= 0)
		{
			items.remove(index);
			if(debugMode.isChecked())
				ChatUtils
					.message("Removed " + itemName + " from AutoCraft list.");
		}
	}
	
	public ItemListSetting getItems()
	{
		return items;
	}
}
