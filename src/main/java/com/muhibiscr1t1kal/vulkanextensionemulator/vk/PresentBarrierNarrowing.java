package com.muhibiscr1t1kal.vulkanextensionemulator.vk;

import com.muhibiscr1t1kal.vulkanextensionemulator.VulkanExtensionEmulator;

public final class PresentBarrierNarrowing {
   public static final int LAYOUT_PRESENT_SRC = 1000001002;
   public static final int STAGE_TRANSFER = 4096;
   public static final int STAGE_BOTTOM_OF_PIPE = 8192;
   public static final int STAGE_ALL_COMMANDS = 65536;
   private static long narrowed;
   private static boolean loggedFirst;

   private PresentBarrierNarrowing() {
   }

   public static boolean enabled() {
      return VulkanExtensionEmulator.config().narrowPresentBarrier;
   }

   public static int narrowDstStage(int dstStage, int srcStage, int newLayout) {
      if (!enabled()) {
         return dstStage;
      } else if (dstStage != 65536) {
         return dstStage;
      } else if (newLayout != 1000001002) {
         return dstStage;
      } else if ((srcStage & 4096) == 0) {
         return dstStage;
      } else {
         if (!loggedFirst) {
            loggedFirst = true;
            VulkanExtensionEmulator.LOGGER.info("[Vulkan Extension Emulator] Present barrier narrowed: dstStage ALL_COMMANDS -> BOTTOM_OF_PIPE (lets the next frame's tiling overlap the present)");
         }

         ++narrowed;
         return 8192;
      }
   }

   public static String stats() {
      return narrowed == 0L ? "present barriers: none narrowed" : "present barriers: " + narrowed + " narrowed";
   }
}
