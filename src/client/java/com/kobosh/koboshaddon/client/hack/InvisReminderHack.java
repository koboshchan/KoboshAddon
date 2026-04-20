/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package com.kobosh.koboshaddon.client.hack;

import net.minecraft.block.Block;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.item.PotionItem;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.registry.Registries;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.util.ChatUtils;
import net.wurstclient.util.InteractionSimulator;

@SearchTags({"invis reminder", "invisibility reminder", "auto invis",
	"emergency refill"})
public final class InvisReminderHack extends Hack implements UpdateListener
{
	private final SliderSetting reminderTime =
		new SliderSetting("Reminder Time",
			"Time in seconds to show reminder before invisibility runs out", 30,
			5, 120, 5, ValueDisplay.INTEGER);
	
	private final CheckboxSetting emergencyRefill =
		new CheckboxSetting("Emergency Refill",
			"Automatically refill invisibility when running low", true);
	
	private final SliderSetting refillTime = new SliderSetting("Refill Time",
		"Time in seconds to start emergency refill process", 10, 2, 30, 1,
		ValueDisplay.INTEGER);
	
	// State tracking
	private boolean reminderSent = false;
	private boolean refillInProgress = false;
	private int solidBlockCheckTicks = 0;
	private boolean isOnSolidBlock = false;
	private int emergencyTicks = 0;
	private boolean placedEmergencyBlock = false;
	
	// Potion throwing state
	private boolean playerFrozen = false;
	private float originalYaw = 0;
	private float originalPitch = 0;
	private int rotationRestoreTicks = 0;
	
	public InvisReminderHack()
	{
		super("InvisReminder");
		setCategory(Category.ITEMS);
		addSetting(reminderTime);
		addSetting(emergencyRefill);
		addSetting(refillTime);
	}
	
	@Override
	public String getRenderName()
	{
		String name = getName();
		
		StatusEffectInstance invisEffect =
			MC.player.getStatusEffect(StatusEffects.INVISIBILITY);
		if(invisEffect != null)
		{
			int ticksLeft = invisEffect.getDuration();
			int secondsLeft = ticksLeft / 20;
			name += " [" + secondsLeft + "s]";
			
			if(refillInProgress)
				name += " (Refilling)";
		}
		
		return name;
	}
	
