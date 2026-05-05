/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package com.kobosh.koboshaddon.client.hack;

import net.minecraft.block.Blocks;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
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
		if(MC.player == null || MC.world == null)
		{
			prevAttack = false;
			return;
		}

		currentTarget = getTarget();

		boolean attacking = MC.options.attackKey.isPressed();
		boolean clicked = attacking && !prevAttack;
		prevAttack = attacking;

		if(currentTarget == null || !clicked)
			return;

		BlockPos feet = currentTarget.up();
		BlockPos head = feet.up();
		boolean blocked = !MC.world.getBlockState(feet).isReplaceable()
			|| !MC.world.getBlockState(head).isReplaceable();
		if(blocked)
		{
			ChatUtils.error("Target space is blocked.");
			return;
		}

		if(!includeLiquids.isChecked()
			&& (!MC.world.getFluidState(currentTarget).isEmpty()
				|| !MC.world.getFluidState(feet).isEmpty()))
		{
			ChatUtils.error("Target is in liquid.");
			return;
		}

		MC.player.setPosition(currentTarget.getX() + 0.5, currentTarget.getY() + 1.1,
			currentTarget.getZ() + 0.5);
		MC.player.setVelocity(0, 0.2, 0);
	}

	@Override
	public void onRender(MatrixStack matrixStack, float partialTicks)
	{
		if(currentTarget == null || MC.world == null)
			return;

		Box box = new Box(currentTarget.up());
		boolean solid = !MC.world.getBlockState(currentTarget).isOf(Blocks.AIR)
			&& MC.world.getFluidState(currentTarget).isEmpty();
		int color = solid ? solidColor.getColorI(0x90) : passableColor.getColorI(0x90);
		RenderUtils.drawOutlinedBox(matrixStack, box, color, false);
		RenderUtils.drawSolidBox(matrixStack, box, color & 0x40FFFFFF, false);
	}

	private BlockPos getTarget()
	{
		if(MC.getCameraEntity() == null)
			return null;
		HitResult hit = MC.getCameraEntity().raycast(reach.getValue(), 0,
			includeLiquids.isChecked());
		if(hit.getType() != HitResult.Type.BLOCK)
			return null;
		return ((BlockHitResult)hit).getBlockPos();
	}
}
