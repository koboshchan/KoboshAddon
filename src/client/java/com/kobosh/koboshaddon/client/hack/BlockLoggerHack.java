/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package com.kobosh.koboshaddon.client.hack;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;
import java.util.stream.Collectors;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.block.Block;
import net.minecraft.client.render.VertexFormat.DrawMode;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.WurstClient;
import net.wurstclient.WurstRenderLayers;
import net.wurstclient.events.PacketInputListener;
import net.wurstclient.events.RenderListener;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.BlockSetting;
import net.wurstclient.settings.ChunkAreaSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.util.BlockVertexCompiler;
import net.wurstclient.util.ChatUtils;
import net.wurstclient.util.EasyVertexBuffer;
import net.wurstclient.util.RegionPos;
import net.wurstclient.util.RenderUtils;
import net.wurstclient.util.RotationUtils;
import net.wurstclient.util.chunk.ChunkSearcher;
import net.wurstclient.util.chunk.ChunkSearcherCoordinator;
import net.wurstclient.util.json.JsonUtils;

@SearchTags({"block logger", "block finder", "block tracker", "block esp"})
public final class BlockLoggerHack extends Hack
	implements UpdateListener, RenderListener
{
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
	
	// Search system (like SearchHack)
	private final ChunkSearcherCoordinator coordinator =
		new ChunkSearcherCoordinator(area);
	private ForkJoinPool forkJoinPool;
	private ForkJoinTask<HashSet<BlockPos>> getMatchingBlocksTask;
	private ForkJoinTask<ArrayList<int[]>> compileVerticesTask;
	private EasyVertexBuffer vertexBuffer;
	private RegionPos bufferRegion;
	private boolean bufferUpToDate;
	
	public BlockLoggerHack()
	{
		super("BlockLogger");
		setCategory(Category.RENDER);
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
		
		if(vertexBuffer != null)
			vertexBuffer.close();
		vertexBuffer = null;
		bufferRegion = null;
		
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
		
		if(compileVerticesTask == null)
			startCompileVerticesTask();
		
		if(!compileVerticesTask.isDone())
			return;
		
		if(!bufferUpToDate)
			setBufferFromTask();
	}
	
	@Override
	public void onRender(MatrixStack matrixStack, float partialTicks)
	{
		if(vertexBuffer == null || bufferRegion == null)
			return;
		
		// Green color for blocks
		RenderSystem.setShaderColor(0, 1, 0, 0.5F);
		
		matrixStack.push();
		RenderUtils.applyRegionalRenderOffset(matrixStack, bufferRegion);
		
		vertexBuffer.draw(matrixStack, WurstRenderLayers.ESP_QUADS);
		
		matrixStack.pop();
		
		RenderSystem.setShaderColor(1, 1, 1, 1);
	}
	
	private void stopBuildingBuffer()
	{
		if(getMatchingBlocksTask != null)
			getMatchingBlocksTask.cancel(true);
		getMatchingBlocksTask = null;
		
		if(compileVerticesTask != null)
			compileVerticesTask.cancel(true);
		compileVerticesTask = null;
		
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
	
	private void startCompileVerticesTask()
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
		
		compileVerticesTask = forkJoinPool
			.submit(() -> BlockVertexCompiler.compile(matchingBlocks));
	}
	
	private void setBufferFromTask()
	{
		ArrayList<int[]> vertices = compileVerticesTask.join();
		RegionPos region = RenderUtils.getCameraRegion();
		
		if(vertexBuffer != null)
			vertexBuffer.close();
		
		vertexBuffer = EasyVertexBuffer.createAndUpload(DrawMode.QUADS,
			VertexFormats.POSITION_COLOR, buffer -> {
				for(int[] vertex : vertices)
					buffer.vertex(vertex[0] - region.x(), vertex[1],
						vertex[2] - region.z()).color(0xFF00FF00); // Green
																	// color
			});
		
		bufferUpToDate = true;
		bufferRegion = region;
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
			
		}catch(Exception e)
		{
			ChatUtils
				.error("Failed to load blocks from JSON: " + e.getMessage());
		}
	}
}
