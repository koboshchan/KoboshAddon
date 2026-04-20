/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package com.kobosh.koboshaddon.client.hack;

import java.util.Comparator;
import java.util.stream.Stream;

import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.entity.Entity;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.util.math.Vec3d;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.MouseUpdateListener;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.AimAtSetting;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.util.BlockUtils;
import net.wurstclient.util.EntityUtils;
import net.wurstclient.util.Rotation;
import net.wurstclient.util.RotationUtils;

@SearchTags({"dragon aim bot", "dragon aimbot", "aimbot",
	"ender dragon targeting", "dragon"})
public final class DragonAimBotHack extends Hack
	implements UpdateListener, MouseUpdateListener
{
	private final SliderSetting range =
		new SliderSetting("Range", 50, 0, 500, 1, ValueDisplay.DECIMAL);
	
	private final SliderSetting rotationSpeed =
		new SliderSetting("Rotation Speed", 30, 1, 180, 1,
			ValueDisplay.DEGREES.withSuffix("/tick"));
	
	private final SliderSetting fov =
		new SliderSetting("FOV", "Field of view for targeting the dragon.", 120,
			30, 360, 10, ValueDisplay.DEGREES);
	
	private final AimAtSetting aimAt =
		new AimAtSetting("What point in the dragon's hitbox to aim at.");
	
	private final SliderSetting predictMovement = new SliderSetting(
		"Predict movement",
		"Controls the strength of DragonAimBot's movement prediction algorithm.",
		0.2, 0, 2, 0.01, ValueDisplay.PERCENTAGE);
	
	private final SliderSetting verticalOffset = new SliderSetting(
		"Vertical offset",
		"Vertical aiming offset. -100% = one dragon height down, +100% = one dragon height up.",
		0, -1, 1, 0.01, ValueDisplay.PERCENTAGE);
	
	private final SliderSetting ignoreMouseInput = new SliderSetting(
		"Ignore mouse input", "How much to ignore existing mouse movement.", 0,
		0, 1, 0.01, ValueDisplay.PERCENTAGE);
	
	private final CheckboxSetting checkLOS = new CheckboxSetting(
		"Check line of sight", "Only target dragons that are visible.", true);
	
	private final CheckboxSetting aimWhileBlocking =
		new CheckboxSetting("Aim while blocking",
			"Continue aiming while blocking/using items.", false);
	
	private final CheckboxSetting snap = new CheckboxSetting("Snap",
		"Instantly snap to targets instead of smooth rotation.", false);
	
	private EnderDragonEntity target;
	private float nextYaw;
	private float nextPitch;
	
	public DragonAimBotHack()
	{
		super("DragonAimBot");
		setCategory(Category.COMBAT);
		
		addSetting(range);
		addSetting(rotationSpeed);
		addSetting(fov);
		addSetting(aimAt);
		addSetting(predictMovement);
		addSetting(verticalOffset);
		addSetting(ignoreMouseInput);
		addSetting(checkLOS);
		addSetting(aimWhileBlocking);
		addSetting(snap);
	}
	
	@Override
	protected void onEnable()
	{
		// disable incompatible hacks
		WURST.getHax().aimAssistHack.setEnabled(false);
		WURST.getHax().clickAuraHack.setEnabled(false);
		WURST.getHax().crystalAuraHack.setEnabled(false);
		WURST.getHax().fightBotHack.setEnabled(false);
		WURST.getHax().killauraHack.setEnabled(false);
		WURST.getHax().killauraLegitHack.setEnabled(false);
		WURST.getHax().multiAuraHack.setEnabled(false);
		WURST.getHax().protectHack.setEnabled(false);
		WURST.getHax().tpAuraHack.setEnabled(false);
		
		EVENTS.add(UpdateListener.class, this);
		EVENTS.add(MouseUpdateListener.class, this);
	}
	
	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		EVENTS.remove(MouseUpdateListener.class, this);
		target = null;
	}
	
	@Override
	public void onUpdate()
	{
		target = null;
		
		// don't aim when a container/inventory screen is open
		if(MC.currentScreen instanceof HandledScreen)
			return;
		
		if(!aimWhileBlocking.isChecked() && MC.player.isUsingItem())
			return;
		
		chooseTarget();
		if(target == null)
			return;
		
		// Calculate predicted target position
		Vec3d hitVec;
		if(predictMovement.getValue() > 0)
		{
			// Get the desired aim point first
			Vec3d aimPoint = aimAt.getAimPoint(target);
			
			// Use movement prediction but maintain the aim point offset
			double d = RotationUtils.getEyesPos()
				.distanceTo(target.getBoundingBox().getCenter())
				* predictMovement.getValue();
			double posX =
				target.getX() + (target.getX() - target.lastRenderX) * d;
			double posY =
				target.getY() + (target.getY() - target.lastRenderY) * d;
			double posZ =
				target.getZ() + (target.getZ() - target.lastRenderZ) * d;
			
			// Apply the aim point offset to the predicted position
			Vec3d targetCenter = target.getBoundingBox().getCenter();
			Vec3d aimOffset = aimPoint.subtract(targetCenter);
			
			hitVec = new Vec3d(posX + aimOffset.x, posY + aimOffset.y,
				posZ + aimOffset.z);
		}else
		{
			// Use normal aim point without prediction
			hitVec = aimAt.getAimPoint(target);
		}
		
		// Apply vertical offset
		if(verticalOffset.getValue() != 0)
		{
			double offsetAmount =
				verticalOffset.getValue() * target.getHeight();
			hitVec = hitVec.add(0, offsetAmount, 0);
		}
		
		if(checkLOS.isChecked() && !BlockUtils.hasLineOfSight(hitVec))
		{
			target = null;
			return;
		}
		
		// get needed rotation
		Rotation needed = RotationUtils.getNeededRotations(hitVec);
		
		if(snap.isChecked())
		{
			// Instantly snap to target - directly set player rotation
			MC.player.setYaw(needed.yaw());
			MC.player.setPitch(needed.pitch());
			// Set next values to prevent mouse update from interfering
			nextYaw = needed.yaw();
			nextPitch = needed.pitch();
		}else
		{
			// turn towards target smoothly
			Rotation next = RotationUtils.slowlyTurnTowards(needed,
				rotationSpeed.getValueF());
			nextYaw = next.yaw();
			nextPitch = next.pitch();
		}
	}
	
	private void chooseTarget()
	{
		Stream<Entity> stream = EntityUtils.getAttackableEntities();
		
		// Only target Ender Dragons
		stream = stream.filter(e -> e instanceof EnderDragonEntity);
		
		double rangeSq = range.getValueSq();
		stream = stream.filter(e -> MC.player.squaredDistanceTo(e) <= rangeSq);
		
		if(fov.getValue() < 360.0)
			stream = stream.filter(e -> RotationUtils.getAngleToLookVec(
				aimAt.getAimPoint(e)) <= fov.getValue() / 2.0);
			
		// Don't target ourselves (shouldn't happen with dragons, but safety
		// check)
		stream = stream.filter(e -> e != MC.player);
		
		target = (EnderDragonEntity)stream
			.min(Comparator.comparingDouble(
				e -> RotationUtils.getAngleToLookVec(aimAt.getAimPoint(e))))
			.orElse(null);
	}
	
	@Override
	public void onMouseUpdate(MouseUpdateEvent event)
	{
		if(target == null || MC.player == null)
			return;
		
		float curYaw = MC.player.getYaw();
		float curPitch = MC.player.getPitch();
		int diffYaw = (int)(nextYaw - curYaw);
		int diffPitch = (int)(nextPitch - curPitch);
		
		// If we are <1 degree off but still missing the hitbox,
		// slightly exaggerate the difference to fix that.
		if(diffYaw == 0 && diffPitch == 0 && !RotationUtils
			.isFacingBox(target.getBoundingBox(), range.getValue()))
		{
			diffYaw = nextYaw < curYaw ? -1 : 1;
			diffPitch = nextPitch < curPitch ? -1 : 1;
		}
		
		double inputFactor = 1 - ignoreMouseInput.getValue();
		int mouseInputX = (int)(event.getDefaultDeltaX() * inputFactor);
		int mouseInputY = (int)(event.getDefaultDeltaY() * inputFactor);
		
		event.setDeltaX(mouseInputX + diffYaw);
		event.setDeltaY(mouseInputY + diffPitch);
	}
}
