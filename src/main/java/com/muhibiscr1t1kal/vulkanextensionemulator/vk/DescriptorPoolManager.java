package com.muhibiscr1t1kal.vulkanextensionemulator.vk;

import com.muhibiscr1t1kal.vulkanextensionemulator.VulkanExtensionEmulator;
import java.nio.LongBuffer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkAllocationCallbacks;
import org.lwjgl.vulkan.VkDescriptorPoolCreateInfo;
import org.lwjgl.vulkan.VkDescriptorPoolSize;
import org.lwjgl.vulkan.VkDescriptorSetAllocateInfo;
import org.lwjgl.vulkan.VkDevice;

public final class DescriptorPoolManager {
   private static final int SETS_PER_BLOCK = 2048;
   private static final int UNIFORM_BUFFERS_PER_BLOCK = 8192;
   private static final int SAMPLERS_PER_BLOCK = 8192;
   private static final int TEXEL_BUFFERS_PER_BLOCK = 8192;
   private final VkDevice device;
   private final Deque free = new ArrayDeque();
   private final List inFlight = new ArrayList();
   private Pool current;
   private long setsAllocated;
   private long poolsCreated;
   private boolean loggedFirstUse;
   private long generation;

   public DescriptorPoolManager(VkDevice device) {
      this.device = device;
   }

   public static int setsPerBlock() {
      int i = VulkanExtensionEmulator.config().descriptorBlockSets;
      if (i > 0) {
         return i;
      } else {
         return VulkanExtensionEmulator.deviceLocalBytes() >= 2147483648L ? 4096 : 2048;
      }
   }

   public void warm(int pools, int blocks) {
      if (pools > 0) {
         int created = 0;

         for(int i = 0; i < pools; ++i) {
            Pool pool = new Pool();

            for(int j = 0; j < blocks; ++j) {
               long l = this.createBlock();
               if (l == 0L) {
                  return;
               }

               pool.blocks.add(l);
            }

            this.free.push(pool);
            ++created;
         }

         VulkanExtensionEmulator.LOGGER.info("[Vulkan Extension Emulator] Pre-warmed {} descriptor pool(s) ({} block(s), {} sets/block)", new Object[]{created, blocks, setsPerBlock()});
      }
   }

   public long currentSubmitIndex() {
      return this.current == null ? Long.MIN_VALUE : this.current.submitIndex;
   }

   public void beginSubmit(long submitIndex, long completedIndex) {
      this.recycle(completedIndex);
      if (this.current == null || this.current.submitIndex != submitIndex) {
         if (this.current != null) {
            this.inFlight.add(this.current);
         }

         this.current = this.free.isEmpty() ? new Pool() : (Pool)this.free.pop();
         this.current.submitIndex = submitIndex;
         this.current.currentBlock = this.current.blocks.isEmpty() ? -1 : 0;
         this.current.setsUsedInCurrentBlock = 0;
         if (!this.current.blocks.isEmpty()) {
            int i = 0;

            for(Iterator var6 = this.current.blocks.iterator(); var6.hasNext(); ++i) {
               long l = (Long)var6.next();
               VK10.vkResetDescriptorPool(this.device, l, 0);
            }

            StutterLog.notePoolReset(i);
            this.current.currentBlock = 0;
            ++this.generation;
         }
      }

   }

   private void recycle(long completedIndex) {
      for(int i = this.inFlight.size() - 1; i >= 0; --i) {
         Pool descriptorpoolmanager$pool = (Pool)this.inFlight.get(i);
         if (descriptorpoolmanager$pool.submitIndex <= completedIndex) {
            this.inFlight.remove(i);
            this.free.push(descriptorpoolmanager$pool);
         }
      }

   }

