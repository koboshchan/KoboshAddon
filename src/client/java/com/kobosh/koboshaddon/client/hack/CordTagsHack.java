/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package com.kobosh.koboshaddon.client.hack;

import java.util.Locale;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.EnumSetting;

@SearchTags({"coord tags", "coords", "x y z", "health tags"})
public final class CordTagsHack extends Hack
{
	private final EnumSetting<Axis> axis = new EnumSetting<>("Axis",
		"Which coordinate to show next to HealthTags. Only one axis is shown.",
		Axis.values(), Axis.X);

	public CordTagsHack()
	{
		super("CordTags");
		setCategory(Category.RENDER);
		addSetting(axis);
	}

	public Text addCoord(PlayerEntity player, MutableText nametag)
	{
		if(!isEnabled())
			return nametag;

		double value = switch(axis.getSelected())
		{
			case X -> player.getX();
			case Y -> player.getY();
			case Z -> player.getZ();
		};

		String coord = String.format(Locale.ROOT, " %.1f", value);
		MutableText formattedCoord = Text.literal(coord).formatted(Formatting.AQUA);
		return nametag.append(formattedCoord);
	}

	private enum Axis
	{
		X("X"),
		Y("Y"),
		Z("Z");

		private final String displayName;

		private Axis(String displayName)
		{
			this.displayName = displayName;
		}

		@Override
		public String toString()
		{
			return displayName;
		}
	}
}