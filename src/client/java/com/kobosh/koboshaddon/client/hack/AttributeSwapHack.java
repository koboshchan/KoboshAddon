package com.kobosh.koboshaddon.client.hack;

import java.util.Optional;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.LeftClickListener;
import net.wurstclient.events.PlayerAttacksEntityListener;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.util.InventoryUtils;

@SearchTags({"attribute swap", "shield breaker", "auto lunge", "lunge"})
public final class AttributeSwapHack extends Hack implements LeftClickListener, PlayerAttacksEntityListener, UpdateListener
{
	private final CheckboxSetting autoLunge = new CheckboxSetting("Auto Lunge",
		"Swap to a lunge enchanted weapon from hotbar on left-click.", false);

	private final CheckboxSetting shieldBreaker = new CheckboxSetting("Shield Breaker",
		"Swap to an axe when attacking blocking players.", false);

	private final CheckboxSetting noSwapAfterBreak = new CheckboxSetting("No Swap Back On Break",
		"Do not swap back to original slot if the player's shield was broken.", true);

	private final SliderSetting targetSlot = new SliderSetting("Target slot",
		"Slot to swap to on attack if not doing shield breaking/lunge.", 1, 1, 9, 1, ValueDisplay.INTEGER);

	private final CheckboxSetting swapBack = new CheckboxSetting("Swap back",
		"Swap back to the original slot after a delay.", true);

	private final SliderSetting delay = new SliderSetting("Swap-back delay",
		"Delay in ticks before swapping back.", 1, 1, 20, 1, ValueDisplay.INTEGER);

	private int prevSlot = -1;
	private int ticksLeft = 0;
	private boolean didSwap = false;
	private Registry<Enchantment> enchantmentRegistry;

	public AttributeSwapHack()
	{
		super("AttribSwap");
		setCategory(Category.COMBAT);
		addSetting(autoLunge);
		addSetting(shieldBreaker);
		addSetting(noSwapAfterBreak);
		addSetting(targetSlot);
		addSetting(swapBack);
		addSetting(delay);
	}

	@Override
	protected void onEnable()
	{
		EVENTS.add(LeftClickListener.class, this);
		EVENTS.add(PlayerAttacksEntityListener.class, this);
		EVENTS.add(UpdateListener.class, this);
		prevSlot = -1;
		ticksLeft = 0;
		didSwap = false;
		enchantmentRegistry = null;
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(LeftClickListener.class, this);
		EVENTS.remove(PlayerAttacksEntityListener.class, this);
		EVENTS.remove(UpdateListener.class, this);
	}

	@Override
	public void onLeftClick(LeftClickEvent event)
	{
		if(MC.player == null || MC.level == null || !autoLunge.isChecked())
			return;

		if(swapBack.isChecked() && !didSwap)
		{
			prevSlot = MC.player.getInventory().getSelectedSlot();
		}
		didSwap = false;

		if(enchantmentRegistry == null)
		{
			enchantmentRegistry = MC.level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
		}

		int bestSlot = -1;
		int bestLevel = 0;

		Optional<Holder.Reference<Enchantment>> lungeHolder = enchantmentRegistry.get(Enchantments.LUNGE);
		if(lungeHolder.isPresent())
		{
			for(int i = 0; i < 9; i++)
			{
				ItemStack stack = MC.player.getInventory().getItem(i);
				int level = EnchantmentHelper.getItemEnchantmentLevel(lungeHolder.get(), stack);
				if(level > 0 && level >= bestLevel)
				{
					bestSlot = i;
					bestLevel = level;
				}
			}
		}

		if(bestSlot != -1)
		{
			InventoryUtils.selectItem(bestSlot);
			didSwap = true;
			MC.player.connection.send(new ServerboundPlayerActionPacket(
				ServerboundPlayerActionPacket.Action.STAB,
				MC.player.blockPosition(),
				MC.player.getNearestViewDirection()
			));
		}

		if(swapBack.isChecked() && didSwap)
		{
			ticksLeft = (int)delay.getValue();
		}
	}

	@Override
	public void onPlayerAttacksEntity(Entity target)
	{
		if(MC.player == null || MC.level == null)
			return;

		if(swapBack.isChecked() && !didSwap)
		{
			prevSlot = MC.player.getInventory().getSelectedSlot();
		}
		didSwap = false;

		if(shieldBreaker.isChecked())
		{
			if(target instanceof Player player && player.isBlocking())
			{
				for(int i = 0; i < 9; i++)
				{
					ItemStack stack = MC.player.getInventory().getItem(i);
					if(stack.getItem() instanceof AxeItem)
					{
						InventoryUtils.selectItem(i);
						didSwap = true;
						break;
					}
				}
				if(didSwap && noSwapAfterBreak.isChecked())
				{
					// Stay on axe, do not swap back
					prevSlot = -1;
					ticksLeft = 0;
					return;
				}
			}
			else
			{
				InventoryUtils.selectItem((int)targetSlot.getValue() - 1);
				didSwap = true;
			}
		}
		else
		{
			InventoryUtils.selectItem((int)targetSlot.getValue() - 1);
			didSwap = true;
		}

		if(swapBack.isChecked() && didSwap)
		{
			ticksLeft = (int)delay.getValue();
		}
	}

	@Override
	public void onUpdate()
	{
		if(ticksLeft > 0)
		{
			ticksLeft--;
			if(ticksLeft == 0 && prevSlot != -1)
			{
				InventoryUtils.selectItem(prevSlot);
				prevSlot = -1;
				didSwap = false;
			}
		}
	}
}
