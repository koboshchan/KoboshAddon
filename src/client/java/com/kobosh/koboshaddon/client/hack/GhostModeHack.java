/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package com.kobosh.koboshaddon.client.hack;

import net.minecraft.client.gui.screen.DeathScreen;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.util.ChatUtils;

@SearchTags({"ghost mode", "death bypass", "keep playing after death"})
public final class GhostModeHack extends Hack implements UpdateListener
{
	private final CheckboxSetting fullFood = new CheckboxSetting("Full food",
		"Keeps client-side food bar full while in ghost mode.", true);

	private boolean active;

	public GhostModeHack()
	{
		super("GhostMode");
		setCategory(Category.OTHER);
		addSetting(fullFood);
	}

	@Override
	protected void onEnable()
	{
		active = false;
		EVENTS.add(UpdateListener.class, this);
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		active = false;
		ChatUtils.message("Ghost mode disabled.");
		if(MC.player != null)
			MC.player.requestRespawn();
	}

	@Override
	public void onUpdate()
	{
		if(MC.player == null)
			return;

		if(MC.currentScreen instanceof DeathScreen)
		{
			MC.setScreen(null);
			if(!active)
			{
				active = true;
				ChatUtils.message("Ghost mode enabled.");
			}
		}

		if(!active)
			return;

		if(MC.player.getHealth() < 1.0F)
			MC.player.setHealth(20.0F);

		if(fullFood.isChecked())
			MC.player.getHungerManager().setFoodLevel(20);
	}
}
