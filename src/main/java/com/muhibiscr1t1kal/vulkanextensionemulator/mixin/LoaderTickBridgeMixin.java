package com.muhibiscr1t1kal.vulkanextensionemulator.mixin;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(
   targets = {"net.minecraft.client.Minecraft"}
)
public abstract class LoaderTickBridgeMixin {
   @Inject(
      method = {"tick"},
      at = @At("TAIL"),
      require = 0
   )
   private void vulkanextensionemulator$loaderBridgeEndTick(CallbackInfo var1) {
      ClientTickEvents.END_CLIENT_TICK.invoker().onEndTick((Minecraft)(Object)this);
   }
}