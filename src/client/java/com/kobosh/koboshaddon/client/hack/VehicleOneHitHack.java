/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package com.kobosh.koboshaddon.client.hack;

import net.minecraft.entity.vehicle.AbstractMinecartEntity;
import net.minecraft.entity.vehicle.BoatEntity;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.util.hit.EntityHitResult;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.PacketOutputListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.SliderSetting;

@SearchTags({"vehicle one hit", "VehicleDestroy", "OneHitVehicle",
	"boat destroy", "minecart destroy"})
public final class VehicleOneHitHack extends Hack
	implements PacketOutputListener
{
	private final SliderSetting amount =
		new SliderSetting("Amount", "The number of packets to send.", 16, 1,
			100, 1, SliderSetting.ValueDisplay.INTEGER);
	
	public VehicleOneHitHack()
	{
		super("VehicleOneHit");
		setCategory(Category.COMBAT);
		addSetting(amount);
	}
	
	@Override
	protected void onEnable()
	{
		EVENTS.add(PacketOutputListener.class, this);
	}
	
	@Override
	protected void onDisable()
	{
		EVENTS.remove(PacketOutputListener.class, this);
	}
	
	@Override
	public void onSentPacket(PacketOutputEvent event)
	{
		if(!(event.getPacket() instanceof PlayerInteractEntityC2SPacket))
			return;
		
		if(!(MC.crosshairTarget instanceof EntityHitResult ehr))
			return;
		
		if(!(ehr.getEntity() instanceof AbstractMinecartEntity)
			&& !(ehr.getEntity() instanceof BoatEntity))
			return;
		
		for(int i = 0; i < amount.getValueI() - 1; i++)
			MC.player.networkHandler.getConnection().send(event.getPacket(),
				null);
	}
}
