package com.muhibiscr1t1kal.vulkanextensionemulator.vk;

import com.muhibiscr1t1kal.vulkanextensionemulator.VulkanExtensionEmulator;
import org.lwjgl.vulkan.VkCommandBuffer;

public final class BarrierCoalescer {
   public static final int ALL_COMMANDS = 65536;
   public static final int MEMORY_RW = 98304;
   private static long lastCmd;
   private static int lastSrcStage;
   private static int lastDstStage;
   private static int lastSrcAccess;
   private static int lastDstAccess;
   private static boolean barrierIsCurrent;
   private static long issued;
   private static long coalesced;

   private BarrierCoalescer() {
   }

   public static boolean enabled() {
      return VulkanExtensionEmulator.config().coalesceBarriers;
   }

   public static boolean shouldSkip(VkCommandBuffer cmd, int srcStage, int dstStage, int srcAccess, int dstAccess, int imageCount, int bufferCount) {
      if (!enabled()) {
         ++issued;
         return false;
      } else if (imageCount == 0 && bufferCount == 0) {
         boolean flag = barrierIsCurrent && lastCmd == cmd.address() && lastSrcStage == srcStage && lastDstStage == dstStage && lastSrcAccess == srcAccess && lastDstAccess == dstAccess;
         if (flag) {
            ++coalesced;
            return true;
         } else {
            note(cmd, srcStage, dstStage, srcAccess, dstAccess, true);
            ++issued;
            return false;
         }
      } else {
         note(cmd, srcStage, dstStage, srcAccess, dstAccess, false);
         ++issued;
         return false;
      }
   }

   private static void note(VkCommandBuffer cmd, int srcStage, int dstStage, int srcAccess, int dstAccess, boolean coalescable) {
      lastCmd = cmd.address();
      lastSrcStage = srcStage;
      lastDstStage = dstStage;
      lastSrcAccess = srcAccess;
      lastDstAccess = dstAccess;
      barrierIsCurrent = coalescable;
   }

   public static void commandRecorded() {
      barrierIsCurrent = false;
   }

   public static void reset() {
      lastCmd = 0L;
      barrierIsCurrent = false;
   }

   public static long issuedCount() {
      return issued;
   }

   public static long coalescedCount() {
      return coalesced;
   }

   public static String stats() {
      long i = issued + coalesced;
      return i == 0L ? "barriers: none issued" : String.format("barriers: %d issued, %d coalesced (%.1f%% removed)", new Object[]{issued, coalesced, 100.0 * (double)coalesced / (double)i});
   }
}
