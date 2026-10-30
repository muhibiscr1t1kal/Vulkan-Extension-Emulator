package com.muhibiscr1t1kal.vulkanextensionemulator.vk;

import com.muhibiscr1t1kal.vulkanextensionemulator.VulkanExtensionEmulator;
import org.lwjgl.vulkan.EXTHostQueryReset;
import org.lwjgl.vulkan.KHRTimelineSemaphore;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VK12;
import org.lwjgl.vulkan.VKCapabilitiesDevice;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkQueue;
import org.lwjgl.vulkan.VkSemaphoreWaitInfo;

public final class Core12Emulation {
   public static final String EXT_HOST_QUERY_RESET = "VK_EXT_host_query_reset";
   public static final String KHR_TIMELINE_SEMAPHORE = "VK_KHR_timeline_semaphore";

   private static boolean useHostQueryResetExt;
   private static boolean useTimelineSemaphoreExt;
   private static boolean resolved;
   private static boolean warnedNoQueryReset;
   private static boolean warnedNoTimeline;
   private static VkQueue fallbackQueue;

   private Core12Emulation() {
   }

   public static void resolve(VkDevice device, VkQueue graphicsQueue) {
      if (!resolved) {
         resolved = true;
         fallbackQueue = graphicsQueue;

         VKCapabilitiesDevice capabilities = device.getCapabilities();

         useHostQueryResetExt =
            capabilities.vkResetQueryPool == 0L
            && capabilities.vkResetQueryPoolEXT != 0L;

         useTimelineSemaphoreExt =
            capabilities.vkWaitSemaphores == 0L
            && capabilities.vkWaitSemaphoresKHR != 0L;

         if (useHostQueryResetExt || useTimelineSemaphoreExt) {
            VulkanExtensionEmulator.LOGGER.info(
               "[Vulkan Extension Emulator] VK1.2 core -> VK1.1 extension routing: vkResetQueryPool={}, vkWaitSemaphores={}",
               useHostQueryResetExt ? "EXT_host_query_reset" : "core",
               useTimelineSemaphoreExt ? "KHR_timeline_semaphore" : "core"
            );
         }

         if (capabilities.vkResetQueryPool == 0L
            && capabilities.vkResetQueryPoolEXT == 0L) {
            VulkanExtensionEmulator.LOGGER.warn(
               "[Vulkan Extension Emulator] Neither vkResetQueryPool nor its EXT form is available; query pool resets will be skipped (timestamps are profiling-only)."
            );
         }

         if (capabilities.vkWaitSemaphores == 0L
            && capabilities.vkWaitSemaphoresKHR == 0L) {
            VulkanExtensionEmulator.LOGGER.error(
               "[Vulkan Extension Emulator] No timeline semaphore wait available. Frame pacing will fall back to a queue-idle stall, which is correct but slow."
            );
         }
      }
   }

   public static void resetQueryPool(
      VkDevice device,
      long queryPool,
      int firstQuery,
      int queryCount
   ) {
      if (useHostQueryResetExt) {
         EXTHostQueryReset.vkResetQueryPoolEXT(
            device,
            queryPool,
            firstQuery,
            queryCount
         );
      } else if (device.getCapabilities().vkResetQueryPool != 0L) {
         VK12.vkResetQueryPool(
            device,
            queryPool,
            firstQuery,
            queryCount
         );
      } else if (!warnedNoQueryReset) {
         warnedNoQueryReset = true;

         VulkanExtensionEmulator.LOGGER.warn(
            "[Vulkan Extension Emulator] vkResetQueryPool unavailable; skipping query pool reset"
         );
      }
   }

   public static int waitSemaphores(
      VkDevice device,
      VkSemaphoreWaitInfo info,
      long timeout
   ) {
      if (useTimelineSemaphoreExt) {
         return KHRTimelineSemaphore.vkWaitSemaphoresKHR(
            device,
            info,
            timeout
         );
      } else if (device.getCapabilities().vkWaitSemaphores != 0L) {
         return VK12.vkWaitSemaphores(
            device,
            info,
            timeout
         );
      } else if (timeout == 0L) {
         return 2;
      } else {
         if (!warnedNoTimeline) {
            warnedNoTimeline = true;

            VulkanExtensionEmulator.LOGGER.warn(
               "[Vulkan Extension Emulator] Timeline wait unavailable; stalling on vkQueueWaitIdle (correct but slow)"
            );
         }

         if (fallbackQueue != null) {
            try {
               VK10.vkQueueWaitIdle(fallbackQueue);
               return 0;
            } catch (Throwable exception) {
               VulkanExtensionEmulator.LOGGER.error(
                  "[Vulkan Extension Emulator] vkQueueWaitIdle failed",
                  exception
               );
               return 0;
            }
         } else {
            VulkanExtensionEmulator.LOGGER.error(
               "[Vulkan Extension Emulator] No timeline semaphore wait and no fallback queue captured; frame pacing is unsafe"
            );
            return 0;
         }
      }
   }

   public static boolean needsTimelineExtension() {
      return useTimelineSemaphoreExt;
   }
}