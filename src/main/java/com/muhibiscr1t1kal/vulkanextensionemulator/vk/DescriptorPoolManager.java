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
   private static final int UNIFORM_BUFFERS_PER_BLOCK = 4096;
   private static final int SAMPLERS_PER_BLOCK = 4096;
   private static final int TEXEL_BUFFERS_PER_BLOCK = 4096;

   private final VkDevice device;
   private final Deque<Pool> free = new ArrayDeque<>();
   private final List<Pool> inFlight = new ArrayList<>();

   private Pool current;
   private long setsAllocated;
   private long poolsCreated;
   private boolean loggedFirstUse;

   public DescriptorPoolManager(VkDevice device) {
      this.device = device;
   }

   public long currentSubmitIndex() {
      return current == null ? Long.MIN_VALUE : current.submitIndex;
   }

   public void beginSubmit(long submitIndex, long completedIndex) {
      recycle(completedIndex);

      if (current == null || current.submitIndex != submitIndex) {
         if (current != null) {
            inFlight.add(current);
         }

         current = free.isEmpty() ? new Pool() : free.pop();
         current.submitIndex = submitIndex;
         current.currentBlock = current.blocks.isEmpty() ? -1 : 0;
         current.setsUsedInCurrentBlock = 0;

         if (!current.blocks.isEmpty()) {
            for (long pool : current.blocks) {
               VK10.vkResetDescriptorPool(device, pool, 0);
            }

            current.currentBlock = 0;
         }
      }
   }

   private void recycle(long completedIndex) {
      for (int i = inFlight.size() - 1; i >= 0; --i) {
         Pool pool = inFlight.get(i);

         if (pool.submitIndex <= completedIndex) {
            inFlight.remove(i);
            free.push(pool);
         }
      }
   }

   public long allocate(long layout) {
      if (current == null) {
         beginSubmit(0L, -1L);
      }

      if ((current.currentBlock < 0
         || current.setsUsedInCurrentBlock >= SETS_PER_BLOCK)
         && !advanceBlock()) {
         return 0L;
      }

      long pool = current.blocks.get(current.currentBlock);

      try (MemoryStack stack = MemoryStack.stackPush()) {
         LongBuffer layouts = stack.longs(layout);

         VkDescriptorSetAllocateInfo allocateInfo =
            VkDescriptorSetAllocateInfo.calloc(stack)
               .sType$Default()
               .descriptorPool(pool)
               .pSetLayouts(layouts);

         LongBuffer descriptorSet = stack.mallocLong(1);

         int result = VK10.vkAllocateDescriptorSets(
            device,
            allocateInfo,
            descriptorSet
         );

         if (result == -1000069000 || result == -12) {
            if (!advanceBlock()) {
               return 0L;
            }

            pool = current.blocks.get(current.currentBlock);
            allocateInfo.descriptorPool(pool);

            result = VK10.vkAllocateDescriptorSets(
               device,
               allocateInfo,
               descriptorSet
            );
         }

         if (result != 0) {
            VulkanExtensionEmulator.LOGGER.error(
               "[Vulkan Extension Emulator] vkAllocateDescriptorSets failed (code={}, layout=0x{})",
               result,
               Long.toHexString(layout)
            );
            return 0L;
         }

         ++current.setsUsedInCurrentBlock;
         ++setsAllocated;

         if (!loggedFirstUse) {
            loggedFirstUse = true;

            VulkanExtensionEmulator.LOGGER.info(
               "[Vulkan Extension Emulator] push-descriptor emulation active (descriptor pool + vkCmdBindDescriptorSets)"
            );
         }

         return descriptorSet.get(0);
      } catch (Throwable exception) {
         VulkanExtensionEmulator.LOGGER.error(
            "[Vulkan Extension Emulator] Descriptor allocation failed",
            exception
         );
         return 0L;
      }
   }

   private boolean advanceBlock() {
      int nextBlock = current.currentBlock + 1;

      if (nextBlock >= current.blocks.size()) {
         long pool = createBlock();

         if (pool == 0L) {
            return false;
         }

         current.blocks.add(pool);
      }

      current.currentBlock = nextBlock;
      current.setsUsedInCurrentBlock = 0;

      return true;
   }

   private long createBlock() {
      try (MemoryStack stack = MemoryStack.stackPush()) {
         VkDescriptorPoolSize.Buffer poolSizes =
            VkDescriptorPoolSize.calloc(3, stack);

         poolSizes.get(0)
            .type(6)
            .descriptorCount(UNIFORM_BUFFERS_PER_BLOCK);

         poolSizes.get(1)
            .type(1)
            .descriptorCount(SAMPLERS_PER_BLOCK);

         poolSizes.get(2)
            .type(4)
            .descriptorCount(TEXEL_BUFFERS_PER_BLOCK);

         VkDescriptorPoolCreateInfo createInfo =
            VkDescriptorPoolCreateInfo.calloc(stack)
               .sType$Default()
               .maxSets(SETS_PER_BLOCK)
               .pPoolSizes(poolSizes);

         LongBuffer poolBuffer = stack.mallocLong(1);

         int result = VK10.vkCreateDescriptorPool(
            device,
            createInfo,
            (VkAllocationCallbacks) null,
            poolBuffer
         );

         if (result != 0) {
            VulkanExtensionEmulator.LOGGER.error(
               "[Vulkan Extension Emulator] vkCreateDescriptorPool failed: {}",
               result
            );
            return 0L;
         }

         ++poolsCreated;
         return poolBuffer.get(0);
      } catch (Throwable exception) {
         VulkanExtensionEmulator.LOGGER.error(
            "[Vulkan Extension Emulator] Descriptor pool creation failed",
            exception
         );
         return 0L;
      }
   }

   public void destroy() {
      List<Pool> pools = new ArrayList<>(inFlight);
      pools.addAll(free);

      if (current != null) {
         pools.add(current);
      }

      for (Pool pool : pools) {
         for (long handle : pool.blocks) {
            try {
               VK10.vkDestroyDescriptorPool(
                  device,
                  handle,
                  (VkAllocationCallbacks) null
               );
            } catch (Throwable ignored) {
            }
         }
      }

      inFlight.clear();
      free.clear();
      current = null;
   }

   public long setsAllocated() {
      return setsAllocated;
   }

   public long poolsCreated() {
      return poolsCreated;
   }

   private static final class Pool {
      final List<Long> blocks = new ArrayList<>();
      long submitIndex = -1L;
      int setsUsedInCurrentBlock;
      int currentBlock = -1;
   }
}