/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package com.kobosh.koboshaddon.client.command;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.wurstclient.command.CmdError;
import net.wurstclient.command.CmdException;
import net.wurstclient.command.CmdSyntaxError;
import net.wurstclient.command.Command;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.util.ChatUtils;

public final class FlyToCmd extends Command implements UpdateListener
{
	private BlockPos targetPos;
	private boolean active = false;
	
	public FlyToCmd()
	{
		super("flyto", "Walks directly to the specified coordinates.",
			".flyto <x> <y> <z>", "Use '.flyto stop' to stop movement.");
	}
	
	@Override
	public void call(String[] args) throws CmdException
	{
		if(args.length == 1 && args[0].equalsIgnoreCase("stop"))
		{
			stop();
			return;
		}
		
		if(args.length != 3)
			throw new CmdSyntaxError();
		
		int x, y, z;
		try
		{
			x = Integer.parseInt(args[0]);
			y = Integer.parseInt(args[1]);
			z = Integer.parseInt(args[2]);
		}catch(NumberFormatException e)
		{
			throw new CmdError(
				"Invalid coordinates. Please enter valid integers.");
		}
		
		// Validate Y coordinate bounds
		int minY = MC.world.getBottomY();
		int maxY = minY + MC.world.getHeight() - 1;
		if(y < minY || y > maxY)
		{
			throw new CmdError("Y coordinate must be between "
				+ minY + " and " + maxY + ".");
		}
		
		targetPos = new BlockPos(x, y, z);
		active = true;
		
		EVENTS.add(UpdateListener.class, this);
		
		ChatUtils.message("Flying to [" + x + ", " + y + ", " + z + "]");
		ChatUtils.message("Use '.flyto stop' to stop movement.");
	}
	
	@Override
	public void onUpdate()
	{
		if(!active || targetPos == null)
		{
			stop();
			return;
		}
		
		Vec3d playerPos = new Vec3d(MC.player.getX(), MC.player.getY(), MC.player.getZ());
		Vec3d targetCenter = Vec3d.ofCenter(targetPos);
		
		// Check if we've reached the target (within 2 blocks)
		double distance = playerPos.distanceTo(targetCenter);
		if(distance < 2.0)
		{
			ChatUtils.message("Arrived at [" + targetPos.getX() + ", "
				+ targetPos.getY() + ", " + targetPos.getZ() + "]");
			stop();
			return;
		}
		
		// Calculate direction and movement
		double deltaX = targetCenter.x - playerPos.x;
		double deltaY = targetCenter.y - playerPos.y;
		double deltaZ = targetCenter.z - playerPos.z;
		double horizontalDistance =
			Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);
		
		// Reset all movement keys
		MC.options.forwardKey.setPressed(false);
		MC.options.backKey.setPressed(false);
		MC.options.leftKey.setPressed(false);
		MC.options.rightKey.setPressed(false);
		MC.options.jumpKey.setPressed(false);
		MC.options.sneakKey.setPressed(false);
		
		// Face the target direction
		if(horizontalDistance > 0.5)
		{
			WURST.getRotationFaker().faceVectorClient(targetCenter);
			MC.options.forwardKey.setPressed(true);
		}
		
		// Handle vertical movement
		if(deltaY > 1.5)
		{
			// Target is above, jump/fly up
			MC.options.jumpKey.setPressed(true);
		}else if(deltaY < -1.5)
		{
			// Target is below, descend
			MC.options.sneakKey.setPressed(true);
		}
		
		// Sprint for faster movement
		MC.player.setSprinting(true);
	}
	
	private void stop()
	{
		if(!active)
			return;
		
		active = false;
		targetPos = null;
		
		EVENTS.remove(UpdateListener.class, this);
		
		// Release all movement keys
		MC.options.forwardKey.setPressed(false);
		MC.options.backKey.setPressed(false);
		MC.options.leftKey.setPressed(false);
		MC.options.rightKey.setPressed(false);
		MC.options.jumpKey.setPressed(false);
		MC.options.sneakKey.setPressed(false);
		MC.player.setSprinting(false);
		
		ChatUtils.message("FlyTo stopped.");
	}
	
	public boolean isActive()
	{
		return active;
	}
	
	public BlockPos getTargetPos()
	{
		return targetPos;
	}
}
