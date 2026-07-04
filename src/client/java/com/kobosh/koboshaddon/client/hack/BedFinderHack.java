/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package com.kobosh.koboshaddon.client.hack;

import net.minecraft.core.Holder;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;
import java.util.stream.Collectors;

import net.minecraft.world.level.block.Block;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.PacketInputListener;
import net.wurstclient.events.RenderListener;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.ChunkAreaSetting;
import net.wurstclient.settings.ColorSetting;
import net.wurstclient.settings.EspStyleSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.util.ChatUtils;
import net.wurstclient.util.RenderUtils;
import net.wurstclient.util.RotationUtils;
import net.wurstclient.util.chunk.ChunkSearcher;
import net.wurstclient.util.chunk.ChunkSearcherCoordinator;

@SearchTags({"bed finder", "bed esp", "beds", "sleep", "spawn point",
	"respawn"})
public final class BedFinderHack extends Hack
	implements UpdateListener, RenderListener
{
	private final EspStyleSetting style = new EspStyleSetting();
	
	private final ColorSetting color = new ColorSetting("Color",
		"Color of ESP overlays around beds.", new Color(139, 31, 90));
	
	private final List<String> bedTypes = Arrays.asList("white_bed",
		"orange_bed", "magenta_bed", "light_blue_bed", "yellow_bed", "lime_bed",
		"pink_bed", "gray_bed", "light_gray_bed", "cyan_bed", "purple_bed",
		"blue_bed", "brown_bed", "green_bed", "red_bed", "black_bed");
	
	private final List<Block> bedBlocks = new ArrayList<>();
	
	private final ChunkAreaSetting area = new ChunkAreaSetting("Area",
		"The area around the player to search in.\n"
			+ "Higher values require a faster computer.");
	
	private final SliderSetting limit = new SliderSetting("Limit",
		"The maximum number of beds to display.\n"
			+ "Higher values require a faster computer.",
		4, 3, 6, 1, ValueDisplay.LOGARITHMIC);
	private int prevLimit;
	private boolean notify;
	
	private final ChunkSearcherCoordinator coordinator =
		new ChunkSearcherCoordinator((pos, state) -> bedBlocks
			.contains(state.getBlock()), area);
	
	private ForkJoinPool forkJoinPool;
	private ForkJoinTask<HashSet<BlockPos>> getMatchingBlocksTask;
	private List<AABB> bedBoxes = List.of();
	private boolean bufferUpToDate;
	
	public BedFinderHack()
	{
		super("BedFinder");
		setCategory(Category.RENDER);
		addSetting(style);
		addSetting(color);
		addSetting(area);
		addSetting(limit);
		
		// Initialize bed blocks
		for(String bedType : bedTypes)
		{
			Block bed =
				BuiltInRegistries.BLOCK.get(Identifier.fromNamespaceAndPath("minecraft", bedType)).map(Holder::value).orElse(null);
			if(bed != null)
				bedBlocks.add(bed);
		}
	}
	
	@Override
	public String getRenderName()
	{
		return getName() + " [" + bedBlocks.size() + " types]";
	}
	
	@Override
	protected void onEnable()
	{
		prevLimit = limit.getValueI();
		notify = true;
		
		forkJoinPool = new ForkJoinPool();
		
		bufferUpToDate = false;
		
		EVENTS.add(UpdateListener.class, this);
		EVENTS.add(PacketInputListener.class, coordinator);
		EVENTS.add(RenderListener.class, this);
	}
	
	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		EVENTS.remove(PacketInputListener.class, coordinator);
		EVENTS.remove(RenderListener.class, this);
		
		stopBuildingBuffer();
		coordinator.reset();
		forkJoinPool.shutdownNow();
		bedBoxes = List.of();
	}
	
	@Override
	public void onUpdate()
	{
		boolean searchersChanged = false;
		
		if(coordinator.update())
			searchersChanged = true;
		
		if(searchersChanged)
			stopBuildingBuffer();
		
		if(!coordinator.isDone())
			return;
		
		// check if limit has changed
		if(limit.getValueI() != prevLimit)
		{
			stopBuildingBuffer();
			prevLimit = limit.getValueI();
			notify = true;
		}
		
		// build the buffer
		
		if(getMatchingBlocksTask == null)
			startGetMatchingBlocksTask();
		
		if(!getMatchingBlocksTask.isDone())
			return;
		
		if(!bufferUpToDate)
			setBoxesFromTask();
	}
	
	@Override
	public void onRender(PoseStack matrixStack, float partialTicks)
	{
		if(bedBoxes.isEmpty())
			return;
		
		if(style.hasBoxes())
		{
			int quadsColor = color.getColorI(0x40);
			int linesColor = color.getColorI(0x80);
			RenderUtils.drawSolidBoxes(matrixStack, bedBoxes, quadsColor, false);
			RenderUtils.drawOutlinedBoxes(matrixStack, bedBoxes, linesColor,
				false);
		}
		
		if(style.hasLines())
		{
			int tracerColor = color.getColorI(0x80);
			RenderUtils.drawTracers(matrixStack, partialTicks,
				bedBoxes.stream().map(AABB::getCenter).toList(), tracerColor,
				false);
		}
	}
	
	private void stopBuildingBuffer()
	{
		if(getMatchingBlocksTask != null)
			getMatchingBlocksTask.cancel(true);
		getMatchingBlocksTask = null;
		
		bufferUpToDate = false;
	}
	
	private void startGetMatchingBlocksTask()
	{
		BlockPos eyesPos = BlockPos.containing(RotationUtils.getEyesPos());
		Comparator<BlockPos> comparator =
			Comparator.comparingInt(pos -> eyesPos.distManhattan(pos));
		
		getMatchingBlocksTask = forkJoinPool.submit(() -> coordinator
			.getMatches().parallel().map(ChunkSearcher.Result::pos)
			.sorted(comparator).limit(limit.getValueLog())
			.collect(Collectors.toCollection(HashSet::new)));
	}
	
	private void setBoxesFromTask()
	{
		HashSet<BlockPos> matchingBlocks = getMatchingBlocksTask.join();
		
		if(matchingBlocks.size() < limit.getValueLog())
			notify = true;
		else if(notify)
		{
			ChatUtils.warning("BedFinder found \u00a7lA LOT\u00a7r of beds!"
				+ " To prevent lag, it will only show the closest \u00a76"
				+ limit.getValueString() + "\u00a7r results.");
			notify = false;
		}

		bedBoxes = matchingBlocks.stream().map(AABB::new).toList();
		
		bufferUpToDate = true;
	}
}
