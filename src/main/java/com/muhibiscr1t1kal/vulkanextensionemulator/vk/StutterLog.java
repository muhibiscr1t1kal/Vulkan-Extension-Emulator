package com.muhibiscr1t1kal.vulkanextensionemulator.vk;

import com.muhibiscr1t1kal.vulkanextensionemulator.VulkanExtensionEmulator;

public final class StutterLog {
   private static final long WINDOW_NS = 120000000000L;
   private static final long SLOW_FRAME_NS = 80000000L;
   private static final long THROTTLE_NS = 10000000000L;
   private static volatile long windowStart = Long.MAX_VALUE;
   private static volatile long lastReport;
   private static volatile long lastEventLog;
   private static volatile long poolCreates;
   private static volatile long poolResets;
   private static volatile long fbEvicts;

   private StutterLog() {
   }

   public static void markWorldJoin() {
      if (VulkanExtensionEmulator.config().stutterLog) {
         windowStart = System.nanoTime();
         poolCreates = 0L;
         poolResets = 0L;
         fbEvicts = 0L;
         VulkanExtensionEmulator.LOGGER.info("[Vulkan Extension Emulator] Stutter telemetry armed (120s window after world join)");
      }
   }

   public static void noteFrame(long totalNs, long emuNs, long gpuWaitNs, long acquireNs) {
      if (VulkanExtensionEmulator.config().stutterLog) {
         long now = System.nanoTime();
         if (windowStart != Long.MAX_VALUE && now - windowStart <= 120000000000L && totalNs >= 80000000L) {
            if (now - lastReport >= 10000000000L) {
               lastReport = now;
               long other = Math.max(0L, totalNs - emuNs - gpuWaitNs - acquireNs);
               VulkanExtensionEmulator.LOGGER.warn("[Vulkan Extension Emulator] STUTTER {}ms = emu {}ms + gpuWait {}ms + acquire {}ms + other {}ms | pools +{} resets {} fbEvicts {}", new Object[]{ms(totalNs), ms(emuNs), ms(gpuWaitNs), ms(acquireNs), ms(other), poolCreates, poolResets, fbEvicts});
            }
         }
      }
   }

   public static void notePoolCreate() {
      if (VulkanExtensionEmulator.config().stutterLog) {
         ++poolCreates;
      }

   }

   public static void notePoolReset(int blocks) {
      if (VulkanExtensionEmulator.config().stutterLog) {
         poolResets += (long)blocks;
         long now = System.nanoTime();
         if (now - lastEventLog >= 15000000000L) {
            lastEventLog = now;
            VulkanExtensionEmulator.LOGGER.info("[Vulkan Extension Emulator] descriptor churn: +{} pools, {} block resets, {} fb evicts since world join", new Object[]{poolCreates, poolResets, fbEvicts});
         }

      }
   }

   public static void noteFramebufferEvict() {
      if (VulkanExtensionEmulator.config().stutterLog) {
         ++fbEvicts;
      }

   }

   private static String ms(long ns) {
      return String.format("%.1f", new Object[]{(double)ns / 1000000.0});
   }
}
