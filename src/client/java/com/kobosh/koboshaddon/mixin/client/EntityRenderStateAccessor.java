package com.kobosh.koboshaddon.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.network.chat.Component;

@Mixin(targets = "net.minecraft.class_10017", remap = false)
public interface EntityRenderStateAccessor
{
	@Accessor(value = "field_53337", remap = false)
    Component koboshaddon$getNameTag();

	@Accessor(value = "field_53337", remap = false)
	void koboshaddon$setNameTag(Component nameTag);
}