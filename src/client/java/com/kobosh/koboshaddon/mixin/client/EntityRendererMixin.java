package com.kobosh.koboshaddon.mixin.client;

import java.lang.reflect.Field;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.kobosh.koboshaddon.client.hack.CordTagsHack;

import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.wurstclient.WurstClient;
import net.wurstclient.hack.Hack;

@Mixin(value = EntityRenderer.class, priority = 500)
public abstract class EntityRendererMixin<T extends Entity, S extends EntityRenderState>
{
	private static final String[] NAME_FIELD_CANDIDATES =
		{"nameTag", "name", "displayName"};
	private static Field cachedNameField;

	@Inject(
		method = "extractRenderState(Lnet/minecraft/entity/Entity;Lnet/minecraft/client/render/entity/state/EntityRenderState;F)V",
		at = @At("TAIL"))
	private void addCoordToDisplayName(T entity, S state, float tickProgress,
		CallbackInfo ci)
	{
		if(!(entity instanceof PlayerEntity player))
			return;

		Text nameTag = getNameTag(state);
		if(nameTag == null)
			return;

		Hack hack = WurstClient.INSTANCE.getHax().getHackByName("CordTags");
		if(!(hack instanceof CordTagsHack cordTags) || !cordTags.isEnabled())
			return;

		setNameTag(state, cordTags.addCoord(player, nameTag.copy()));
	}

	private Text getNameTag(S state)
	{
		Field field = getNameField(state);
		if(field == null)
			return null;

		try
		{
			return (Text)field.get(state);
		}catch(ReflectiveOperationException e)
		{
			return null;
		}
	}

	private void setNameTag(S state, Text value)
	{
		Field field = getNameField(state);
		if(field == null)
			return;

		try
		{
			field.set(state, value);
		}catch(ReflectiveOperationException e)
		{
			// Ignore if this version stores the name in a different way.
		}
	}

	private Field getNameField(S state)
	{
		if(cachedNameField != null)
			return cachedNameField;

		Class<?> clazz = state.getClass();
		for(String candidate : NAME_FIELD_CANDIDATES)
		{
			try
			{
				Field field = clazz.getField(candidate);
				if(Text.class.isAssignableFrom(field.getType()))
				{
					cachedNameField = field;
					return field;
				}
			}catch(NoSuchFieldException e)
			{
				// Try next candidate name.
			}
		}

		for(Field field : clazz.getFields())
			if(Text.class.isAssignableFrom(field.getType()))
			{
				cachedNameField = field;
				return field;
			}

		return null;
	}
}