package com.muhibiscr1t1kal.vulkanextensionemulator.vk;

import com.muhibiscr1t1kal.vulkanextensionemulator.VulkanExtensionEmulator;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.KHRSynchronization2;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkCommandBufferSubmitInfo;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkQueue;
import org.lwjgl.vulkan.VkSemaphoreSubmitInfo;
import org.lwjgl.vulkan.VkSubmitInfo;
import org.lwjgl.vulkan.VkSubmitInfo2;
import org.lwjgl.vulkan.VkTimelineSemaphoreSubmitInfo;

public final class SubmitEmulation {
   private static long submitCount;
   private static boolean warnedNoTimeline;

   private SubmitEmulation() {
   }

   public static int queueSubmit(VkQueue queue, VkSubmitInfo2.Buffer submits, long fence) {
      if (queue != null && submits != null) {
         VkDevice vkdevice = queue.getDevice();
         if (vkdevice != null && vkdevice.getCapabilities().vkQueueSubmit2KHR != 0L) {
            return KHRSynchronization2.vkQueueSubmit2KHR(queue, submits, fence);
         } else {
            try {
               MemoryStack memorystack = MemoryStack.stackPush();

               int i2;
               try {
                  int i = submits.remaining();
                  VkSubmitInfo.Buffer buffer = VkSubmitInfo.calloc(i, memorystack);

                  int l1;
                  for(l1 = 0; l1 < i; ++l1) {
                     VkSubmitInfo2 vksubmitinfo2 = (VkSubmitInfo2)submits.get(l1);
                     VkSubmitInfo vksubmitinfo = ((VkSubmitInfo)buffer.get(l1)).sType$Default();
                     VkSemaphoreSubmitInfo.Buffer buffer1 = vksubmitinfo2.pWaitSemaphoreInfos();
                     LongBuffer longbuffer = null;
                     int i3;
                     if (buffer1 != null && buffer1.remaining() > 0) {
                        int k = buffer1.remaining();
                        LongBuffer longbuffer1 = memorystack.mallocLong(k);
                        IntBuffer intbuffer = memorystack.mallocInt(k);
                        longbuffer = memorystack.mallocLong(k);
                        boolean flag = false;

                        for(int l = 0; l < k; ++l) {
                           VkSemaphoreSubmitInfo vksemaphoresubmitinfo = (VkSemaphoreSubmitInfo)buffer1.get(l);
                           longbuffer1.put(l, vksemaphoresubmitinfo.semaphore());
                           i3 = (int)vksemaphoresubmitinfo.stageMask();
                           intbuffer.put(l, i3 == 0 ? 1 : i3);
                           long j1 = vksemaphoresubmitinfo.value();
                           longbuffer.put(l, j1);
                           if (j1 != 0L) {
                              flag = true;
                           }
                        }

                        vksubmitinfo.waitSemaphoreCount(k);
                        vksubmitinfo.pWaitSemaphores(longbuffer1);
                        vksubmitinfo.pWaitDstStageMask(intbuffer);
                        if (!flag) {
                           longbuffer = null;
                        }
                     }

                     VkCommandBufferSubmitInfo.Buffer buffer2 = vksubmitinfo2.pCommandBufferInfos();
                     int l2;
                     if (buffer2 != null && buffer2.remaining() > 0) {
                        int j2 = buffer2.remaining();
                        PointerBuffer pointerbuffer = memorystack.mallocPointer(j2);

                        for(l2 = 0; l2 < j2; ++l2) {
                           pointerbuffer.put(l2, ((VkCommandBufferSubmitInfo)buffer2.get(l2)).commandBuffer());
                        }

                        vksubmitinfo.pCommandBuffers(pointerbuffer);
                     }

                     VkSemaphoreSubmitInfo.Buffer buffer3 = vksubmitinfo2.pSignalSemaphoreInfos();
                     LongBuffer longbuffer2 = null;
                     if (buffer3 != null && buffer3.remaining() > 0) {
                        l2 = buffer3.remaining();
                        LongBuffer longbuffer3 = memorystack.mallocLong(l2);
                        longbuffer2 = memorystack.mallocLong(l2);
                        boolean flag1 = false;

                        for(i3 = 0; i3 < l2; ++i3) {
                           VkSemaphoreSubmitInfo vksemaphoresubmitinfo1 = (VkSemaphoreSubmitInfo)buffer3.get(i3);
                           longbuffer3.put(i3, vksemaphoresubmitinfo1.semaphore());
                           long k1 = vksemaphoresubmitinfo1.value();
                           longbuffer2.put(i3, k1);
                           if (k1 != 0L) {
                              flag1 = true;
                           }
                        }

                        vksubmitinfo.pSignalSemaphores(longbuffer3);
                        if (!flag1) {
                           longbuffer2 = null;
                        }
                     }

                     if (longbuffer != null || longbuffer2 != null) {
                        VkTimelineSemaphoreSubmitInfo vktimelinesemaphoresubmitinfo = VkTimelineSemaphoreSubmitInfo.calloc(memorystack).sType(1000207003);
                        if (longbuffer != null) {
                           vktimelinesemaphoresubmitinfo.waitSemaphoreValueCount(longbuffer.capacity());
                           vktimelinesemaphoresubmitinfo.pWaitSemaphoreValues(longbuffer);
                        }

                        if (longbuffer2 != null) {
                           vktimelinesemaphoresubmitinfo.signalSemaphoreValueCount(longbuffer2.capacity());
                           vktimelinesemaphoresubmitinfo.pSignalSemaphoreValues(longbuffer2);
                        }

                        vksubmitinfo.pNext(vktimelinesemaphoresubmitinfo.address());
                     }
                  }

                  Trace.mark("vkQueueSubmit", "count=" + i);
                  l1 = VK10.vkQueueSubmit(queue, buffer, fence);
                  Trace.mark("vkQueueSubmit returned", l1);
                  if (submitCount++ == 0L) {
                     VulkanExtensionEmulator.LOGGER.info("[Vulkan Extension Emulator] sync2 submit emulation active (vkQueueSubmit2KHR -> vkQueueSubmit + timeline pNext)");
                  }

                  if (l1 != 0) {
                     VulkanExtensionEmulator.LOGGER.error("[Vulkan Extension Emulator] vkQueueSubmit returned {}", l1);
                  }

                  i2 = l1;
               } catch (Throwable var25) {
                  if (memorystack != null) {
                     try {
                        memorystack.close();
                     } catch (Throwable var24) {
                        var25.addSuppressed(var24);
                     }
                  }

                  throw var25;
               }

               if (memorystack != null) {
                  memorystack.close();
               }

               return i2;
            } catch (Throwable var26) {
               VulkanExtensionEmulator.LOGGER.error("[Vulkan Extension Emulator] Submit translation failed", var26);
               return -13;
            }
         }
      } else {
         return -13;
      }
   }

   public static long submitsTranslated() {
      return submitCount;
   }
}
