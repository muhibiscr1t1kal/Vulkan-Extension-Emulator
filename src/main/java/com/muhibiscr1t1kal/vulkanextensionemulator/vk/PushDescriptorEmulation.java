package com.muhibiscr1t1kal.vulkanextensionemulator.vk;

import com.muhibiscr1t1kal.vulkanextensionemulator.VulkanExtensionEmulator;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkCopyDescriptorSet;
import org.lwjgl.vulkan.VkDescriptorImageInfo;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkWriteDescriptorSet;

public final class PushDescriptorEmulation {
   private static DescriptorPoolManager pools;
   private static VkDevice device;
   private static boolean nativeAvailable;
   private static final DescriptorCache cache = new DescriptorCache();
   private static final Map allocatableLayouts = new ConcurrentHashMap();
   private static final Map layoutByPipelineAndSet = new ConcurrentHashMap();
   private static long skippedPushes;
   private static long lastGeneration;
   private static long nativeFallbacks;
   private static int skippedLogged;
   private static long pushCount;
   private static boolean warnedNoPool;
   private static boolean warnedAllocFailure;
   private static boolean warnedNoLayout;
   private static long allocFailures;
   private static long boundCmd;
   private static long boundLayout;
   private static long boundSet;
   private static int boundSlot = -1;
   private static long redundantBindsSkipped;

   private static void noteBound(VkCommandBuffer cmd, long layout, int slot, long set) {
      boundCmd = cmd.address();
      boundLayout = layout;
      boundSlot = slot;
      boundSet = set;
   }

   private static void clearBound() {
      boundCmd = 0L;
      boundLayout = 0L;
      boundSet = 0L;
      boundSlot = -1;
   }

   private PushDescriptorEmulation() {
   }

   public static void init(VkDevice vkDevice) {
      device = vkDevice;
      lastGeneration = 0L;
      if (vkDevice != null) {
         nativeAvailable = vkDevice.getCapabilities().vkCmdPushDescriptorSetKHR != 0L;
      }

      if (nativeAvailable) {
         if (pools == null) {
            VulkanExtensionEmulator.LOGGER.info("[Vulkan Extension Emulator] VK_KHR_push_descriptor available natively on the created device; passing through (no pool emulation)");
         }

      } else {
         if (pools == null) {
            pools = new DescriptorPoolManager(vkDevice);
            VulkanExtensionEmulator.LOGGER.info("[Vulkan Extension Emulator] Descriptor pool manager initialised");
            if (VulkanExtensionEmulator.config().prewarmDescriptorPools) {
               pools.warm(2, 1);
            }
         }

      }
   }

   public static boolean nativePushDescriptorAvailable() {
      return nativeAvailable;
   }

   public static void shutdown() {
      if (pools != null) {
         try {
            if (device != null) {
               VK10.vkDeviceWaitIdle(device);
            }
         } catch (Throwable var1) {
         }

         VulkanExtensionEmulator.LOGGER.info("[Vulkan Extension Emulator] {}", cache.stats());
         VulkanExtensionEmulator.LOGGER.info("[Vulkan Extension Emulator] descriptor binds: {} pushes, {} redundant binds skipped, {} allocation failure(s)", new Object[]{pushCount, redundantBindsSkipped, allocFailures});
         pools.destroy();
         pools = null;
      }

      cache.invalidate();
      allocatableLayouts.clear();
      nativeAvailable = false;
   }

   public static void invalidateCaches() {
      cache.invalidate();
      clearBound();
   }

   public static void onResourceDestroyed(long handle) {
      cache.invalidateResource(handle);
      clearBound();
   }

   public static void markAllocatable(long layout) {
      allocatableLayouts.put(layout, Boolean.TRUE);
   }

   public static void beginSubmit(long submitIndex, long completedIndex) {
      if (pools != null) {
         pools.beginSubmit(submitIndex, completedIndex);
         long gen = pools.currentGeneration();
         if (gen != lastGeneration) {
            lastGeneration = gen;
            cache.invalidate();
         }

         clearBound();
      }

   }

