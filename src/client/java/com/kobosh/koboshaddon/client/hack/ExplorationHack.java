/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package com.kobosh.koboshaddon.client.hack;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.settings.TextFieldSetting;
import net.wurstclient.util.ChatUtils;

@SearchTags({"exploration", "auto explore", "grid exploration",
	"coordinate exploration"})
public final class ExplorationHack extends Hack implements UpdateListener
{
	private final TextFieldSetting startX = new TextFieldSetting("Start X",
		"Starting X coordinate for exploration", "1000");
	
	private final TextFieldSetting startZ = new TextFieldSetting("Start Z",
		"Starting Z coordinate for exploration", "1000");
	
	private final TextFieldSetting endX = new TextFieldSetting("End X",
		"Ending X coordinate for exploration", "-1000");
	
	private final TextFieldSetting endZ = new TextFieldSetting("End Z",
		"Ending Z coordinate for exploration", "-1000");
	
	private final SliderSetting exploreHeight = new SliderSetting("Explore Height",
		"Height (Y coordinate) to maintain while exploring.", 200, -64, 320, 1, ValueDisplay.INTEGER);
	
	private final SliderSetting pathWidth = new SliderSetting("Path Width",
		"Width of each exploration path.", 100, 1, 1000, 1, ValueDisplay.INTEGER);
	
	private final SliderSetting speed =
		new SliderSetting("Speed", "Speed of movement towards target", 1.0, 0.1,
			5.0, 0.1, ValueDisplay.DECIMAL);
	
	// State variables
	private BlockPos currentTarget;
	private boolean isMovingRight = true; // Direction of horizontal movement
	private int currentZ;
	private boolean isExploring = false;
	private boolean isPaused = false;
	private int startXInt, startZInt, endXInt, endZInt, exploreHeightInt,
		pathWidthInt;
	
	public ExplorationHack()
	{
		super("Exploration");
		setCategory(Category.MOVEMENT);
		
		addSetting(startX);
		addSetting(startZ);
		addSetting(endX);
		addSetting(endZ);
		addSetting(exploreHeight);
		addSetting(pathWidth);
		addSetting(speed);
	}
	
	@Override
	public String getRenderName()
	{
		String name = "Exploration";
		
		if(isExploring && currentTarget != null)
		{
			// Calculate progress percentage
			double totalZDistance = Math.abs(endZInt - startZInt);
			double currentZDistance = Math.abs(currentZ - startZInt);
			double progress =
				totalZDistance > 0 ? currentZDistance / totalZDistance : 0;
			int percentage = (int)(progress * 100);
			
			if(isPaused)
			{
				name += " " + percentage + "%, paused";
			}else
			{
				name += " [" + currentTarget.getX() + ", "
					+ currentTarget.getZ() + "] " + percentage + "%";
			}
		}
		
		return name;
	}
	
	@Override
	protected void onEnable()
	{
		// Parse settings
		try
		{
			startXInt = Integer.parseInt(startX.getValue());
			startZInt = Integer.parseInt(startZ.getValue());
			endXInt = Integer.parseInt(endX.getValue());
			endZInt = Integer.parseInt(endZ.getValue());
		}catch(NumberFormatException e)
		{
			ChatUtils.error(
				"Invalid coordinate values. Please enter valid integers.");
			setEnabled(false);
			return;
		}
		
		exploreHeightInt = exploreHeight.getValueI();
		pathWidthInt = pathWidth.getValueI();
		if(pathWidthInt == 0)
			pathWidthInt = 1;
		
		// Validate coordinates
		if(startXInt == endXInt || startZInt == endZInt)
		{
			ChatUtils.error("Start and end coordinates must be different.");
			setEnabled(false);
			return;
		}
		
		// Initialize exploration
		currentZ = startZInt;
		isMovingRight = endXInt > startXInt;
		currentTarget = new BlockPos(startXInt, exploreHeightInt, currentZ);
		isExploring = true;
		
		EVENTS.add(UpdateListener.class, this);
		ChatUtils.message("Starting exploration from [" + startXInt + ", "
			+ startZInt + "] to [" + endXInt + ", " + endZInt + "]");
		ChatUtils.message("First target: [" + currentTarget.getX() + ", "
			+ currentTarget.getY() + ", " + currentTarget.getZ() + "]");
	}
	
	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		isExploring = false;
		currentTarget = null;
		
