package com.kobosh.koboshaddon.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import net.wurstclient.Feature;
import net.wurstclient.settings.Setting;

@Mixin(Feature.class)
public interface FeatureInvoker
{
	@Invoker("addSetting")
	void koboshaddon$addSetting(Setting setting);
}
