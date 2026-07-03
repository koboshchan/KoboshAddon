package com.kobosh.koboshaddon.client.hack;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.wurstclient.Category;
import net.wurstclient.WurstClient;
import net.wurstclient.events.GUIRenderListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.ColorSetting;
import net.wurstclient.settings.EnumSetting;

public final class HackList2Hack extends Hack implements GUIRenderListener
{
	private final ColorSetting color = new ColorSetting("Color",
		"Color of the HackList text.", Color.WHITE);
	
	private final EnumSetting<Position> position = new EnumSetting<>("Position",
		"Which side of the screen the HackList should be shown on.",
		Position.values(), Position.RIGHT);

	private final EnumSetting<VerticalPosition> verticalPosition = new EnumSetting<>("Vertical position",
		"Whether the HackList should be shown at the top or bottom of the screen.",
		VerticalPosition.values(), VerticalPosition.TOP);
	
	private final Map<Hack, CheckboxSetting> visibilitySettings =
		new HashMap<>();
	
	public HackList2Hack()
	{
		super("HackList2");
		setCategory(Category.RENDER);
		addSetting(color);
		addSetting(position);
		addSetting(verticalPosition);
		
		// Initial population of settings
		updateSettings();
	}
	
	private void updateSettings()
	{
		List<Hack> allHax =
			new ArrayList<>(WurstClient.INSTANCE.getHax().getAllHax());
		allHax.sort(Comparator.comparing(Hack::getName));
		
		for(Hack hack : allHax)
		{
			if(hack == this || visibilitySettings.containsKey(hack))
				continue;
			
			CheckboxSetting setting =
				new CheckboxSetting("Show " + hack.getName(), true);
			visibilitySettings.put(hack, setting);
			addSetting(setting);
		}
	}
	
	@Override
	public void onEnable()
	{
		EVENTS.add(GUIRenderListener.class, this);
	}
	
	@Override
	public void onDisable()
	{
		EVENTS.remove(GUIRenderListener.class, this);
	}
	
	@Override
	public void onRenderGUI(GuiGraphicsExtractor context, float partialTicks)
	{
		// Refresh settings in case new hacks were added by other addons
		if(visibilitySettings.size() < WurstClient.INSTANCE.getHax().countHax() - 1)
			updateSettings();
		
		List<Hack> activeHax = WurstClient.INSTANCE.getHax().getAllHax()
			.stream().filter(Hack::isEnabled)
			.filter(h -> {
				CheckboxSetting setting = visibilitySettings.get(h);
				return setting == null || setting.isChecked();
			})
			.sorted(Comparator.comparing(Hack::getRenderName))
			.toList();
		
		if(activeHax.isEmpty())
			return;
		
		Font tr = MC.font;
		int textColor = color.getColorI();
		int screenHeight = context.guiHeight();
		boolean isBottom = verticalPosition.getSelected() == VerticalPosition.BOTTOM;
		int posY = isBottom ? screenHeight - 11 : 2;
		
		for(Hack hack : activeHax)
		{
			String s = hack.getRenderName();
			int posX;
			
			if(position.getSelected() == Position.LEFT)
				posX = 2;
			else
			{
				int screenWidth = context.guiWidth();
				int stringWidth = tr.width(s);
				posX = screenWidth - stringWidth - 2;
			}
			
			context.text(tr, s, posX + 1, posY + 1, 0xFF000000, false);
			context.text(tr, s, posX, posY, textColor | 0xFF000000, false);
			
			if(isBottom)
				posY -= 9;
			else
				posY += 9;
		}
	}
	
	public enum Position
	{
		LEFT("Left"),
		RIGHT("Right");
		
		private final String name;
		
		private Position(String name)
		{
			this.name = name;
		}
		
		@Override
		public String toString()
		{
			return name;
		}
	}

	public enum VerticalPosition
	{
		TOP("Top"),
		BOTTOM("Bottom");
		
		private final String name;
		
		private VerticalPosition(String name)
		{
			this.name = name;
		}
		
		@Override
		public String toString()
		{
			return name;
		}
	}
}
