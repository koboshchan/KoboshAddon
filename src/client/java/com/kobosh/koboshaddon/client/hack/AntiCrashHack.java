/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package com.kobosh.koboshaddon.client.hack;

import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.protocol.game.ClientboundExplodePacket;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
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
		if(event.getPacket() instanceof ClientboundExplodePacket packet)
		{
			net.minecraft.world.phys.Vec3 explodePos = packet.center();
			net.minecraft.world.phys.Vec3 playerKnockback = new net.minecraft.world.phys.Vec3(0, 0, 0);
			if(packet.playerKnockback().isPresent()) {
				playerKnockback = packet.playerKnockback().get();
			}
			if(explodePos.x() > 30_000_000 || explodePos.y() > 30_000_000 || explodePos.z() > 30_000_000 ||
			   explodePos.x() < -30_000_000 || explodePos.y() < -30_000_000 || explodePos.z() < -30_000_000 ||
			   playerKnockback.x > 30_000_000 || playerKnockback.y > 30_000_000 || playerKnockback.z > 30_000_000 ||
			   playerKnockback.x < -30_000_000 || playerKnockback.y < -30_000_000 || playerKnockback.z < -30_000_000)
			{
				cancel(event);
			}
			return;
		}

		if(event.getPacket() instanceof ClientboundLevelParticlesPacket packet)
		{
			if(packet.getCount() > 100_000)
				cancel(event);
			return;
		}

		if(event.getPacket() instanceof ClientboundPlayerPositionPacket packet)
		{
			net.minecraft.world.phys.Vec3 playerPos = packet.change().position();
			if(playerPos.x > 30_000_000 || playerPos.y > 30_000_000 || playerPos.z > 30_000_000 ||
			   playerPos.x < -30_000_000 || playerPos.y < -30_000_000 || playerPos.z < -30_000_000)
			{
				cancel(event);
			}
			return;
		}

		if(event.getPacket() instanceof ClientboundSetEntityMotionPacket packet)
		{
			if(MC.player == null)
			{
				cancel(event);
				return;
			}
			if(packet.id() == MC.player.getId())
			{
				net.minecraft.world.phys.Vec3 movement = packet.movement();
				if(movement.x > 1000 || movement.y > 1000 || movement.z > 1000 ||
				   movement.x < -1000 || movement.y < -1000 || movement.z < -1000)
				{
					cancel(event);
				}
			}
		}
	}

	private void cancel(PacketInputEvent event)
	{
		event.cancel();
		if(log.isChecked())
			ChatUtils.message("Blocked suspicious crash packet.");
	}
}
