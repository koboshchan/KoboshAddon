/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package com.kobosh.koboshaddon.client.hack;

import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;

@SearchTags({"air walk", "airwalk", "air walk hack"})
public final class AirWalkHack extends Hack implements UpdateListener
{
	private final SliderSetting yHeight = new SliderSetting("Y-height",
		"The Y-level you walk on.", 64, -64, 320, 1, ValueDisplay.INTEGER);
	
	public AirWalkHack()
	{
		super("AirWalk");
		setCategory(Category.MOVEMENT);
		addSetting(yHeight);
	}
	
	@Override
	protected void onEnable()
	{
		EVENTS.add(UpdateListener.class, this);
	}
	
	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
	}
	
	@Override
	public void onUpdate()
	{
		if(MC.player == null)
			return;
		
		double targetY = yHeight.getValue();
		
		// If player is falling through the target Y, stop them
		if(MC.player.getY() <= targetY && MC.player.getDeltaMovement().y < 0)
		{
			MC.player.setDeltaMovement(MC.player.getDeltaMovement().x, 0,
				MC.player.getDeltaMovement().z);
			MC.player.setPos(MC.player.getX(), targetY, MC.player.getZ());
			MC.player.setOnGround(true);
		}
	}
}
