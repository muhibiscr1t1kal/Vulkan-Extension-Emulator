package com.muhibiscr1t1kal.vulkanextensionemulator.mixin;

import com.mojang.blaze3d.vulkan.VulkanGpuSurface;
import com.muhibiscr1t1kal.vulkanextensionemulator.vk.BarrierCoalescer;
import com.muhibiscr1t1kal.vulkanextensionemulator.vk.DynamicRenderingEmulation;
import com.muhibiscr1t1kal.vulkanextensionemulator.vk.PushDescriptorEmulation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({VulkanGpuSurface.class})
public abstract class SurfaceRecordMixin {
   @Inject(
      method = {"blitFromTexture"},
      at = @At("HEAD"),
      require = 0
   )
   private void vulkanextensionemulator$blitRecorded(CallbackInfo ci) {
      BarrierCoalescer.commandRecorded();
   }

   @Inject(
      method = {"acquireNextTexture"},
      at = @At("HEAD"),
      require = 0
   )
   private void vulkanextensionemulator$acquireRecorded(CallbackInfo ci) {
      BarrierCoalescer.commandRecorded();
   }

   @Inject(
      method = {"present"},
      at = @At("HEAD"),
      require = 0
   )
   private void vulkanextensionemulator$presentRecorded(CallbackInfo ci) {
      BarrierCoalescer.commandRecorded();
   }

   @Inject(
      method = {"configure"},
      at = @At("HEAD"),
      require = 0
   )
   private void vulkanextensionemulator$swapchainReset(CallbackInfo ci) {
      BarrierCoalescer.reset();
      DynamicRenderingEmulation.invalidateAll();
      PushDescriptorEmulation.invalidateCaches();
   }
}