   public static boolean pushDescriptorSet(VkCommandBuffer cmd, int bindPoint, long pipelineLayout, int set, VkWriteDescriptorSet.Buffer writes, long setLayout) {
      if (cmd != null && writes != null) {
         if (writes.remaining() == 0) {
            return true;
         } else {
            long i = System.nanoTime();

            boolean var11;
            try {
               if (pools != null) {
                  if (setLayout == 0L) {
                     setLayout = (Long)layoutByPipelineAndSet.get(pipelineLayout * 31L + (long)set);
                  }

                  if (setLayout == 0L) {
                     ++skippedPushes;
                     if (!warnedNoLayout) {
                        warnedNoLayout = true;
                        VulkanExtensionEmulator.LOGGER.error("[Vulkan Extension Emulator] Push descriptor with non-empty writes but no descriptor-set-layout (bindPoint={}, set={}, pipelineLayout=0x{}, writes={}); draw will use stale bindings", new Object[]{bindPoint, set, Long.toHexString(pipelineLayout), writes.remaining()});
                     }

                     var11 = false;
                     return var11;
                  }

                  long j = VulkanExtensionEmulator.config().cacheDescriptors ? cache.lookup(setLayout, writes) : 0L;
                  boolean var43;
                  if (j == 0L) {
                     long k = pools.allocate(setLayout);
                     if (k == 0L) {
                        if (!warnedAllocFailure) {
                           warnedAllocFailure = true;
                           VulkanExtensionEmulator.LOGGER.error("[Vulkan Extension Emulator] Descriptor allocation FAILED (pool exhausted or driver error); skipping push. Affected object will be invisible this frame. bindPoint={} set={} writes={}", new Object[]{bindPoint, set, writes.remaining()});
                        }

                        ++allocFailures;
                        ++skippedPushes;
                        clearBound();
                        if (skippedLogged < 3) {
                           ++skippedLogged;
                           VulkanExtensionEmulator.LOGGER.error("[Vulkan Extension Emulator] Skipping push (bindPoint={}, set={}, writes={}); draw will use stale bindings this frame. Vanilla terrain sections may disappear.", new Object[]{bindPoint, set, writes.remaining()});
                        }

                        var43 = false;
                        return var43;
                     }

                     try {
                        MemoryStack memorystack = MemoryStack.stackPush();

                        boolean flag1;
                        try {
                           int l = writes.remaining();
                           VkWriteDescriptorSet.Buffer buffer = VkWriteDescriptorSet.calloc(l, memorystack);
                           int i1 = 0;

                           while(true) {
                              if (i1 >= l) {
                                 Trace.mark("vkUpdateDescriptorSets", "writes=" + l);
                                 VK10.vkUpdateDescriptorSets(device, buffer, (VkCopyDescriptorSet.Buffer)null);
                                 LongBuffer longbuffer1 = memorystack.longs(k);
                                 Trace.mark("vkCmdBindDescriptorSets", "set=" + set);
                                 VK10.vkCmdBindDescriptorSets(cmd, bindPoint, pipelineLayout, set, longbuffer1, (IntBuffer)null);
                                 if (VulkanExtensionEmulator.config().cacheDescriptors) {
                                    cache.store(setLayout, writes, k);
                                 }

                                 noteBound(cmd, pipelineLayout, set, k);
                                 layoutByPipelineAndSet.put(pipelineLayout * 31L + (long)set, setLayout);
                                 ++pushCount;
                                 flag1 = true;
                                 break;
                              }

                              VkWriteDescriptorSet vkwritedescriptorset = (VkWriteDescriptorSet)writes.get(i1);
                              VkWriteDescriptorSet vkwritedescriptorset1 = (VkWriteDescriptorSet)buffer.get(i1);
                              vkwritedescriptorset1.sType$Default().dstSet(k).dstBinding(vkwritedescriptorset.dstBinding()).dstArrayElement(vkwritedescriptorset.dstArrayElement()).descriptorCount(vkwritedescriptorset.descriptorCount()).descriptorType(vkwritedescriptorset.descriptorType());
                              if (vkwritedescriptorset.pBufferInfo() != null) {
                                 vkwritedescriptorset1.pBufferInfo(vkwritedescriptorset.pBufferInfo());
                              }

                              if (vkwritedescriptorset.pImageInfo() != null) {
                                 int i2 = vkwritedescriptorset.descriptorType();
                                 int i3 = vkwritedescriptorset.descriptorCount();
                                 if (i3 <= 0) {
                                    i3 = 1;
                                 }

                                 if (i2 != 1 && i2 != 2 && i2 != 3) {
                                    vkwritedescriptorset1.pImageInfo(vkwritedescriptorset.pImageInfo());
                                 } else {
                                    VkDescriptorImageInfo.Buffer imageInfo = VkDescriptorImageInfo.calloc(i3, memorystack);
                                    VkDescriptorImageInfo.Buffer sourceInfo = vkwritedescriptorset.pImageInfo();

                                    for(int i4 = 0; i4 < i3; ++i4) {
                                       VkDescriptorImageInfo source = (VkDescriptorImageInfo)sourceInfo.get(i4);
                                       ((VkDescriptorImageInfo)imageInfo.get(i4)).sampler(source.sampler()).imageView(source.imageView()).imageLayout(source.imageLayout());
                                    }

                                    vkwritedescriptorset1.pImageInfo(imageInfo);
                                 }
                              }

                              if (vkwritedescriptorset.pTexelBufferView() != null) {
                                 vkwritedescriptorset1.pTexelBufferView(vkwritedescriptorset.pTexelBufferView());
                              }

                              ++i1;
                           }
                        } catch (Throwable var37) {
                           if (memorystack != null) {
                              try {
                                 memorystack.close();
                              } catch (Throwable var34) {
                                 var37.addSuppressed(var34);
                              }
                           }

                           throw var37;
                        }

                        if (memorystack != null) {
                           memorystack.close();
                        }

                        return flag1;
                     } catch (Throwable var38) {
                        VulkanExtensionEmulator.LOGGER.error("[Vulkan Extension Emulator] Push descriptor emulation failed", var38);
                        boolean var16 = false;
                        return var16;
                     }
                  }

                  if (boundSet == j && boundSlot == set && boundLayout == pipelineLayout && boundCmd == cmd.address()) {
                     ++redundantBindsSkipped;
                     ++pushCount;
                     boolean var41 = true;
                     return var41;
                  }

                  MemoryStack memorystack1 = MemoryStack.stackPush();

                  boolean flag;
                  try {
                     LongBuffer longbuffer = memorystack1.longs(j);
                     VK10.vkCmdBindDescriptorSets(cmd, bindPoint, pipelineLayout, set, longbuffer, (IntBuffer)null);
                     noteBound(cmd, pipelineLayout, set, j);
                     ++pushCount;
                     flag = true;
                  } catch (Throwable var36) {
                     if (memorystack1 != null) {
                        try {
                           memorystack1.close();
                        } catch (Throwable var35) {
                           var36.addSuppressed(var35);
                        }
                     }

                     throw var36;
                  }

                  if (memorystack1 != null) {
                     memorystack1.close();
                  }

                  var43 = flag;
                  return var43;
               }

               if (!warnedNoPool) {
                  warnedNoPool = true;
                  VulkanExtensionEmulator.LOGGER.error("[Vulkan Extension Emulator] Push descriptor called before pool init — descriptors will be missing this frame");
               }

               var11 = false;
            } finally {
               FrameProfiler.addEmulation(i);
            }

            return var11;
         }
      } else {
         return false;
      }
   }

   public static long pushCount() {
      return pushCount;
   }

   public static long cacheHits() {
      return cache.hits();
   }

   public static long cacheMisses() {
      return cache.misses();
   }

   public static long redundantBindsSkipped() {
      return redundantBindsSkipped;
   }

   public static DescriptorPoolManager pools() {
      return pools;
   }

   public static long skippedPushes() {
      return skippedPushes;
   }
}
