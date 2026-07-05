package com.kobosh.koboshaddon.client.hack;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.RenderListener;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.BlockListSetting;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.ColorSetting;
import net.wurstclient.settings.EnumSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.util.BlockUtils;
import net.wurstclient.util.InventoryUtils;
import net.wurstclient.util.RenderUtils;
import net.wurstclient.util.RotationUtils;

@SearchTags({"grid place", "torch grid", "layout place"})
public final class GridHack extends Hack implements UpdateListener, RenderListener
{
	private final EnumSetting<FilterMode> filterMode = new EnumSetting<>("Filter Mode",
		"Filter placements based on staggered layout.", FilterMode.values(), FilterMode.StaggeredOdd);

	private final BlockListSetting blocksSetting = new BlockListSetting("Blocks",
		"Blocks to place on the grid.", "minecraft:torch", "minecraft:wall_torch");

	private final CheckboxSetting place = new CheckboxSetting("Place",
		"Automatically place blocks on grid positions.", true);

	private final CheckboxSetting dynamicHeight = new CheckboxSetting("Dynamic Height",
		"Attach grid placements to the world surface height.", true);

	private final SliderSetting gridSize = new SliderSetting("Grid Size",
		"Radius of grid elements around the center.", 2, 1, 5, 1, ValueDisplay.INTEGER);

	private final SliderSetting gridGap = new SliderSetting("Grid Gap",
		"Distance between grid placements.", 13, 1, 30, 1, ValueDisplay.INTEGER);

	private final SliderSetting switchRange = new SliderSetting("Switch Range",
		"Distance to search for existing blocks to center the grid.", 3, 1, 10, 1, ValueDisplay.INTEGER);

	private final SliderSetting placeRange = new SliderSetting("Place Range",
		"Maximum range to place blocks.", 4.25, 1, 6, 0.05, ValueDisplay.DECIMAL);

	private final CheckboxSetting show = new CheckboxSetting("Show",
		"Render grid boxes.", true);

	private final ColorSetting mainColor = new ColorSetting("Center Color",
		"Color for the active grid center.", java.awt.Color.GREEN);

	private final ColorSetting gridColor = new ColorSetting("Grid Color",
		"Color for outer grid elements.", java.awt.Color.YELLOW);

	private BlockPos centerPos = null;
	private final List<BlockPos> placements = new ArrayList<>();

	public GridHack()
	{
		super("Grid");
		setCategory(Category.BLOCKS);
		addSetting(filterMode);
		addSetting(blocksSetting);
		addSetting(place);
		addSetting(dynamicHeight);
		addSetting(gridSize);
		addSetting(gridGap);
		addSetting(switchRange);
		addSetting(placeRange);
		addSetting(show);
		addSetting(mainColor);
		addSetting(gridColor);
	}

	@Override
	protected void onEnable()
	{
		EVENTS.add(UpdateListener.class, this);
		EVENTS.add(RenderListener.class, this);
		centerPos = null;
		placements.clear();
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		EVENTS.remove(RenderListener.class, this);
	}

	@Override
	public void onUpdate()
	{
		if(MC.player == null || MC.level == null)
			return;

		BlockPos nearPos = findNearest();
		if(nearPos != null)
			centerPos = nearPos;

		if(centerPos == null)
			return;

		placements.clear();

		int size = (int)gridSize.getValue();
		int gap = (int)gridGap.getValue();

		for(int x = -size; x <= size; x++)
		{
			for(int z = -size; z <= size; z++)
			{
				if(x == 0 && z == 0)
					continue;

				if(filterMode.getSelected() == FilterMode.StaggeredEven && (x & 1) == (z & 1))
					continue;
				if(filterMode.getSelected() == FilterMode.StaggeredOdd && (x & 1) != (z & 1))
					continue;

				BlockPos pos = centerPos.offset(x * gap, 0, z * gap);
				if(dynamicHeight.isChecked())
					pos = getHeight(pos);

				placements.add(pos);
			}
		}

		if(place.isChecked())
		{
			for(BlockPos pos : placements)
			{
				if(MC.level.getBlockState(pos).canBeReplaced() &&
				   pos.closerThan(MC.player.blockPosition(), placeRange.getValue()))
				{
					// Find any matching block in hotbar
					for(String name : blocksSetting.getBlockNames())
					{
						Block block = net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(
							net.minecraft.resources.Identifier.parse(name))
							.map(net.minecraft.core.Holder::value)
							.orElse(null);
						if(block != null && block != Blocks.AIR)
						{
							int slot = findItemInHotbar(block);
							if(slot != -1)
							{
								if(placeBlock(pos, slot))
									break;
							}
						}
					}
				}
			}
		}
	}

