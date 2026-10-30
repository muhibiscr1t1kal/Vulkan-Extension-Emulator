package com.muhibiscr1t1kal.vulkanextensionemulator.mixin;

import com.mojang.blaze3d.vulkan.VulkanPhysicalDevice;
import com.muhibiscr1t1kal.vulkanextensionemulator.VulkanExtensionEmulator;
import com.muhibiscr1t1kal.vulkanextensionemulator.vk.Emulation;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({VulkanPhysicalDevice.class})
public abstract class VulkanPhysicalDeviceMixin {
   @Inject(
      method = {"getMissingExtensions"},
      at = @At("RETURN"),
      cancellable = true,
      require = 0
   )
   private void vulkanextensionemulator$hideEmulatedExtensions(Collection requested, CallbackInfoReturnable cir) {
      Set set = (Set)cir.getReturnValue();
      if (set != null && !set.isEmpty()) {
         Set set1 = new LinkedHashSet();
         Set set2 = new LinkedHashSet();
         Iterator var6 = set.iterator();

         while(var6.hasNext()) {
            String s = (String)var6.next();
            if (VulkanExtensionEmulator.canEmulateExtension(s)) {
               Emulation.markExtension(s);
               set2.add(s);
            } else {
               set1.add(s);
            }
         }

         if (!set2.isEmpty()) {
            VulkanExtensionEmulator.LOGGER.info("[Vulkan Extension Emulator] Claiming {} extension(s) as emulated: {}", set2.size(), set2);
         }

         if (!set1.isEmpty()) {
            Emulation.markUnemulatable();
            VulkanExtensionEmulator.LOGGER.warn("[Vulkan Extension Emulator] Cannot emulate: {} -- device will be rejected", set1);
         }

         cir.setReturnValue(set1);
      }

   }

   @Inject(
      method = {"hasDeviceExtension"},
      at = @At("RETURN"),
      cancellable = true,
      require = 0
   )
   private void vulkanextensionemulator$claimEmulatedExtension(String name, CallbackInfoReturnable cir) {
      if (!cir.getReturnValueZ() && Emulation.isExtensionEmulated(name)) {
         cir.setReturnValue(true);
      }

   }
}
