package com.muhibiscr1t1kal.vulkanextensionemulator.mixin;

import com.mojang.blaze3d.vulkan.VulkanDevice;
import com.muhibiscr1t1kal.vulkanextensionemulator.VulkanExtensionEmulator;
import com.muhibiscr1t1kal.vulkanextensionemulator.vk.BarrierCoalescer;
import com.muhibiscr1t1kal.vulkanextensionemulator.vk.Core12Emulation;
import com.muhibiscr1t1kal.vulkanextensionemulator.vk.DynamicRenderingEmulation;
import com.muhibiscr1t1kal.vulkanextensionemulator.vk.Emulation;
import com.muhibiscr1t1kal.vulkanextensionemulator.vk.MultiDrawEmulation;
import com.muhibiscr1t1kal.vulkanextensionemulator.vk.NativeCaps;
import com.muhibiscr1t1kal.vulkanextensionemulator.vk.PipelineCache;
import com.muhibiscr1t1kal.vulkanextensionemulator.vk.PipelineDepth;
import com.muhibiscr1t1kal.vulkanextensionemulator.vk.PresentBarrierNarrowing;
import com.muhibiscr1t1kal.vulkanextensionemulator.vk.PushDescriptorEmulation;
import com.muhibiscr1t1kal.vulkanextensionemulator.vk.VulkanProbe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({VulkanDevice.class})
public abstract class DeviceInitMixin {
   @Inject(
      method = {"<init>"},
      at = {@At("RETURN")},
      require = 0
   )
   private void vulkanextensionemulator$initPools(CallbackInfo ci) {
      VulkanDevice vulkandevice = (VulkanDevice)(Object)this;
      NativeCaps.resolve(vulkandevice.vkDevice());
      BarrierCoalescer.arm(VulkanExtensionEmulator.config().coalesceBarriers && Emulation.isExtensionEmulated("VK_KHR_synchronization2"));
      Core12Emulation.resolve(vulkandevice.vkDevice(), vulkandevice.graphicsQueue().vkQueue());
      initPipelineCache(vulkandevice);
      MultiDrawEmulation.init(driverHasMultiDrawIndirect());
      PushDescriptorEmulation.init(vulkandevice.vkDevice());
      if (DynamicRenderingEmulation.active()) {
         DynamicRenderingEmulation.init(vulkandevice.vkDevice());
      }

   }

   @Inject(
      method = {"close"},
      at = {@At("HEAD")},
      require = 0
   )
   private void vulkanextensionemulator$destroyPools(CallbackInfo ci) {
      PipelineCache.shutdown();
      PushDescriptorEmulation.shutdown();
      DynamicRenderingEmulation.shutdown();
      VulkanExtensionEmulator.LOGGER.info("[Vulkan Extension Emulator] {}", MultiDrawEmulation.stats());
      VulkanExtensionEmulator.LOGGER.info("[Vulkan Extension Emulator] {}", BarrierCoalescer.stats());
      VulkanExtensionEmulator.LOGGER.info("[Vulkan Extension Emulator] {}", PresentBarrierNarrowing.stats());
      VulkanExtensionEmulator.LOGGER.info("[Vulkan Extension Emulator] {}", PipelineDepth.stats());
   }

   private static void initPipelineCache(VulkanDevice self) {
      try {
         VulkanProbe.Result vulkanprobe$result = VulkanExtensionEmulator.probeResult();
         long i = 0L;
         long j = 0L;
         int k = 0;
         if (vulkanprobe$result != null) {
            i = vulkanprobe$result.deviceName() == null ? 0L : (long)vulkanprobe$result.deviceName().hashCode();
            j = (long)vulkanprobe$result.apiMajor() << 32 | (long)(vulkanprobe$result.apiMinor() << 16) | (long)vulkanprobe$result.apiPatch();
            k = vulkanprobe$result.driverInfo() == null ? 0 : vulkanprobe$result.driverInfo().hashCode();
         }

         PipelineCache.init(self.vkDevice(), i, j, k);
      } catch (Throwable var7) {
         VulkanExtensionEmulator.LOGGER.warn("[Vulkan Extension Emulator] Pipeline cache init failed", var7);
      }

   }

   private static boolean driverHasMultiDrawIndirect() {
      VulkanProbe.Result vulkanprobe$result = VulkanExtensionEmulator.probeResult();
      return vulkanprobe$result == null ? false : Boolean.TRUE.equals(vulkanprobe$result.features().get("multiDrawIndirect"));
   }
}