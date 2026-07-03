/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package com.kobosh.koboshaddon.client.hack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.lwjgl.glfw.GLFW;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.ai.PathFinder;
import net.wurstclient.ai.PathProcessor;
import net.wurstclient.commands.PathCmd;
import net.wurstclient.events.GUIRenderListener;
import net.wurstclient.events.RenderListener;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.util.BlockBreaker;
import net.wurstclient.util.BlockUtils;
import net.wurstclient.util.OverlayRenderer;
import net.wurstclient.util.RenderUtils;
import net.wurstclient.util.RotationUtils;

@SearchTags({"fast excavator", "instant excavator", "speed excavator", "excavator copy"})
public final class FastExcavatorHack extends Hack
	implements UpdateListener, RenderListener, GUIRenderListener
{
	private final SliderSetting range =
		new SliderSetting("Range", 5, 2, 6, 0.05, ValueDisplay.DECIMAL);

	private final SliderSetting maxBlocks =
		new SliderSetting("Max blocks", "Maximum blocks to break per tick.", 5, 2, 10, 1, ValueDisplay.INTEGER);
	
	private final OverlayRenderer overlay = new OverlayRenderer();
	
	private Step step;
	private BlockPos posLookingAt;
	private Area area;
	private BlockPos currentBlock;
	private ExcavatorPathFinder pathFinder;
	private PathProcessor processor;
	private int lastActiveMinY = -1;
	
	public FastExcavatorHack()
	{
		super("FastExcavator");
		setCategory(Category.BLOCKS);
		addSetting(range);
		addSetting(maxBlocks);
	}
	
	@Override
	public String getRenderName()
	{
		String name = getName();
		
		if(step == Step.EXCAVATE && area != null)
		{
			int totalBlocks = area.blocksList.size();
			double brokenBlocks = totalBlocks - area.remainingBlocks;
			double progress = brokenBlocks / totalBlocks;
			int percentage = (int)(progress * 100);
			name += " " + percentage + "%";
		}
		
		return name;
	}
	
	@Override
	protected void onEnable()
	{
		// disable conflicting hacks
		WURST.getHax().autoMineHack.setEnabled(false);
		WURST.getHax().bowAimbotHack.setEnabled(false);
		WURST.getHax().excavatorHack.setEnabled(false);
		WURST.getHax().nukerHack.setEnabled(false);
		WURST.getHax().nukerLegitHack.setEnabled(false);
		WURST.getHax().speedNukerHack.setEnabled(false);
		WURST.getHax().templateToolHack.setEnabled(false);
		WURST.getHax().tunnellerHack.setEnabled(false);
		WURST.getHax().veinMinerHack.setEnabled(false);
		
		step = Step.START_POS;
		lastActiveMinY = -1;
		
		EVENTS.add(UpdateListener.class, this);
		EVENTS.add(RenderListener.class, this);
		EVENTS.add(GUIRenderListener.class, this);
	}
	
	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		EVENTS.remove(RenderListener.class, this);
		EVENTS.remove(GUIRenderListener.class, this);
		
		for(Step step : Step.values())
			step.pos = null;
		posLookingAt = null;
		area = null;
		
		MC.gameMode.stopDestroyBlock();
		overlay.resetProgress();
		currentBlock = null;
		
		pathFinder = null;
		processor = null;
		PathProcessor.releaseControls();
	}
	
	@Override
	public void onUpdate()
	{
		if(step.selectPos)
			handlePositionSelection();
		else if(step == Step.SCAN_AREA)
			scanArea();
		else if(step == Step.EXCAVATE)
			excavate();
	}
	
	@Override
	public void onRender(PoseStack matrixStack, float partialTicks)
	{
		if(pathFinder != null)
		{
			PathCmd pathCmd = WURST.getCmds().pathCmd;
			pathFinder.renderPath(matrixStack, pathCmd.isDebugMode(),
				pathCmd.isDepthTest());
		}
		
		int black = 0x80000000;
		int gray = 0x26404040;
		int green1 = 0x2600FF00;
		int green2 = 0x4D00FF00;
		
		// area
		if(area != null)
		{
			// recently scanned blocks
			if(step == Step.SCAN_AREA && area.progress < 1)
			{
				ArrayList<AABB> boxes = new ArrayList<>();
				for(int i = Math.max(0, area.blocksList.size()
					- area.scanSpeed); i < area.blocksList.size(); i++)
					boxes.add(new AABB(area.blocksList.get(i)).inflate(0.005));
				
				RenderUtils.drawOutlinedBoxes(matrixStack, boxes, black, true);
				RenderUtils.drawSolidBoxes(matrixStack, boxes, green1, true);
			}
			
			// area box
			AABB areaBox = new AABB(area.minX, area.minY, area.minZ,
				area.minX + area.sizeX, area.minY + area.sizeY,
				area.minZ + area.sizeZ).deflate(1 / 16.0);
			RenderUtils.drawOutlinedBox(matrixStack, areaBox, black, true);
			
			// area scanner
			if(area.progress < 1)
			{
				double scannerX =
					Mth.lerp(area.progress, areaBox.minX, areaBox.maxX);
				AABB scanner = areaBox.setMinX(scannerX).setMaxX(scannerX);
				
				RenderUtils.drawOutlinedBox(matrixStack, scanner, black, true);
				RenderUtils.drawSolidBox(matrixStack, scanner, green2, true);
			}
		}
		
		// area preview
		if(area == null && step == Step.END_POS && step.pos != null)
		{
			AABB preview = AABB.encapsulatingFullBlocks(Step.START_POS.pos, Step.END_POS.pos)
				.deflate(1 / 16.0);
			RenderUtils.drawOutlinedBox(matrixStack, preview, black, true);
		}
		
		// selected positions
		ArrayList<AABB> selectedBoxes = new ArrayList<>();
		for(Step step : Step.SELECT_POSITION_STEPS)
			if(step.pos != null)
				selectedBoxes.add(new AABB(step.pos).deflate(1 / 16.0));
		RenderUtils.drawOutlinedBoxes(matrixStack, selectedBoxes, black, false);
		RenderUtils.drawSolidBoxes(matrixStack, selectedBoxes, green1, false);
		
		// posLookingAt
		if(posLookingAt != null)
		{
			AABB box = new AABB(posLookingAt).deflate(1 / 16.0);
			RenderUtils.drawOutlinedBox(matrixStack, box, black, false);
			RenderUtils.drawSolidBox(matrixStack, box, gray, false);
		}
		
		overlay.render(matrixStack, partialTicks, currentBlock);
	}
	
	@Override
	public void onRenderGUI(GuiGraphicsExtractor context, float partialTicks)
	{
		String message;
		if(step.selectPos && step.pos != null)
			message = "Press enter to confirm, or select a different position.";
		else
			message = step.message;
		
		Font tr = MC.font;
		int msgWidth = tr.width(message);
		
		int msgX1 = context.guiWidth() / 2 - msgWidth / 2;
		int msgX2 = msgX1 + msgWidth + 2;
		int msgY1 = context.guiHeight() / 2 + 1;
		int msgY2 = msgY1 + 10;
		
		// background
		context.fill(msgX1, msgY1, msgX2, msgY2, 0x80000000);
		
		// text
		context.text(tr, message, msgX1 + 2, msgY1 + 1, 0xFFFFFFFF, false);
	}
	
	public void enableWithArea(BlockPos pos1, BlockPos pos2)
	{
		setEnabled(true);
		Step.START_POS.pos = pos1;
		Step.END_POS.pos = pos2;
		step = Step.SCAN_AREA;
	}
	
	private void handlePositionSelection()
	{
		// continue with next step
		if(step.pos != null
			&& InputConstants.isKeyDown(MC.getWindow(), GLFW.GLFW_KEY_ENTER))
		{
			step = Step.values()[step.ordinal() + 1];
			
			// delete posLookingAt
			if(!step.selectPos)
				posLookingAt = null;
			
			return;
		}
		
		if(MC.hitResult instanceof BlockHitResult)
		{
			// set posLookingAt
			posLookingAt = ((BlockHitResult)MC.hitResult).getBlockPos();
			
			// offset if sneaking
			if(MC.options.keyShift.isDown())
				posLookingAt = posLookingAt
					.relative(((BlockHitResult)MC.hitResult).getDirection());
			
		}else
			posLookingAt = null;
		
		// set selected position
		if(posLookingAt != null && MC.options.keyUse.isDown())
			step.pos = posLookingAt;
	}
	
	private void scanArea()
	{
		// initialize area
		if(area == null)
		{
			area = new Area(Step.START_POS.pos, Step.END_POS.pos);
			Step.START_POS.pos = null;
			Step.END_POS.pos = null;
		}
		
		// scan area
		for(int i = 0; i < area.scanSpeed && area.iterator.hasNext(); i++)
		{
			area.scannedBlocks++;
			BlockPos pos = area.iterator.next();
			
			if(BlockUtils.canBeClicked(pos))
			{
				area.blocksList.add(pos);
				area.blocksSet.add(pos);
			}
		}
		
		// update progress
		area.progress = (float)area.scannedBlocks / (float)area.totalBlocks;
		
		// continue with next step
		if(!area.iterator.hasNext())
		{
			area.remainingBlocks = area.blocksList.size();
			step = Step.values()[step.ordinal() + 1];
		}
	}
	
	private void excavate()
	{
		// wait for AutoEat to finish eating
		if(WURST.getHax().autoEatHack.isEating())
			return;
		
		// prioritize the closest block from the top layer
		Vec3 eyesVec = RotationUtils.getEyesPos();
		Comparator<BlockPos> cNextTargetBlock =
			Comparator.<BlockPos> comparingInt(BlockPos::getY).reversed()
				.thenComparingDouble(pos -> pos.distToCenterSqr(eyesVec));
		
		// Find highest remaining breakable block in the entire area
		Predicate<BlockPos> pBreakable = MC.player.getAbilities().instabuild
			? BlockUtils::canBeClicked : pos -> BlockUtils.canBeClicked(pos)
				&& !BlockUtils.isUnbreakable(pos);
		
		int highestRemainingY = -1;
		for(BlockPos pos : area.blocksList)
		{
			if(pBreakable.test(pos))
			{
				if(pos.getY() > highestRemainingY)
					highestRemainingY = pos.getY();
			}
		}
		
		if(highestRemainingY == -1)
		{
			setEnabled(false);
			return;
		}
		
		int maxY = area.minY + area.sizeY;
		int chunkIndex = (maxY - highestRemainingY) / 3;
		int activeMinY = maxY - 3 * chunkIndex - 2;
		int activeMaxY = maxY - 3 * chunkIndex;
		
		if(lastActiveMinY != activeMinY)
		{
			pathFinder = null;
			processor = null;
			PathProcessor.releaseControls();
			lastActiveMinY = activeMinY;
		}
		
		// get valid blocks in the active 3-layer chunk
		ArrayList<BlockPos> validBlocks = getValidBlocks(activeMinY, activeMaxY);
		
		if (!validBlocks.isEmpty())
		{
			validBlocks.sort(cNextTargetBlock);
			MC.gameMode.stopDestroyBlock();
			overlay.resetProgress();
			
			BlockPos firstBlock = validBlocks.get(0);
			int bestSlot = getBestHotbarSlot(firstBlock);
			
			ArrayList<BlockPos> blocksToBreak = new ArrayList<>();
			blocksToBreak.add(firstBlock);
			
			for(int i = 1; i < validBlocks.size() && blocksToBreak.size() < maxBlocks.getValueI(); i++)
			{
				BlockPos pos = validBlocks.get(i);
				if(getBestHotbarSlot(pos) == bestSlot)
					blocksToBreak.add(pos);
			}
			
			if(bestSlot != -1)
				MC.player.getInventory().setSelectedSlot(bestSlot);
			
			currentBlock = firstBlock;
			BlockBreaker.breakBlocksWithPacketSpam(blocksToBreak);
		}
		else
		{
			currentBlock = null;
			MC.gameMode.stopDestroyBlock();
			overlay.resetProgress();
		}
		
		overlay.updateProgress();
		
		area.remainingBlocks =
			(int)area.blocksList.parallelStream().filter(pBreakable).count();
		
		if(area.remainingBlocks == 0)
		{
			setEnabled(false);
			return;
		}
		
		if(pathFinder == null)
		{
			BlockPos closestBlock = area.blocksList.parallelStream()
				.filter(pBreakable)
				.filter(pos -> pos.getY() >= activeMinY && pos.getY() <= activeMaxY)
				.min(cNextTargetBlock).orElse(null);
			
			if(closestBlock != null)
				pathFinder = new ExcavatorPathFinder(closestBlock);
		}
		
		// find path
		if(pathFinder != null && !pathFinder.isDone() && !pathFinder.isFailed())
		{
			PathProcessor.lockControls();
			
			pathFinder.think();
			
			if(!pathFinder.isDone() && !pathFinder.isFailed())
				return;
			
			pathFinder.formatPath();
			
			// set processor
			processor = pathFinder.getProcessor();
		}
		
		// check path
		if(processor != null
			&& !pathFinder.isPathStillValid(processor.getIndex()))
		{
			pathFinder = new ExcavatorPathFinder(pathFinder);
			return;
		}
		
		// process path
		if(processor != null)
		{
			processor.process();
			
			if(processor.isDone())
			{
				pathFinder = null;
				processor = null;
				PathProcessor.releaseControls();
			}
		}
	}
	
	private int getBestHotbarSlot(BlockPos pos)
	{
		BlockState state = MC.level.getBlockState(pos);
		net.minecraft.world.entity.player.Inventory inventory = MC.player.getInventory();
		
		float bestSpeed = inventory.getItem(inventory.getSelectedSlot()).getDestroySpeed(state);
		int bestSlot = inventory.getSelectedSlot();
		
		for(int slot = 0; slot < 9; slot++)
		{
			ItemStack stack = inventory.getItem(slot);
			float speed = stack.getDestroySpeed(state);
			if(speed > bestSpeed)
			{
				bestSpeed = speed;
				bestSlot = slot;
			}
		}
		
		if(bestSpeed <= 1.0F)
			return -1;
		
		return bestSlot;
	}
	
	private ArrayList<BlockPos> getValidBlocks(int activeMinY, int activeMaxY)
	{
		Vec3 eyesVec = RotationUtils.getEyesPos();
		BlockPos eyesBlock = BlockPos.containing(eyesVec);
		double rangeSq = Math.pow(range.getValue() + 0.5, 2);
		int blockRange = range.getValueCeil();
		
		return BlockUtils.getAllInBoxStream(eyesBlock, blockRange)
			.filter(pos -> pos.distToCenterSqr(eyesVec) <= rangeSq)
			.filter(area.blocksSet::contains)
			.filter(pos -> pos.getY() >= activeMinY && pos.getY() <= activeMaxY)
			.filter(BlockUtils::canBeClicked)
			.filter(pos -> !BlockUtils.isUnbreakable(pos))
			.sorted(Comparator
				.comparingDouble(pos -> pos.distToCenterSqr(eyesVec)))
			.collect(Collectors.toCollection(ArrayList::new));
	}
	
	private static enum Step
	{
		START_POS("Select start position.", true),
		
		END_POS("Select end position.", true),
		
		SCAN_AREA("Scanning area...", false),
		
		EXCAVATE("Excavating...", false);
		
		private static final Step[] SELECT_POSITION_STEPS =
			{START_POS, END_POS};
		
		private final String message;
		private boolean selectPos;
		
		private BlockPos pos;
		
		private Step(String message, boolean selectPos)
		{
			this.message = message;
			this.selectPos = selectPos;
		}
	}
	
	private static class Area
	{
		private final int minX, minY, minZ;
		private final int sizeX, sizeY, sizeZ;
		
		private final int totalBlocks, scanSpeed;
		private final Iterator<BlockPos> iterator;
		
		private int scannedBlocks, remainingBlocks;
		private float progress;
		
		private final ArrayList<BlockPos> blocksList = new ArrayList<>();
		private final HashSet<BlockPos> blocksSet = new HashSet<>();
		
		private Area(BlockPos start, BlockPos end)
		{
			int startX = start.getX();
			int startY = start.getY();
			int startZ = start.getZ();
			
			int endX = end.getX();
			int endY = end.getY();
			int endZ = end.getZ();
			
			minX = Math.min(startX, endX);
			minY = Math.min(startY, endY);
			minZ = Math.min(startZ, endZ);
			
			sizeX = Math.abs(startX - endX);
			sizeY = Math.abs(startY - endY);
			sizeZ = Math.abs(startZ - endZ);
			
			totalBlocks = (sizeX + 1) * (sizeY + 1) * (sizeZ + 1);
			scanSpeed = Mth.clamp(totalBlocks / 30, 1, 16384);
			iterator = BlockUtils.getAllInBox(start, end).iterator();
		}
	}
	
	private static class ExcavatorPathFinder extends PathFinder
	{
		public ExcavatorPathFinder(BlockPos goal)
		{
			super(goal);
			setThinkTime(10);
		}
		
		public ExcavatorPathFinder(ExcavatorPathFinder pathFinder)
		{
			super(pathFinder);
		}
		
		@Override
		protected boolean checkDone()
		{
			BlockPos goal = getGoal();
			
			return done = goal.below(2).equals(current)
				|| goal.above().equals(current) || goal.north().equals(current)
				|| goal.south().equals(current) || goal.west().equals(current)
				|| goal.east().equals(current)
				|| goal.below().north().equals(current)
				|| goal.below().south().equals(current)
				|| goal.below().west().equals(current)
				|| goal.below().east().equals(current);
		}
	}
}
