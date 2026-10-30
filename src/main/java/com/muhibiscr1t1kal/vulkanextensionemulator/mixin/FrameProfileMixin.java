package com.muhibiscr1t1kal.vulkanextensionemulator.mixin;

import com.mojang.blaze3d.vulkan.VulkanGpuSurface;
import com.muhibiscr1t1kal.vulkanextensionemulator.vk.FrameProfiler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({VulkanGpuSurface.class})
public abstract class FrameProfileMixin {
   private long vulkanextensionemulator$acquireStart;

   @Inject(
      method = {"acquireNextTexture"},
      at = @At("HEAD"),
      require = 0
   )
   private void vulkanextensionemulator$beginFrame(CallbackInfo ci) {
      FrameProfiler.beginFrame();
      this.vulkanextensionemulator$acquireStart = System.nanoTime();
   }

   @Inject(
      method = {"acquireNextTexture"},
      at = @At("RETURN"),
      require = 0
   )
   private void vulkanextensionemulator$endAcquire(CallbackInfo ci) {
      FrameProfiler.addAcquire(this.vulkanextensionemulator$acquireStart);
   }

   @Inject(
      method = {"present"},
      at = @At("RETURN"),
      require = 0
   )
   private void vulkanextensionemulator$endFrame(CallbackInfo ci) {
      FrameProfiler.endFrame();
   }
}
