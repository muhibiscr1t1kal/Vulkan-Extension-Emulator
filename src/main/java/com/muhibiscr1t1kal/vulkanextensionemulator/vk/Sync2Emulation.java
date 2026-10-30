package com.muhibiscr1t1kal.vulkanextensionemulator.vk;

import com.muhibiscr1t1kal.vulkanextensionemulator.VulkanExtensionEmulator;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.KHRSynchronization2;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkBufferMemoryBarrier;
import org.lwjgl.vulkan.VkBufferMemoryBarrier2;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkDependencyInfo;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkImageMemoryBarrier;
import org.lwjgl.vulkan.VkImageMemoryBarrier2;
import org.lwjgl.vulkan.VkImageSubresourceRange;
import org.lwjgl.vulkan.VkMemoryBarrier;
import org.lwjgl.vulkan.VkMemoryBarrier2;

public final class Sync2Emulation {
   private static final long HIGH_BITS = -4294967296L;
   private static boolean warnedHighStageBits;
   private static boolean warnedTimestampsDisabled;
   private static long barrierCount;

   private Sync2Emulation() {
   }

   public static boolean pipelineBarrier(VkCommandBuffer cmd, VkDependencyInfo info) {
      if (cmd != null && info != null) {
         VkDevice vkdevice = cmd.getDevice();
         if (vkdevice != null && vkdevice.getCapabilities().vkCmdPipelineBarrier2KHR != 0L) {
            KHRSynchronization2.vkCmdPipelineBarrier2KHR(cmd, info);
            return true;
         } else {
            long i = System.nanoTime();

            boolean var7;
            try {
               boolean flag2;
               MemoryStack memorystack;
               label394: {
                  memorystack = MemoryStack.stackPush();

                  try {
                     long j = 0L;
                     long k = 0L;
                     boolean flag = false;
                     VkMemoryBarrier2.Buffer buffer = info.pMemoryBarriers();
                     VkMemoryBarrier.Buffer buffer1 = null;
                     if (buffer != null && buffer.remaining() > 0) {
                        int l = buffer.remaining();
                        buffer1 = VkMemoryBarrier.calloc(l, memorystack);

                        for(int i1 = 0; i1 < l; ++i1) {
                           VkMemoryBarrier2 vkmemorybarrier2 = (VkMemoryBarrier2)buffer.get(i1);
                           j |= vkmemorybarrier2.srcStageMask();
                           k |= vkmemorybarrier2.dstStageMask();
                           ((VkMemoryBarrier)buffer1.get(i1)).sType$Default().srcAccessMask((int)vkmemorybarrier2.srcAccessMask()).dstAccessMask((int)vkmemorybarrier2.dstAccessMask());
                        }
                     }

                     VkBufferMemoryBarrier2.Buffer buffer2 = info.pBufferMemoryBarriers();
                     VkBufferMemoryBarrier.Buffer buffer3 = null;
                     if (buffer2 != null && buffer2.remaining() > 0) {
                        int k2 = buffer2.remaining();
                        buffer3 = VkBufferMemoryBarrier.calloc(k2, memorystack);

                        for(int j1 = 0; j1 < k2; ++j1) {
                           VkBufferMemoryBarrier2 vkbuffermemorybarrier2 = (VkBufferMemoryBarrier2)buffer2.get(j1);
                           j |= vkbuffermemorybarrier2.srcStageMask();
                           k |= vkbuffermemorybarrier2.dstStageMask();
                           ((VkBufferMemoryBarrier)buffer3.get(j1)).sType$Default().srcAccessMask((int)vkbuffermemorybarrier2.srcAccessMask()).dstAccessMask((int)vkbuffermemorybarrier2.dstAccessMask()).srcQueueFamilyIndex(vkbuffermemorybarrier2.srcQueueFamilyIndex()).dstQueueFamilyIndex(vkbuffermemorybarrier2.dstQueueFamilyIndex()).buffer(vkbuffermemorybarrier2.buffer()).offset(vkbuffermemorybarrier2.offset()).size(vkbuffermemorybarrier2.size());
                        }
                     }

                     VkImageMemoryBarrier2.Buffer buffer4 = info.pImageMemoryBarriers();
                     VkImageMemoryBarrier.Buffer buffer5 = null;
                     int j3;
                     int i3;
                     if (buffer4 != null && buffer4.remaining() > 0) {
                        i3 = buffer4.remaining();
                        buffer5 = VkImageMemoryBarrier.calloc(i3, memorystack);

                        for(j3 = 0; j3 < i3; ++j3) {
                           VkImageMemoryBarrier2 vkimagememorybarrier2 = (VkImageMemoryBarrier2)buffer4.get(j3);
                           j |= vkimagememorybarrier2.srcStageMask();
                           k |= vkimagememorybarrier2.dstStageMask();
                           if (vkimagememorybarrier2.newLayout() == 1000001002) {
                              flag = true;
                           }

                           VkImageSubresourceRange vkimagesubresourcerange = vkimagememorybarrier2.subresourceRange();
                           VkImageMemoryBarrier vkimagememorybarrier = (VkImageMemoryBarrier)buffer5.get(j3);
                           vkimagememorybarrier.sType$Default().srcAccessMask((int)vkimagememorybarrier2.srcAccessMask()).dstAccessMask((int)vkimagememorybarrier2.dstAccessMask()).oldLayout(vkimagememorybarrier2.oldLayout()).newLayout(vkimagememorybarrier2.newLayout()).srcQueueFamilyIndex(vkimagememorybarrier2.srcQueueFamilyIndex()).dstQueueFamilyIndex(vkimagememorybarrier2.dstQueueFamilyIndex()).image(vkimagememorybarrier2.image());
                           vkimagememorybarrier.subresourceRange().aspectMask(vkimagesubresourcerange.aspectMask()).baseMipLevel(vkimagesubresourcerange.baseMipLevel()).levelCount(vkimagesubresourcerange.levelCount()).baseArrayLayer(vkimagesubresourcerange.baseArrayLayer()).layerCount(vkimagesubresourcerange.layerCount());
                        }
                     }

                     checkHighBits(j | k);
                     i3 = (int)j;
                     j3 = (int)k;
                     if (i3 == 0) {
                        i3 = 1;
                     }

                     if (j3 == 0) {
                        j3 = 8192;
                     }

                     boolean flag1 = flag && (buffer == null || buffer.remaining() == 0) && (buffer2 == null || buffer2.remaining() == 0) && buffer4 != null && buffer4.remaining() == 1;
                     if (flag1) {
                        j3 = PresentBarrierNarrowing.narrowDstStage(j3, i3, 1000001002);
                     }

                     int k3 = buffer1 == null ? 0 : buffer1.remaining();
                     int l3 = buffer3 == null ? 0 : buffer3.remaining();
                     int l1 = buffer5 == null ? 0 : buffer5.remaining();
                     Trace.barrier(i3, j3, k3, l3, l1);
                     int i2 = k3 > 0 ? ((VkMemoryBarrier)buffer1.get(0)).srcAccessMask() : 0;
                     int j2 = k3 > 0 ? ((VkMemoryBarrier)buffer1.get(0)).dstAccessMask() : 0;
                     if (!BarrierCoalescer.shouldSkip(cmd, i3, j3, i2, j2, l1, l3)) {
                        VK10.vkCmdPipelineBarrier(cmd, i3, j3, info.dependencyFlags(), buffer1, buffer3, buffer5);
                        Trace.mark("vkCmdPipelineBarrier returned");
                        if (barrierCount++ == 0L) {
                           VulkanExtensionEmulator.LOGGER.info("[Vulkan Extension Emulator] sync2 barrier emulation active (vkCmdPipelineBarrier2KHR -> vkCmdPipelineBarrier)");
                        }

                        flag2 = true;
                        break label394;
                     }

                     flag2 = true;
                  } catch (Throwable var32) {
                     if (memorystack != null) {
                        try {
                           memorystack.close();
                        } catch (Throwable var31) {
                           var32.addSuppressed(var31);
                        }
                     }

                     throw var32;
                  }

                  if (memorystack != null) {
                     memorystack.close();
                  }

                  return flag2;
               }

               if (memorystack != null) {
                  memorystack.close();
               }

               var7 = flag2;
               return var7;
            } catch (Throwable var33) {
               VulkanExtensionEmulator.LOGGER.error("[Vulkan Extension Emulator] Barrier translation failed", var33);
               var7 = false;
            } finally {
               FrameProfiler.addEmulation(i);
            }

            return var7;
         }
      } else {
         return false;
      }
   }

