/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package com.kobosh.koboshaddon.client.hack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.util.BlockUtils;
import net.wurstclient.util.ChatUtils;

@SearchTags({"ItemTp", "item tp", "item teleport", "AutoPickup", "auto pickup"})
public final class ItemTpHack extends Hack implements UpdateListener
{
	private final Random random = new Random();
	
	private final SliderSetting range =
		new SliderSetting("Range", 4.25, 1, 6, 0.05, ValueDisplay.DECIMAL);
	
	private final SliderSetting cooldown = new SliderSetting("Cooldown",
		"Cooldown between teleports in ticks (20 ticks = 1 second).", 20, 1,
		100, 1, ValueDisplay.INTEGER);
	
	private final CheckboxSetting directTeleport =
		new CheckboxSetting("Direct teleport",
			"Teleports directly to item instead of near it.", true);
	
	private final CheckboxSetting blockCenter =
		new CheckboxSetting("Block center",
			"Teleports to block center to avoid clipping into walls.", true);
	
	private final CheckboxSetting autoDisable =
		new CheckboxSetting("Auto disable",
			"Automatically disables when no items are in range.", false);
	
	private final CheckboxSetting silent = new CheckboxSetting("Silent",
		"Don't send chat messages when teleporting to items.", true);
	
	private final CheckboxSetting checkLOS =
		new CheckboxSetting("Check line of sight",
			"Ensures that you don't teleport to items through blocks.\n\n"
				+ "Slower but can help with anti-cheat plugins.",
			false);
	
	private final CheckboxSetting checkPushback =
		new CheckboxSetting("Check pushback",
			"Detects when server resets position due to wall blocking.\n"
				+ "Automatically switches to next closest item when blocked.",
			true);
	
	private long lastTeleportTime = 0;
	private Vec3 positionBeforeTp = null;
	private Vec3 expectedPosition = null;
	private long teleportTimestamp = 0;
	private boolean waitingForPushbackCheck = false;
	private List<Entity> excludedItems = new ArrayList<>();
	
	public ItemTpHack()
	{
		super("ItemTP");
		setCategory(Category.MOVEMENT);
		
		addSetting(range);
		addSetting(cooldown);
		addSetting(directTeleport);
		addSetting(blockCenter);
		addSetting(autoDisable);
		addSetting(silent);
		addSetting(checkLOS);
		addSetting(checkPushback);
	}
	
