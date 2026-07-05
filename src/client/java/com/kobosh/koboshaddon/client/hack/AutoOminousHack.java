package com.kobosh.koboshaddon.client.hack;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.components.LerpingBossEvent;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.WurstClient;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.ItemListSetting;
import net.wurstclient.util.InventoryUtils;

@SearchTags({"auto ominous", "ominous bottle", "auto drink"})
public final class AutoOminousHack extends Hack implements UpdateListener
{
	private final ItemListSetting items = new ItemListSetting("Items",
		"Items to automatically drink.", "minecraft:ominous_bottle");

	private final CheckboxSetting pauseAuras = new CheckboxSetting("Pause Auras",
		"Pauses combat hacks while drinking.", true);

	private boolean drinking = false;
	private int oldSlot = -1;
	private final List<Hack> pausedHacks = new ArrayList<>();

	@SuppressWarnings("unchecked")
	private static final Class<? extends Hack>[] AURAS = new Class[]{
		net.wurstclient.hacks.KillauraHack.class,
		net.wurstclient.hacks.CrystalAuraHack.class,
		net.wurstclient.hacks.AnchorAuraHack.class,
		net.wurstclient.hacks.ClickAuraHack.class,
		net.wurstclient.hacks.MultiAuraHack.class,
		net.wurstclient.hacks.TpAuraHack.class
	};

	public AutoOminousHack()
	{
		super("AutoOminous");
		setCategory(Category.COMBAT);
		addSetting(items);
		addSetting(pauseAuras);
	}

	@Override
	protected void onEnable()
	{
		EVENTS.add(UpdateListener.class, this);
		drinking = false;
		oldSlot = -1;
		pausedHacks.clear();
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		if(drinking)
			stopDrinking();
	}

	@Override
	public void onUpdate()
	{
		if(MC.player == null || MC.level == null)
			return;

		if(isRaidActive())
		{
			if(drinking)
				stopDrinking();
			return;
		}

		int slot = findBottleSlot();
		if(slot == -1)
		{
			if(drinking)
				stopDrinking();
			return;
		}

		if(!drinking)
		{
			startDrinking(slot);
		}
		else
		{
			// Keep using
			InventoryUtils.selectItem(slot);
			MC.options.keyUse.setDown(true);
			IMC.getInteractionManager().rightClickItem();
		}
	}

	private boolean isRaidActive()
	{
		if(MC.player.hasEffect(MobEffects.BAD_OMEN) ||
		   MC.player.hasEffect(MobEffects.TRIAL_OMEN) ||
		   MC.player.hasEffect(MobEffects.RAID_OMEN))
			return true;

		if(MC.gui.hud.getBossOverlay() != null && MC.gui.hud.getBossOverlay().events != null)
		{
			for(LerpingBossEvent event : MC.gui.hud.getBossOverlay().events.values())
			{
				if(event.getName().getString().toLowerCase().contains("raid"))
					return true;
			}
		}

		return false;
	}

	private int findBottleSlot()
	{
		Inventory inventory = MC.player.getInventory();
		for(int i = 0; i < 9; i++)
		{
			ItemStack stack = inventory.getItem(i);
			String name = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
			if(items.getItemNames().contains(name))
				return i;
		}
		return -1;
	}

	private void startDrinking(int slot)
	{
		oldSlot = MC.player.getInventory().getSelectedSlot();
		drinking = true;

		// Pause auras
		pausedHacks.clear();
		if(pauseAuras.isChecked())
		{
			for(Class<? extends Hack> clazz : AURAS)
			{
				Hack hack = getHack(clazz);
				if(hack != null && hack.isEnabled())
				{
					hack.setEnabled(false);
					pausedHacks.add(hack);
				}
			}
		}

		InventoryUtils.selectItem(slot);
		MC.options.keyUse.setDown(true);
		IMC.getInteractionManager().rightClickItem();
	}

	private void stopDrinking()
	{
		MC.options.keyUse.setDown(false);
		drinking = false;

		if(oldSlot != -1)
		{
			InventoryUtils.selectItem(oldSlot);
			oldSlot = -1;
		}

		// Resume auras
		for(Hack hack : pausedHacks)
		{
			hack.setEnabled(true);
		}
		pausedHacks.clear();
	}

	private static <T extends Hack> T getHack(Class<T> clazz)
	{
		for(Hack hack : WurstClient.INSTANCE.getHax().getAllHax())
		{
			if(clazz.isInstance(hack))
			{
				return clazz.cast(hack);
			}
		}
		return null;
	}
}
