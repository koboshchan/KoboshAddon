package com.kobosh.koboshaddon.client.hack;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.TrialSpawnerBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.TrialSpawnerBlockEntity;
import net.minecraft.world.level.block.entity.trialspawner.TrialSpawnerState;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.CameraTransformViewBobbingListener;
import net.wurstclient.events.RenderListener;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.ColorSetting;
import net.wurstclient.settings.EspStyleSetting;
import net.wurstclient.util.RenderUtils;
import net.wurstclient.util.chunk.ChunkUtils;

@SearchTags({"trial spawner esp", "trial spawner finder", "spawner finder"})
public final class TrialSpawnerEspHack extends Hack
	implements UpdateListener, CameraTransformViewBobbingListener, RenderListener
{
	private final EspStyleSetting style = new EspStyleSetting();
	
	private final ColorSetting color = new ColorSetting("Color",
		"Color of ESP overlays around trial spawners.", Color.ORANGE);
	
	private final CheckboxSetting ignoreCooldown = new CheckboxSetting(
		"Ignore cooldown",
		"Whether to ignore trial spawners that are currently in cooldown.",
		true);
	
	private final List<BlockPos> spawnerPoses = new ArrayList<>();
	
	public TrialSpawnerEspHack()
	{
		super("TrialSpawnerESP");
		setCategory(Category.RENDER);
		addSetting(style);
		addSetting(color);
		addSetting(ignoreCooldown);
	}
	
	@Override
	public void onEnable()
	{
		EVENTS.add(UpdateListener.class, this);
		EVENTS.add(CameraTransformViewBobbingListener.class, this);
		EVENTS.add(RenderListener.class, this);
	}
	
	@Override
	public void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		EVENTS.remove(CameraTransformViewBobbingListener.class, this);
		EVENTS.remove(RenderListener.class, this);
		
		spawnerPoses.clear();
	}
	
	@Override
	public void onCameraTransformViewBobbing(
		CameraTransformViewBobbingEvent event)
	{
		if(style.hasLines())
			event.cancel();
	}
	
	@Override
	public void onUpdate()
	{
		spawnerPoses.clear();
		ChunkUtils.getLoadedBlockEntities().forEach(this::checkSpawner);
	}
	
	private void checkSpawner(BlockEntity be)
	{
		if(!(be instanceof TrialSpawnerBlockEntity))
			return;
		
		BlockPos pos = be.getBlockPos();
		var state = MC.level.getBlockState(pos);
		
		if(!state.is(Blocks.TRIAL_SPAWNER))
			return;
		
		if(ignoreCooldown.isChecked())
		{
			TrialSpawnerState spawnerState =
				state.getValue(TrialSpawnerBlock.STATE);
			if(spawnerState == TrialSpawnerState.COOLDOWN)
				return;
		}
		
		spawnerPoses.add(pos);
	}
	
	@Override
	public void onRender(PoseStack matrixStack, float partialTicks)
	{
		if(spawnerPoses.isEmpty())
			return;
		
		List<AABB> boxes = new ArrayList<>();
		for(BlockPos pos : spawnerPoses)
			boxes.add(new AABB(pos));
		
		if(style.hasBoxes())
		{
			int quadsColor = color.getColorI(0x40);
			int linesColor = color.getColorI(0x80);
			RenderUtils.drawSolidBoxes(matrixStack, boxes, quadsColor, false);
			RenderUtils.drawOutlinedBoxes(matrixStack, boxes, linesColor,
				false);
		}
		
		if(style.hasLines())
		{
			int tracerColor = color.getColorI(0x80);
			RenderUtils.drawTracers(matrixStack, partialTicks,
				boxes.stream().map(AABB::getCenter).toList(), tracerColor,
				false);
		}
	}
}
