package com.muhibiscr1t1kal.vulkanextensionemulator.vk;

import org.lwjgl.vulkan.VkDevice;

public final class NativeCaps {
   private static boolean resolved;
   public static boolean pushDescriptor;
   public static boolean sync2Barrier;
   public static boolean sync2Timestamp;
   public static boolean submit2;
   public static boolean dynamicRendering;

   private NativeCaps() {
   }

   public static void resolve(VkDevice device) {
      if (!resolved && device != null) {
         resolved = true;
         pushDescriptor = device.getCapabilities().vkCmdPushDescriptorSetKHR != 0L;
         sync2Barrier = device.getCapabilities().vkCmdPipelineBarrier2KHR != 0L;
         sync2Timestamp = device.getCapabilities().vkCmdWriteTimestamp2KHR != 0L;
         submit2 = device.getCapabilities().vkQueueSubmit2KHR != 0L;
         dynamicRendering = device.getCapabilities().vkCmdBeginRenderingKHR != 0L;
      }
   }
}
