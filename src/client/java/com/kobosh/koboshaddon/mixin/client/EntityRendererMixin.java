package com.kobosh.koboshaddon.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.kobosh.koboshaddon.client.hack.CordTagsHack;

import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.network.chat.Component;
import net.wurstclient.WurstClient;
import net.wurstclient.hack.Hack;

@Mixin(value = net.minecraft.client.renderer.entity.EntityRenderer.class, priority = 500)
public abstract class EntityRendererMixin<T extends Entity, S extends EntityRenderState>
{
	@Inject(
		method = "extractRenderState(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/client/renderer/entity/state/EntityRenderState;F)V",
		remap = false,
		at = @At("TAIL"))
	private void addCoordToDisplayName(T entity, S state, float tickProgress,
		CallbackInfo ci)
	{
		EntityRenderStateAccessor accessor = (EntityRenderStateAccessor)state;
		if(accessor.koboshaddon$getNameTag() == null)
			return;
		if(!(entity instanceof Player player))
			return;

		Hack hack = WurstClient.INSTANCE.getHax().getHackByName("CordTags");
		if(!(hack instanceof CordTagsHack cordTags) || !cordTags.isEnabled())
			return;

		Component nameTag = accessor.koboshaddon$getNameTag();
		accessor.koboshaddon$setNameTag(cordTags.addCoord(player, nameTag.copy()));
	}
}