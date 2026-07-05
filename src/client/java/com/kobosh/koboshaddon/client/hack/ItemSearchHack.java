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
import net.wurstclient.settings.ItemListSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.settings.SwingHandSetting;
import net.wurstclient.settings.SwingHandSetting.SwingHand;
import net.wurstclient.util.ChatUtils;
import net.wurstclient.util.chunk.ChunkUtils;

@SearchTags({"item search", "chest search", "chest finder", "item finder",
	"auto search"})
public final class ItemSearchHack extends Hack implements UpdateListener
{
	private final ItemListSetting targetItem = new ItemListSetting(
		"Target items",
		"The items to search for in chests.",
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
			"ItemSearch enabled. Searching for: " + String.join(", ", targetItem.getItemNames()));
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
				ChatUtils.warning("No more chests to search. Target items not found.");
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
		
		// Check all slots in the chest for our target item
		boolean itemFound = false;
		String foundName = "";
		for(Slot slot : screen.getMenu().slots)
		{
			ItemStack stack = slot.getItem();
			if(stack.isEmpty())
				continue;
			
			String itemName = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
			if(targetItem.getItemNames().contains(itemName))
			{
				itemFound = true;
				foundName = itemName;
				break;
			}
		}
		
		if(itemFound)
		{
			foundItem = true;
			ChatUtils.message("Found " + foundName + " at "
				+ currentTarget.toShortString()
				+ "! Keeping chest open and disabling ItemSearch.");
		}else
		{
			// Item not found, close chest and mark as searched
			if(currentTarget != null)
				searchedChests.add(currentTarget);
			
			MC.player.closeContainer();
			currentTarget = null;
		}
	}
	
	private void findNextChest()
	{
		Set<BlockPos> chests = findChests();
		
		currentTarget = chests.stream()
			.filter(pos -> !searchedChests.contains(pos))
			.min(Comparator.comparingDouble(pos -> MC.player.distanceToSqr(
				pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5)))
			.orElse(null);
	}
	
	private Set<BlockPos> findChests()
	{
		Set<BlockPos> chests = new HashSet<>();
		int radius = (int)Math.ceil(range.getValue());
		BlockPos playerPos = MC.player.blockPosition();
		
		for(int x = -radius; x <= radius; x++)
		{
			for(int y = -radius; y <= radius; y++)
			{
				for(int z = -radius; z <= radius; z++)
				{
					BlockPos pos = playerPos.offset(x, y, z);
					if(MC.player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5,
						pos.getZ() + 0.5) > range.getValue() * range.getValue())
						continue;
					
					Block block = MC.level.getBlockState(pos).getBlock();
					boolean isSearchable = block == Blocks.CHEST || block == Blocks.TRAPPED_CHEST;
					
					if(!isSearchable && searchShulkers.isChecked())
						isSearchable = block instanceof net.minecraft.world.level.block.ShulkerBoxBlock;
						
					if(!isSearchable && searchHoppers.isChecked())
						isSearchable = block == Blocks.HOPPER;
						
					if(!isSearchable && searchBarrels.isChecked())
						isSearchable = block == Blocks.BARREL;
						
					if(isSearchable)
						chests.add(pos);
				}
			}
		}
		
		return chests;
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
		LocalPlayer player = MC.player;
		MultiPlayerGameMode im = MC.gameMode;
		
		if(player == null || im == null || currentTarget == null)
			return;
		
		// Verify distance is still valid
		double distSqr = MC.player.distanceToSqr(currentTarget.getX() + 0.5,
			currentTarget.getY() + 0.5, currentTarget.getZ() + 0.5);
		if(distSqr > range.getValue() * range.getValue())
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
}
