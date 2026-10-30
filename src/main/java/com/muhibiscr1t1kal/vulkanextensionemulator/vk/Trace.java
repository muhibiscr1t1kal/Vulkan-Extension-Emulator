package com.muhibiscr1t1kal.vulkanextensionemulator.vk;

import com.muhibiscr1t1kal.vulkanextensionemulator.VulkanExtensionEmulator;

public final class Trace {
   private static long seq;

   private Trace() {
   }

   public static void mark(String what) {
      if (VulkanExtensionEmulator.config().trace) {
         VulkanExtensionEmulator.LOGGER.info("[Vulkan Extension Emulator][trace {}] {}", Long.valueOf((long)(seq++)), what);
      }

   }

   public static void mark(String what, Object detail) {
      if (VulkanExtensionEmulator.config().trace) {
         VulkanExtensionEmulator.LOGGER.info("[Vulkan Extension Emulator][trace {}] {} : {}", new Object[]{Long.valueOf((long)(seq++)), what, detail});
      }

   }

   public static void barrier(int srcStage, int dstStage, int memCount, int bufCount, int imgCount) {
      if (VulkanExtensionEmulator.config().trace) {
         VulkanExtensionEmulator.LOGGER.info("[Vulkan Extension Emulator][trace {}] barrier src=0x{} dst=0x{} mem={} buf={} img={}", new Object[]{Long.valueOf((long)(seq++)), Integer.toHexString(srcStage), Integer.toHexString(dstStage), memCount, bufCount, imgCount});
      }

   }
}
