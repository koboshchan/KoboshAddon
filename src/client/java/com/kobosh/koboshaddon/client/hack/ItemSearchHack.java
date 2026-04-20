/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package com.kobosh.koboshaddon.client.hack;

import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.entity.HopperBlockEntity;
import net.minecraft.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.screen.slot.Slot;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.FaceTargetSetting;
import net.wurstclient.settings.FaceTargetSetting.FaceTarget;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.settings.SwingHandSetting;
import net.wurstclient.settings.SwingHandSetting.SwingHand;
import net.wurstclient.settings.TextFieldSetting;
import net.wurstclient.util.ChatUtils;
import net.wurstclient.util.chunk.ChunkUtils;

@SearchTags({"item search", "chest search", "chest finder", "item finder",
	"auto search"})
public final class ItemSearchHack extends Hack implements UpdateListener
{
	private final TextFieldSetting targetItem = new TextFieldSetting(
		"Target item",
		"The item to search for in chests (e.g., 'minecraft:diamond', 'minecraft:enchanted_book')",
		"minecraft:diamond");
	
	private final SliderSetting range = new SliderSetting("Range",
		"Range to search for chests", 5, 1, 10, 0.5, ValueDisplay.DECIMAL);
	
	private final SliderSetting delay =
		new SliderSetting("Delay", "Delay between chest interactions (ms)", 200,
			0, 1000, 50, ValueDisplay.INTEGER.withSuffix("ms"));
	
	private final CheckboxSetting searchShulkers = new CheckboxSetting(
		"Search shulker boxes", "Also search inside shulker boxes", true);
	
	private final CheckboxSetting searchHoppers = new CheckboxSetting(
		"Search hoppers", "Also search inside hoppers", true);
	
	private final CheckboxSetting searchBarrels = new CheckboxSetting(
		"Search barrels", "Also search inside barrels", true);
	
	private final CheckboxSetting requireExactMatch =
		new CheckboxSetting("Require exact match",
			"Only find items with exact ID match (ignores enchantments, etc.)",
			false);
	
	private final FaceTargetSetting faceTarget =
		FaceTargetSetting.withoutPacketSpam(this, FaceTarget.SERVER);
	
	private final SwingHandSetting swingHand =
		new SwingHandSetting(this, SwingHand.SERVER);
	
	private final Set<BlockPos> searchedChests = new HashSet<>();
	private BlockPos currentTarget;
	private long lastInteractionTime;
	private boolean foundItem = false;
	private boolean searchingInProgress = false;
	
	public ItemSearchHack()
	{
		super("ItemSearch");
		setCategory(Category.ITEMS);
		
		addSetting(targetItem);
		addSetting(range);
		addSetting(delay);
		addSetting(searchShulkers);
		addSetting(searchHoppers);
		addSetting(searchBarrels);
		addSetting(requireExactMatch);
		addSetting(faceTarget);
		addSetting(swingHand);
	}
	
	@Override
	protected void onEnable()
	{
		EVENTS.add(UpdateListener.class, this);
		
		// Reset search history when re-enabled
		searchedChests.clear();
		currentTarget = null;
		foundItem = false;
		searchingInProgress = false;
		
		ChatUtils.message(
			"ItemSearch enabled. Searching for: " + targetItem.getValue());
	}
	
	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		
		// Only close screen if item was NOT found (so we keep chest open when
		// item is found)
		if(!foundItem && MC.currentScreen instanceof HandledScreen)
			MC.player.closeHandledScreen();
		
		currentTarget = null;
		searchingInProgress = false;
		
