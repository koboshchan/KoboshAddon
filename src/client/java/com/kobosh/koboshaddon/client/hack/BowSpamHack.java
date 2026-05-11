/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package com.kobosh.koboshaddon.client.hack;

import net.minecraft.item.BowItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;

@SearchTags({"bow spam", "fast bow", "rapid bow", "bow auto fire"})
public final class BowSpamHack extends Hack implements UpdateListener
{
	private final SliderSetting minChargeTicks = new SliderSetting(
		"Min charge ticks",
		"How many ticks to charge before releasing. Lower = faster, weaker shots.",
		1, 1, 20, 1, ValueDisplay.INTEGER);

	private int reuseDelay;

	public BowSpamHack()
	{
		super("BowSpam");
		setCategory(Category.COMBAT);
		addSetting(minChargeTicks);
	}

	@Override
	protected void onEnable()
	{
		reuseDelay = 0;
		EVENTS.add(UpdateListener.class, this);
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		reuseDelay = 0;
		if(MC.player != null && MC.player.isUsingItem())
			MC.player.stopUsingItem();
	}

	@Override
	public void onUpdate()
	{
		if(MC.player == null || MC.interactionManager == null)
			return;
		if(MC.currentScreen != null)
			return;

		Hand hand = getBowHand();
		if(hand == null)
		{
			reuseDelay = 0;
			return;
		}

		if(reuseDelay > 0)
			reuseDelay--;

		if(MC.player.isUsingItem())
		{
			if(MC.player.getActiveHand() != hand)
				return;

			if(MC.player.getItemUseTime() >= minChargeTicks.getValueI())
			{
				// Release using-player state directly to avoid getting stuck in draw.
				MC.player.stopUsingItem();
				reuseDelay = 1;
			}
			return;
		}

		if(reuseDelay > 0)
			return;

		MC.interactionManager.interactItem(MC.player, hand);
	}

	private Hand getBowHand()
	{
		ItemStack mainHand = MC.player.getMainHandStack();
		if(mainHand.getItem() instanceof BowItem)
			return Hand.MAIN_HAND;

		ItemStack offHand = MC.player.getOffHandStack();
		if(offHand.getItem() instanceof BowItem)
			return Hand.OFF_HAND;

		return null;
	}
}