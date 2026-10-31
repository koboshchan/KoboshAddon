/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package com.kobosh.koboshaddon.client.hack;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.util.BlockUtils;
import net.wurstclient.util.InteractionSimulator;
import net.wurstclient.util.RotationUtils;

@SearchTags({"strip aura", "stripaura", "auto strip", "autostrip"})
public final class StripAuraHack extends Hack implements UpdateListener
{
	private final SliderSetting range =
		new SliderSetting("Range", 5.0, 1.0, 6.0, 0.05, ValueDisplay.DECIMAL);

	private final SliderSetting limit =
		new SliderSetting("Limit", "Max blocks to strip per tick.", 3, 1, 10, 1, ValueDisplay.INTEGER);

	private final SliderSetting delay =
		new SliderSetting("Delay", "Tick delay between actions.", 2, 0, 20, 1, ValueDisplay.INTEGER);

	private int delayTimer = 0;

	public StripAuraHack()
	{
		super("StripAura");
		setCategory(Category.BLOCKS);
		addSetting(range);
		addSetting(limit);
		addSetting(delay);
	}

	@Override
	protected void onEnable()
	{
		EVENTS.add(UpdateListener.class, this);
		delayTimer = 0;
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
	}

	@Override
	public void onUpdate()
	{
		if(delayTimer > 0)
		{
			delayTimer--;
			return;
		}

		if(MC.level == null || MC.player == null)
			return;

		int axeSlot = getAxeSlot();
		if(axeSlot == -1)
			return;

		Vec3 eyesVec = MC.player.getEyePosition();
		BlockPos eyesBlock = BlockPos.containing(eyesVec);
		double rangeSq = Math.pow(range.getValue(), 2);
		int blockRange = range.getValueCeil();

		List<BlockPos> logs = BlockUtils.getAllInBoxStream(eyesBlock, blockRange)
			.filter(pos -> pos.distToCenterSqr(eyesVec) <= rangeSq)
			.filter(BlockUtils::canBeClicked)
			.filter(pos -> {
				BlockState state = MC.level.getBlockState(pos);
				// AxeItem was replaced by data-driven block transformers in 26.3.
				// Match registered stripped counterparts, including bamboo blocks.
				var id = net.minecraft.core.registries.BuiltInRegistries.BLOCK
					.getKey(state.getBlock());
				return !id.getPath().startsWith("stripped_")
					&& net.minecraft.core.registries.BuiltInRegistries.BLOCK.containsKey(
						net.minecraft.resources.Identifier.fromNamespaceAndPath(
							id.getNamespace(), "stripped_" + id.getPath()));
			})
			.sorted(Comparator.comparingDouble(pos -> pos.distToCenterSqr(eyesVec)))
			.collect(Collectors.toList());

		if(logs.isEmpty())
			return;

		int oldSlot = MC.player.getInventory().getSelectedSlot();
		MC.player.getInventory().setSelectedSlot(axeSlot);

		int stripCount = Math.min(logs.size(), limit.getValueI());
		for(int i = 0; i < stripCount; i++)
		{
			BlockPos pos = logs.get(i);
			BlockHitResult hitResult = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
			InteractionSimulator.rightClickBlock(hitResult);
			MC.player.swing(InteractionHand.MAIN_HAND, MC.player.getMainHandItem().getInteractAnimation(), false);
		}

		MC.player.getInventory().setSelectedSlot(oldSlot);
		delayTimer = delay.getValueI();
	}

	private int getAxeSlot()
	{
		net.minecraft.world.entity.player.Inventory inventory = MC.player.getInventory();
		for(int slot = 0; slot < 9; slot++)
		{
			ItemStack stack = inventory.getItem(slot);
			if(stack.is(ItemTags.AXES))
				return slot;
		}
		return -1;
	}
}
