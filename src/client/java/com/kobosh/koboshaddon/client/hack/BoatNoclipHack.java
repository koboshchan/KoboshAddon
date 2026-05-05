/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package com.kobosh.koboshaddon.client.hack;

import net.minecraft.entity.vehicle.BoatEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.SliderSetting;

@SearchTags({"boat noclip", "boat phase", "boat fly"})
public final class BoatNoclipHack extends Hack implements UpdateListener
{
	private final CheckboxSetting speed =
		new CheckboxSetting("Speed", "Apply custom horizontal speed.", true);

	private final SliderSetting horizontalSpeed = new SliderSetting(
		"Horizontal speed", "Horizontal speed while in a boat.", 10, 0, 50, 0.1,
		SliderSetting.ValueDisplay.DECIMAL);

	private final SliderSetting verticalSpeed = new SliderSetting(
		"Vertical speed", "Vertical speed while jumping/sneaking.", 6, 0, 20,
		0.1, SliderSetting.ValueDisplay.DECIMAL);

	private final SliderSetting fallSpeed = new SliderSetting("Fall speed",
		"Downward speed when no movement key is pressed.", 0, 0, 5, 0.05,
		SliderSetting.ValueDisplay.DECIMAL);

	public BoatNoclipHack()
	{
		super("BoatNoclip");
		setCategory(Category.MOVEMENT);
		addSetting(speed);
		addSetting(horizontalSpeed);
		addSetting(verticalSpeed);
		addSetting(fallSpeed);
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
		if(MC.player != null && MC.player.getVehicle() instanceof BoatEntity boat)
			boat.noClip = false;
	}

	@Override
	public void onUpdate()
	{
		if(MC.player == null)
			return;
		if(!(MC.player.getVehicle() instanceof BoatEntity boat))
			return;

		boat.noClip = true;
		boat.setYaw(MC.player.getYaw());

		double velX = boat.getVelocity().x;
		double velZ = boat.getVelocity().z;
		double velY = -fallSpeed.getValue() / 20.0;

		if(speed.isChecked())
		{
			double inputX = 0;
			double inputZ = 0;
			if(MC.options.forwardKey.isPressed())
				inputZ += 1;
			if(MC.options.backKey.isPressed())
				inputZ -= 1;
			if(MC.options.leftKey.isPressed())
				inputX += 1;
			if(MC.options.rightKey.isPressed())
				inputX -= 1;

			if(inputX != 0 || inputZ != 0)
			{
				double yawRad = Math.toRadians(MC.player.getYaw());
				double sin = MathHelper.sin((float)yawRad);
				double cos = MathHelper.cos((float)yawRad);
				double mag = Math.sqrt(inputX * inputX + inputZ * inputZ);
				inputX /= mag;
				inputZ /= mag;
				double speedVal = horizontalSpeed.getValue() / 20.0;
				velX = (inputZ * -sin + inputX * cos) * speedVal;
				velZ = (inputZ * cos + inputX * sin) * speedVal;
			}
		}

		if(MC.options.jumpKey.isPressed())
			velY = verticalSpeed.getValue() / 20.0;
		else if(MC.options.sneakKey.isPressed() || MC.options.sprintKey.isPressed())
			velY = -verticalSpeed.getValue() / 20.0;

		boat.setVelocity(new Vec3d(velX, velY, velZ));
	}
}