   public long allocate(long layout) {
      if (this.current == null) {
         this.beginSubmit(0L, -1L);
      }

      if ((this.current.currentBlock < 0 || this.current.setsUsedInCurrentBlock >= setsPerBlock()) && !this.advanceBlock()) {
         return 0L;
      } else {
         long i = (Long)this.current.blocks.get(this.current.currentBlock);

         try {
            MemoryStack memorystack = MemoryStack.stackPush();

            long k;
            label96: {
               label97: {
                  try {
                     LongBuffer longbuffer = memorystack.longs(layout);
                     VkDescriptorSetAllocateInfo vkdescriptorsetallocateinfo = VkDescriptorSetAllocateInfo.calloc(memorystack).sType$Default().descriptorPool(i).pSetLayouts(longbuffer);
                     LongBuffer longbuffer1 = memorystack.mallocLong(1);
                     int j = VK10.vkAllocateDescriptorSets(this.device, vkdescriptorsetallocateinfo, longbuffer1);
                     if (j == -1000069000 || j == -12) {
                        if (!this.advanceBlock()) {
                           k = 0L;
                           break label96;
                        }

                        i = (Long)this.current.blocks.get(this.current.currentBlock);
                        vkdescriptorsetallocateinfo.descriptorPool(i);
                        j = VK10.vkAllocateDescriptorSets(this.device, vkdescriptorsetallocateinfo, longbuffer1);
                     }

                     if (j != 0) {
                        VulkanExtensionEmulator.LOGGER.error("[Vulkan Extension Emulator] vkAllocateDescriptorSets failed (code={}, layout=0x{})", j, Long.toHexString(layout));
                        k = 0L;
                        break label97;
                     }

                     ++this.current.setsUsedInCurrentBlock;
                     ++this.setsAllocated;
                     if (!this.loggedFirstUse) {
                        this.loggedFirstUse = true;
                        VulkanExtensionEmulator.LOGGER.info("[Vulkan Extension Emulator] push-descriptor emulation active (descriptor pool + vkCmdBindDescriptorSets)");
                     }

                     k = longbuffer1.get(0);
                  } catch (Throwable var13) {
                     if (memorystack != null) {
                        try {
                           memorystack.close();
                        } catch (Throwable var12) {
                           var13.addSuppressed(var12);
                        }
                     }

                     throw var13;
                  }

                  if (memorystack != null) {
                     memorystack.close();
                  }

                  return k;
               }

               if (memorystack != null) {
                  memorystack.close();
               }

               return k;
            }

            if (memorystack != null) {
               memorystack.close();
            }

            return k;
         } catch (Throwable var14) {
            VulkanExtensionEmulator.LOGGER.error("[Vulkan Extension Emulator] Descriptor allocation failed", var14);
            return 0L;
         }
      }
   }

   private boolean advanceBlock() {
      int i = this.current.currentBlock + 1;
      if (i >= this.current.blocks.size()) {
         long j = this.createBlock();
         if (j == 0L) {
            return false;
         }

         this.current.blocks.add(j);
      }

      this.current.currentBlock = i;
      this.current.setsUsedInCurrentBlock = 0;
      return true;
   }

   private long createBlock() {
      try {
         MemoryStack memorystack = MemoryStack.stackPush();

         long j;
         label50: {
            try {
               int i1 = setsPerBlock();
               VkDescriptorPoolSize.Buffer buffer = VkDescriptorPoolSize.calloc(3, memorystack);
               ((VkDescriptorPoolSize)buffer.get(0)).type(6).descriptorCount(i1 * 2);
               ((VkDescriptorPoolSize)buffer.get(1)).type(1).descriptorCount(i1 * 2);
               ((VkDescriptorPoolSize)buffer.get(2)).type(4).descriptorCount(i1 * 2);
               VkDescriptorPoolCreateInfo vkdescriptorpoolcreateinfo = VkDescriptorPoolCreateInfo.calloc(memorystack).sType$Default().maxSets(i1).pPoolSizes(buffer);
               LongBuffer longbuffer = memorystack.mallocLong(1);
               int i = VK10.vkCreateDescriptorPool(this.device, vkdescriptorpoolcreateinfo, (VkAllocationCallbacks)null, longbuffer);
               if (i != 0) {
                  VulkanExtensionEmulator.LOGGER.error("[Vulkan Extension Emulator] vkCreateDescriptorPool failed: {}", i);
                  j = 0L;
                  break label50;
               }

               ++this.poolsCreated;
               StutterLog.notePoolCreate();
               j = longbuffer.get(0);
            } catch (Throwable var10) {
               if (memorystack != null) {
                  try {
                     memorystack.close();
                  } catch (Throwable var9) {
                     var10.addSuppressed(var9);
                  }
               }

               throw var10;
            }

            if (memorystack != null) {
               memorystack.close();
            }

            return j;
         }

         if (memorystack != null) {
            memorystack.close();
         }

         return j;
      } catch (Throwable var11) {
         VulkanExtensionEmulator.LOGGER.error("[Vulkan Extension Emulator] Descriptor pool creation failed", var11);
         return 0L;
      }
   }

   public void destroy() {
      List list = new ArrayList(this.inFlight);
      list.addAll(this.free);
      if (this.current != null) {
         list.add(this.current);
      }

      Iterator var2 = list.iterator();

      while(var2.hasNext()) {
         Pool descriptorpoolmanager$pool = (Pool)var2.next();
         Iterator var4 = descriptorpoolmanager$pool.blocks.iterator();

         while(var4.hasNext()) {
            long i = (Long)var4.next();

            try {
               VK10.vkDestroyDescriptorPool(this.device, i, (VkAllocationCallbacks)null);
            } catch (Throwable var8) {
            }
         }
      }

      this.inFlight.clear();
      this.free.clear();
      this.current = null;
      ++this.generation;
   }

   public long setsAllocated() {
      return this.setsAllocated;
   }

   public long currentGeneration() {
      return this.generation;
   }

   public long poolsCreated() {
      return this.poolsCreated;
   }

   private static final class Pool {
      final List blocks = new ArrayList();
      long submitIndex = -1L;
      int setsUsedInCurrentBlock;
      int currentBlock = -1;
   }
}