	@Override
	protected void onEnable()
	{
		lastTeleportTime = 0;
		positionBeforeTp = null;
		expectedPosition = null;
		teleportTimestamp = 0;
		waitingForPushbackCheck = false;
		excludedItems.clear();
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
		LocalPlayer player = MC.player;
		if(player == null || MC.level == null)
			return;
		
		long currentTime = System.currentTimeMillis();
		
		// Check for pushback if we're waiting for it
		if(waitingForPushbackCheck && checkPushback.isChecked())
		{
			// Wait at least 2 ticks (100ms) for server to respond
			if(currentTime - teleportTimestamp >= 100)
			{
				Vec3 currentPos = new Vec3(player.getX(), player.getY(), player.getZ());
				
				// Check if we got pushed back (within 0.5 blocks of original
				// position)
				if(positionBeforeTp != null && expectedPosition != null)
				{
					double distanceFromOriginal =
						currentPos.distanceTo(positionBeforeTp);
					double distanceFromExpected =
						currentPos.distanceTo(expectedPosition);
					
					// If we're much closer to original position than expected,
					// we got pushed back
					if(distanceFromOriginal < 0.5 && distanceFromExpected > 1.0)
					{
						if(!silent.isChecked())
							ChatUtils.message(
								"Pushback detected, trying next item...");
							
						// Add the current target to excluded list and try next
						// item
						Entity lastTarget = findClosestItem(player, false);
						if(lastTarget != null)
							excludedItems.add(lastTarget);
							
						// Clear pushback check state and continue to find next
						// item
						waitingForPushbackCheck = false;
						positionBeforeTp = null;
						expectedPosition = null;
						
						// Don't return, let it find next item immediately
					}else
					{
						// Teleport was successful, clear state
						waitingForPushbackCheck = false;
						positionBeforeTp = null;
						expectedPosition = null;
						excludedItems.clear(); // Reset excluded items on
												// successful teleport
						return;
					}
				}else
				{
					// Clear state if we don't have proper tracking data
					waitingForPushbackCheck = false;
					positionBeforeTp = null;
					expectedPosition = null;
				}
			}else
			{
				// Still waiting for pushback check
				return;
			}
		}
		
		// Check cooldown
		long cooldownMs = (long)(cooldown.getValueI() * 50); // Convert ticks to
																// ms
		if(currentTime - lastTeleportTime < cooldownMs)
			return;
		
		// Find closest item entity (excluding ones that caused pushback)
		Entity closestItem = findClosestItem(player, true);
		
		if(closestItem == null)
		{
			// If no items available, try clearing excluded items and search
			// again
			if(!excludedItems.isEmpty())
			{
				excludedItems.clear();
				closestItem = findClosestItem(player, true);
			}
			
			if(closestItem == null)
			{
				if(autoDisable.isChecked())
				{
					ChatUtils.message("No items in range. Disabling ItemTP.");
					setEnabled(false);
				}
				return;
			}
		}
		
		// Check line of sight if enabled
		if(checkLOS.isChecked())
		{
			Vec3 itemCenter = closestItem.getBoundingBox().getCenter();
			if(!BlockUtils.hasLineOfSight(itemCenter))
				return; // Skip items that aren't visible
		}
		
		// Calculate teleport position
		double targetX, targetY, targetZ;
		
		if(blockCenter.isChecked())
		{
			// Use block center coordinates to avoid clipping
			BlockPos itemBlockPos = BlockPos.containing(closestItem.getX(),
				closestItem.getY(), closestItem.getZ());
			targetX = itemBlockPos.getX() + 0.5;
			targetY = itemBlockPos.getY();
			targetZ = itemBlockPos.getZ() + 0.5;
		}else
		{
			// Use exact item coordinates
			targetX = closestItem.getX();
			targetY = closestItem.getY();
			targetZ = closestItem.getZ();
		}
		
		// Record position before teleport for pushback detection
		if(checkPushback.isChecked())
		{
			positionBeforeTp = new Vec3(player.getX(), player.getY(), player.getZ());
			expectedPosition = new Vec3(targetX, targetY, targetZ);
			teleportTimestamp = currentTime;
			waitingForPushbackCheck = true;
		}
		
		// Teleport to item
		if(directTeleport.isChecked())
		{
			// Teleport directly to calculated position
			player.setPos(targetX, targetY, targetZ);
		}else
		{
			// Use TpAura-style random teleport near calculated position
			double finalX = targetX + random.nextInt(3) * 2 - 2;
			double finalZ = targetZ + random.nextInt(3) * 2 - 2;
			player.setPos(finalX, targetY, finalZ);
			
			// Update expected position for pushback detection
			if(checkPushback.isChecked())
				expectedPosition = new Vec3(finalX, targetY, finalZ);
		}
		
		lastTeleportTime = currentTime;
		
		// Send chat message if not in silent mode
		if(!silent.isChecked())
		{
			ItemEntity itemEntity = (ItemEntity)closestItem;
			ChatUtils.message(
				"Teleported to " + itemEntity.getItem().getHoverName().getString()
					+ " (" + itemEntity.getItem().getCount() + ")");
		}
	}
	
	private Entity findClosestItem(LocalPlayer player,
                                        boolean excludeBlocked)
	{
		double rangeSq = Math.pow(range.getValue(), 2);
		Stream<Entity> stream = StreamSupport
			.stream(MC.level.entitiesForRendering().spliterator(), false)
			.filter(e -> e instanceof ItemEntity).filter(e -> !e.isRemoved())
			.filter(e -> player.distanceToSqr(e) <= rangeSq);
		
		// Exclude items that caused pushback if requested
		if(excludeBlocked && !excludedItems.isEmpty())
			stream = stream.filter(e -> !excludedItems.contains(e));
		
		return stream.min(Comparator.comparingDouble(player::distanceToSqr))
			.orElse(null);
	}
	
	@Override
	public String getRenderName()
	{
		long currentTime = System.currentTimeMillis();
		long cooldownMs = (long)(cooldown.getValueI() * 50);
		long timeLeft = cooldownMs - (currentTime - lastTeleportTime);
		
		if(timeLeft > 0)
		{
			return getName() + " [" + (timeLeft / 1000.0) + "s]";
		}
		
		return getName();
	}
}
