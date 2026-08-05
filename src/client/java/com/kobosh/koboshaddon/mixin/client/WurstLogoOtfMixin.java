package com.kobosh.koboshaddon.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.wurstclient.other_features.WurstLogoOtf;
import net.wurstclient.settings.CheckboxSetting;

@Mixin(WurstLogoOtf.class)
public abstract class WurstLogoOtfMixin
{
	@Unique
	private final CheckboxSetting koboshaddon$never = new CheckboxSetting(
		"Never",
		"Never show the Wurst logo, regardless of the Visibility setting above.",
		false);

	@Inject(method = "<init>", at = @At("RETURN"))
	private void koboshaddon$addNeverSetting(CallbackInfo ci)
	{
		((FeatureInvoker)this).koboshaddon$addSetting(koboshaddon$never);
	}

	@Inject(method = "isVisible", at = @At("HEAD"), cancellable = true)
	private void koboshaddon$overrideVisible(
		CallbackInfoReturnable<Boolean> cir)
	{
		if(koboshaddon$never.isChecked())
			cir.setReturnValue(false);
	}
}
