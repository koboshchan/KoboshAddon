/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package com.kobosh.koboshaddon.client.command;

import com.kobosh.koboshaddon.client.AddonFeatureRegistry;
import com.kobosh.koboshaddon.client.hack.AutoCraftHack;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.wurstclient.command.CmdError;
import net.wurstclient.command.CmdException;
import net.wurstclient.command.CmdSyntaxError;
import net.wurstclient.command.Command;
import net.wurstclient.util.ChatUtils;

public final class AutoCraftCmd extends Command
{
	public AutoCraftCmd()
	{
		super("autocraft", "Manages AutoCraft items.", ".autocraft add <item>",
			".autocraft remove <item>", ".autocraft list", ".autocraft clear");
	}
	
	@Override
	public void call(String[] args) throws CmdException
	{
		if(args.length == 0)
			throw new CmdSyntaxError();
		
		AutoCraftHack autoCraft = AddonFeatureRegistry.autoCraftHack;
		
		switch(args[0].toLowerCase())
		{
			case "add":
			handleAdd(autoCraft, args);
			break;
			
			case "remove":
			case "delete":
			handleRemove(autoCraft, args);
			break;
			
			case "list":
			handleList(autoCraft);
			break;
			
			case "clear":
			handleClear(autoCraft);
			break;
			
			default:
			throw new CmdSyntaxError();
		}
	}
	
	private void handleAdd(AutoCraftHack autoCraft, String[] args)
		throws CmdException
	{
		if(args.length != 2)
			throw new CmdSyntaxError();
		
		String itemName = args[1];
		
		// Validate item exists
		if(!isValidItem(itemName))
			throw new CmdError("Item \"" + itemName + "\" not found.");
		
		autoCraft.addItem(itemName);
		ChatUtils.message("Added " + itemName + " to AutoCraft list.");
	}
	
	private void handleRemove(AutoCraftHack autoCraft, String[] args)
		throws CmdException
	{
		if(args.length != 2)
			throw new CmdSyntaxError();
		
		String itemName = args[1];
		autoCraft.removeItem(itemName);
		ChatUtils.message("Removed " + itemName + " from AutoCraft list.");
	}
	
	private void handleList(AutoCraftHack autoCraft)
	{
		var items = autoCraft.getItems().getItemNames();
		
		if(items.isEmpty())
		{
			ChatUtils.message("AutoCraft list is empty.");
			return;
		}
		
		ChatUtils.message("AutoCraft items (" + items.size() + "):");
		for(String item : items)
			ChatUtils.message("- " + item);
	}
	
	private void handleClear(AutoCraftHack autoCraft)
	{
		// Remove all items by removing from the end backwards
		var itemNames = autoCraft.getItems().getItemNames();
		for(int i = itemNames.size() - 1; i >= 0; i--)
			autoCraft.getItems().remove(i);
		
		ChatUtils.message("Cleared AutoCraft list.");
	}
	
	private boolean isValidItem(String itemName)
	{
		try
		{
			// Try to parse as registry identifier
			Identifier id = Identifier.tryParse(itemName);
			if(id == null)
				return false;
			
			// Check if item exists in registry
			return BuiltInRegistries.ITEM.containsKey(id);
		}catch(Exception e)
		{
			return false;
		}
	}
	
	@Override
	public String getPrimaryAction()
	{
		return "Add Item";
	}
	
	@Override
	public void doPrimaryAction()
	{
		if(MC.player == null)
			return;
		
		ItemStack heldStack = MC.player.getMainHandItem();
		if(heldStack.isEmpty())
		{
			ChatUtils.error("Please hold an item to add it to AutoCraft.");
			return;
		}
		
		Item item = heldStack.getItem();
		String itemName = BuiltInRegistries.ITEM.getKey(item).toString();
		
		AutoCraftHack autoCraft = AddonFeatureRegistry.autoCraftHack;
		autoCraft.addItem(itemName);
		ChatUtils.message("Added " + itemName + " to AutoCraft list.");
	}
}
