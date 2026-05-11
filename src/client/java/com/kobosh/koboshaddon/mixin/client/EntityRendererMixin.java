package com.kobosh.koboshaddon.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.kobosh.koboshaddon.client.hack.CordTagsHack;

import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.wurstclient.WurstClient;
import net.wurstclient.hack.Hack;

@Mixin(targets = "net.minecraft.class_897", priority = 500)
public abstract class EntityRendererMixin<T extends Entity, S extends EntityRenderState>
{
	@Inject(
		method = "method_62354(Lnet/minecraft/class_1297;Lnet/minecraft/class_10017;F)V",
		remap = false,
		at = @At("TAIL"))
	private void addCoordToDisplayName(T entity, S state, float tickProgress,
		CallbackInfo ci)
	{
		EntityRenderStateAccessor accessor = (EntityRenderStateAccessor)state;
		if(accessor.koboshaddon$getNameTag() == null)
			return;
		if(!(entity instanceof PlayerEntity player))
			return;

		Hack hack = WurstClient.INSTANCE.getHax().getHackByName("CordTags");
		if(!(hack instanceof CordTagsHack cordTags) || !cordTags.isEnabled())
			return;

		Text nameTag = accessor.koboshaddon$getNameTag();
		accessor.koboshaddon$setNameTag(cordTags.addCoord(player, nameTag.copy()));
	}
}