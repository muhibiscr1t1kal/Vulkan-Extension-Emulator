package com.muhibiscr1t1kal.vulkanextensionemulator.vk;

import com.muhibiscr1t1kal.vulkanextensionemulator.VulkanExtensionEmulator;

public final class PipelineDepth {
   public static final int STOCK_DEPTH = 2;
   private static int extra;
   private static boolean armed;
   private static int degradeFrames;
   private static int slowStreak;

   private PipelineDepth() {
   }

   public static boolean enabled() {
      if (!VulkanExtensionEmulator.config().deeperPipeline) {
         return false;
      } else if (VulkanExtensionEmulator.config().desktopSafeMode && VulkanExtensionEmulator.isDesktopSafeProfile()) {
         return false;
      } else {
         return !VulkanExtensionEmulator.isShaderModInstalled();
      }
   }

   public static int requestedExtra() {
      if (!enabled()) {
         return 0;
      } else {
         return degraded() ? 0 : Math.max(0, Math.min(VulkanExtensionEmulator.config().extraFramesInFlight, 1));
      }
   }

   public static void noteFrameTime(long ns) {
      if (!VulkanExtensionEmulator.config().adaptiveDepth) {
         degradeFrames = 0;
         slowStreak = 0;
      } else {
         long slowNs = (long)VulkanExtensionEmulator.config().adaptiveDepthSlowMs * 1000000L;
         if (ns >= slowNs) {
            ++slowStreak;
            if (slowStreak >= VulkanExtensionEmulator.config().adaptiveDepthConsecutive) {
               if (degradeFrames == 0) {
                  VulkanExtensionEmulator.LOGGER.warn("[Vulkan Extension Emulator] Adaptive depth: degrading to stock {} frames in flight ({} consecutive slow frames)", 2, slowStreak);
               }

               degradeFrames = VulkanExtensionEmulator.config().adaptiveDepthRecoverFrames;
            }
         } else {
            slowStreak = 0;
         }

         if (degradeFrames > 0) {
            --degradeFrames;
            if (degradeFrames == 0) {
               VulkanExtensionEmulator.LOGGER.info("[Vulkan Extension Emulator] Adaptive depth: restored to {} frames in flight", poolCount());
            }
         }

      }
   }

   public static void resetAdaptive() {
      degradeFrames = 0;
      slowStreak = 0;
   }

   public static boolean degraded() {
      return degradeFrames > 0;
   }

   public static void armRings(int i) {
      extra = Math.max(0, i);
      armed = extra > 0;
   }

   public static boolean armed() {
      return armed;
   }

   public static int poolCount() {
      return 2 + extra;
   }

   public static long adjustWaitTarget(long waitIndex, long currentSubmitIndex) {
      if (armed && extra > 0 && !degraded()) {
         long relaxed = waitIndex - (long)extra;
         if (relaxed >= currentSubmitIndex) {
            relaxed = currentSubmitIndex - 1L;
         }

         return relaxed;
      } else {
         return waitIndex;
      }
   }

   public static String stats() {
      return armed && extra > 0 ? "pipeline depth: " + (2 + extra) + " frames in flight (extra " + extra + ")" : "pipeline depth: unchanged";
   }
}
