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

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.RenderListener;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.ColorSetting;
import net.wurstclient.settings.EspStyleSetting;
import net.wurstclient.util.ChatUtils;
import net.wurstclient.util.RenderUtils;
import net.wurstclient.util.chunk.ChunkUtils;

@SearchTags({"spawner player esp", "spawner esp", "player spawner",
	"activated spawner"})
public final class SpawnerPlayerEspHack extends Hack
	implements UpdateListener, RenderListener
{
	private final EspStyleSetting style = new EspStyleSetting();
	
	private final ColorSetting color = new ColorSetting("Color",
		"Color of the ESP boxes around activated spawners.", Color.RED);
	
	private final List<BlockPos> activatedSpawners = new ArrayList<>();
	private final Set<BlockPos> reportedSpawners = new HashSet<>();
	
	public SpawnerPlayerEspHack()
	{
		super("SpawnerPlayerESP");
		setCategory(Category.RENDER);
		addSetting(style);
		addSetting(color);
	}
	
	@Override
	protected void onEnable()
	{
		EVENTS.add(UpdateListener.class, this);
		EVENTS.add(RenderListener.class, this);
		
		// Clear reported spawners when enabling
		reportedSpawners.clear();
	}
	
	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		EVENTS.remove(RenderListener.class, this);
		
		// Clear data when disabling
		activatedSpawners.clear();
		reportedSpawners.clear();
	}
	
	@Override
	public void onUpdate()
	{
		activatedSpawners.clear();
		
		// Check all loaded block entities for activated spawners
		ChunkUtils.getLoadedBlockEntities().forEach(this::checkSpawner);
	}
	
	private void checkSpawner(BlockEntity blockEntity)
	{
		// Only check spawner block entities
		if(!(blockEntity instanceof SpawnerBlockEntity))
			return;
		
		BlockPos pos = blockEntity.getBlockPos();
		
		// Verify it's actually a spawner block (extra safety check)
		if(!MC.level.getBlockState(pos).is(Blocks.SPAWNER))
			return;
		
		try
		{
			// Get NBT data from the spawner
			CompoundTag nbt =
				blockEntity.saveWithFullMetadata(MC.level.registryAccess());
			
			if(nbt == null || nbt.isEmpty())
				return;
			
			// Check if spawner has Delay field
			if(!nbt.contains("Delay"))
				return;
			
			// Get the current delay value
			short delay = nbt.getShort("Delay").orElse((short)0);
			
			// Normal spawner delay is 20 ticks (20s in NBT)
			// If it's not 20, a player has activated it
			if(delay != 20)
			{
				activatedSpawners.add(pos);
				
				// Report in chat if not already reported
				if(!reportedSpawners.contains(pos))
				{
					reportedSpawners.add(pos);
					String mobType = "Unknown";
					
					// Try to get mob type from SpawnData
					if(nbt.contains("SpawnData"))
					{
						CompoundTag spawnData = nbt.getCompound("SpawnData").orElse(null);
						if(spawnData != null && spawnData.contains("entity"))
						{
							CompoundTag entity =
								spawnData.getCompound("entity").orElse(null);
							if(entity != null && entity.contains("id"))
							{
								mobType = entity.getString("id")
									.orElse("").replace("minecraft:", "");
							}
						}
					}
					
					ChatUtils.message("Activated spawner detected! " + "Pos: "
						+ pos.toShortString() + " | Mob: " + mobType
						+ " | Delay: " + delay + " (normal: 20)");
				}
			}else
			{
				// If delay is back to normal, remove from reported list
				reportedSpawners.remove(pos);
			}
			
		}catch(Exception e)
		{
			// Silently ignore errors reading NBT data
		}
	}
	
	@Override
	public void onRender(PoseStack matrixStack, float partialTicks)
	{
		if(activatedSpawners.isEmpty())
			return;
		
		// Create boxes for all activated spawners
		List<AABB> boxes = new ArrayList<>();
		for(BlockPos pos : activatedSpawners)
		{
			boxes.add(new AABB(pos));
		}
		
		// Render boxes using ChestESP style
		if(style.hasBoxes())
		{
			int quadsColor = color.getColorI(0x40);
			int linesColor = color.getColorI(0x80);
			
			RenderUtils.drawSolidBoxes(matrixStack, boxes, quadsColor, false);
			RenderUtils.drawOutlinedBoxes(matrixStack, boxes, linesColor,
				false);
		}
		
		// Render tracers if enabled
		if(style.hasLines())
		{
			List<net.minecraft.world.phys.Vec3> centers =
				boxes.stream().map(AABB::getCenter).toList();
			
			int tracerColor = color.getColorI(0x80);
			RenderUtils.drawTracers(matrixStack, partialTicks, centers,
				tracerColor, false);
		}
	}
	
	@Override
	public String getRenderName()
	{
		String name = getName();
		
		if(isEnabled() && !activatedSpawners.isEmpty())
		{
			name += " [" + activatedSpawners.size() + "]";
		}
		
		return name;
	}
}
