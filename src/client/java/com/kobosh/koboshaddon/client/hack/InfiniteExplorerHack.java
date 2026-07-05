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

@SearchTags({"infinite exploration", "spiral exploration", "auto explore",
	"outward exploration"})
public final class InfiniteExplorerHack extends Hack implements UpdateListener
{
	private final SliderSetting diameter = new SliderSetting("Diameter",
		"Diameter of the circular exploration area.", 2000, 100, 50000, 100, ValueDisplay.INTEGER);
	
	private final SliderSetting exploreHeight = new SliderSetting("Explore Height",
		"Height (Y coordinate) to maintain while exploring.", 200, -64, 320, 1, ValueDisplay.INTEGER);
	
	private final SliderSetting stepSize = new SliderSetting("Step Size",
		"Distance between exploration points.", 50, 1, 500, 1, ValueDisplay.INTEGER);
	
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
		UP(0, -1),
		LEFT(-1, 0),
		DOWN(0, 1);
		
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
		diameterInt = Math.abs(diameter.getValueI());
		exploreHeightInt = exploreHeight.getValueI();
		stepSizeInt = Math.abs(stepSize.getValueI());
		
		if(stepSizeInt == 0)
			stepSizeInt = 1;
		if(diameterInt == 0)
			diameterInt = 200;
		
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
			MC.options.keyUp.setDown(false);
			MC.options.keyDown.setDown(false);
			MC.options.keyLeft.setDown(false);
			MC.options.keyRight.setDown(false);
			MC.options.keyJump.setDown(false);
			MC.options.keyShift.setDown(false);
		}
	}
}
