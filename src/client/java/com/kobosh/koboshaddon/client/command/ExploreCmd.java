/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package com.kobosh.koboshaddon.client.command;

import com.kobosh.koboshaddon.client.AddonFeatureRegistry;
import net.wurstclient.command.CmdException;
import net.wurstclient.command.CmdSyntaxError;
import net.wurstclient.command.Command;
import com.kobosh.koboshaddon.client.hack.ExplorationHack;
import net.wurstclient.util.ChatUtils;

public final class ExploreCmd extends Command
{
	public ExploreCmd()
	{
		super("explore", "Control the Exploration hack.", ".explore pause",
			".explore resume", ".explore toggle");
	}
	
	@Override
	public void call(String[] args) throws CmdException
	{
		if(args.length != 1)
			throw new CmdSyntaxError();
		
		ExplorationHack explorationHack = AddonFeatureRegistry.explorationHack;
		
		if(!explorationHack.isEnabled())
		{
			ChatUtils.error("Exploration hack is not enabled.");
			return;
		}
		
		switch(args[0].toLowerCase())
		{
			case "pause":
			if(explorationHack.isPaused())
			{
				ChatUtils.message("Exploration is already paused.");
			}else
			{
				explorationHack.setPaused(true);
				ChatUtils.message("Exploration paused.");
			}
			break;
			
			case "resume":
			if(!explorationHack.isPaused())
			{
				ChatUtils.message("Exploration is not paused.");
			}else
			{
				explorationHack.setPaused(false);
				ChatUtils.message("Exploration resumed.");
			}
			break;
			
			case "toggle":
			if(explorationHack.isPaused())
			{
				explorationHack.setPaused(false);
				ChatUtils.message("Exploration resumed.");
			}else
			{
				explorationHack.setPaused(true);
				ChatUtils.message("Exploration paused.");
			}
			break;
			
			default:
			throw new CmdSyntaxError();
		}
	}
}
