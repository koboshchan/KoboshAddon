package com.kobosh.koboshaddon.client.command;

import net.wurstclient.WurstClient;
import net.wurstclient.command.CmdException;
import net.wurstclient.command.CmdSyntaxError;
import net.wurstclient.command.Command;
import net.wurstclient.util.ChatUtils;

public final class ACmd extends Command
{
	private static final int MAX_RECURSION = 5;

	public ACmd()
	{
		super("a", "Execute an alias.", ".a <name>");
	}

	@Override
	public void call(String[] args) throws CmdException
	{
		if(args.length != 1)
			throw new CmdSyntaxError("Syntax: .a <name>");

		String name = args[0];
		executeAlias(name, 0);
	}

	private void executeAlias(String name, int depth)
	{
		if(depth > MAX_RECURSION)
		{
			ChatUtils.error("Max alias recursion depth reached!");
			return;
		}

		String command = AliasManager.getAlias(name);
		if(command == null)
		{
			ChatUtils.error("Alias '" + name + "' does not exist.");
			return;
		}

		if(command.startsWith("."))
		{
			// Run as Wurst command
			String cmdText = command.substring(1);
			if(cmdText.toLowerCase().startsWith("a "))
			{
				String nestedName = cmdText.substring(2).trim();
				executeAlias(nestedName, depth + 1);
			}else
			{
				WurstClient.INSTANCE.getCmdProcessor().process(cmdText);
			}
		}else
		{
			if(MC.getConnection() == null)
			{
				ChatUtils.error("Not connected to a server or world.");
				return;
			}
			if(command.startsWith("/"))
				MC.getConnection().sendCommand(command.substring(1));
			else
				MC.getConnection().sendChat(command);
		}
	}
}
