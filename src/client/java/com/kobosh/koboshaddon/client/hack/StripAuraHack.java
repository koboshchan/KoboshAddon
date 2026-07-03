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

import net.minecraft.block.BlockState;
import net.minecraft.item.AxeItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
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

		if(MC.world == null || MC.player == null)
			return;

		int axeSlot = getAxeSlot();
		if(axeSlot == -1)
			return;

		Vec3d eyesVec = RotationUtils.getEyesPos();
		BlockPos eyesBlock = BlockPos.ofFloored(eyesVec);
		double rangeSq = Math.pow(range.getValue(), 2);
		int blockRange = range.getValueCeil();

		List<BlockPos> logs = BlockUtils.getAllInBoxStream(eyesBlock, blockRange)
			.filter(pos -> pos.getSquaredDistance(eyesVec) <= rangeSq)
			.filter(BlockUtils::canBeClicked)
			.filter(pos -> {
				BlockState state = MC.world.getBlockState(pos);
				return AxeItem.STRIPPED_BLOCKS.containsKey(state.getBlock());
			})
			.sorted(Comparator.comparingDouble(pos -> pos.getSquaredDistance(eyesVec)))
			.collect(Collectors.toList());

		if(logs.isEmpty())
			return;

		int oldSlot = MC.player.getInventory().getSelectedSlot();
		MC.player.getInventory().setSelectedSlot(axeSlot);

		int stripCount = Math.min(logs.size(), limit.getValueI());
		for(int i = 0; i < stripCount; i++)
		{
			BlockPos pos = logs.get(i);
			BlockHitResult hitResult = new BlockHitResult(Vec3d.ofCenter(pos), Direction.UP, pos, false);
			InteractionSimulator.rightClickBlock(hitResult);
			MC.player.swingHand(Hand.MAIN_HAND);
		}

		MC.player.getInventory().setSelectedSlot(oldSlot);
		delayTimer = delay.getValueI();
	}

	private int getAxeSlot()
	{
		net.minecraft.entity.player.PlayerInventory inventory = MC.player.getInventory();
		for(int slot = 0; slot < 9; slot++)
		{
			ItemStack stack = inventory.getStack(slot);
			if(stack.getItem() instanceof AxeItem)
				return slot;
		}
		return -1;
	}
}
