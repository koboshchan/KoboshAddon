/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package com.kobosh.koboshaddon.client.hack;

import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.RightClickListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;

@SearchTags({"use", "fast use", "throw copy"})
public final class UseHack extends Hack implements RightClickListener
{
	private final SliderSetting amount = new SliderSetting("Amount",
		"Amount of uses per click.", 16, 2, 64, 1, ValueDisplay.INTEGER);
	
	public UseHack()
	{
		super("Use");
		
		setCategory(Category.OTHER);
		addSetting(amount);
	}
	
	@Override
	public String getRenderName()
	{
		return getName() + " [" + amount.getValueString() + "]";
	}
	
	@Override
	protected void onEnable()
	{
		EVENTS.add(RightClickListener.class, this);
	}
	
	@Override
	protected void onDisable()
	{
		EVENTS.remove(RightClickListener.class, this);
	}
	
	@Override
	public void onRightClick(RightClickEvent event)
	{
		if(MC.missTime > 0)
			return;
		
		if(!MC.options.keyUse.isDown())
			return;
		
		for(int i = 0; i < amount.getValueI(); i++)
		{
			if(MC.hitResult.getType() == BlockHitResult.Type.BLOCK)
			{
				BlockHitResult hitResult = (BlockHitResult)MC.hitResult;
				IMC.getInteractionManager().rightClickBlock(
					hitResult.getBlockPos(), hitResult.getDirection(),
					hitResult.getLocation());
			}
			
			IMC.getInteractionManager().rightClickItem();
		}
	}
}