		if(foundItem)
			ChatUtils.message(
				"ItemSearch disabled. Item was found! Chest kept open.");
		else
			ChatUtils.message("ItemSearch disabled.");
	}
	
	@Override
	public void onUpdate()
	{
		// If we're currently viewing a chest screen, check its contents
		if(MC.currentScreen instanceof HandledScreen<?> screen)
		{
			handleOpenChest(screen);
			return;
		}
		
		// Reset interaction state when not in chest screen
		searchingInProgress = false;
		
		// Check delay before next interaction
		if(System.currentTimeMillis() - lastInteractionTime < delay.getValueI())
			return;
		
		// Find next chest to search
		if(currentTarget == null)
		{
			findNextChest();
			if(currentTarget == null)
			{
				ChatUtils.warning("No more chests to search. Item '"
					+ targetItem.getValue() + "' not found.");
				setEnabled(false);
				return;
			}
		}
		
		// Try to interact with the current target chest
		interactWithChest();
	}
	
	private void handleOpenChest(HandledScreen<?> screen)
	{
		if(!searchingInProgress || foundItem)
			return;
		
		// Get the target item we're looking for
		Item targetItemType = getItemFromString(targetItem.getValue());
		if(targetItemType == null)
		{
			ChatUtils.error("Invalid target item: " + targetItem.getValue());
			setEnabled(false);
			return;
		}
		
		// Check all slots in the chest for our target item
		boolean itemFound = false;
		for(Slot slot : screen.getScreenHandler().slots)
		{
			ItemStack stack = slot.getStack();
			if(stack.isEmpty())
				continue;
			
			boolean matches;
			if(requireExactMatch.isChecked())
				matches = stack.getItem() == targetItemType;
			else
				matches = stack.isOf(targetItemType);
			
			if(matches)
			{
				itemFound = true;
				break;
			}
		}
		
		if(itemFound)
		{
			foundItem = true;
			ChatUtils.message("Found " + targetItem.getValue() + " at "
				+ currentTarget.toShortString() + "!");
			ChatUtils.message("Keeping chest open and disabling ItemSearch.");
			setEnabled(false);
		}else
		{
			// Item not found, close chest and mark as searched
			if(currentTarget != null)
				searchedChests.add(currentTarget);
			
			MC.player.closeHandledScreen();
			currentTarget = null;
			lastInteractionTime = System.currentTimeMillis();
		}
	}
	
	private void findNextChest()
	{
		ClientPlayerEntity player = MC.player;
		Vec3d playerPos = player.getPos();
		double rangeSq = range.getValueSq();
		
		Stream<BlockPos> stream = ChunkUtils.getLoadedBlockEntities()
			.filter(this::isValidContainer).map(BlockEntity::getPos)
			.filter(pos -> !searchedChests.contains(pos))
			.filter(pos -> playerPos
				.squaredDistanceTo(Vec3d.ofCenter(pos)) <= rangeSq);
		
		currentTarget = stream
			.min(Comparator.comparingDouble(
				pos -> playerPos.squaredDistanceTo(Vec3d.ofCenter(pos))))
			.orElse(null);
		
		if(currentTarget != null)
		{
			ChatUtils.message("Next target: " + getChestTypeAt(currentTarget)
				+ " at " + currentTarget.toShortString());
		}
	}
	
	private boolean isValidContainer(BlockEntity be)
	{
		if(be instanceof ChestBlockEntity)
			return true;
		if(be instanceof ShulkerBoxBlockEntity && searchShulkers.isChecked())
			return true;
		if(be instanceof HopperBlockEntity && searchHoppers.isChecked())
			return true;
		
		// Check for barrels if enabled
		if(searchBarrels.isChecked())
		{
			Block block = be.getCachedState().getBlock();
			if(block == Blocks.BARREL)
				return true;
		}
		
		return false;
	}
	
	private String getChestTypeAt(BlockPos pos)
	{
		BlockEntity be = MC.world.getBlockEntity(pos);
		if(be instanceof ChestBlockEntity)
			return "Chest";
		if(be instanceof ShulkerBoxBlockEntity)
			return "Shulker Box";
		if(be instanceof HopperBlockEntity)
			return "Hopper";
		
		Block block = MC.world.getBlockState(pos).getBlock();
		if(block == Blocks.BARREL)
			return "Barrel";
		
		return "Container";
	}
	
	private void interactWithChest()
	{
		if(currentTarget == null || MC.player == null)
			return;
		
		ClientPlayerEntity player = MC.player;
		ClientPlayerInteractionManager im = MC.interactionManager;
		
		// Check if we're still in range
		if(player.squaredDistanceTo(Vec3d.ofCenter(currentTarget)) > range
			.getValueSq())
		{
			ChatUtils.warning("Chest at " + currentTarget.toShortString()
				+ " is out of range.");
			currentTarget = null;
			return;
		}
		
		// Create hit result for the block
		Vec3d blockCenter = Vec3d.ofCenter(currentTarget);
		Direction side = Direction.UP; // Default to top face
		Vec3d hitVec = blockCenter.add(0, 0.5, 0);
		BlockHitResult hitResult =
			new BlockHitResult(hitVec, side, currentTarget, false);
		
		// Face the chest
		faceTarget.face(blockCenter);
		
		// Right-click on the chest
		Hand hand = Hand.MAIN_HAND;
		ActionResult result = im.interactBlock(player, hand, hitResult);
		
		// Swing hand if interaction was successful
		if(result.isAccepted() && result.shouldSwingHand())
			swingHand.swing(hand);
		
		// Mark that we're searching and set interaction time
		searchingInProgress = true;
		lastInteractionTime = System.currentTimeMillis();
	}
	
	private Item getItemFromString(String itemId)
	{
		try
		{
			Identifier id = Identifier.tryParse(itemId);
			if(id == null)
				return null;
			return Registries.ITEM.get(id);
		}catch(Exception e)
		{
			return null;
		}
	}
}