		// Release movement keys
		MC.options.keyUp.setDown(false);
		MC.options.keyDown.setDown(false);
		MC.options.keyLeft.setDown(false);
		MC.options.keyRight.setDown(false);
		MC.options.keyJump.setDown(false);
		MC.options.keyShift.setDown(false);
	}
	
	@Override
	public void onUpdate()
	{
		if(!isExploring || currentTarget == null)
		{
			setEnabled(false);
			return;
		}
		
		// Check if exploration is complete
		if(hasReachedEnd())
		{
			ChatUtils.message("Exploration completed!");
			setEnabled(false);
			return;
		}
		
		// If paused, stop all movement but keep the hack enabled
		if(isPaused)
		{
			// Reset all movement keys when paused
			MC.options.keyUp.setDown(false);
			MC.options.keyDown.setDown(false);
			MC.options.keyLeft.setDown(false);
			MC.options.keyRight.setDown(false);
			MC.options.keyJump.setDown(false);
			MC.options.keyShift.setDown(false);
			return;
		}
		
		// Move towards current target
		moveTowards(currentTarget);
		
		// Check if we've reached the current target
		if(hasReachedTarget(currentTarget))
		{
			// Calculate next target
			BlockPos nextTarget = calculateNextTarget();
			if(nextTarget != null)
			{
				currentTarget = nextTarget;
				ChatUtils.message("Moving to next target: ["
					+ currentTarget.getX() + ", " + currentTarget.getY() + ", "
					+ currentTarget.getZ() + "]");
			}else
			{
				ChatUtils.message("Exploration completed!");
				setEnabled(false);
			}
		}
	}
	
	private void moveTowards(BlockPos target)
	{
		Vec3 playerPos = new Vec3(MC.player.getX(), MC.player.getY(), MC.player.getZ());
		Vec3 targetPos = Vec3.atCenterOf(target);
		
		// Calculate horizontal distance and direction
		double deltaX = targetPos.x - playerPos.x;
		double deltaZ = targetPos.z - playerPos.z;
		double deltaY = targetPos.y - playerPos.y;
		double horizontalDistance =
			Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);
		
		// Reset all movement keys
		MC.options.keyUp.setDown(false);
		MC.options.keyDown.setDown(false);
		MC.options.keyLeft.setDown(false);
		MC.options.keyRight.setDown(false);
		MC.options.keyJump.setDown(false);
		MC.options.keyShift.setDown(false);
		
		// Face the target direction
		if(horizontalDistance > 0.5)
		{
			WURST.getRotationFaker().faceVectorClient(targetPos);
			MC.options.keyUp.setDown(true);
		}
		
		// Handle vertical movement
		if(deltaY > 1.0)
		{
			MC.options.keyJump.setDown(true);
		}else if(deltaY < -1.0)
		{
			MC.options.keyShift.setDown(true);
		}
	}
	
	private boolean hasReachedTarget(BlockPos target)
	{
		Vec3 playerPos = new Vec3(MC.player.getX(), MC.player.getY(), MC.player.getZ());
		Vec3 targetPos = Vec3.atCenterOf(target);
		
		double deltaX = targetPos.x - playerPos.x;
		double deltaZ = targetPos.z - playerPos.z;
		double horizontalDistance =
			Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);
		
		return horizontalDistance < 3.0; // Within 3 blocks of target
	}
	
	private BlockPos calculateNextTarget()
	{
		int currentTargetX = currentTarget.getX();
		int currentTargetZ = currentTarget.getZ();
		
		// Check if we're at the start position and need to go to end of first
		// line
		if(currentTargetX == startXInt && currentTargetZ == startZInt
			&& isMovingRight)
		{
			// Move to the end of the first line
			return new BlockPos(endXInt, exploreHeightInt, currentZ);
		}
		
		// If we're at the end of a horizontal line
		if((isMovingRight && currentTargetX == endXInt)
			|| (!isMovingRight && currentTargetX == startXInt))
		{
			// Move to next Z line
			if(endZInt > startZInt)
			{
				currentZ += pathWidthInt;
				if(currentZ > endZInt)
					return null; // Exploration complete
			}else
			{
				currentZ -= pathWidthInt;
				if(currentZ < endZInt)
					return null; // Exploration complete
			}
			
			// Switch direction for next line
			isMovingRight = !isMovingRight;
			
			// Set X coordinate for new line
			int nextX = isMovingRight ? startXInt : endXInt;
			return new BlockPos(nextX, exploreHeightInt, currentZ);
		}else
		{
			// Continue in current direction to the end of the line
			int nextX = isMovingRight ? endXInt : startXInt;
			return new BlockPos(nextX, exploreHeightInt, currentZ);
		}
	}
	
	private boolean hasReachedEnd()
	{
		if(endZInt > startZInt)
			return currentZ > endZInt;
		else
			return currentZ < endZInt;
	}
	
	public boolean isPaused()
	{
		return isPaused;
	}
	
	public void setPaused(boolean paused)
	{
		this.isPaused = paused;
		
		// Stop all movement when pausing
		if(paused)
		{
			MC.options.keyUp.setDown(false);
			MC.options.keyDown.setDown(false);
			MC.options.keyLeft.setDown(false);
			MC.options.keyRight.setDown(false);
			MC.options.keyJump.setDown(false);
			MC.options.keyShift.setDown(false);
		}
	}
}
