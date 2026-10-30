package com.muhibiscr1t1kal.vulkanextensionemulator.mixin;

import com.mojang.blaze3d.vulkan.VulkanCommandEncoder;
import com.muhibiscr1t1kal.vulkanextensionemulator.vk.FrameProfiler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({VulkanCommandEncoder.class})
public abstract class GpuWaitProfileMixin {
   private long vulkanextensionemulator$waitStart;

   @Inject(
      method = {"awaitSubmitCompletion"},
      at = @At("HEAD"),
      require = 0
   )
   private void vulkanextensionemulator$beginWait(long submitIndex, long timeout, CallbackInfoReturnable cir) {
      this.vulkanextensionemulator$waitStart = System.nanoTime();
   }

   @Inject(
      method = {"awaitSubmitCompletion"},
      at = @At("RETURN"),
      require = 0
   )
   private void vulkanextensionemulator$endWait(long submitIndex, long timeout, CallbackInfoReturnable cir) {
      FrameProfiler.addGpuWait(this.vulkanextensionemulator$waitStart);
   }
}
