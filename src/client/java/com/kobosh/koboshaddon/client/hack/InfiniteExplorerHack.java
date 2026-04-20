/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package com.kobosh.koboshaddon.client.hack;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.settings.TextFieldSetting;
import net.wurstclient.util.ChatUtils;

@SearchTags({"infinite exploration", "spiral exploration", "auto explore",
	"outward exploration"})
public final class InfiniteExplorerHack extends Hack implements UpdateListener
{
	private final TextFieldSetting diameter = new TextFieldSetting("Diameter",
		"Diameter of the circular exploration area", "2000");
	
	private final TextFieldSetting exploreHeight =
		new TextFieldSetting("Explore Height",
			"Height (Y coordinate) to maintain while exploring", "200");
	
	private final TextFieldSetting stepSize = new TextFieldSetting("Step Size",
		"Distance between exploration points", "50");
	
	private final SliderSetting speed =
		new SliderSetting("Speed", "Speed of movement towards target", 1.0, 0.1,
			5.0, 0.1, ValueDisplay.DECIMAL);
	
	// Spiral movement state
	private BlockPos currentTarget;
	private BlockPos centerPos;
	private int spiralSteps;
	private int currentStep;
	private Direction currentDirection;
	private int directionSteps;
	private int currentDirectionStep;
	private boolean isExploring = false;
	private boolean isPaused = false;
	private int diameterInt, exploreHeightInt, stepSizeInt;
	
	// Spiral movement directions (right, down, left, up)
	private enum Direction
	{
		RIGHT(1, 0),
		DOWN(0, 1),
		LEFT(-1, 0),
		UP(0, -1);
		
		private final int deltaX;
		private final int deltaZ;
		
		Direction(int deltaX, int deltaZ)
		{
			this.deltaX = deltaX;
			this.deltaZ = deltaZ;
		}
		
		public Direction next()
		{
			Direction[] values = Direction.values();
			return values[(this.ordinal() + 1) % values.length];
		}
	}
	
	public InfiniteExplorerHack()
	{
		super("InfiniteExplorer");
		setCategory(Category.MOVEMENT);
		
		addSetting(diameter);
		addSetting(exploreHeight);
		addSetting(stepSize);
		addSetting(speed);
	}
	
	@Override
	public String getRenderName()
	{
		String name = "InfiniteExplorer";
		
		if(isExploring && currentTarget != null)
		{
			// Calculate progress as distance from center
			int currentX = currentTarget.getX() - centerPos.getX();
			int currentZ = currentTarget.getZ() - centerPos.getZ();
			double distanceFromCenter =
				Math.sqrt(currentX * currentX + currentZ * currentZ);
			double maxRadius = diameterInt / 2.0;
			double progress =
				maxRadius > 0 ? distanceFromCenter / maxRadius : 0;
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
			diameterInt = Math.abs(Integer.parseInt(diameter.getValue()));
			exploreHeightInt = Integer.parseInt(exploreHeight.getValue());
			stepSizeInt = Math.abs(Integer.parseInt(stepSize.getValue()));
			
			if(stepSizeInt == 0)
				stepSizeInt = 1;
			if(diameterInt == 0)
				diameterInt = 200;
			
		}catch(NumberFormatException e)
		{
			ChatUtils
				.error("Invalid values. Please enter valid positive integers.");
			setEnabled(false);
			return;
		}
		
		// Initialize spiral exploration from current position
		centerPos = new BlockPos((int)MC.player.getX(), exploreHeightInt,
			(int)MC.player.getZ());
		currentTarget = centerPos;
		
		// Initialize spiral state
		spiralSteps = 1;
		currentStep = 0;
		currentDirection = Direction.RIGHT;
		directionSteps = 1;
		currentDirectionStep = 0;
		isExploring = true;
		
		EVENTS.add(UpdateListener.class, this);
		ChatUtils.message("Starting infinite spiral exploration from center ["
			+ centerPos.getX() + ", " + centerPos.getZ() + "]");
		ChatUtils.message("Exploration diameter: " + diameterInt
			+ " blocks, step size: " + stepSizeInt);
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
		MC.options.forwardKey.setPressed(false);
		MC.options.backKey.setPressed(false);
		MC.options.leftKey.setPressed(false);
		MC.options.rightKey.setPressed(false);
		MC.options.jumpKey.setPressed(false);
		MC.options.sneakKey.setPressed(false);
	}
	