   public static boolean writeTimestamp(VkCommandBuffer cmd, long stageMask, long queryPool, int query) {
      if (cmd == null) {
         return false;
      } else if (VulkanExtensionEmulator.config().disableTimestamps) {
         if (!warnedTimestampsDisabled) {
            warnedTimestampsDisabled = true;
            VulkanExtensionEmulator.LOGGER.info("[Vulkan Extension Emulator] Timestamp queries skipped (driver reports timestampValidBits=0)");
         }

         return true;
      } else {
         VkDevice vkdevice = cmd.getDevice();
         if (vkdevice != null && vkdevice.getCapabilities().vkCmdWriteTimestamp2KHR != 0L) {
            KHRSynchronization2.vkCmdWriteTimestamp2KHR(cmd, stageMask, queryPool, query);
            return true;
         } else {
            Trace.mark("vkCmdWriteTimestamp", "stage=0x" + Long.toHexString(stageMask));

            try {
               checkHighBits(stageMask);
               int i = (int)stageMask;
               if (i == 0) {
                  i = 8192;
               } else {
                  i = Integer.highestOneBit(i);
               }

               VK10.vkCmdWriteTimestamp(cmd, i, queryPool, query);
               return true;
            } catch (Throwable var8) {
               VulkanExtensionEmulator.LOGGER.error("[Vulkan Extension Emulator] Timestamp translation failed", var8);
               return false;
            }
         }
      }
   }

   private static void checkHighBits(long mask) {
      if ((mask & -4294967296L) != 0L && !warnedHighStageBits) {
         warnedHighStageBits = true;
         VulkanExtensionEmulator.LOGGER.warn("[Vulkan Extension Emulator] Barrier uses sync2-only stage bits above bit 31 (0x{}); these have no Vulkan 1.0 equivalent and were dropped.", Long.toHexString(mask & -4294967296L));
      }

   }

   public static long barriersTranslated() {
      return barrierCount;
   }
}
