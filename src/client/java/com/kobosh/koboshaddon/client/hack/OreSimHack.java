/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package com.kobosh.koboshaddon.client.hack;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.RenderListener;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.ColorSetting;
import net.wurstclient.settings.EspStyleSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.util.BlockUtils;
import net.wurstclient.util.RenderUtils;

@SearchTags({"ore sim", "oresim", "ore scanner"})
public final class OreSimHack extends Hack
	implements UpdateListener, RenderListener
{
	private final SliderSetting range = new SliderSetting("Chunk range",
		"Taxi-cap chunk range to scan around you.", 5, 1, 10, 1,
		SliderSetting.ValueDisplay.INTEGER);

	private final CheckboxSetting checkExposed = new CheckboxSetting("Air-check",
		"Only render ores with at least one exposed face.", true);

	private final EspStyleSetting style = new EspStyleSetting();

	private final ColorSetting color = new ColorSetting("Color",
		"Color used for rendered ore boxes.", new Color(255, 170, 0));

	private final List<BlockPos> ores = new ArrayList<>();
	private int ticksUntilRescan = 0;

	public OreSimHack()
	{
		super("OreSim");
		setCategory(Category.RENDER);
		addSetting(range);
		addSetting(checkExposed);
		addSetting(style);
		addSetting(color);
	}

	@Override
	protected void onEnable()
	{
		ores.clear();
		ticksUntilRescan = 0;
		EVENTS.add(UpdateListener.class, this);
		EVENTS.add(RenderListener.class, this);
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		EVENTS.remove(RenderListener.class, this);
		ores.clear();
	}

	@Override
	public void onUpdate()
	{
		if(MC.player == null || MC.level == null)
			return;

		if(--ticksUntilRescan > 0)
			return;
		ticksUntilRescan = 40;
		ores.clear();
		Set<BlockPos> found = new HashSet<>();
		int blockRange = range.getValueI() * 16;
		BlockPos center = MC.player.blockPosition();
		BlockPos min = center.offset(-blockRange, -64, -blockRange);
		BlockPos max = center.offset(blockRange, 64, blockRange);

		for(BlockPos pos : BlockPos.betweenClosed(min, max))
		{
			BlockState state = MC.level.getBlockState(pos);
			if(!isOre(state.getBlock()))
				continue;
			if(checkExposed.isChecked() && !hasExposedFace(pos))
				continue;
			if(found.add(pos.immutable()))
				ores.add(pos.immutable());
		}
	}

	@Override
	public void onRender(PoseStack matrixStack, float partialTicks)
	{
		if(ores.isEmpty())
			return;

		int lineColor = color.getColorI(0xAA);
		int fillColor = color.getColorI(0x33);
		for(BlockPos pos : ores)
		{
			AABB box = new AABB(pos);
			
			if(style.hasBoxes())
			{
				RenderUtils.drawOutlinedBox(matrixStack, box, lineColor, false);
				RenderUtils.drawSolidBox(matrixStack, box, fillColor, false);
			}

			if(style.hasLines())
				RenderUtils.drawTracer(matrixStack, partialTicks,
					Vec3.atCenterOf(pos), lineColor, false);
		}
	}

	@Override
	public String getRenderName()
	{
		return isEnabled() ? getName() + " [" + ores.size() + "]" : getName();
	}

	private boolean hasExposedFace(BlockPos pos)
	{
		for(Direction d : Direction.values())
			if(!BlockUtils.isOpaqueFullCube(pos.relative(d)))
				return true;
		return false;
	}

	private boolean isOre(Block block)
	{
		return block == Blocks.COAL_ORE || block == Blocks.DEEPSLATE_COAL_ORE
			|| block == Blocks.IRON_ORE || block == Blocks.DEEPSLATE_IRON_ORE
			|| block == Blocks.COPPER_ORE
			|| block == Blocks.DEEPSLATE_COPPER_ORE
			|| block == Blocks.GOLD_ORE || block == Blocks.DEEPSLATE_GOLD_ORE
			|| block == Blocks.REDSTONE_ORE
			|| block == Blocks.DEEPSLATE_REDSTONE_ORE
			|| block == Blocks.LAPIS_ORE || block == Blocks.DEEPSLATE_LAPIS_ORE
			|| block == Blocks.DIAMOND_ORE
			|| block == Blocks.DEEPSLATE_DIAMOND_ORE
			|| block == Blocks.EMERALD_ORE
			|| block == Blocks.DEEPSLATE_EMERALD_ORE
			|| block == Blocks.NETHER_GOLD_ORE
			|| block == Blocks.NETHER_QUARTZ_ORE
			|| block == Blocks.ANCIENT_DEBRIS;
	}
}
