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

import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.client.util.math.MatrixStack;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.RenderListener;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.ColorSetting;
import net.wurstclient.settings.TextFieldSetting;
import net.wurstclient.util.ChatUtils;
import net.wurstclient.util.RenderUtils;

@SearchTags({"advanced item esp", "item esp", "loot esp"})
public final class AdvancedItemEspHack extends Hack
	implements RenderListener, UpdateListener
{
	private final TextFieldSetting items = new TextFieldSetting("Items",
		"Comma-separated item IDs.",
		"minecraft:elytra,minecraft:totem_of_undying,minecraft:netherite_ingot,minecraft:diamond_block");

	private final CheckboxSetting tracers =
		new CheckboxSetting("Tracers", "Render tracers to matching items.", true);

	private final CheckboxSetting chatFeedback = new CheckboxSetting(
		"Chat feedback", "Print chat when a matching item is first seen.", true);

	private final ColorSetting color = new ColorSetting("Color",
		"Color used for item boxes and tracers.", new Color(255, 25, 255));

	private final Set<String> parsedItems = new HashSet<>();
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
		parseItems();
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
		parseItems();
		if(MC.world == null)
			return;
		seenEntities.removeIf(id -> MC.world.getEntityById(id) == null);
	}

	@Override
	public void onRender(MatrixStack matrixStack, float partialTicks)
	{
		if(MC.world == null)
			return;

		count = 0;
		for(Entity entity : MC.world.getEntities())
		{
			if(!(entity instanceof ItemEntity itemEntity))
				continue;

			Item item = itemEntity.getStack().getItem();
			Identifier id = Registries.ITEM.getId(item);
			if(id == null || !parsedItems.contains(id.toString()))
				continue;

			if(chatFeedback.isChecked() && seenEntities.add(entity.getId()))
				ChatUtils.message(itemEntity.getStack().getName().getString() + " found at "
					+ entity.getBlockX() + ", " + entity.getBlockY() + ", "
					+ entity.getBlockZ());

			Box box = entity.getBoundingBox();
			int outline = color.getColorI(0xA0);
			int fill = color.getColorI(0x30);
			RenderUtils.drawOutlinedBox(matrixStack, box, outline, false);
			RenderUtils.drawSolidBox(matrixStack, box, fill, false);

			if(tracers.isChecked())
			{
				Vec3d center = box.getCenter();
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

	private void parseItems()
	{
		parsedItems.clear();
		Arrays.stream(items.getValue().split(",")).map(String::trim)
			.filter(s -> !s.isEmpty()).forEach(parsedItems::add);
	}
}