	private BlockPos findNearest()
	{
		int r = (int)switchRange.getValue();
		BlockPos playerPos = MC.player.blockPosition();
		for(BlockPos pos : BlockPos.withinManhattan(playerPos, r, r, r))
		{
			Block block = MC.level.getBlockState(pos).getBlock();
			if(blocksSetting.contains(block))
				return pos;
		}
		return null;
	}

	private BlockPos getHeight(BlockPos blockPos)
	{
		BlockPos pos = blockPos;
		while(!MC.level.getBlockState(pos).canBeReplaced() &&
			  pos.getY() < MC.level.getHeight(Heightmap.Types.WORLD_SURFACE, pos.getX(), pos.getZ()))
		{
			pos = pos.above(1);
		}

		while(MC.level.getBlockState(pos.below(1)).canBeReplaced() &&
			  pos.getY() > MC.level.getMinY())
		{
			pos = pos.below(1);
		}

		return pos;
	}

	private int findItemInHotbar(Block block)
	{
		Inventory inventory = MC.player.getInventory();
		for(int i = 0; i < 9; i++)
		{
			ItemStack stack = inventory.getItem(i);
			if(stack.getItem() == block.asItem())
				return i;
		}
		return -1;
	}

	private boolean placeBlock(BlockPos pos, int itemSlot)
	{
		Vec3 eyesPos = RotationUtils.getEyesPos();

		for(Direction side : Direction.values())
		{
			BlockPos neighbor = pos.relative(side);
			Direction side2 = side.getOpposite();

			if(eyesPos.distanceToSqr(Vec3.atCenterOf(pos)) >= eyesPos
				.distanceToSqr(Vec3.atCenterOf(neighbor)))
				continue;

			if(!BlockUtils.canBeClicked(neighbor))
				continue;

			Vec3 hitVec = Vec3.atCenterOf(neighbor)
				.add(Vec3.atLowerCornerOf(side2.getUnitVec3i()).scale(0.5));

			if(eyesPos.distanceToSqr(hitVec) > 18.0625)
				continue;

			int prev = MC.player.getInventory().getSelectedSlot();
			InventoryUtils.selectItem(itemSlot);

			RotationUtils.getNeededRotations(hitVec).sendPlayerLookPacket();
			IMC.getInteractionManager().rightClickBlock(neighbor, side2, hitVec);
			MC.player.swing(InteractionHand.MAIN_HAND);
			MC.rightClickDelay = 4;

			InventoryUtils.selectItem(prev);
			return true;
		}
		return false;
	}

	@Override
	public void onRender(PoseStack matrixStack, float partialTicks)
	{
		if(!show.isChecked() || centerPos == null)
			return;

		List<AABB> centerList = List.of(new AABB(centerPos));
		int cColorQuads = mainColor.getColorI() | 0x40000000;
		int cColorLines = mainColor.getColorI() | 0x80000000;
		RenderUtils.drawSolidBoxes(matrixStack, centerList, cColorQuads, false);
		RenderUtils.drawOutlinedBoxes(matrixStack, centerList, cColorLines, false);

		List<AABB> outerList = new ArrayList<>();
		for(BlockPos pos : placements)
		{
			outerList.add(new AABB(pos));
		}
		if(!outerList.isEmpty())
		{
			int gColorQuads = gridColor.getColorI() | 0x40000000;
			int gColorLines = gridColor.getColorI() | 0x80000000;
			RenderUtils.drawSolidBoxes(matrixStack, outerList, gColorQuads, false);
			RenderUtils.drawOutlinedBoxes(matrixStack, outerList, gColorLines, false);
		}
	}

	public enum FilterMode
	{
		None("None"),
		StaggeredEven("Even"),
		StaggeredOdd("Odd");

		private final String name;

		private FilterMode(String name)
		{
			this.name = name;
		}

		@Override
		public String toString()
		{
			return name;
		}
	}
}
