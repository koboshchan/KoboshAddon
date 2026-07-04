/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package com.kobosh.koboshaddon.client.hack;

import net.minecraft.world.level.block.Blocks;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.RenderListener;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.ColorSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.util.ChatUtils;
import net.wurstclient.util.RenderUtils;

import java.awt.Color;

@SearchTags({"teleport", "click tp", "tp"})
public final class TeleportHack extends Hack
	implements UpdateListener, RenderListener
{
	private final SliderSetting reach =
		new SliderSetting("Reach", "Raycast range.", 48, 8, 96, 1,
			SliderSetting.ValueDisplay.DECIMAL);

	private final CheckboxSetting includeLiquids = new CheckboxSetting(
		"TP onto liquids", "Allow teleporting onto liquid blocks.", true);

	private final ColorSetting solidColor = new ColorSetting("Solid color",
		"Preview color when target block is solid.", new Color(255, 0, 255));

	private final ColorSetting passableColor = new ColorSetting("Passable color",
		"Preview color when target block is passable.", new Color(0, 255, 255));

	private BlockPos currentTarget;
	private boolean prevAttack = false;

	public TeleportHack()
	{
		super("Teleport");
		setCategory(Category.MOVEMENT);
		addSetting(reach);
		addSetting(includeLiquids);
		addSetting(solidColor);
		addSetting(passableColor);
	}

	@Override
	protected void onEnable()
	{
		EVENTS.add(UpdateListener.class, this);
		EVENTS.add(RenderListener.class, this);
		ChatUtils.message("Press attack (left click) to teleport.");
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		EVENTS.remove(RenderListener.class, this);
		currentTarget = null;
		prevAttack = false;
	}

	@Override
	public void onUpdate()
	{
		if(MC.player == null || MC.level == null)
		{
			prevAttack = false;
			return;
		}

		currentTarget = getTarget();

		boolean attacking = MC.options.keyAttack.isDown();
		boolean clicked = attacking && !prevAttack;
		prevAttack = attacking;

		if(currentTarget == null || !clicked)
			return;

		BlockPos feet = currentTarget.above();
		BlockPos head = feet.above();
		boolean blocked = !MC.level.getBlockState(feet).canBeReplaced()
			|| !MC.level.getBlockState(head).canBeReplaced();
		if(blocked)
		{
			ChatUtils.error("Target space is blocked.");
			return;
		}

		if(!includeLiquids.isChecked()
			&& (!MC.level.getFluidState(currentTarget).isEmpty()
				|| !MC.level.getFluidState(feet).isEmpty()))
		{
			ChatUtils.error("Target is in liquid.");
			return;
		}

		MC.player.setPos(currentTarget.getX() + 0.5, currentTarget.getY() + 1.1,
			currentTarget.getZ() + 0.5);
		MC.player.setDeltaMovement(0, 0.2, 0);
	}

	@Override
	public void onRender(PoseStack matrixStack, float partialTicks)
	{
		if(currentTarget == null || MC.level == null)
			return;

		AABB box = new AABB(currentTarget.above());
		boolean solid = !MC.level.getBlockState(currentTarget).is(Blocks.AIR)
			&& MC.level.getFluidState(currentTarget).isEmpty();
		int color = solid ? solidColor.getColorI(0x90) : passableColor.getColorI(0x90);
		RenderUtils.drawOutlinedBox(matrixStack, box, color, false);
		RenderUtils.drawSolidBox(matrixStack, box, color & 0x40FFFFFF, false);
	}

	private BlockPos getTarget()
	{
		if(MC.getCameraEntity() == null)
			return null;
		HitResult hit = MC.getCameraEntity().pick(reach.getValue(), 0,
			includeLiquids.isChecked());
		if(hit.getType() != BlockHitResult.Type.BLOCK)
			return null;
		return ((BlockHitResult)hit).getBlockPos();
	}
}
