/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package com.kobosh.koboshaddon.client.hack;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PotionItem;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
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
		
		MobEffectInstance invisEffect =
			MC.player.getEffect(MobEffects.INVISIBILITY);
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
				MC.player.setYRot(originalYaw);
				MC.player.setXRot(originalPitch);
				playerFrozen = false;
				WURST.getRotationFaker()
					.faceVectorClient(new Vec3(originalYaw, originalPitch, 0));
			}
		}
		
		// Freeze player movement if needed
		if(playerFrozen)
		{
			MC.player.setDeltaMovement(0, MC.player.getDeltaMovement().y, 0);
		}
		
		MobEffectInstance invisEffect =
			MC.player.getEffect(MobEffects.INVISIBILITY);
		
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
		
		return !MC.level.getBlockState(belowPos).isAir() && MC.level
			.getBlockState(belowPos).isSolid();
	}
	
	private void useInvisibilityPotion()
	{
		// Store original rotation and freeze player
		originalYaw = MC.player.getYRot();
		originalPitch = MC.player.getXRot();
		playerFrozen = true;
		
		// Look down all the way (90 degrees)
		MC.player.setYRot(originalYaw); // Keep original yaw
		MC.player.setXRot(90.0f);
		
		// First try to find invisibility potion in hotbar
		int hotbarSlot = findInvisibilityPotionInHotbar();
		
		if(hotbarSlot != -1)
		{
			// Switch to the potion and use it
			MC.player.getInventory().setSelectedSlot(hotbarSlot);
			MC.gameMode.useItem(MC.player, InteractionHand.MAIN_HAND);
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
				MC.player.getInventory().setSelectedSlot(targetHotbarSlot);
				MC.gameMode.useItem(MC.player, InteractionHand.MAIN_HAND);
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
			ItemStack stack = MC.player.getInventory().getItem(i);
			if(isInvisibilityPotion(stack))
				return i;
		}
		return -1;
	}
	
	private int findInvisibilityPotionInInventory()
	{
		for(int i = 9; i < 36; i++) // Main inventory slots
		{
			ItemStack stack = MC.player.getInventory().getItem(i);
			if(isInvisibilityPotion(stack))
				return i;
		}
		return -1;
	}
	
	private int findEmptyHotbarSlot()
	{
		for(int i = 0; i < 9; i++)
		{
			if(MC.player.getInventory().getItem(i).isEmpty())
				return i;
		}
		return -1;
	}
	
	private boolean isInvisibilityPotion(ItemStack stack)
	{
		if(stack.isEmpty() || !(stack.getItem() instanceof PotionItem))
			return false;
		
		PotionContents potionContents = stack.getComponents()
			.get(net.minecraft.core.component.DataComponents.POTION_CONTENTS);
		if(potionContents == null)
			return false;
		
		for(MobEffectInstance effect : potionContents.getAllEffects())
		{
			if(effect.getEffect() == MobEffects.INVISIBILITY)
				return true;
		}
		return false;
	}
	
	private void moveItemToInventory(int hotbarSlot)
	{
		// Find empty inventory slot
		for(int i = 9; i < 36; i++)
		{
			if(MC.player.getInventory().getItem(i).isEmpty())
			{
				// Move item from hotbar to inventory
				MC.gameMode.handleInventoryMouseClick(
					MC.player.containerMenu.containerId, hotbarSlot, 0,
					net.minecraft.world.inventory.ClickType.PICKUP, MC.player);
				MC.gameMode.handleInventoryMouseClick(
					MC.player.containerMenu.containerId, i, 0,
					net.minecraft.world.inventory.ClickType.PICKUP, MC.player);
				break;
			}
		}
	}
	
	private void moveItemToHotbar(int inventorySlot, int hotbarSlot)
	{
		// Move item from inventory to hotbar
		MC.gameMode.handleInventoryMouseClick(MC.player.containerMenu.containerId,
			inventorySlot, 0, net.minecraft.world.inventory.ClickType.PICKUP,
			MC.player);
		MC.gameMode.handleInventoryMouseClick(MC.player.containerMenu.containerId,
			hotbarSlot, 0, net.minecraft.world.inventory.ClickType.PICKUP,
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
			ItemStack stack = MC.player.getInventory().getItem(i);
			if(!stack.isEmpty()
				&& Block.byItem(stack.getItem()) != null
				&& Block.byItem(stack.getItem()) != Blocks.AIR) // Not air
			{
				blockSlot = i;
				break;
			}
		}
		
		if(blockSlot == -1)
			return false;
		
		// Switch to block and place it
		int oldSlot = MC.player.getInventory().getSelectedSlot();
		MC.player.getInventory().setSelectedSlot(blockSlot);
		
		// Create air place hit result
		Vec3 hitVec = Vec3.atCenterOf(pos);
		BlockHitResult hitResult =
			new BlockHitResult(hitVec, Direction.UP, pos, false);
		
		// Place the block
		InteractionSimulator.rightClickBlock(hitResult);
		
		// Restore original slot
		MC.player.getInventory().setSelectedSlot(oldSlot);
		
		return true;
	}
}
