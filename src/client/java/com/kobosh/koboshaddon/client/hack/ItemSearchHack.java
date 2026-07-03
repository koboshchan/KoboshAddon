/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package com.kobosh.koboshaddon.client.hack;

import net.minecraft.core.Holder;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
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
		
		ChatUtils.message(
			"ItemSearch enabled. Searching for: " + targetItem.getValue());
	}
	
	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		
		// Only close screen if item was NOT found (so we keep chest open when
		// item is found)
		if(!foundItem && MC.gui.screen() instanceof AbstractContainerScreen)
			MC.player.closeContainer();
		
		currentTarget = null;
		
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
		if(MC.gui.screen() instanceof AbstractContainerScreen<?> screen)
		{
			handleOpenChest(screen);
			if(foundItem)
				setEnabled(false);
			return;
		}
		
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
	
	private void handleOpenChest(AbstractContainerScreen<?> screen)
	{
		if(currentTarget == null || foundItem)
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
		for(Slot slot : screen.getMenu().slots)
		{
			ItemStack stack = slot.getItem();
			if(stack.isEmpty())
				continue;
			
			boolean matches;
			if(requireExactMatch.isChecked())
				matches = stack.getItem() == targetItemType;
			else
				matches = stack.is(targetItemType);
			
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
				+ currentTarget.toShortString()
				+ "! Keeping chest open and disabling ItemSearch.");
		}else
		{
			// Item not found, close chest and mark as searched
			if(currentTarget != null)
				searchedChests.add(currentTarget);
			
			MC.player.closeContainer();
			currentTarget = null;
			lastInteractionTime = System.currentTimeMillis();
		}
	}
	
	private void findNextChest()
	{
		LocalPlayer player = MC.player;
		Vec3 playerPos = new Vec3(player.getX(), player.getY(), player.getZ());
		double rangeSq = range.getValueSq();
		
		Stream<BlockPos> stream = ChunkUtils.getLoadedBlockEntities()
			.filter(this::isValidContainer).map(BlockEntity::getBlockPos)
			.filter(pos -> !searchedChests.contains(pos))
			.filter(pos -> playerPos
				.distanceToSqr(Vec3.atCenterOf(pos)) <= rangeSq);
		
		currentTarget = stream
			.min(Comparator.comparingDouble(
				pos -> playerPos.distanceToSqr(Vec3.atCenterOf(pos))))
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
			Block block = be.getBlockState().getBlock();
			if(block == Blocks.BARREL)
				return true;
		}
		
		return false;
	}
	
	private String getChestTypeAt(BlockPos pos)
	{
		BlockEntity be = MC.level.getBlockEntity(pos);
		if(be instanceof ChestBlockEntity)
			return "Chest";
		if(be instanceof ShulkerBoxBlockEntity)
			return "Shulker AABB";
		if(be instanceof HopperBlockEntity)
			return "Hopper";
		
		Block block = MC.level.getBlockState(pos).getBlock();
		if(block == Blocks.BARREL)
			return "Barrel";
		
		return "Container";
	}
	
	private void interactWithChest()
	{
		if(currentTarget == null || MC.player == null)
			return;
		
		LocalPlayer player = MC.player;
		MultiPlayerGameMode im = MC.gameMode;
		
		// Check if we're still in range
		if(player.distanceToSqr(Vec3.atCenterOf(currentTarget)) > range
			.getValueSq())
		{
			ChatUtils.warning("Chest at " + currentTarget.toShortString()
				+ " is out of range.");
			currentTarget = null;
			return;
		}
		
		// Create hit result for the block
		Vec3 blockCenter = Vec3.atCenterOf(currentTarget);
		Direction side = Direction.UP; // Default to top face
		Vec3 hitVec = blockCenter.add(0, 0.5, 0);
		BlockHitResult hitResult =
			new BlockHitResult(hitVec, side, currentTarget, false);
		
		// Face the chest
		faceTarget.face(blockCenter);
		
		// Right-click on the chest
		InteractionHand hand = InteractionHand.MAIN_HAND;
		InteractionResult result = im.useItemOn(player, hand, hitResult);
		
		// Swing hand if interaction was successful
		if(result.consumesAction())
			swingHand.swing(hand);
		
		// Set interaction time
		lastInteractionTime = System.currentTimeMillis();
	}
	
	private Item getItemFromString(String itemId)
	{
		try
		{
			Identifier id = Identifier.tryParse(itemId);
			if(id == null)
				return null;
			return BuiltInRegistries.ITEM.get(id).map(Holder::value).orElse(null);
		}catch(Exception e)
		{
			return null;
		}
	}
}
