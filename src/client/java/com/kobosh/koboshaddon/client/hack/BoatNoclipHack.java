/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package com.kobosh.koboshaddon.client.hack;

import net.minecraft.world.entity.vehicle.boat.Boat;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
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
		if(MC.player != null && MC.player.getVehicle() instanceof Boat boat)
			boat.noPhysics = false;
	}

	@Override
	public void onUpdate()
	{
		if(MC.player == null)
			return;
		if(!(MC.player.getVehicle() instanceof Boat boat))
			return;

		boat.noPhysics = true;
		boat.setYRot(MC.player.getYRot());

		double velX = boat.getDeltaMovement().x;
		double velZ = boat.getDeltaMovement().z;
		double velY = -fallSpeed.getValue() / 20.0;

		if(speed.isChecked())
		{
			double inputX = 0;
			double inputZ = 0;
			if(MC.options.keyUp.isDown())
				inputZ += 1;
			if(MC.options.keyDown.isDown())
				inputZ -= 1;
			if(MC.options.keyLeft.isDown())
				inputX += 1;
			if(MC.options.keyRight.isDown())
				inputX -= 1;

			if(inputX != 0 || inputZ != 0)
			{
				double yawRad = Math.toRadians(MC.player.getYRot());
				double sin = Mth.sin((float)yawRad);
				double cos = Mth.cos((float)yawRad);
				double mag = Math.sqrt(inputX * inputX + inputZ * inputZ);
				inputX /= mag;
				inputZ /= mag;
				double speedVal = horizontalSpeed.getValue() / 20.0;
				velX = (inputZ * -sin + inputX * cos) * speedVal;
				velZ = (inputZ * cos + inputX * sin) * speedVal;
			}
		}

		if(MC.options.keyJump.isDown())
			velY = verticalSpeed.getValue() / 20.0;
		else if(MC.options.keyShift.isDown() || MC.options.keySprint.isDown())
			velY = -verticalSpeed.getValue() / 20.0;

		boat.setDeltaMovement(new Vec3(velX, velY, velZ));
	}
}
