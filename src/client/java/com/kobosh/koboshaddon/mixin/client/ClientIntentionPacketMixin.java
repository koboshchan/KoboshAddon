package com.kobosh.koboshaddon.mixin.client;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.kobosh.koboshaddon.client.hack.BungeeCordSpoofHack;

import net.minecraft.network.protocol.handshake.ClientIntent;
import net.minecraft.network.protocol.handshake.ClientIntentionPacket;
import net.wurstclient.WurstClient;
import net.wurstclient.hack.Hack;

@Mixin(ClientIntentionPacket.class)
public class ClientIntentionPacketMixin
{
	@Shadow @Final private String hostName;
	@Shadow @Final private ClientIntent intention;

	@Inject(method = "hostName", at = @At("HEAD"), cancellable = true)
	private void onGetHostName(CallbackInfoReturnable<String> cir)
	{
		if(intention == ClientIntent.LOGIN)
		{
			BungeeCordSpoofHack hack = getHack(BungeeCordSpoofHack.class);
			if(hack != null && hack.isEnabled())
			{
				String spoofed = hack.getSpoofedAddress(hostName);
				if(spoofed != null)
				{
					cir.setReturnValue(spoofed);
				}
			}
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
