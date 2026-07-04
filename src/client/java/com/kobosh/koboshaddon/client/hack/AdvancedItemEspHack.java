/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package com.kobosh.koboshaddon.client.hack;

import java.awt.Color;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import com.mojang.blaze3d.vertex.PoseStack;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.RenderListener;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.ColorSetting;
import net.wurstclient.settings.ItemListSetting;
import net.wurstclient.util.ChatUtils;
import net.wurstclient.util.RenderUtils;

@SearchTags({"advanced item esp", "item esp", "loot esp"})
public final class AdvancedItemEspHack extends Hack
	implements RenderListener, UpdateListener
{
	private final ItemListSetting items = new ItemListSetting("Items",
		"Items that will be highlighted.",
		"minecraft:elytra", "minecraft:totem_of_undying", "minecraft:netherite_ingot", "minecraft:diamond_block");

	private final CheckboxSetting tracers =
		new CheckboxSetting("Tracers", "Render tracers to matching items.", true);

	private final CheckboxSetting chatFeedback = new CheckboxSetting(
		"Chat feedback", "Print chat when a matching item is first seen.", true);

	private final ColorSetting color = new ColorSetting("Color",
		"Color used for item boxes and tracers.", new Color(255, 25, 255));
	private final Set<Integer> seenEntities = new HashSet<>();
	private int count;

	public AdvancedItemEspHack()
	{
		super("AdvancedItemESP");
		setCategory(Category.RENDER);
		addSetting(items);
		addSetting(tracers);
		addSetting(chatFeedback);
		addSetting(color);
	}

	@Override
	protected void onEnable()
	{
		seenEntities.clear();
		EVENTS.add(RenderListener.class, this);
		EVENTS.add(UpdateListener.class, this);
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(RenderListener.class, this);
		EVENTS.remove(UpdateListener.class, this);
		seenEntities.clear();
		count = 0;
	}

	@Override
	public void onUpdate()
	{
		if(MC.level == null)
			return;
		seenEntities.removeIf(id -> MC.level.getEntity(id) == null);
	}

	@Override
	public void onRender(PoseStack matrixStack, float partialTicks)
	{
		if(MC.level == null)
			return;

		count = 0;
		for(Entity entity : MC.level.entitiesForRendering())
		{
			if(!(entity instanceof ItemEntity itemEntity))
				continue;

			Item item = itemEntity.getItem().getItem();
			Identifier id = BuiltInRegistries.ITEM.getKey(item);
			if(id == null || !items.getItemNames().contains(id.toString()))
				continue;

			if(chatFeedback.isChecked() && seenEntities.add(entity.getId()))
				ChatUtils.message(itemEntity.getItem().getHoverName().getString() + " found at "
					+ entity.getBlockX() + ", " + entity.getBlockY() + ", "
					+ entity.getBlockZ());

			AABB box = entity.getBoundingBox();
			int outline = color.getColorI(0xA0);
			int fill = color.getColorI(0x30);
			RenderUtils.drawOutlinedBox(matrixStack, box, outline, false);
			RenderUtils.drawSolidBox(matrixStack, box, fill, false);

			if(tracers.isChecked())
			{
				Vec3 center = box.getCenter();
				RenderUtils.drawTracer(matrixStack, partialTicks, center, outline,
					false);
			}

			count++;
		}
	}

	@Override
	public String getRenderName()
	{
		return isEnabled() ? getName() + " [" + count + "]" : getName();
	}


}
