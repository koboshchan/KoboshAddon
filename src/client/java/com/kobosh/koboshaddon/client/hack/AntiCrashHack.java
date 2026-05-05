/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package com.kobosh.koboshaddon.client.hack;

import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.ExplosionS2CPacket;
import net.minecraft.network.packet.s2c.play.ParticleS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.PacketInputListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.util.ChatUtils;

@SearchTags({"anti crash", "packet crash", "crash protection"})
public final class AntiCrashHack extends Hack implements PacketInputListener
{
	private final CheckboxSetting log =
		new CheckboxSetting("Log", "Logs when a suspicious packet is cancelled.", false);

	public AntiCrashHack()
	{
		super("AntiCrash");
		setCategory(Category.OTHER);
		addSetting(log);
	}

	@Override
	protected void onEnable()
	{
		EVENTS.add(PacketInputListener.class, this);
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(PacketInputListener.class, this);
	}

	@Override
	public void onReceivedPacket(PacketInputEvent event)
	{
		if(event.getPacket() instanceof ExplosionS2CPacket)
			return;

		if(event.getPacket() instanceof ParticleS2CPacket packet)
		{
			if(packet.getCount() > 100_000)
				cancel(event);
			return;
		}

		if(event.getPacket() instanceof PlayerPositionLookS2CPacket)
			return;

		if(event.getPacket() instanceof EntityVelocityUpdateS2CPacket)
		{
			if(MC.player == null)
				cancel(event);
		}
	}

	private void cancel(PacketInputEvent event)
	{
		event.cancel();
		if(log.isChecked())
			ChatUtils.message("Blocked suspicious crash packet.");
	}
}
