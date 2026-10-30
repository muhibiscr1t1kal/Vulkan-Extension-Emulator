package com.muhibiscr1t1kal.vulkanextensionemulator.mixin;

import com.mojang.blaze3d.vulkan.VulkanBackend;
import com.mojang.blaze3d.vulkan.VulkanPhysicalDevice;
import com.mojang.blaze3d.vulkan.init.VulkanFeature;
import com.muhibiscr1t1kal.vulkanextensionemulator.VulkanExtensionEmulator;
import com.muhibiscr1t1kal.vulkanextensionemulator.vk.Emulation;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;
import org.lwjgl.vulkan.VkPhysicalDevice;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({VulkanBackend.class})
public abstract class VulkanBackendMixin {
   @Inject(
      method = {"deviceMeetsFeatureQueryRequirements"},
      at = @At("RETURN"),
      cancellable = true,
      require = 0
   )
   private static void vulkanextensionemulator$allowVulkan11ForQuery(VkPhysicalDevice device, CallbackInfoReturnable cir) {
      if (!cir.getReturnValueZ() && VulkanExtensionEmulator.config().bypassApiVersionCheck) {
         VulkanExtensionEmulator.LOGGER.info("[Vulkan Extension Emulator] Bypassing Vulkan 1.2 feature-query gate (1.1 + KHR/EXT equivalents present)");
         cir.setReturnValue(true);
      }

   }

   @Inject(
      method = {"isDeviceSuitable"},
      at = @At("RETURN"),
      cancellable = true,
      require = 0
   )
   private static void vulkanextensionemulator$forceDeviceSuitable(VkPhysicalDevice device, CallbackInfoReturnable cir) {
      if (!cir.getReturnValueZ() && VulkanExtensionEmulator.config().bypassApiVersionCheck) {
         if (VulkanExtensionEmulator.allRemainingGapsAreEmulatable()) {
            VulkanExtensionEmulator.LOGGER.info("[Vulkan Extension Emulator] Forcing device suitable — all gaps are emulated");
            Emulation.setGateOpened(true);
            cir.setReturnValue(true);
         } else {
            VulkanExtensionEmulator.LOGGER.warn("[Vulkan Extension Emulator] Device has non-emulatable gaps; leaving rejection intact");
         }
      }

   }

   @Inject(
      method = {"isFeatureSupported"},
      at = @At("RETURN"),
      cancellable = true,
      require = 0
   )
   private static void vulkanextensionemulator$claimEmulatedFeature(VkPhysicalDevice device, VulkanFeature feature, CallbackInfoReturnable cir) {
      if (!cir.getReturnValueZ()) {
         String s = VulkanExtensionEmulator.featureName(feature);
         if (s != null && VulkanExtensionEmulator.canEmulateFeature(s)) {
            Emulation.markFeature(s);
            cir.setReturnValue(true);
         }
      }

   }

   @ModifyVariable(
      method = {"createDevice(Ljava/util/Collection;Lcom/mojang/blaze3d/vulkan/VulkanPhysicalDevice;Ljava/util/Set;)Lorg/lwjgl/vulkan/VkDevice;"},
      at = @At("HEAD"),
      argsOnly = true,
      index = 0,
      require = 0
   )
   private static Collection vulkanextensionemulator$stripEmulatedExtensions(Collection requested) {
      if (requested == null) {
         return requested;
      } else {
         Collection collection = new ArrayList();
         Set set = new LinkedHashSet();
         Iterator var3 = requested.iterator();

         while(var3.hasNext()) {
            String s = (String)var3.next();
            if (Emulation.mustStripFromDeviceCreate(s)) {
               set.add(s);
            } else {
               collection.add(s);
            }
         }

         if (!set.isEmpty()) {
            VulkanExtensionEmulator.LOGGER.info("[Vulkan Extension Emulator] Stripped {} emulated extension(s) from vkCreateDevice: {}", set.size(), set);
         }

         Set set1 = new LinkedHashSet();
         Iterator var7 = Emulation.substituteExtensions().iterator();

         while(var7.hasNext()) {
            String s1 = (String)var7.next();
            if (!collection.contains(s1)) {
               collection.add(s1);
               set1.add(s1);
            }
         }

         if (!set1.isEmpty()) {
            VulkanExtensionEmulator.LOGGER.info("[Vulkan Extension Emulator] Added {} substitute extension(s) for VK1.2 core functions: {}", set1.size(), set1);
         }

         return collection;
      }
   }

   @ModifyVariable(
      method = {"createDevice(Ljava/util/Collection;Lcom/mojang/blaze3d/vulkan/VulkanPhysicalDevice;Ljava/util/Set;)Lorg/lwjgl/vulkan/VkDevice;"},
      at = @At("HEAD"),
      argsOnly = true,
      index = 2,
      require = 0
   )
   private static Set vulkanextensionemulator$stripEmulatedFeatures(Set features) {
      if (features != null && !Emulation.features().isEmpty()) {
         Set set = new LinkedHashSet();
         Set set1 = new LinkedHashSet();
         Iterator var3 = features.iterator();

         while(true) {
            while(var3.hasNext()) {
               VulkanFeature vulkanfeature = (VulkanFeature)var3.next();
               String s = VulkanExtensionEmulator.featureName(vulkanfeature);
               if (s != null && Emulation.isFeatureEmulated(s)) {
                  set1.add(s);
               } else {
                  set.add(vulkanfeature);
               }
            }

            if (!set1.isEmpty()) {
               VulkanExtensionEmulator.LOGGER.info("[Vulkan Extension Emulator] Stripped {} emulated feature(s) from vkCreateDevice: {}", set1.size(), set1);
            }

            return set;
         }
      } else {
         return features;
      }
   }

   @Inject(
      method = {"createDevice(Ljava/util/Collection;Lcom/mojang/blaze3d/vulkan/VulkanPhysicalDevice;Ljava/util/Set;)Lorg/lwjgl/vulkan/VkDevice;"},
      at = @At("RETURN"),
      require = 0
   )
   private static void vulkanextensionemulator$deviceCreated(Collection exts, VulkanPhysicalDevice physical, Set features, CallbackInfoReturnable cir) {
      if (cir.getReturnValue() != null) {
         VulkanExtensionEmulator.LOGGER.info("[Vulkan Extension Emulator] ****  vkCreateDevice SUCCEEDED  ****");
         VulkanExtensionEmulator.LOGGER.info("[Vulkan Extension Emulator] Emulating: {}", Emulation.describe());
      }

   }
}
