package com.kobosh.koboshaddon.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.network.chat.Component;

@Mixin(targets = "net.minecraft.client.renderer.entity.state.EntityRenderState", remap = false)
public interface EntityRenderStateAccessor
{
	@Accessor(value = "nameTag", remap = false)
    Component koboshaddon$getNameTag();

	@Accessor(value = "nameTag", remap = false)
	void koboshaddon$setNameTag(Component nameTag);
}