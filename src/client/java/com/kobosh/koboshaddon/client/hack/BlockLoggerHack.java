/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package com.kobosh.koboshaddon.client.hack;

import java.awt.Color;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;
import java.util.stream.Collectors;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import net.minecraft.block.Block;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.WurstClient;
import net.wurstclient.events.PacketInputListener;
import net.wurstclient.events.RenderListener;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.BlockSetting;
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
import net.wurstclient.util.json.JsonUtils;

@SearchTags({"block logger", "block finder", "block tracker", "block esp"})
public final class BlockLoggerHack extends Hack
	implements UpdateListener, RenderListener
{
	private final EspStyleSetting style = new EspStyleSetting();
	
	private final ColorSetting color = new ColorSetting("Color",
		"Color of the ESP highlight for found blocks.", new Color(0, 255, 0));
	
	private final BlockSetting block = new BlockSetting("Block",
		"The type of block to search for.", "minecraft:diamond_ore", false);
	private Block lastBlock;
	
	private final ChunkAreaSetting area = new ChunkAreaSetting("Area",
		"The area around the player to search in.\n"
			+ "Higher values require a faster computer.");
	
	private final SliderSetting limit = new SliderSetting("Limit",
		"The maximum number of blocks to display.\n"
			+ "Higher values require a faster computer.",
		4, 3, 6, 1, ValueDisplay.LOGARITHMIC);
	private int prevLimit;
	private boolean notify;
	
	// Logging system
	private final Set<BlockPos> loggedBlocks = new HashSet<>();
	private String currentFileName;
	private Path logsFolder;
	
	// Blocks loaded from a log file via ViewLogsCmd
	private List<Box> loadedBlockBoxes = List.of();
	
	// Search system (like SearchHack)
	private final ChunkSearcherCoordinator coordinator =
		new ChunkSearcherCoordinator(area);
	private ForkJoinPool forkJoinPool;
	private ForkJoinTask<HashSet<BlockPos>> getMatchingBlocksTask;
	private List<Box> blockBoxes = List.of();
	private boolean bufferUpToDate;
	
	public BlockLoggerHack()
	{
		super("BlockLogger");
		setCategory(Category.RENDER);
		addSetting(style);
		addSetting(color);
		addSetting(block);
		addSetting(area);
		addSetting(limit);
	}
	
	@Override
	public String getRenderName()
	{
		return getName() + " [" + block.getBlockName().replace("minecraft:", "")
			+ ": " + loggedBlocks.size() + "]";
	}
	
	@Override
	protected void onEnable()
	{
		lastBlock = block.getBlock();
		coordinator.setTargetBlock(lastBlock);
		prevLimit = limit.getValueI();
		notify = true;
		
		// Setup logging
		setupLogging();
		
		forkJoinPool = new ForkJoinPool();
		bufferUpToDate = false;
		
		EVENTS.add(UpdateListener.class, this);
		EVENTS.add(PacketInputListener.class, coordinator);
		EVENTS.add(RenderListener.class, this);
		
		ChatUtils
			.message("Started logging " + block.getBlockName() + " blocks");
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
		blockBoxes = List.of();
		
		ChatUtils.message(
			"Stopped logging. Found " + loggedBlocks.size() + " blocks total.");
		if(currentFileName != null)
			ChatUtils.message("Saved to: " + currentFileName);
	}
	
	@Override
	public void onUpdate()
	{
		boolean searchersChanged = false;
		
		// Clear ChunkSearchers if block has changed
		Block currentBlock = block.getBlock();
		if(currentBlock != lastBlock)
		{
			lastBlock = currentBlock;
			coordinator.setTargetBlock(lastBlock);
			searchersChanged = true;
		}
		
		if(coordinator.update())
			searchersChanged = true;
		
		if(searchersChanged)
			stopBuildingBuffer();
		
		if(!coordinator.isDone())
			return;
		
		// Check if limit has changed
		if(limit.getValueI() != prevLimit)
		{
			stopBuildingBuffer();
			prevLimit = limit.getValueI();
			notify = true;
		}
		
		// Build the buffer
		if(getMatchingBlocksTask == null)
			startGetMatchingBlocksTask();
		
		if(!getMatchingBlocksTask.isDone())
			return;
		
		if(!bufferUpToDate)
			setBoxesFromTask();
	}
	
	@Override
	public void onRender(MatrixStack matrixStack, float partialTicks)
	{
		List<Box> allBoxes = new ArrayList<>(blockBoxes);
		allBoxes.addAll(loadedBlockBoxes);
		
		if(allBoxes.isEmpty())
			return;
		
		if(style.hasBoxes())
		{
			int quadsColor = color.getColorI(0x40);
			int linesColor = color.getColorI(0x80);
			RenderUtils.drawSolidBoxes(matrixStack, allBoxes, quadsColor, false);
			RenderUtils.drawOutlinedBoxes(matrixStack, allBoxes, linesColor,
				false);
		}
		
		if(style.hasLines())
		{
			int tracerColor = color.getColorI(0x80);
			RenderUtils.drawTracers(matrixStack, partialTicks,
				allBoxes.stream().map(Box::getCenter).toList(), tracerColor,
				false);
		}
	}
	
	private void stopBuildingBuffer()
	{
		if(getMatchingBlocksTask != null)
			getMatchingBlocksTask.cancel(true);
		getMatchingBlocksTask = null;
		blockBoxes = List.of();
		
		bufferUpToDate = false;
	}
	
	private void startGetMatchingBlocksTask()
	{
		BlockPos eyesPos = BlockPos.ofFloored(RotationUtils.getEyesPos());
		Comparator<BlockPos> comparator =
			Comparator.comparingInt(pos -> eyesPos.getManhattanDistance(pos));
		
		getMatchingBlocksTask = forkJoinPool.submit(() -> {
			HashSet<BlockPos> matchingBlocks = coordinator.getMatches()
				.parallel().map(ChunkSearcher.Result::pos).sorted(comparator)
				.limit(limit.getValueLog())
				.collect(Collectors.toCollection(HashSet::new));
			
			// Log new blocks as they're found
			for(BlockPos pos : matchingBlocks)
			{
				if(!loggedBlocks.contains(pos))
				{
					loggedBlocks.add(pos);
					ChatUtils.message("Found " + block.getBlockName() + " at "
						+ pos.getX() + ", " + pos.getY() + ", " + pos.getZ());
					saveBlockToJson(pos);
				}
			}
			
			return matchingBlocks;
		});
	}
	
	private void setBoxesFromTask()
	{
		HashSet<BlockPos> matchingBlocks = getMatchingBlocksTask.join();
		
		if(matchingBlocks.size() < limit.getValueLog())
			notify = true;
		else if(notify)
		{
			ChatUtils.warning("Search found \u00a7lA LOT\u00a7r of blocks!"
				+ " To prevent lag, it will only show the closest \u00a76"
				+ limit.getValueString() + "\u00a7r results.");
			notify = false;
		}

		blockBoxes = matchingBlocks.stream().map(Box::new).toList();
		
		bufferUpToDate = true;
	}
	
	private void setupLogging()
	{
		try
		{
			logsFolder =
				WurstClient.INSTANCE.getWurstFolder().resolve("block_logs");
			Files.createDirectories(logsFolder);
			
			// Create filename with current time
			long currentTime = System.currentTimeMillis();
			String blockName = block.getBlockName();
			if(blockName.startsWith("minecraft:"))
				blockName = blockName.substring(10);
			
			currentFileName = currentTime + "_" + blockName + ".json";
			
		}catch(IOException e)
		{
			ChatUtils.error("Failed to setup logging: " + e.getMessage());
		}
	}
	
	private void saveBlockToJson(BlockPos pos)
	{
		if(currentFileName == null || logsFolder == null)
			return;
		
		try
		{
			Path filePath = logsFolder.resolve(currentFileName);
			JsonObject root;
			
			// Load existing data or create new
			if(Files.exists(filePath))
			{
				try
				{
					root = JsonUtils.parseFileToObject(filePath).toJsonObject();
				}catch(Exception e)
				{
					// If file is corrupted, create new
					root = new JsonObject();
				}
			}else
			{
				root = new JsonObject();
				root.addProperty("block_type", block.getBlockName());
				root.addProperty("created_time", System.currentTimeMillis());
				root.add("blocks", new JsonArray());
			}
			
			// Add new block
			JsonArray blocks = root.getAsJsonArray("blocks");
			JsonObject blockData = new JsonObject();
			blockData.addProperty("x", pos.getX());
			blockData.addProperty("y", pos.getY());
			blockData.addProperty("z", pos.getZ());
			blockData.addProperty("found_time", System.currentTimeMillis());
			
			blocks.add(blockData);
			
			// Save to file
			JsonUtils.toJson(root, filePath);
			
		}catch(Exception e)
		{
			ChatUtils.error("Failed to save block data: " + e.getMessage());
		}
	}
	
	public Set<BlockPos> getFoundBlocks()
	{
		return new HashSet<>(loggedBlocks);
	}
	
	public void clearFoundBlocks()
	{
		loggedBlocks.clear();
		loadedBlockBoxes = List.of();
	}
	
	public void addBlocksFromJson(JsonObject jsonData)
	{
		try
		{
			if(!jsonData.has("blocks"))
				return;
			
			JsonArray blocks = jsonData.getAsJsonArray("blocks");
			for(int i = 0; i < blocks.size(); i++)
			{
				JsonObject blockData = blocks.get(i).getAsJsonObject();
				int x = blockData.get("x").getAsInt();
				int y = blockData.get("y").getAsInt();
				int z = blockData.get("z").getAsInt();
				
				BlockPos pos = new BlockPos(x, y, z);
				loggedBlocks.add(pos);
			}
			
			// Populate the rendered boxes from all loaded blocks so the
			// highlight is visible immediately, even before the chunk searcher
			// has had a chance to scan the area.
			loadedBlockBoxes =
				loggedBlocks.stream().map(Box::new).toList();
			
		}catch(Exception e)
		{
			ChatUtils
				.error("Failed to load blocks from JSON: " + e.getMessage());
		}
	}
	
	public ColorSetting getColorSetting()
	{
		return color;
	}
	
	public EspStyleSetting getStyleSetting()
	{
		return style;
	}
}
