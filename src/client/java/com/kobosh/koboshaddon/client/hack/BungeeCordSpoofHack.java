package com.kobosh.koboshaddon.client.hack;

import java.util.Arrays;
import java.util.List;

import net.minecraft.client.multiplayer.ServerData;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.TextFieldSetting;

@SearchTags({"bungeecord spoof", "bungee spoof", "bungee bypass"})
public final class BungeeCordSpoofHack extends Hack
{
	private final CheckboxSetting whitelist = new CheckboxSetting("Whitelist",
		"Only spoof IP when joining whitelisted servers.", false);

	private final TextFieldSetting servers = new TextFieldSetting("Servers",
		"Semicolon-separated whitelisted server IPs.", "localhost");

	private final TextFieldSetting forwardedIP = new TextFieldSetting("Forwarded IP",
		"The spoofed IP to forward to the backend server.", "127.0.0.1");

	private final CheckboxSetting spoofProfile = new CheckboxSetting("Spoof Profile",
		"Forward profile token (requires online-mode backend).", false);

	public BungeeCordSpoofHack()
	{
		super("BungeeCordSpoof");
		setCategory(Category.OTHER);
		addSetting(whitelist);
		addSetting(servers);
		addSetting(forwardedIP);
		addSetting(spoofProfile);
	}

	@Override
	protected void onEnable()
	{
	}

	@Override
	protected void onDisable()
	{
	}

	public String getSpoofedAddress(String host)
	{
		if(whitelist.isChecked())
		{
			ServerData serverData = MC.getCurrentServer();
			String currentIp = serverData != null ? serverData.ip : "localhost";
			List<String> allowed = Arrays.asList(servers.getValue().split(";"));
			boolean found = false;
			for(String ip : allowed)
			{
				if(currentIp.toLowerCase().contains(ip.trim().toLowerCase()))
				{
					found = true;
					break;
				}
			}
			if(!found)
				return null;
		}

		String uuid = MC.getUser().getProfileId().toString().replace("-", "");
		String address = host + "\0" + forwardedIP.getValue() + "\0" + uuid;

		if(spoofProfile.isChecked())
		{
			com.google.gson.Gson gson = new com.google.gson.Gson();
			com.mojang.authlib.properties.PropertyMap properties = MC.getGameProfile().properties();
			address += "\0" + gson.toJson(properties.values().toArray());
		}

		return address;
	}
}
