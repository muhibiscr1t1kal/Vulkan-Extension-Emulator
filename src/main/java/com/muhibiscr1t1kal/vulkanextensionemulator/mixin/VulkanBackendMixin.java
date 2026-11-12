package com.muhibiscr1t1kal.vulkanextensionemulator.mixin;

import com.mojang.renderpearl.api.device.BackendCreationException;
import com.mojang.renderpearl.backend.vulkan.VulkanBackend;
import com.mojang.renderpearl.backend.vulkan.VulkanPhysicalDevice;
import com.mojang.renderpearl.backend.vulkan.init.FeatureSet;
import com.mojang.renderpearl.backend.vulkan.init.VulkanFeature;
import com.mojang.renderpearl.backend.vulkan.init.VulkanPNextStruct;
import com.muhibiscr1t1kal.vulkanextensionemulator.VulkanExtensionEmulator;
import com.muhibiscr1t1kal.vulkanextensionemulator.vk.Emulation;
import com.muhibiscr1t1kal.vulkanextensionemulator.vk.VulkanProbe;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkPhysicalDevice;
import org.lwjgl.vulkan.VkPhysicalDeviceHostQueryResetFeaturesEXT;
import org.lwjgl.vulkan.VkPhysicalDeviceTimelineSemaphoreFeaturesKHR;
import org.lwjgl.vulkan.VkPhysicalDeviceVulkan12Features;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({VulkanBackend.class})
public abstract class VulkanBackendMixin {

   @Inject(
      method = {"deviceMeetsFeatureQueryRequirements"},
      at = {@At("RETURN")},
      cancellable = true,
      require = 0
   )
   private static void vulkanextensionemulator$allowVulkan11ForQuery(
      VkPhysicalDevice device,
      CallbackInfoReturnable<Boolean> cir
   ) {
      if (!cir.getReturnValueZ()
            && VulkanExtensionEmulator.config().bypassApiVersionCheck) {

         VulkanExtensionEmulator.LOGGER.info(
            "[Vulkan Extension Emulator] Bypassing Vulkan 1.2 feature-query gate (1.1 + KHR/EXT equivalents present)"
         );

         cir.setReturnValue(true);
      }
   }

   @Inject(
      method = {"checkDeviceSuitability"},
      at = {@At("RETURN")},
      cancellable = true,
      require = 0
   )
   private static void vulkanextensionemulator$forceDeviceSuitable(
      VkPhysicalDevice device,
      Set<?> required,
      Set<?> requiredIfAvailable,
      CallbackInfoReturnable<BackendCreationException> cir
   ) {
      BackendCreationException backendCreationException = cir.getReturnValue();

      if (backendCreationException == null
            || !VulkanExtensionEmulator.config().bypassApiVersionCheck) {
         return;
      }

      List<String> missingCapabilities =
         backendCreationException.getMissingCapabilities();

      if (missingCapabilities == null) {
         missingCapabilities = List.of();
      }

      Set<String> claimedCapabilities = new LinkedHashSet<>();
      boolean allEmulatable = true;

      Iterator<String> iterator = missingCapabilities.iterator();

      while (iterator.hasNext()) {
         String capability = iterator.next();

         if (!capability.equals("VULKAN_CORE_1_2")
               && !capability.equals("VULKAN_CORE_1_1")) {

            if (VulkanExtensionEmulator.canEmulateExtension(capability)) {
               Emulation.markExtension(capability);
               claimedCapabilities.add(capability);

            } else if (VulkanExtensionEmulator.canEmulateFeature(capability)) {
               Emulation.markFeature(capability);
               claimedCapabilities.add(capability);

            } else {
               allEmulatable = false;
               Emulation.markUnemulatable();
            }

         } else {
            claimedCapabilities.add(capability);
         }
      }

      if (!claimedCapabilities.isEmpty() && allEmulatable) {

         VulkanExtensionEmulator.LOGGER.info(
            "[Vulkan Extension Emulator] Claiming {} missing capability/capabilities as emulated: {}",
            claimedCapabilities.size(),
            claimedCapabilities
         );

         VulkanExtensionEmulator.LOGGER.info(
            "[Vulkan Extension Emulator] Forcing device suitable — all gaps are emulated"
         );

         Emulation.setGateOpened(true);
         cir.setReturnValue(null);

      } else if (!allEmulatable) {

         VulkanExtensionEmulator.LOGGER.warn(
            "[Vulkan Extension Emulator] Device has non-emulatable gaps; leaving rejection intact"
         );
      }
   }