	@Override
	protected void onEnable()
	{
		EVENTS.add(UpdateListener.class, this);
		reminderSent = false;
		refillInProgress = false;
		solidBlockCheckTicks = 0;
		emergencyTicks = 0;
		placedEmergencyBlock = false;
		
		// Reset rotation state
		playerFrozen = false;
		rotationRestoreTicks = 0;
	}
	
	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
	}
	
	@Override
	public void onUpdate()
	{
		// Handle rotation restoration
		if(rotationRestoreTicks > 0)
		{
			rotationRestoreTicks--;
			if(rotationRestoreTicks == 0)
			{
				// Restore original rotation and unfreeze player
				MC.player.setYaw(originalYaw);
				MC.player.setPitch(originalPitch);
				playerFrozen = false;
				WURST.getRotationFaker()
					.faceVectorClient(new Vec3d(originalYaw, originalPitch, 0));
			}
		}
		
		// Freeze player movement if needed
		if(playerFrozen)
		{
			MC.player.setVelocity(0, MC.player.getVelocity().y, 0);
		}
		
		StatusEffectInstance invisEffect =
			MC.player.getStatusEffect(StatusEffects.INVISIBILITY);
		
		// Reset state if no invisibility effect
		if(invisEffect == null)
		{
			reminderSent = false;
			refillInProgress = false;
			solidBlockCheckTicks = 0;
			emergencyTicks = 0;
			placedEmergencyBlock = false;
			
			// Also reset rotation state
			if(playerFrozen)
			{
				playerFrozen = false;
				rotationRestoreTicks = 0;
			}
			return;
		}
		
		int ticksLeft = invisEffect.getDuration();
		int secondsLeft = ticksLeft / 20;
		int reminderThreshold = (int)reminderTime.getValue();
		int refillThreshold = (int)refillTime.getValue();
		
		// Send reminder
		if(!reminderSent && secondsLeft <= reminderThreshold)
		{
			ChatUtils.message(
				"Invisibility running out in " + secondsLeft + " seconds!");
			reminderSent = true;
		}
		
		// Emergency refill logic
		if(emergencyRefill.isChecked() && secondsLeft <= refillThreshold)
		{
			if(!refillInProgress)
			{
				refillInProgress = true;
				solidBlockCheckTicks = 0;
				emergencyTicks = 0;
				placedEmergencyBlock = false;
				ChatUtils.message("Starting emergency invisibility refill...");
			}
			
			handleEmergencyRefill(ticksLeft);
		}
		
		// Reset reminder when effect is refreshed
		if(secondsLeft > reminderThreshold && reminderSent)
		{
			reminderSent = false;
		}
	}
	
	private void handleEmergencyRefill(int ticksLeft)
	{
		// Check if standing on solid block for first 8 seconds (160 ticks)
		if(solidBlockCheckTicks < 160)
		{
			isOnSolidBlock = isStandingOnSolidBlock();
			solidBlockCheckTicks++;
			
			if(isOnSolidBlock)
			{
				// Use invisibility potion if available
				useInvisibilityPotion();
				refillInProgress = false; // Success, stop the process
				return;
			}
		}
		
		// Last 2 seconds (40 ticks) - emergency measures
		if(ticksLeft <= 40)
		{
			emergencyTicks++;
			
			if(!placedEmergencyBlock && !isStandingOnSolidBlock())
			{
				// Air place block under player
				BlockPos belowPos = new BlockPos((int)MC.player.getX(),
					(int)(MC.player.getY() - 1), (int)MC.player.getZ());
				
				if(airPlaceBlock(belowPos))
				{
					placedEmergencyBlock = true;
					ChatUtils.message("Emergency block placed!");
				}
			}
			
			// Try to use potion on the emergency block
			if(placedEmergencyBlock || isStandingOnSolidBlock())
			{
				useInvisibilityPotion();
				refillInProgress = false;
			}
		}
	}
	
	private boolean isStandingOnSolidBlock()
	{
		BlockPos belowPos = new BlockPos((int)MC.player.getX(),
			(int)(MC.player.getY() - 0.1), (int)MC.player.getZ());
		
		return !MC.world.getBlockState(belowPos).isAir() && MC.world
			.getBlockState(belowPos).isSolidBlock(MC.world, belowPos);
	}
	
	private void useInvisibilityPotion()
	{
		// Store original rotation and freeze player
		originalYaw = MC.player.getYaw();
		originalPitch = MC.player.getPitch();
		playerFrozen = true;
		
		// Look down all the way (90 degrees)
		MC.player.setYaw(originalYaw); // Keep original yaw
		MC.player.setPitch(90.0f);
		
		// First try to find invisibility potion in hotbar
		int hotbarSlot = findInvisibilityPotionInHotbar();
		
		if(hotbarSlot != -1)
		{
			// Switch to the potion and use it
			MC.player.getInventory().selectedSlot = hotbarSlot;
			MC.interactionManager.interactItem(MC.player, Hand.MAIN_HAND);
			ChatUtils.message("Used invisibility potion from hotbar slot "
				+ (hotbarSlot + 1));
			
			// Try to refill the slot from inventory
			refillHotbarFromInventory(hotbarSlot);
		}else
		{
			// No potion in hotbar, try to get one from inventory
			int inventorySlot = findInvisibilityPotionInInventory();
			
			if(inventorySlot != -1)
			{
				// Find empty hotbar slot or use slot 9
				int targetHotbarSlot = findEmptyHotbarSlot();
				
				if(targetHotbarSlot == -1)
				{
					// Hotbar full, move slot 9 to inventory
					targetHotbarSlot = 8; // Slot 9 (0-indexed)
					moveItemToInventory(targetHotbarSlot);
				}
				
				// Move potion to hotbar and use it
				moveItemToHotbar(inventorySlot, targetHotbarSlot);
				MC.player.getInventory().selectedSlot = targetHotbarSlot;
				MC.interactionManager.interactItem(MC.player, Hand.MAIN_HAND);
				ChatUtils.message(
					"Moved and used invisibility potion from inventory");
			}else
			{
				ChatUtils.error("No invisibility potions found!");
				// Reset if no potion found
				playerFrozen = false;
				return;
			}
		}
		
		// Schedule rotation restoration after 5 ticks
		rotationRestoreTicks = 5;
	}
	
	private int findInvisibilityPotionInHotbar()
	{
		for(int i = 0; i < 9; i++)
		{
			ItemStack stack = MC.player.getInventory().getStack(i);
			if(isInvisibilityPotion(stack))
				return i;
		}
		return -1;
	}
	
	private int findInvisibilityPotionInInventory()
	{
		for(int i = 9; i < 36; i++) // Main inventory slots
		{
			ItemStack stack = MC.player.getInventory().getStack(i);
			if(isInvisibilityPotion(stack))
				return i;
		}
		return -1;
	}
	
	private int findEmptyHotbarSlot()
	{
		for(int i = 0; i < 9; i++)
		{
			if(MC.player.getInventory().getStack(i).isEmpty())
				return i;
		}
		return -1;
	}
	
	private boolean isInvisibilityPotion(ItemStack stack)
	{
		if(stack.isEmpty() || !(stack.getItem() instanceof PotionItem))
			return false;
		
		PotionContentsComponent potionContents = stack.getComponents()
			.get(net.minecraft.component.DataComponentTypes.POTION_CONTENTS);
		if(potionContents == null)
			return false;
		
		for(StatusEffectInstance effect : potionContents.getEffects())
		{
			if(effect.getEffectType() == StatusEffects.INVISIBILITY)
				return true;
		}
		return false;
	}
	
	private void moveItemToInventory(int hotbarSlot)
	{
		// Find empty inventory slot
		for(int i = 9; i < 36; i++)
		{
			if(MC.player.getInventory().getStack(i).isEmpty())
			{
				// Move item from hotbar to inventory
				MC.interactionManager.clickSlot(
					MC.player.currentScreenHandler.syncId, hotbarSlot, 0,
					net.minecraft.screen.slot.SlotActionType.PICKUP, MC.player);
				MC.interactionManager.clickSlot(
					MC.player.currentScreenHandler.syncId, i, 0,
					net.minecraft.screen.slot.SlotActionType.PICKUP, MC.player);
				break;
			}
		}
	}
	
	private void moveItemToHotbar(int inventorySlot, int hotbarSlot)
	{
		// Move item from inventory to hotbar
		MC.interactionManager.clickSlot(MC.player.currentScreenHandler.syncId,
			inventorySlot, 0, net.minecraft.screen.slot.SlotActionType.PICKUP,
			MC.player);
		MC.interactionManager.clickSlot(MC.player.currentScreenHandler.syncId,
			hotbarSlot, 0, net.minecraft.screen.slot.SlotActionType.PICKUP,
			MC.player);
	}
	
	private void refillHotbarFromInventory(int hotbarSlot)
	{
		// Look for another invisibility potion in inventory
		int inventorySlot = findInvisibilityPotionInInventory();
		
		if(inventorySlot != -1)
		{
			moveItemToHotbar(inventorySlot, hotbarSlot);
			ChatUtils.message("Refilled invisibility potion in hotbar");
		}
	}
	
	private boolean airPlaceBlock(BlockPos pos)
	{
		// Find a solid block in hotbar
		int blockSlot = -1;
		for(int i = 0; i < 9; i++)
		{
			ItemStack stack = MC.player.getInventory().getStack(i);
			if(!stack.isEmpty()
				&& Block.getBlockFromItem(stack.getItem()) != null
				&& Block.getBlockFromItem(stack.getItem()) != Registries.BLOCK
					.get(0)) // Not air
			{
				blockSlot = i;
				break;
			}
		}
		
		if(blockSlot == -1)
			return false;
		
		// Switch to block and place it
		int oldSlot = MC.player.getInventory().selectedSlot;
		MC.player.getInventory().selectedSlot = blockSlot;
		
		// Create air place hit result
		Vec3d hitVec = Vec3d.ofCenter(pos);
		BlockHitResult hitResult =
			new BlockHitResult(hitVec, Direction.UP, pos, false);
		
		// Place the block
		InteractionSimulator.rightClickBlock(hitResult);
		
		// Restore original slot
		MC.player.getInventory().selectedSlot = oldSlot;
		
		return true;
	}
}
