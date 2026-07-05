package com.kobosh.koboshaddon.client.hack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.PacketInputListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.TextFieldSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;

@SearchTags({"login command", "login", "auto command", "join command"})
public final class LoginCommandHack extends Hack implements PacketInputListener
{
	private final SliderSetting delay = new SliderSetting("Delay (ms)",
		"Delay in milliseconds before executing the commands.", 1000, 0, 5000, 50, ValueDisplay.INTEGER);

	private final TextFieldSetting commands = new TextFieldSetting("Commands",
		"Semicolon-separated list of commands to run on join.", ".togglepvp");

	private final CheckboxSetting whitelist = new CheckboxSetting("Whitelist",
		"Only run on specific servers.", false);

	private final TextFieldSetting servers = new TextFieldSetting("Servers",
		"Semicolon-separated list of whitelisted server IPs.", "localhost");

	private final Timer timer = new Timer();

	public LoginCommandHack()
	{
		super("LoginCommand");
		setCategory(Category.OTHER);
		addSetting(delay);
		addSetting(commands);
		addSetting(whitelist);
		addSetting(servers);
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
		if(event.getPacket() instanceof ClientboundLoginPacket)
		{
			if(whitelist.isChecked() && !isIpWhitelisted())
				return;

			List<String> cmds = Arrays.asList(commands.getValue().split(";"));
			long delayMs = (long)delay.getValue();

			for(String cmd : cmds)
			{
				final String finalCmd = cmd.trim();
				if(finalCmd.isEmpty())
					continue;

				timer.schedule(new TimerTask()
				{
					@Override
					public void run()
					{
						MC.execute(() -> {
							if(MC.player != null && MC.player.connection != null)
							{
								if(finalCmd.startsWith("."))
								{
									net.wurstclient.WurstClient.INSTANCE.getCmdProcessor().process(finalCmd.substring(1));
								}
								else
								{
									MC.player.connection.sendChat(finalCmd);
								}
							}
						});
					}
				}, delayMs);
			}
		}
	}

	private boolean isIpWhitelisted()
	{
		List<String> allowed = Arrays.asList(servers.getValue().split(";"));
		
		ServerData serverData = MC.getCurrentServer();
		String serverIp = serverData != null ? serverData.ip : null;
		
		String remoteAddress = null;
		try {
			if (MC.getConnection() != null && MC.getConnection().getConnection() != null) {
				java.net.SocketAddress addr = MC.getConnection().getConnection().getRemoteAddress();
				if (addr != null) {
					remoteAddress = addr.toString();
				}
			}
		} catch (Exception e) {}

		for(String ip : allowed)
		{
			String cleaned = ip.trim().toLowerCase();
			if(cleaned.isEmpty())
				continue;
			
			if(serverIp != null && (serverIp.toLowerCase().contains(cleaned) || cleaned.contains(serverIp.toLowerCase())))
				return true;
				
			if(remoteAddress != null && (remoteAddress.toLowerCase().contains(cleaned) || cleaned.contains(remoteAddress.toLowerCase())))
				return true;
				
			if(cleaned.equals("localhost") || cleaned.equals("127.0.0.1"))
			{
				if(serverIp == null || serverIp.isEmpty() || serverIp.equals("localhost") || serverIp.equals("127.0.0.1"))
					return true;
			}
		}
		
		return false;
	}
}
