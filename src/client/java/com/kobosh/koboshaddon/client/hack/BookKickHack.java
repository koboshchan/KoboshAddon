/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package com.kobosh.koboshaddon.client.hack;

import java.util.List;
import java.util.Optional;

import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.hack.Hack;
import net.minecraft.world.item.Items;
import net.minecraft.network.protocol.game.ServerboundEditBookPacket;
import net.wurstclient.util.ChatUtils;

@SearchTags({"dupe", "book"})
public final class BookKickHack extends Hack
{
	public BookKickHack()
	{
		super("BookKick");
		setCategory(Category.ITEMS);
	}
	
	@Override
	protected void onEnable()
	{
		assert MC.player != null;
		if(!(MC.player.getMainHandItem()
			.getItem() == Items.WRITABLE_BOOK))
		{
			ChatUtils.error("Please hold a writable book!");
			setEnabled(false);
			return;
		}
		MC.player.connection.send(new ServerboundEditBookPacket(
			MC.player.getInventory().getSelectedSlot(), List.of(""),
			Optional.of("The quick brown fox jumps over the lazy dog")));
		setEnabled(false);
	}
}
