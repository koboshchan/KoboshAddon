/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package com.kobosh.koboshaddon.client.command;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import net.minecraft.world.item.ItemStack;
import net.wurstclient.WurstClient;
import net.wurstclient.command.CmdException;
import net.wurstclient.command.Command;
import net.wurstclient.util.ChatUtils;

public final class ViewNbtCmd extends Command
{
	public ViewNbtCmd()
	{
		super("viewnbt", "Shows full components/NBT of your main-hand item.",
			".viewnbt", ".viewnbt save");
	}

	@Override
	public void call(String[] args) throws CmdException
	{
		if(MC.player == null)
			return;

		ItemStack stack = MC.player.getMainHandItem();
		if(stack.isEmpty())
		{
			ChatUtils.error("No item in main hand.");
			return;
		}

		String data = stack.getComponents().toString();
		ChatUtils.message(data);

		if(args.length == 1 && "save".equalsIgnoreCase(args[0]))
			save(data);
	}

	private void save(String data)
	{
		Path outDir = WurstClient.INSTANCE.getWurstFolder().resolve("SavedNBT");
		Path outFile = outDir.resolve("ViewedNBTData.txt");
		try
		{
			Files.createDirectories(outDir);
			Files.writeString(outFile, data + System.lineSeparator(),
				StandardCharsets.UTF_8, StandardOpenOption.CREATE,
				StandardOpenOption.APPEND);
			ChatUtils.message("Saved to " + outFile.toString());
		}catch(IOException e)
		{
			ChatUtils.error("Failed to save NBT: " + e.getMessage());
		}
	}
}