   @ModifyVariable(
      method = {
         "createDevice(Lcom/mojang/renderpearl/backend/vulkan/init/FeatureSet;Lcom/mojang/renderpearl/backend/vulkan/VulkanPhysicalDevice;)Lorg/lwjgl/vulkan/VkDevice;"
      },
      at = @At("HEAD"),
      argsOnly = true,
      index = 0,
      require = 0
   )
   private static FeatureSet vulkanextensionemulator$stripEmulatedFromDeviceCreate(
      FeatureSet featureSet
   ) {
      if (featureSet == null) {
         return featureSet;
      }

      if (VulkanExtensionEmulator.forcePushEmulation()) {
         Emulation.markExtension("VK_KHR_push_descriptor");
      }

      Set<String> strippedExtensions = new LinkedHashSet<>();
      Set<String> deviceExtensions = new LinkedHashSet<>();

      Iterator<String> extensionIterator = featureSet.extensions().iterator();

      while (extensionIterator.hasNext()) {
         String extension = extensionIterator.next();

         if (Emulation.mustStripFromDeviceCreate(extension)) {
            strippedExtensions.add(extension);
         } else {
            deviceExtensions.add(extension);
         }
      }

      if (!strippedExtensions.isEmpty()) {
         VulkanExtensionEmulator.LOGGER.info(
            "[Vulkan Extension Emulator] Stripped {} emulated extension(s) from vkCreateDevice: {}",
            strippedExtensions.size(),
            strippedExtensions
         );
      }

      Set<String> addedExtensions = new LinkedHashSet<>();

      Iterator<String> substituteIterator =
         Emulation.substituteExtensions().iterator();

      while (substituteIterator.hasNext()) {
         String substitute = substituteIterator.next();

         if (!deviceExtensions.contains(substitute)) {
            deviceExtensions.add(substitute);
            addedExtensions.add(substitute);
         }
      }

      if (!addedExtensions.isEmpty()) {
         VulkanExtensionEmulator.LOGGER.info(
            "[Vulkan Extension Emulator] Added {} substitute extension(s) for VK1.2 core functions: {}",
            addedExtensions.size(),
            addedExtensions
         );
      }

      VulkanProbe.Result result = VulkanExtensionEmulator.probeResult();

      boolean versionAtLeast12 =
         result != null
         && (
            result.apiMajor() > 1
            || result.apiMajor() == 1 && result.apiMinor() >= 2
         );

      boolean timelineExtension =
         result != null
         && Boolean.TRUE.equals(
            result.extensions().get("VK_KHR_timeline_semaphore")
         );

      boolean hostResetExtension =
         result != null
         && Boolean.TRUE.equals(
            result.extensions().get("VK_EXT_host_query_reset")
         );

      Set<VulkanFeature> deviceFeatures = new LinkedHashSet<>();
      Set<String> emulatedFeatures = new LinkedHashSet<>();
      Set<String> routedFeatures = new LinkedHashSet<>();

      Iterator<VulkanFeature> featureIterator =
         featureSet.features().iterator();

      while (featureIterator.hasNext()) {
         VulkanFeature vulkanFeature = featureIterator.next();

         String featureName =
            VulkanExtensionEmulator.featureName(vulkanFeature);

         if (featureName == null) {
            deviceFeatures.add(vulkanFeature);

         } else {

            if (!versionAtLeast12
                  && vulkanFeature.struct() != null
                  && vulkanFeature.struct().pNextStructClass()
                     == VkPhysicalDeviceVulkan12Features.class) {

               if (featureName.equals("timelineSemaphore")
                     && timelineExtension) {

                  deviceFeatures.add(
                     new VulkanFeature(
                        new VulkanPNextStruct(
                           VkPhysicalDeviceTimelineSemaphoreFeaturesKHR.class
                        ),
                        featureName,
                        VkPhysicalDeviceTimelineSemaphoreFeaturesKHR.TIMELINESEMAPHORE
                     )
                  );

                  routedFeatures.add(featureName);
                  continue;
               }

               if (featureName.equals("hostQueryReset")
                     && hostResetExtension) {

                  deviceFeatures.add(
                     new VulkanFeature(
                        new VulkanPNextStruct(
                           VkPhysicalDeviceHostQueryResetFeaturesEXT.class
                        ),
                        featureName,
                        VkPhysicalDeviceHostQueryResetFeaturesEXT.HOSTQUERYRESET
                     )
                  );

                  routedFeatures.add(featureName);
                  continue;
               }
            }

            if (VulkanExtensionEmulator.canEmulateFeature(featureName)) {
               Emulation.markFeature(featureName);
               emulatedFeatures.add(featureName);

            } else {
               deviceFeatures.add(vulkanFeature);
            }
         }
      }

      if (!routedFeatures.isEmpty()) {
         VulkanExtensionEmulator.LOGGER.info(
            "[Vulkan Extension Emulator] Routed {} core-1.2 feature(s) to KHR/EXT structs on this Vulkan 1.1 device: {}",
            routedFeatures.size(),
            routedFeatures
         );
      }

      if (!emulatedFeatures.isEmpty()) {
         VulkanExtensionEmulator.LOGGER.info(
            "[Vulkan Extension Emulator] Left {} emulated feature(s) disabled on device (emulated in software): {}",
            emulatedFeatures.size(),
            emulatedFeatures
         );
      }

      return new FeatureSet(
         featureSet.name(),
         deviceExtensions,
         deviceFeatures,
         featureSet.condition()
      );
   }

   @Inject(
      method = {
         "createDevice(Lcom/mojang/renderpearl/backend/vulkan/init/FeatureSet;Lcom/mojang/renderpearl/backend/vulkan/VulkanPhysicalDevice;)Lorg/lwjgl/vulkan/VkDevice;"
      },
      at = {@At("RETURN")},
      require = 0
   )
   private static void vulkanextensionemulator$deviceCreated(
      FeatureSet featureSet,
      VulkanPhysicalDevice physical,
      CallbackInfoReturnable<VkDevice> cir
   ) {
      if (cir.getReturnValue() != null) {
         VulkanExtensionEmulator.LOGGER.info(
            "[Vulkan Extension Emulator] ****  vkCreateDevice SUCCEEDED  ****"
         );

         VulkanExtensionEmulator.LOGGER.info(
            "[Vulkan Extension Emulator] Emulating: {}",
            Emulation.describe()
         );
      }
   }
}