/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package com.kobosh.koboshaddon.client.command;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import com.kobosh.koboshaddon.client.AddonFeatureRegistry;
import com.kobosh.koboshaddon.client.hack.BlockLoggerHack;
import com.google.gson.JsonObject;

import net.wurstclient.DontBlock;
import net.wurstclient.WurstClient;
import net.wurstclient.command.CmdError;
import net.wurstclient.command.CmdException;
import net.wurstclient.command.CmdSyntaxError;
import net.wurstclient.command.Command;
import net.wurstclient.util.ChatUtils;
import net.wurstclient.util.json.JsonUtils;

@DontBlock
public final class ViewLogsCmd extends Command
{
	public ViewLogsCmd()
	{
		super("viewlogs", "View and highlight blocks from saved JSON logs.",
			".viewlogs list [<page>]", ".viewlogs load <filename>",
			".viewlogs clear", ".viewlogs help",
			"Files are saved in '.minecraft/wurst/block_logs'.");
	}
	
	@Override
	public void call(String[] args) throws CmdException
	{
		if(args.length < 1)
			throw new CmdSyntaxError();
		
		switch(args[0].toLowerCase())
		{
			case "list":
			listLogs(args);
			break;
			
			case "load":
			loadLog(args);
			break;
			
			case "clear":
			clearLogs();
			break;
			
			case "help":
			printViewLogsHelp();
			break;
			
			default:
			throw new CmdSyntaxError();
		}
	}
	
	private void listLogs(String[] args) throws CmdException
	{
		Path logsFolder =
			WurstClient.INSTANCE.getWurstFolder().resolve("block_logs");
		
		if(!Files.exists(logsFolder))
		{
			ChatUtils.message(
				"No block logs folder found. Create some logs first with BlockLogger.");
			return;
		}
		
		List<Path> files = new ArrayList<>();
		try(Stream<Path> stream = Files.list(logsFolder))
		{
			files = stream.filter(Files::isRegularFile)
				.filter(path -> path.getFileName().toString().endsWith(".json"))
				.sorted((a, b) -> b.getFileName().toString()
					.compareTo(a.getFileName().toString()))
				.toList();
		}catch(IOException e)
		{
			throw new CmdError("Failed to list log files: " + e.getMessage());
		}
		
		if(files.isEmpty())
		{
			ChatUtils.message("No log files found.");
			return;
		}
		
		// Parse page number
		int page = 1;
		if(args.length >= 2)
		{
			try
			{
				page = Integer.parseInt(args[1]);
			}catch(NumberFormatException e)
			{
				throw new CmdSyntaxError("Invalid page number.");
			}
		}
		
		// Pagination
		int pageSize = 8;
		int maxPage = (int)Math.ceil((double)files.size() / pageSize);
		if(page < 1 || page > maxPage)
		{
			throw new CmdError(
				"Invalid page. Must be between 1 and " + maxPage + ".");
		}
		
		int startIndex = (page - 1) * pageSize;
		int endIndex = Math.min(startIndex + pageSize, files.size());
		List<Path> pageFiles = files.subList(startIndex, endIndex);
		
		ChatUtils
			.message("Block log files (Page " + page + "/" + maxPage + "):");
		
		for(Path file : pageFiles)
		{
			String fileName = file.getFileName().toString();
			try
			{
				JsonObject data =
					JsonUtils.parseFileToObject(file).toJsonObject();
				String blockType = data.has("block_type")
					? data.get("block_type").getAsString() : "unknown";
				int blockCount = data.has("blocks")
					? data.getAsJsonArray("blocks").size() : 0;
				long createdTime = data.has("created_time")
					? data.get("created_time").getAsLong() : 0;
				
				ChatUtils.message("§7" + fileName + " §r- " + blockType + " ("
					+ blockCount + " blocks)");
			}catch(Exception e)
			{
				ChatUtils.message("§7" + fileName + " §c- corrupted file");
			}
		}
		
		if(page < maxPage)
			ChatUtils.message(
				"Use '.viewlogs list " + (page + 1) + "' for more files.");
	}
	
	private void loadLog(String[] args) throws CmdException
	{
		if(args.length != 2)
			throw new CmdSyntaxError();
		
		String fileName = args[1];
		if(!fileName.endsWith(".json"))
			fileName += ".json";
		
		Path logsFolder =
			WurstClient.INSTANCE.getWurstFolder().resolve("block_logs");
		Path filePath = logsFolder.resolve(fileName);
		
		if(!Files.exists(filePath))
		{
			throw new CmdError("Log file '" + fileName + "' not found.");
		}
		
		try
		{
			JsonObject data =
				JsonUtils.parseFileToObject(filePath).toJsonObject();
			
			// Get BlockLogger hack
			BlockLoggerHack blockLogger = AddonFeatureRegistry.blockLoggerHack;
			
			// Load block type and set it
			if(data.has("block_type"))
			{
				String blockType = data.get("block_type").getAsString();
				blockLogger.getSettings().values().stream()
					.filter(setting -> setting.getName().equals("Block Type"))
					.findFirst().ifPresent(
						setting -> ((net.wurstclient.settings.TextFieldSetting)setting)
							.setValue(blockType));
			}
			
			// Clear existing blocks and load new ones
			blockLogger.clearFoundBlocks();
			blockLogger.addBlocksFromJson(data);
			
			// Enable the hack to show highlights
			if(!blockLogger.isEnabled())
				blockLogger.setEnabled(true);
			
			int blockCount =
				data.has("blocks") ? data.getAsJsonArray("blocks").size() : 0;
			String blockType = data.has("block_type")
				? data.get("block_type").getAsString() : "unknown";
			
			ChatUtils.message("Loaded " + blockCount + " " + blockType
				+ " blocks from " + fileName);
			ChatUtils.message("Blocks are now highlighted with BlockLogger.");
			
		}catch(Exception e)
		{
			throw new CmdError("Failed to load log file: " + e.getMessage());
		}
	}
	
	private void clearLogs() throws CmdException
	{
		BlockLoggerHack blockLogger = AddonFeatureRegistry.blockLoggerHack;
		blockLogger.clearFoundBlocks();
		
		ChatUtils.message("Cleared all highlighted blocks from BlockLogger.");
	}

	private void printViewLogsHelp()
	{
		ChatUtils.message("ViewLogs commands:");
		ChatUtils.message(".viewlogs list [page]");
		ChatUtils.message(".viewlogs load <filename>");
		ChatUtils.message(".viewlogs clear");
	}
}
