/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package com.kobosh.koboshaddon.client.hack;

import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.entity.vehicle.boat.Boat;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.world.phys.EntityHitResult;
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
		if(!(event.getPacket() instanceof ServerboundInteractPacket))
			return;
		
		if(!(MC.hitResult instanceof EntityHitResult ehr))
			return;
		
		if(!(ehr.getEntity() instanceof AbstractMinecart)
			&& !(ehr.getEntity() instanceof Boat))
			return;
		
		for(int i = 0; i < amount.getValueI() - 1; i++)
			MC.player.connection.getConnection().send(event.getPacket(),
				null);
	}
}
