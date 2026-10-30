package com.muhibiscr1t1kal.vulkanextensionemulator.vk;

import com.muhibiscr1t1kal.vulkanextensionemulator.VulkanExtensionEmulator;

public final class PipelineDepth {
   public static final int STOCK_DEPTH = 2;
   private static int extra;
   private static boolean armed;

   private PipelineDepth() {
   }

   public static boolean enabled() {
      return VulkanExtensionEmulator.config().deeperPipeline;
   }

   public static int requestedExtra() {
      return !enabled() ? 0 : Math.max(0, Math.min(VulkanExtensionEmulator.config().extraFramesInFlight, 1));
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
      if (armed && extra > 0) {
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
