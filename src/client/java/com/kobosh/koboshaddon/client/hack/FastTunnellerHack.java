package com.kobosh.koboshaddon.client.hack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.lwjgl.glfw.GLFW;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Options;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.WurstClient;
import net.wurstclient.events.GUIRenderListener;
import net.wurstclient.events.RenderListener;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.hack.HackList;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.EnumSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.util.BlockBreaker;
import net.wurstclient.util.BlockUtils;
import net.wurstclient.util.ChatUtils;
import net.wurstclient.util.OverlayRenderer;
import net.wurstclient.util.RenderUtils;
import net.wurstclient.util.RotationUtils;

@SearchTags({"fast tunneller", "speed tunneller", "instant tunneller"})
public final class FastTunnellerHack extends Hack
	implements UpdateListener, RenderListener, GUIRenderListener
{
	private final EnumSetting<TunnelSize> size = new EnumSetting<>(
		"Tunnel size", TunnelSize.values(), TunnelSize.SIZE_3X3);
	
	private final SliderSetting limit = new SliderSetting("Limit",
		"Automatically stops once the tunnel has reached the given length.\n\n"
			+ "0 = no limit",
		0, 0, 1000, 1, ValueDisplay.INTEGER.withSuffix(" blocks")
			.withLabel(1, "1 block").withLabel(0, "disabled"));

	private final SliderSetting maxBlocks =
		new SliderSetting("Max blocks", "Maximum blocks to break per tick.", 5, 2, 10, 1, ValueDisplay.INTEGER);

	private final CheckboxSetting restoreHacks = new CheckboxSetting("Restore hacks",
		"Re-enable conflicting hacks when FastTunneller is disabled.", true);

	private BlockPos start;
	private Direction direction;
	private int length;
	private BlockPos currentBlock;
	private final OverlayRenderer overlay = new OverlayRenderer();
	private final List<Hack> previouslyEnabledHacks = new ArrayList<>();

	public FastTunnellerHack()
	{
		super("FastTunneller");
		setCategory(Category.BLOCKS);
		addSetting(size);
		addSetting(limit);
		addSetting(maxBlocks);
		addSetting(restoreHacks);
	}

	@Override
	public String getRenderName()
	{
		if(limit.getValueI() == 0)
			return getName();
		return getName() + " [" + length + "/" + limit.getValueI() + "]";
	}

	@Override
	protected void onEnable()
	{
		// Disable conflicting hacks
		previouslyEnabledHacks.clear();
		HackList hax = WURST.getHax();
		Hack[] incompatibleHax = {
			hax.autoMineHack, hax.excavatorHack, hax.fightBotHack,
			hax.followHack, hax.instantBunkerHack, hax.nukerHack,
			hax.nukerLegitHack, hax.protectHack, hax.speedNukerHack,
			hax.veinMinerHack, hax.autoSwitchHack, hax.autoToolHack,
			hax.autoWalkHack, hax.blinkHack, hax.flightHack,
			hax.scaffoldWalkHack, hax.sneakHack
		};
		for(Hack hack : incompatibleHax)
		{
			if(hack.isEnabled())
			{
				previouslyEnabledHacks.add(hack);
				hack.setEnabled(false);
			}
		}

		start = BlockPos.containing(MC.player.position());
		direction = MC.player.getDirection();
		length = 0;
		currentBlock = null;

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

		overlay.resetProgress();
		if(currentBlock != null)
		{
			MC.gameMode.stopDestroyBlock();
			currentBlock = null;
		}

		if(restoreHacks.isChecked())
		{
			for(Hack hack : previouslyEnabledHacks)
			{
				hack.setEnabled(true);
			}
		}
		previouslyEnabledHacks.clear();
	}

	@Override
	public void onUpdate()
	{
		if(MC.player == null || MC.level == null)
			return;

		// Suppress player movements
		Options gs = MC.options;
		KeyMapping[] bindings = {gs.keyUp, gs.keyDown, gs.keyLeft, gs.keyRight,
			gs.keyJump, gs.keyShift};
		for(KeyMapping binding : bindings)
			binding.setDown(false);

		BlockPos base = start.relative(direction, length);
		TunnelSize selectedSize = size.getSelected();

		// Collect all blocks in the 3x3x2 chunk (at depth length and length + 1)
		ArrayList<BlockPos> chunkBlocks = new ArrayList<>();
		chunkBlocks.addAll(getSliceBlocks(base, selectedSize));
		chunkBlocks.addAll(getSliceBlocks(base.relative(direction, 1), selectedSize));

		ArrayList<BlockPos> remainingBlocks = new ArrayList<>();
		for(BlockPos pos : chunkBlocks)
		{
			if(BlockUtils.canBeClicked(pos) && !BlockUtils.isUnbreakable(pos))
			{
				remainingBlocks.add(pos);
			}
		}

		// Sort blocks: top-to-bottom, closest first
		Vec3 eyesVec = RotationUtils.getEyesPos();
		Comparator<BlockPos> cNextTargetBlock =
			Comparator.<BlockPos> comparingInt(BlockPos::getY).reversed()
				.thenComparingDouble(pos -> pos.distToCenterSqr(eyesVec));

		if(remainingBlocks.isEmpty())
		{
			// Current 2-block forward chunk is cleared, move forward!
			length += 2;
			if(limit.getValueI() > 0 && length >= limit.getValueI())
			{
				ChatUtils.message("Tunnel completed.");
				setEnabled(false);
			}
			currentBlock = null;
			overlay.resetProgress();
			return;
		}

		remainingBlocks.sort(cNextTargetBlock);

		// Group blocks by the best tool slot of the first block
		BlockPos firstBlock = remainingBlocks.get(0);
		int bestSlot = getBestHotbarSlot(firstBlock);

		ArrayList<BlockPos> blocksToBreak = new ArrayList<>();
		blocksToBreak.add(firstBlock);

		for(int i = 1; i < remainingBlocks.size() && blocksToBreak.size() < maxBlocks.getValueI(); i++)
		{
			BlockPos pos = remainingBlocks.get(i);
			if(getBestHotbarSlot(pos) == bestSlot)
				blocksToBreak.add(pos);
		}

		if(bestSlot != -1)
			MC.player.getInventory().setSelectedSlot(bestSlot);

		currentBlock = firstBlock;
		BlockBreaker.breakBlocksWithPacketSpam(blocksToBreak);
		overlay.updateProgress();

		// Move player forward to the appropriate standing position
		BlockPos standingPos = length == 0 ? start : start.relative(direction, length - 1);
		double dist = MC.player.position().distanceToSqr(Vec3.atCenterOf(standingPos));
		if(dist > 0.5)
		{
			Vec3 vec = Vec3.atCenterOf(standingPos);
			WURST.getRotationFaker().faceVectorClientIgnorePitch(vec);
			MC.options.keyUp.setDown(true);
		}
	}

	@Override
	public void onRender(PoseStack matrixStack, float partialTicks)
	{
		if(start == null || direction == null)
			return;

		int black = 0x80000000;
		int green = 0x2600FF00;

		BlockPos base = start.relative(direction, length);
		TunnelSize selectedSize = size.getSelected();

		ArrayList<BlockPos> chunkBlocks = new ArrayList<>();
		chunkBlocks.addAll(getSliceBlocks(base, selectedSize));
		chunkBlocks.addAll(getSliceBlocks(base.relative(direction, 1), selectedSize));

		ArrayList<AABB> boxes = new ArrayList<>();
		for(BlockPos pos : chunkBlocks)
		{
			if(BlockUtils.canBeClicked(pos) && !BlockUtils.isUnbreakable(pos))
				boxes.add(new AABB(pos).inflate(0.005));
		}

		RenderUtils.drawOutlinedBoxes(matrixStack, boxes, black, true);
		RenderUtils.drawSolidBoxes(matrixStack, boxes, green, true);

		overlay.render(matrixStack, partialTicks, currentBlock);
	}

	@Override
	public void onRenderGUI(GuiGraphicsExtractor context, float partialTicks)
	{
		String message = "FastTunneller length: " + length;
		if(limit.getValueI() > 0)
			message += " / " + limit.getValueI();

		net.minecraft.client.gui.Font tr = MC.font;
		int msgWidth = tr.width(message);
		int msgX1 = context.guiWidth() / 2 - msgWidth / 2;
		int msgX2 = msgX1 + msgWidth + 2;
		int msgY1 = context.guiHeight() / 2 + 1;
		int msgY2 = msgY1 + 10;

		context.fill(msgX1, msgY1, msgX2, msgY2, 0x80000000);
		context.text(tr, message, msgX1 + 2, msgY1 + 1, 0xFFFFFFFF, false);
	}

	private ArrayList<BlockPos> getSliceBlocks(BlockPos basePos, TunnelSize selectedSize)
	{
		ArrayList<BlockPos> slice = new ArrayList<>();
		Vec3i from = selectedSize.from;
		Vec3i to = selectedSize.to;

		int minX = Math.min(to.getX(), from.getX());
		int maxX = Math.max(to.getX(), from.getX());
		int minY = Math.min(to.getY(), from.getY());
		int maxY = Math.max(to.getY(), from.getY());

		for(int x = minX; x <= maxX; x++)
		{
			for(int y = minY; y <= maxY; y++)
			{
				BlockPos pos = basePos.relative(direction.getCounterClockWise(), x).above(y);
				slice.add(pos);
			}
		}
		return slice;
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

	public enum TunnelSize
	{
		SIZE_1X2("1x2", new Vec3i(0, 1, 0), new Vec3i(0, 0, 0)),
		SIZE_1X3("1x3", new Vec3i(0, 2, 0), new Vec3i(0, 0, 0)),
		SIZE_1X4("1x4", new Vec3i(0, 3, 0), new Vec3i(0, 0, 0)),
		SIZE_1X5("1x5", new Vec3i(0, 4, 0), new Vec3i(0, 0, 0)),
		
		SIZE_2X2("2x2", new Vec3i(1, 1, 0), new Vec3i(0, 0, 0)),
		SIZE_2X3("2x3", new Vec3i(1, 2, 0), new Vec3i(0, 0, 0)),
		SIZE_2X4("2x4", new Vec3i(1, 3, 0), new Vec3i(0, 0, 0)),
		SIZE_2X5("2x5", new Vec3i(1, 4, 0), new Vec3i(0, 0, 0)),
		
		SIZE_3X2("3x2", new Vec3i(1, 1, 0), new Vec3i(-1, 0, 0)),
		SIZE_3X3("3x3", new Vec3i(1, 2, 0), new Vec3i(-1, 0, 0)),
		SIZE_3X4("3x4", new Vec3i(1, 3, 0), new Vec3i(-1, 0, 0)),
		SIZE_3X5("3x5", new Vec3i(1, 4, 0), new Vec3i(-1, 0, 0)),
		
		SIZE_4X2("4x2", new Vec3i(2, 1, 0), new Vec3i(-1, 0, 0)),
		SIZE_4X3("4x3", new Vec3i(2, 2, 0), new Vec3i(-1, 0, 0)),
		SIZE_4X4("4x4", new Vec3i(2, 3, 0), new Vec3i(-1, 0, 0)),
		SIZE_4X5("4x5", new Vec3i(2, 4, 0), new Vec3i(-1, 0, 0)),
		
		SIZE_5X2("5x2", new Vec3i(2, 1, 0), new Vec3i(-2, 0, 0)),
		SIZE_5X3("5x3", new Vec3i(2, 2, 0), new Vec3i(-2, 0, 0)),
		SIZE_5X4("5x4", new Vec3i(2, 3, 0), new Vec3i(-2, 0, 0)),
		SIZE_5X5("5x5", new Vec3i(2, 4, 0), new Vec3i(-2, 0, 0));
		
		private final String name;
		private final Vec3i from;
		private final Vec3i to;
		
		private TunnelSize(String name, Vec3i from, Vec3i to)
		{
			this.name = name;
			this.from = from;
			this.to = to;
		}
		
		@Override
		public String toString()
		{
			return name;
		}
	}
}
