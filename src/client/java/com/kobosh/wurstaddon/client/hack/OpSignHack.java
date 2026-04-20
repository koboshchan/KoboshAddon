/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package com.kobosh.wurstaddon.client.hack;

import net.minecraft.block.Blocks;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.Text;
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
		if(!MC.player.getAbilities().creativeMode)
		{
			ChatUtils.error("Creative mode only.");
			setEnabled(false);
			return;
		}
		
		if(!MC.player.getInventory().getStack(36).isEmpty())
		{
			ChatUtils.error("Please clear your shoes slot.");
			setEnabled(false);
			return;
		}
		
		// generate item
		ItemStack stack = new ItemStack(Blocks.OAK_SIGN);
		NbtCompound nbtCompound = new NbtCompound();
		stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbtCompound));
		stack.set(DataComponentTypes.CUSTOM_NAME, Text.literal("Copy Me"));
		
		// give item
		MC.player.getInventory().setStack(36, stack);
		ChatUtils.message("Item has been placed in your shoes slot.");
		setEnabled(false);
	}
}
