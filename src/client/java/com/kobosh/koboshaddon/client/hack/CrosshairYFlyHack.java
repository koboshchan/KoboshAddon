/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package com.kobosh.koboshaddon.client.hack;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;

@SearchTags({"crosshair y", "follow y", "closest to crosshair", "y fly"})
public final class CrosshairYFlyHack extends Hack implements UpdateListener
{
	private final SliderSetting range =
		new SliderSetting("Range", "Maximum target range.", 128, 8, 256, 1,
			ValueDisplay.DECIMAL);

	private final SliderSetting verticalSpeed = new SliderSetting("Vertical speed",
		"How fast to move up/down toward the target Y level.", 0.2, 0.02, 1,
		0.01, ValueDisplay.DECIMAL.withSuffix(" blocks/tick"));

	private final SliderSetting deadzone = new SliderSetting("Deadzone",
		"How close your Y must be before movement stops.", 0.05, 0.01, 0.5,
		0.01, ValueDisplay.DECIMAL.withSuffix(" blocks"));

	public CrosshairYFlyHack()
	{
		super("CrosshairYFly");
		setCategory(Category.MOVEMENT);
		addSetting(range);
		addSetting(verticalSpeed);
		addSetting(deadzone);
	}

	@Override
	protected void onEnable()
	{
		EVENTS.add(UpdateListener.class, this);
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
	}

	@Override
	public void onUpdate()
	{
		if(MC.player == null || MC.world == null)
			return;

		PlayerEntity target = getCrosshairClosestPlayer();
		if(target == null)
			return;

		double deltaY = target.getY() - MC.player.getY();
		if(Math.abs(deltaY) <= deadzone.getValue())
		{
			MC.player.setVelocity(MC.player.getVelocity().x, 0,
				MC.player.getVelocity().z);
			return;
		}

		double ySpeed = verticalSpeed.getValue() * Math.signum(deltaY);
		MC.player.setVelocity(MC.player.getVelocity().x, ySpeed,
			MC.player.getVelocity().z);
	}

	private PlayerEntity getCrosshairClosestPlayer()
	{
		Vec3d eyes = MC.player.getEyePos();
		Vec3d look = MC.player.getRotationVec(1.0F).normalize();
		double rangeSq = range.getValueSq();

		PlayerEntity best = null;
		double bestAngle = Double.MAX_VALUE;
		double bestDistanceSq = Double.MAX_VALUE;

		for(PlayerEntity candidate : MC.world.getPlayers())
		{
			if(candidate == MC.player || !candidate.isAlive() || candidate.isRemoved())
				continue;

			Vec3d targetPos = new Vec3d(candidate.getX(),
				candidate.getY() + candidate.getHeight() * 0.5,
				candidate.getZ());
			Vec3d toTarget = targetPos.subtract(eyes);
			double distanceSq = toTarget.lengthSquared();
			if(distanceSq > rangeSq || distanceSq <= 1.0E-8)
				continue;

			Vec3d dir = toTarget.normalize();
			double dot = look.dotProduct(dir);
			dot = Math.max(-1.0, Math.min(1.0, dot));
			double angle = Math.acos(dot);

			if(angle < bestAngle
				|| (Math.abs(angle - bestAngle) <= 1.0E-6
					&& distanceSq < bestDistanceSq))
			{
				best = candidate;
				bestAngle = angle;
				bestDistanceSq = distanceSq;
			}
		}

		return best;
	}
}