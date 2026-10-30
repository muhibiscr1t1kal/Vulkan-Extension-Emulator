package com.muhibiscr1t1kal.vulkanextensionemulator.mixin;

import com.mojang.blaze3d.vulkan.VulkanGpuSurface;
import com.muhibiscr1t1kal.vulkanextensionemulator.vk.Metrics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({VulkanGpuSurface.class})
public abstract class MetricsMixin {
   @Inject(
      method = {"present"},
      at = @At("RETURN"),
      require = 0
   )
   private void vulkanextensionemulator$frame(CallbackInfo ci) {
      Metrics.frame();
   }
}
