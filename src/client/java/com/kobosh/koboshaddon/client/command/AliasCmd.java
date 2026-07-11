package com.kobosh.koboshaddon.client.command;

import java.util.Arrays;
import java.util.Map;
import net.wurstclient.command.CmdException;
import net.wurstclient.command.CmdSyntaxError;
import net.wurstclient.command.Command;
import net.wurstclient.util.ChatUtils;

public final class AliasCmd extends Command
{
	public AliasCmd()
	{
		super("alias", "Manage command aliases.",
			".alias add <name> <command>",
			".alias edit <name> <command>",
			".alias list",
			".alias remove <name>");
	}

	@Override
	public void call(String[] args) throws CmdException
	{
		if(args.length < 1)
			throw new CmdSyntaxError();

		String action = args[0].toLowerCase();
		switch(action)
		{
			case "add":
			{
				if(args.length < 3)
					throw new CmdSyntaxError("Syntax: .alias add <name> <command>");

				String name = args[1];
				if(AliasManager.hasAlias(name))
				{
					ChatUtils.error("Alias '" + name + "' already exists. Use '.alias edit " + name + " <command>' to modify it.");
					return;
				}

				String command = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
				AliasManager.addAlias(name, command);
				ChatUtils.message("Added alias: " + name + " -> " + command);
				break;
			}

			case "edit":
			{
				if(args.length < 3)
					throw new CmdSyntaxError("Syntax: .alias edit <name> <command>");

				String name = args[1];
				if(!AliasManager.hasAlias(name))
				{
					ChatUtils.error("Alias '" + name + "' does not exist. Use '.alias add " + name + " <command>' to create it.");
					return;
				}

				String command = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
				AliasManager.addAlias(name, command);
				ChatUtils.message("Updated alias: " + name + " -> " + command);
				break;
			}

			case "list":
			{
				if(args.length != 1)
					throw new CmdSyntaxError("Syntax: .alias list");

				Map<String, String> aliases = AliasManager.getAliases();
				if(aliases.isEmpty())
				{
					ChatUtils.message("No active aliases.");
					return;
				}

				ChatUtils.message("Active Aliases:");
				for(Map.Entry<String, String> entry : aliases.entrySet())
				{
					ChatUtils.message("  " + entry.getKey() + " -> " + entry.getValue());
				}
				break;
			}

			case "remove":
			case "delete":
			{
				if(args.length != 2)
					throw new CmdSyntaxError("Syntax: .alias remove <name>");

				String name = args[1];
				if(AliasManager.removeAlias(name))
				{
					ChatUtils.message("Removed alias '" + name + "'.");
				}else
				{
					ChatUtils.error("Alias '" + name + "' does not exist.");
				}
				break;
			}

			default:
				throw new CmdSyntaxError("Unknown subcommand. Use add, edit, list, or remove.");
		}
	}
}
