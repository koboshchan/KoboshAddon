package com.kobosh.koboshaddon.client.command;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.wurstclient.WurstClient;

public final class AliasManager
{
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path FILE_PATH = WurstClient.INSTANCE.getWurstFolder().resolve("aliases.json");
	private static final Map<String, String> aliases = new HashMap<>();

	static
	{
		load();
	}

	public static synchronized void load()
	{
		if(!Files.exists(FILE_PATH))
			return;
		try(Reader reader = Files.newBufferedReader(FILE_PATH))
		{
			java.lang.reflect.Type type = new TypeToken<Map<String, String>>(){}.getType();
			Map<String, String> loaded = GSON.fromJson(reader, type);
			if(loaded != null)
			{
				aliases.clear();
				for(Map.Entry<String, String> entry : loaded.entrySet())
				{
					aliases.put(entry.getKey().toLowerCase(), entry.getValue());
				}
			}
		}catch(Exception e)
		{
			e.printStackTrace();
		}
	}

	public static synchronized void save()
	{
		try
		{
			Files.createDirectories(FILE_PATH.getParent());
			try(Writer writer = Files.newBufferedWriter(FILE_PATH))
			{
				// Use TreeMap so listed items are sorted alphabetically in the JSON file
				GSON.toJson(new TreeMap<>(aliases), writer);
			}
		}catch(Exception e)
		{
			e.printStackTrace();
		}
	}

	public static synchronized Map<String, String> getAliases()
	{
		return new TreeMap<>(aliases);
	}

	public static synchronized void addAlias(String name, String command)
	{
		aliases.put(name.toLowerCase(), command);
		save();
	}

	public static synchronized boolean hasAlias(String name)
	{
		return aliases.containsKey(name.toLowerCase());
	}

	public static synchronized String getAlias(String name)
	{
		return aliases.get(name.toLowerCase());
	}

	public static synchronized boolean removeAlias(String name)
	{
		if(aliases.remove(name.toLowerCase()) != null)
		{
			save();
			return true;
		}
		return false;
	}
}
