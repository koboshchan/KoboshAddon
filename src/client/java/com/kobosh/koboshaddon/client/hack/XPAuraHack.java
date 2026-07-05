package com.kobosh.koboshaddon.client.hack;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.phys.Vec3;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.WurstClient;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;

@SearchTags({"xp aura", "xp orb", "experience aura", "xp teleport"})
public final class XPAuraHack extends Hack implements UpdateListener
{
	private final SliderSetting distance = new SliderSetting("Distance",
		"Maximum distance to detect and teleport to XP orbs.", 8.0, 0, 50, 0.5, ValueDisplay.DECIMAL);

	private final CheckboxSetting pauseAuras = new CheckboxSetting("Pause Auras",
		"Pauses combat hacks while picking up XP.", true);

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

	public XPAuraHack()
	{
		super("XPAura");
		setCategory(Category.COMBAT);
		addSetting(distance);
		addSetting(pauseAuras);
	}

	@Override
	protected void onEnable()
	{
		EVENTS.add(UpdateListener.class, this);
		pausedHacks.clear();
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
	}

	@Override
	public void onUpdate()
	{
		if(MC.player == null || MC.level == null)
			return;

		for(Entity e : MC.level.entitiesForRendering())
		{
			if(e instanceof ExperienceOrb)
			{
				double dist = MC.player.distanceTo(e);
				if(dist <= distance.getValue() && MC.player.hasLineOfSight(e))
				{
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

					Vec3 startPos = MC.player.position();
					Vec3 endPos = e.position();

					splitTeleport(startPos, endPos, 8.5, 0);
					splitTeleport(endPos, startPos, 8.5, 0);

					// Resume auras
					for(Hack hack : pausedHacks)
					{
						hack.setEnabled(true);
					}
					pausedHacks.clear();
					break;
				}
			}
		}
	}

	private void splitTeleport(Vec3 from, Vec3 to, double perBlink, double extraDistance)
	{
		Vec3 playerPos = from;
		Vec3 targetPos = to;
		Vec3 toTarget = targetPos.subtract(from);

		double len = toTarget.length();
		double distance = len - extraDistance;
		if(distance <= 0)
			return;

		toTarget = toTarget.normalize().scale(distance);
		targetPos = playerPos.add(toTarget);

		double ceiledDistance = Math.ceil(distance / perBlink);
		for(int i = 1; i <= ceiledDistance; i++)
		{
			Vec3 tempPos = playerPos.lerp(targetPos, i / ceiledDistance);
			MC.player.connection.send(new ServerboundMovePlayerPacket.Pos(
				tempPos.x, tempPos.y, tempPos.z, true, MC.player.horizontalCollision));
		}
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
