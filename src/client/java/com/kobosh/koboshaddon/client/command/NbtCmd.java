/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package com.kobosh.koboshaddon.client.command;

import net.wurstclient.command.CmdException;
import net.wurstclient.command.Command;

public final class NbtCmd extends Command
{
	private final ViewNbtCmd delegate = new ViewNbtCmd();

	public NbtCmd()
	{
		super("nbt",
			"Alias for .viewnbt (uses component-based output for modern item data).",
			".nbt", ".nbt save");
	}

	@Override
	public void call(String[] args) throws CmdException
	{
		delegate.call(args);
	}
}
