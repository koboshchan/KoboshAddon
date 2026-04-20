/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package com.kobosh.koboshaddon.client.hack;

import org.lwjgl.glfw.GLFW;

import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.wurstclient.Category;
import net.wurstclient.events.GUIRenderListener;
import net.wurstclient.events.RenderListener;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.util.ChatUtils;
import net.wurstclient.util.RenderUtils;

public final class NBTViewerHack extends Hack
	implements UpdateListener, RenderListener, GUIRenderListener
{
	private BlockPos posLookingAt;
	private BlockPos selectedPos;
	private String nbtData;
	private boolean showingNBT;
	
	public NBTViewerHack()
	{
		super("NBTViewer");
		setCategory(Category.RENDER);
	}
	
	@Override
	protected void onEnable()
	{
		posLookingAt = null;
		selectedPos = null;
		nbtData = null;
		showingNBT = false;
		
		EVENTS.add(UpdateListener.class, this);
		EVENTS.add(RenderListener.class, this);
		EVENTS.add(GUIRenderListener.class, this);
	}
	
	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		EVENTS.remove(RenderListener.class, this);
		EVENTS.remove(GUIRenderListener.class, this);
		
		posLookingAt = null;
		selectedPos = null;
		nbtData = null;
		showingNBT = false;
	}
	
	@Override
	public void onUpdate()
	{
		handleBlockSelection();
	}
	
	private void handleBlockSelection()
	{
		// Get block player is looking at
		if(MC.crosshairTarget instanceof BlockHitResult)
		{
			posLookingAt = ((BlockHitResult)MC.crosshairTarget).getBlockPos();
			
			// Offset if sneaking (to select air blocks or adjacent positions)
			if(MC.options.sneakKey.isPressed())
				posLookingAt = posLookingAt
					.offset(((BlockHitResult)MC.crosshairTarget).getSide());
			
		}else
			posLookingAt = null;
		
		// Select block and show NBT data
		if(posLookingAt != null && MC.options.useKey.isPressed())
		{
			selectedPos = posLookingAt;
			showNBTData(selectedPos);
		}
		
		// Close NBT view with ESC
		if(showingNBT && InputUtil.isKeyPressed(MC.getWindow().getHandle(),
			GLFW.GLFW_KEY_ESCAPE))
		{
			showingNBT = false;
			nbtData = null;
			selectedPos = null;
		}
	}
	
	private void showNBTData(BlockPos pos)
	{
		try
		{
			// Get block entity at position
			BlockEntity blockEntity = MC.world.getBlockEntity(pos);
			
			if(blockEntity != null)
			{
				// Get NBT data from block entity
				NbtCompound nbt =
					blockEntity.createNbt(MC.world.getRegistryManager());
				
				if(nbt != null && !nbt.isEmpty())
				{
					// Format NBT data for display
					nbtData = formatNBTData(nbt, pos);
					showingNBT = true;
					
					// Also send to chat
					ChatUtils.message(
						"NBT data for block at " + pos.toShortString() + ":");
					for(String line : nbtData.split("\n"))
					{
						if(!line.trim().isEmpty())
							ChatUtils.message("  " + line);
					}
				}else
				{
					ChatUtils.message("Block at " + pos.toShortString()
						+ " has no NBT data.");
					showingNBT = false;
					nbtData = null;
				}
			}else
			{
				ChatUtils.message("Block at " + pos.toShortString()
					+ " has no block entity (no NBT data).");
				showingNBT = false;
				nbtData = null;
			}
			
		}catch(Exception e)
		{
			ChatUtils.error("Error reading NBT data: " + e.getMessage());
			showingNBT = false;
			nbtData = null;
		}
	}
	
	private String formatNBTData(NbtCompound nbt, BlockPos pos)
	{
		StringBuilder sb = new StringBuilder();
		sb.append("Block Position: ").append(pos.toShortString()).append("\n");
		sb.append("NBT Data:\n");
		
		// Convert NBT to string
		String nbtString = nbt.toString();
		
		// Add some basic formatting
		String[] lines = nbtString.split(",");
		int indentLevel = 0;
		
		for(String line : lines)
		{
			line = line.trim();
			
			// Decrease indent for closing brackets
			if(line.contains("}"))
				indentLevel = Math.max(0, indentLevel - 1);
			
			// Add indentation
			for(int i = 0; i < indentLevel; i++)
				sb.append("  ");
			
			sb.append(line);
			
			// Don't add newline for the last entry
			if(!line.equals(lines[lines.length - 1]))
				sb.append("\n");
			
			// Increase indent for opening brackets
			if(line.contains("{") && !line.contains("}"))
				indentLevel++;
		}
		
		return sb.toString();
	}
	
	@Override
	public void onRender(MatrixStack matrixStack, float partialTicks)
	{
		int black = 0x80000000;
		int gray = 0x26404040;
		int blue = 0x4D0099FF;
		int green = 0x4D00FF00;
		
		// Highlight selected block
		if(selectedPos != null)
		{
			Box box = new Box(selectedPos).contract(1 / 16.0);
			RenderUtils.drawOutlinedBox(matrixStack, box, black, false);
			RenderUtils.drawSolidBox(matrixStack, box, blue, false);
		}
		
		// Highlight block looking at
		if(posLookingAt != null && !posLookingAt.equals(selectedPos))
		{
			Box box = new Box(posLookingAt).contract(1 / 16.0);
			RenderUtils.drawOutlinedBox(matrixStack, box, black, false);
			RenderUtils.drawSolidBox(matrixStack, box, gray, false);
		}
	}
	
	@Override
	public void onRenderGUI(DrawContext context, float partialTicks)
	{
		String message;
		
		if(showingNBT && nbtData != null)
		{
			// Show NBT data in a scrollable window
			renderNBTWindow(context);
			return;
		}
		
		// Show instruction message
		if(selectedPos != null)
			message =
				"Block selected. Look at another block and right-click to view its NBT data, or press ESC to clear.";
		else
			message = "Look at a block and right-click to view its NBT data.";
		
		TextRenderer tr = MC.textRenderer;
		int msgWidth = tr.getWidth(message);
		
		int msgX1 = context.getScaledWindowWidth() / 2 - msgWidth / 2;
		int msgX2 = msgX1 + msgWidth + 2;
		int msgY1 = context.getScaledWindowHeight() / 2 + 1;
		int msgY2 = msgY1 + 10;
		
		// background
		context.fill(msgX1, msgY1, msgX2, msgY2, 0x80000000);
		
		// text
		context.drawText(tr, message, msgX1 + 2, msgY1 + 1, 0xFFFFFFFF, false);
	}
	
	private void renderNBTWindow(DrawContext context)
	{
		if(nbtData == null)
			return;
		
		TextRenderer tr = MC.textRenderer;
		String[] lines = nbtData.split("\n");
		
		// Calculate window size
		int maxWidth = 0;
		for(String line : lines)
			maxWidth = Math.max(maxWidth, tr.getWidth(line));
		
		int windowWidth =
			Math.min(maxWidth + 20, context.getScaledWindowWidth() - 40);
		int windowHeight = Math.min(lines.length * 10 + 30,
			context.getScaledWindowHeight() - 40);
		
		int windowX = (context.getScaledWindowWidth() - windowWidth) / 2;
		int windowY = (context.getScaledWindowHeight() - windowHeight) / 2;
		
		// Window background
		context.fill(windowX, windowY, windowX + windowWidth,
			windowY + windowHeight, 0xC0000000);
		
		// Window border
		context.drawBorder(windowX, windowY, windowWidth, windowHeight,
			0xFFFFFFFF);
		
		// Title bar
		context.fill(windowX + 1, windowY + 1, windowX + windowWidth - 1,
			windowY + 12, 0xFF333333);
		context.drawText(tr, "NBT Data Viewer (Press ESC to close)",
			windowX + 5, windowY + 3, 0xFFFFFFFF, false);
		
		// Content area
		int contentY = windowY + 15;
		int contentHeight = windowHeight - 15;
		int maxLines = (contentHeight - 10) / 10;
		
		// Draw NBT data lines
		for(int i = 0; i < Math.min(lines.length, maxLines); i++)
		{
			String line = lines[i];
			if(tr.getWidth(line) > windowWidth - 20)
			{
				// Truncate long lines
				while(tr.getWidth(line + "...") > windowWidth - 20
					&& line.length() > 0)
					line = line.substring(0, line.length() - 1);
				line += "...";
			}
			
			context.drawText(tr, line, windowX + 5, contentY + i * 10,
				0xFFFFFFFF, false);
		}
		
		// Show scroll indicator if there are more lines
		if(lines.length > maxLines)
		{
			String scrollText =
				"... (" + (lines.length - maxLines) + " more lines)";
			context.drawText(tr, scrollText, windowX + 5,
				contentY + maxLines * 10, 0xFF999999, false);
		}
	}
}
