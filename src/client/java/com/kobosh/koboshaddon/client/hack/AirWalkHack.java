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
import net.wurstclient.settings.TextFieldSetting;

@SearchTags({"air walk", "airwalk", "air walk hack"})
public final class AirWalkHack extends Hack implements UpdateListener
{
	private final TextFieldSetting yHeight =
		new TextFieldSetting("Y-height", "The Y-level you walk on.", "64");
	
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
		
		double targetY = 64;
		try
		{
			targetY = Double.parseDouble(yHeight.getValue());
		}catch(NumberFormatException e)
		{}
		
		// If player is falling through the target Y, stop them
		if(MC.player.getY() <= targetY && MC.player.getVelocity().y < 0)
		{
			MC.player.setVelocity(MC.player.getVelocity().x, 0,
				MC.player.getVelocity().z);
			MC.player.setPosition(MC.player.getX(), targetY, MC.player.getZ());
			MC.player.setOnGround(true);
		}
	}
}
