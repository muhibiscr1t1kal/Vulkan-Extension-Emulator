package com.muhibiscr1t1kal.vulkanextensionemulator.vk;

import com.muhibiscr1t1kal.vulkanextensionemulator.VulkanExtensionEmulator;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkCommandBuffer;

public final class MultiDrawEmulation {
   public static final int DRAW_INDIRECT_STRIDE = 16;
   public static final int DRAW_INDEXED_INDIRECT_STRIDE = 20;
   private static boolean active;
   private static boolean loggedFirstUnroll;
   private static long unrolledBatches;
   private static long unrolledDraws;
   private static int largestBatch;

   private MultiDrawEmulation() {
   }

   public static void init(boolean driverHasMultiDrawIndirect) {
      active = !driverHasMultiDrawIndirect;
      if (active) {
         VulkanExtensionEmulator.LOGGER.info("[Vulkan Extension Emulator] multiDrawIndirect emulation armed (batched indirect draws will be unrolled)");
      }

   }

   public static boolean active() {
      return active;
   }

   public static void drawIndirect(VkCommandBuffer cmd, long buffer, long offset, int drawCount, int stride) {
      if (active && drawCount > 1) {
         long i = System.nanoTime();
         int j = stride > 0 ? stride : 16;
         record(drawCount);

         for(int k = 0; k < drawCount; ++k) {
            VK10.vkCmdDrawIndirect(cmd, buffer, offset + (long)k * (long)j, 1, j);
         }

         FrameProfiler.addEmulation(i);
      } else {
         VK10.vkCmdDrawIndirect(cmd, buffer, offset, drawCount, stride);
      }

   }

   public static void drawIndexedIndirect(VkCommandBuffer cmd, long buffer, long offset, int drawCount, int stride) {
      if (active && drawCount > 1) {
         long i = System.nanoTime();
         int j = stride > 0 ? stride : 20;
         record(drawCount);

         for(int k = 0; k < drawCount; ++k) {
            VK10.vkCmdDrawIndexedIndirect(cmd, buffer, offset + (long)k * (long)j, 1, j);
         }

         FrameProfiler.addEmulation(i);
      } else {
         VK10.vkCmdDrawIndexedIndirect(cmd, buffer, offset, drawCount, stride);
      }

   }

   private static void record(int drawCount) {
      ++unrolledBatches;
      unrolledDraws += (long)drawCount;
      if (drawCount > largestBatch) {
         largestBatch = drawCount;
      }

      if (!loggedFirstUnroll) {
         loggedFirstUnroll = true;
         VulkanExtensionEmulator.LOGGER.info("[Vulkan Extension Emulator] First multi-draw unrolled: {} draws in one batch. (Expected with Sodium; vanilla does not batch.)", drawCount);
      }

   }

   public static long unrolledDrawCount() {
      return unrolledDraws;
   }

   public static String stats() {
      return unrolledBatches == 0L ? "multiDrawIndirect: no batches needed unrolling" : "multiDrawIndirect: unrolled " + unrolledBatches + " batch(es) into " + unrolledDraws + " draw(s), largest batch " + largestBatch;
   }
}
