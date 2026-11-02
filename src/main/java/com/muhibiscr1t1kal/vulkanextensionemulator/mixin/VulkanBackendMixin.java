package com.muhibiscr1t1kal.vulkanextensionemulator.mixin;

import com.mojang.blaze3d.systems.BackendCreationException;
import com.mojang.blaze3d.vulkan.VulkanBackend;
import com.mojang.blaze3d.vulkan.VulkanPhysicalDevice;
import com.mojang.blaze3d.vulkan.init.VulkanFeature;
import com.mojang.blaze3d.vulkan.init.VulkanPNextStruct;
import com.muhibiscr1t1kal.vulkanextensionemulator.VulkanExtensionEmulator;
import com.muhibiscr1t1kal.vulkanextensionemulator.vk.Emulation;
import com.muhibiscr1t1kal.vulkanextensionemulator.vk.VulkanProbe;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkPhysicalDevice;
import org.lwjgl.vulkan.VkPhysicalDeviceHostQueryResetFeaturesEXT;
import org.lwjgl.vulkan.VkPhysicalDeviceTimelineSemaphoreFeaturesKHR;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({VulkanBackend.class})
public abstract class VulkanBackendMixin {

   private static final int VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_TIMELINE_SEMAPHORE_FEATURES_KHR = 1000207000;
   private static final int VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_HOST_QUERY_RESET_FEATURES_EXT = 1000261000;

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
      method = {"isDeviceSuitable"},
      at = {@At("RETURN")},
      cancellable = true,
      require = 0
   )
   private static void vulkanextensionemulator$forceDeviceSuitable(
      VkPhysicalDevice device,
      CallbackInfoReturnable<Boolean> cir
   ) {
      if (cir.getReturnValueZ()
            || !VulkanExtensionEmulator.config().bypassApiVersionCheck) {
         return;
      }

      try {
         Method method = VulkanBackend.class.getDeclaredMethod(
            "throwForMissingRequrements",
            VkPhysicalDevice.class
         );

         method.setAccessible(true);
         method.invoke(null, device);

      } catch (InvocationTargetException invocationtargetexception) {

         Throwable cause = invocationtargetexception.getCause();

         if (cause instanceof BackendCreationException backendcreationexception) {

            List<String> list = backendcreationexception.getMissingCapabilities();

            if (list == null) {
               list = List.of();
            }

            Set<String> set = new LinkedHashSet<>();
            boolean flag = true;

            Iterator<String> iterator = list.iterator();

            while (iterator.hasNext()) {
               String s = iterator.next();

               if (!s.equals("VULKAN_CORE_1_2")
                     && !s.equals("VULKAN_CORE_1_1")) {

                  if (VulkanExtensionEmulator.canEmulateExtension(s)) {
                     Emulation.markExtension(s);
                     set.add(s);

                  } else if (VulkanExtensionEmulator.canEmulateFeature(s)) {
                     Emulation.markFeature(s);
                     set.add(s);

                  } else {
                     flag = false;
                     Emulation.markUnemulatable();
                  }

               } else {
                  set.add(s);
               }
            }

            if (!set.isEmpty() && flag) {

               VulkanExtensionEmulator.LOGGER.info(
                  "[Vulkan Extension Emulator] Claiming {} missing capability/capabilities as emulated: {}",
                  set.size(),
                  set
               );

               VulkanExtensionEmulator.LOGGER.info(
                  "[Vulkan Extension Emulator] Forcing device suitable — all gaps are emulated"
               );

               Emulation.setGateOpened(true);
               cir.setReturnValue(true);

            } else if (!flag) {

               VulkanExtensionEmulator.LOGGER.warn(
                  "[Vulkan Extension Emulator] Device has non-emulatable gaps; leaving rejection intact"
               );
            }

         } else {
            throw new RuntimeException(cause);
         }

      } catch (ReflectiveOperationException reflectionexception) {
         throw new RuntimeException(
            "Failed to invoke VulkanBackend.throwForMissingRequrements",
            reflectionexception
         );
      }
   }

   @ModifyVariable(
      method = {
         "createDevice(Ljava/util/Collection;Lcom/mojang/blaze3d/vulkan/VulkanPhysicalDevice;Ljava/util/Set;)Lorg/lwjgl/vulkan/VkDevice;"
      },
      at = @At("HEAD"),
      argsOnly = true,
      index = 0,
      require = 0
   )
   private static Collection<String> vulkanextensionemulator$stripEmulatedFromDeviceCreate(
      Collection<String> deviceExtensions
   ) {
      if (deviceExtensions == null) {
         return deviceExtensions;
      }

      if (VulkanExtensionEmulator.forcePushEmulation()) {
         Emulation.markExtension("VK_KHR_push_descriptor");
      }

      Set<String> set = new LinkedHashSet<>();
      Set<String> set1 = new LinkedHashSet<>();

      Iterator<String> iterator = deviceExtensions.iterator();

      while (iterator.hasNext()) {
         String s = iterator.next();

         if (Emulation.mustStripFromDeviceCreate(s)) {
            set.add(s);
         } else {
            set1.add(s);
         }
      }

      if (!set.isEmpty()) {
         VulkanExtensionEmulator.LOGGER.info(
            "[Vulkan Extension Emulator] Stripped {} emulated extension(s) from vkCreateDevice: {}",
            set.size(),
            set
         );
      }

      Set<String> set2 = new LinkedHashSet<>();
      Iterator<String> iterator1 = Emulation.substituteExtensions().iterator();

      while (iterator1.hasNext()) {
         String s1 = iterator1.next();

         if (!set1.contains(s1)) {
            set1.add(s1);
            set2.add(s1);
         }
      }

      if (!set2.isEmpty()) {
         VulkanExtensionEmulator.LOGGER.info(
            "[Vulkan Extension Emulator] Added {} substitute extension(s) for VK1.2 core functions: {}",
            set2.size(),
            set2
         );
      }

      return set1;
   }

   @ModifyVariable(
      method = {
         "createDevice(Ljava/util/Collection;Lcom/mojang/blaze3d/vulkan/VulkanPhysicalDevice;Ljava/util/Set;)Lorg/lwjgl/vulkan/VkDevice;"
      },
      at = @At("HEAD"),
      argsOnly = true,
      index = 2,
      require = 0
   )
   private static Set<VulkanFeature> vulkanextensionemulator$filterDeviceFeatures(
      Set<VulkanFeature> vulkanFeatures
   ) {
      if (vulkanFeatures == null) {
         return vulkanFeatures;
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

      Set<VulkanFeature> set3 = new LinkedHashSet<>();
      Set<String> set4 = new LinkedHashSet<>();
      Set<String> set5 = new LinkedHashSet<>();

      Iterator<VulkanFeature> iterator = vulkanFeatures.iterator();

      while (iterator.hasNext()) {
         VulkanFeature vulkanfeature = iterator.next();

         String s2 = VulkanExtensionEmulator.featureName(vulkanfeature);

         if (s2 == null) {
            set3.add(vulkanfeature);

         } else {

            if (!versionAtLeast12
                  && vulkanfeature.struct() == VulkanBackend.VK12_FEATURES_STRUCT) {

               if (s2.equals("timelineSemaphore") && timelineExtension) {

                  set3.add(
                     new VulkanFeature(
                        new VulkanPNextStruct(
                           VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_TIMELINE_SEMAPHORE_FEATURES_KHR,
                           VkPhysicalDeviceTimelineSemaphoreFeaturesKHR.SIZEOF
                        ),
                        s2,
                        VkPhysicalDeviceTimelineSemaphoreFeaturesKHR.TIMELINESEMAPHORE
                     )
                  );

                  set5.add(s2);
                  continue;
               }

               if (s2.equals("hostQueryReset") && hostResetExtension) {

                  set3.add(
                     new VulkanFeature(
                        new VulkanPNextStruct(
                           VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_HOST_QUERY_RESET_FEATURES_EXT,
                           VkPhysicalDeviceHostQueryResetFeaturesEXT.SIZEOF
                        ),
                        s2,
                        VkPhysicalDeviceHostQueryResetFeaturesEXT.HOSTQUERYRESET
                     )
                  );

                  set5.add(s2);
                  continue;
               }
            }

            if (VulkanExtensionEmulator.canEmulateFeature(s2)) {
               Emulation.markFeature(s2);
               set4.add(s2);

            } else {
               set3.add(vulkanfeature);
            }
         }
      }

      if (!set5.isEmpty()) {
         VulkanExtensionEmulator.LOGGER.info(
            "[Vulkan Extension Emulator] Routed {} core-1.2 feature(s) to KHR/EXT structs on this Vulkan 1.1 device: {}",
            set5.size(),
            set5
         );
      }

      if (!set4.isEmpty()) {
         VulkanExtensionEmulator.LOGGER.info(
            "[Vulkan Extension Emulator] Left {} emulated feature(s) disabled on device (emulated in software): {}",
            set4.size(),
            set4
         );
      }

      return set3;
   }

   @Inject(
      method = {
         "createDevice(Ljava/util/Collection;Lcom/mojang/blaze3d/vulkan/VulkanPhysicalDevice;Ljava/util/Set;)Lorg/lwjgl/vulkan/VkDevice;"
      },
      at = {@At("RETURN")},
      require = 0
   )
   private static void vulkanextensionemulator$deviceCreated(
      Collection<String> deviceExtensions,
      VulkanPhysicalDevice physical,
      Set<VulkanFeature> vulkanFeatures,
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