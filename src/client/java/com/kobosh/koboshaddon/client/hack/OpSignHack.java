/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package com.kobosh.koboshaddon.client.hack;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.hack.Hack;
import net.wurstclient.util.ChatUtils;

@SearchTags({"op sign"})
public final class OpSignHack extends Hack
{
	public OpSignHack()
	{
		super("OpSign");
		
		setCategory(Category.ITEMS);
	}
	
	@Override
	protected void onEnable()
	{
		if(!MC.player.getAbilities().instabuild)
		{
			ChatUtils.error("Creative mode only.");
			setEnabled(false);
			return;
		}
		
		if(!MC.player.getInventory().getItem(36).isEmpty())
		{
			ChatUtils.error("Please clear your shoes slot.");
			setEnabled(false);
			return;
		}
		
		// generate item
		ItemStack stack = new ItemStack(Blocks.OAK_SIGN);
		CompoundTag nbtCompound = new CompoundTag();
		stack.set(DataComponents.CUSTOM_DATA, CustomData.of(nbtCompound));
		stack.set(DataComponents.CUSTOM_NAME, Component.literal("Copy Me"));
		
		// give item
		MC.player.getInventory().setItem(36, stack);
		ChatUtils.message("Item has been placed in your shoes slot.");
		setEnabled(false);
	}
}
