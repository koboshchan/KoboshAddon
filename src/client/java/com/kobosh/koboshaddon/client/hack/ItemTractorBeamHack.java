/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package com.kobosh.koboshaddon.client.hack;

import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.util.ChatUtils;

@SearchTags({"item tractor beam", "tractor", "item vacuum"})
public final class ItemTractorBeamHack extends Hack
{
	private final SliderSetting multiplier = new SliderSetting("Multiplier",
		"Higher values increase pull reliability at the cost of hunger.", 90, 1,
		150, 1, SliderSetting.ValueDisplay.INTEGER);

	public ItemTractorBeamHack()
	{
		super("ItemTractorBeam");
		setCategory(Category.ITEMS);
		addSetting(multiplier);
	}

	@Override
	protected void onEnable()
	{
		if(MC.player == null || MC.player.networkHandler == null)
		{
			setEnabled(false);
			return;
		}

		MC.player.networkHandler.sendPacket(new ClientCommandC2SPacket(MC.player,
			ClientCommandC2SPacket.Mode.START_SPRINTING));

		int count = multiplier.getValueI();
		for(int i = 0; i < count; i++)
			sendMovementPackets();

		if(!MC.options.sprintKey.isPressed())
			MC.player.networkHandler.sendPacket(new ClientCommandC2SPacket(
				MC.player, ClientCommandC2SPacket.Mode.STOP_SPRINTING));

		ChatUtils.message("ItemTractorBeam fired " + count + " burst packets.");
		setEnabled(false);
	}

	private void sendMovementPackets()
	{
		MC.player.networkHandler
			.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(
				MC.player.getX(), MC.player.getY() - 1.0E-14, MC.player.getZ(),
				true, MC.player.horizontalCollision));
		MC.player.networkHandler
			.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(
				MC.player.getX(), MC.player.getY() + 1.0E-14, MC.player.getZ(),
				false, MC.player.horizontalCollision));
	}
}