	@Override
	public void onUpdate()
	{
		if(!isExploring || currentTarget == null)
		{
			setEnabled(false);
			return;
		}
		
		// Check if exploration has reached the boundary
		if(hasReachedBoundary())
		{
			ChatUtils.message(
				"Reached exploration boundary! Exploration area completed.");
			setEnabled(false);
			return;
		}
		
		// If paused, stop all movement but keep the hack enabled
		if(isPaused)
		{
			// Reset all movement keys when paused
			MC.options.forwardKey.setPressed(false);
			MC.options.backKey.setPressed(false);
			MC.options.leftKey.setPressed(false);
			MC.options.rightKey.setPressed(false);
			MC.options.jumpKey.setPressed(false);
			MC.options.sneakKey.setPressed(false);
			return;
		}
		
		// Move towards current target
		moveTowards(currentTarget);
		
		// Check if we've reached the current target
		if(hasReachedTarget(currentTarget))
		{
			// Calculate next target using spiral pattern
			BlockPos nextTarget = calculateNextSpiralTarget();
			if(nextTarget != null)
			{
				currentTarget = nextTarget;
				ChatUtils.message("Moving to next target: ["
					+ currentTarget.getX() + ", " + currentTarget.getY() + ", "
					+ currentTarget.getZ() + "]");
			}else
			{
				ChatUtils.message("Spiral exploration completed!");
				setEnabled(false);
			}
		}
	}
	
	private void moveTowards(BlockPos target)
	{
		Vec3d playerPos = new Vec3d(MC.player.getX(), MC.player.getY(), MC.player.getZ());
		Vec3d targetPos = Vec3d.ofCenter(target);
		
		// Calculate horizontal distance and direction
		double deltaX = targetPos.x - playerPos.x;
		double deltaZ = targetPos.z - playerPos.z;
		double deltaY = targetPos.y - playerPos.y;
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
			WURST.getRotationFaker().faceVectorClient(targetPos);
			MC.options.forwardKey.setPressed(true);
		}
		
		// Handle vertical movement
		if(deltaY > 1.0)
		{
			MC.options.jumpKey.setPressed(true);
		}else if(deltaY < -1.0)
		{
			MC.options.sneakKey.setPressed(true);
		}
	}
	
	private boolean hasReachedTarget(BlockPos target)
	{
		Vec3d playerPos = new Vec3d(MC.player.getX(), MC.player.getY(), MC.player.getZ());
		Vec3d targetPos = Vec3d.ofCenter(target);
		
		double deltaX = targetPos.x - playerPos.x;
		double deltaZ = targetPos.z - playerPos.z;
		double horizontalDistance =
			Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);
		
		return horizontalDistance < 3.0; // Within 3 blocks of target
	}
	
	private BlockPos calculateNextSpiralTarget()
	{
		// If we're at the center, move right first
		if(currentTarget.equals(centerPos))
		{
			return new BlockPos(centerPos.getX() + stepSizeInt,
				exploreHeightInt, centerPos.getZ());
		}
		
		// Move in the current direction
		currentDirectionStep++;
		
		int nextX =
			currentTarget.getX() + (currentDirection.deltaX * stepSizeInt);
		int nextZ =
			currentTarget.getZ() + (currentDirection.deltaZ * stepSizeInt);
		
		// Check if we need to turn (completed steps in current direction)
		if(currentDirectionStep >= directionSteps)
		{
			// Turn to next direction
			currentDirection = currentDirection.next();
			currentDirectionStep = 0;
			
			// After moving right or left, increase the number of steps for the
			// next pair of directions
			if(currentDirection == Direction.DOWN
				|| currentDirection == Direction.UP)
			{
				directionSteps++;
			}
		}
		
		return new BlockPos(nextX, exploreHeightInt, nextZ);
	}
	
	private boolean hasReachedBoundary()
	{
		if(currentTarget == null)
			return false;
		
		int deltaX = currentTarget.getX() - centerPos.getX();
		int deltaZ = currentTarget.getZ() - centerPos.getZ();
		double distanceFromCenter =
			Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);
		double radius = diameterInt / 2.0;
		
		return distanceFromCenter > radius;
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
			MC.options.forwardKey.setPressed(false);
			MC.options.backKey.setPressed(false);
			MC.options.leftKey.setPressed(false);
			MC.options.rightKey.setPressed(false);
			MC.options.jumpKey.setPressed(false);
			MC.options.sneakKey.setPressed(false);
		}
	}
}
