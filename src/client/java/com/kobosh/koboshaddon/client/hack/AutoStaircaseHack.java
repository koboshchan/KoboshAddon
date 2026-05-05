/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package com.kobosh.koboshaddon.client.hack;

import net.minecraft.item.BlockItem;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.SliderSetting;

@SearchTags({"auto staircase", "staircase", "auto stairs"})
public final class AutoStaircaseHack extends Hack implements UpdateListener
{
	private final SliderSetting viewAngle = new SliderSetting("View angle",
		"How far forward to aim while building.", 1, 0.1, 30, 0.1,
		SliderSetting.ValueDisplay.DECIMAL);

	private final SliderSetting buildLimit = new SliderSetting("Build limit",
		"Stops climbing when this Y level is reached.", 319, -64, 319, 1,
		SliderSetting.ValueDisplay.INTEGER);

	public AutoStaircaseHack()
	{
		super("AutoStaircase");
		setCategory(Category.MOVEMENT);
		addSetting(viewAngle);
		addSetting(buildLimit);
	}

	@Override
	protected void onEnable()
	{
		EVENTS.add(UpdateListener.class, this);
		if(MC.player != null)
			MC.player.setVelocity(0, 0, 0);
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		releaseMovementKeys();
	}

	@Override
	public void onUpdate()
	{
		if(MC.player == null || MC.world == null || MC.interactionManager == null)
			return;

		if(!(MC.player.getMainHandStack().getItem() instanceof BlockItem))
		{
			releaseMovementKeys();
			return;
		}

		if(MC.player.getY() >= buildLimit.getValue())
		{
			releaseMovementKeys();
			return;
		}

		Direction dir = MC.player.getMovementDirection();
		Vec3d eyes = MC.player.getEyePos();
		Vec3d lookTarget = eyes.add(dir.getOffsetX() * viewAngle.getValue(), 0,
			dir.getOffsetZ() * viewAngle.getValue());
		WURST.getRotationFaker().faceVectorClient(lookTarget);

		if(!MC.player.isOnGround())
			return;

		if(MC.options.backKey.isPressed())
		{
			releaseMovementKeys();
			MC.player.setVelocity(0, 0, 0);
			return;
		}

		BlockPos ahead = MC.player.getBlockPos().offset(dir);
		if(MC.world.getBlockState(ahead).isReplaceable())
		{
			MC.options.forwardKey.setPressed(false);
			MC.options.jumpKey.setPressed(false);
			BlockHitResult hit = new BlockHitResult(Vec3d.ofCenter(ahead),
				Direction.DOWN, ahead, false);
			MC.interactionManager.interactBlock(MC.player, Hand.MAIN_HAND, hit);
			MC.player.swingHand(Hand.MAIN_HAND);
		}else
		{
			MC.options.forwardKey.setPressed(true);
			MC.options.jumpKey.setPressed(true);
		}
	}

	private void releaseMovementKeys()
	{
		if(MC.options == null)
			return;
		MC.options.forwardKey.setPressed(false);
		MC.options.jumpKey.setPressed(false);
	}
}
