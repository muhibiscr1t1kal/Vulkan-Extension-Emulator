package com.muhibiscr1t1kal.vulkanextensionemulator.vk;

import com.mojang.blaze3d.systems.RenderPassDescriptor;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vulkan.VulkanConst;
import com.muhibiscr1t1kal.vulkanextensionemulator.VulkanExtensionEmulator;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.KHRDynamicRendering;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VK12;
import org.lwjgl.vulkan.VkAllocationCallbacks;
import org.lwjgl.vulkan.VkAttachmentDescription;
import org.lwjgl.vulkan.VkAttachmentReference;
import org.lwjgl.vulkan.VkClearValue;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkFramebufferCreateInfo;
import org.lwjgl.vulkan.VkGraphicsPipelineCreateInfo;
import org.lwjgl.vulkan.VkPipelineRenderingCreateInfoKHR;
import org.lwjgl.vulkan.VkRect2D;
import org.lwjgl.vulkan.VkRenderPassBeginInfo;
import org.lwjgl.vulkan.VkRenderPassCreateInfo;
import org.lwjgl.vulkan.VkRenderingAttachmentInfo;
import org.lwjgl.vulkan.VkRenderingInfo;
import org.lwjgl.vulkan.VkSubpassDescription;

public final class DynamicRenderingEmulation {
   private static final int ST_PIPELINE_RENDERING_CREATE_INFO = 1000044002;
   private static final int LAYOUT_GENERAL = 1;
   private static VkDevice device;
   private static final int MAX_FRAMEBUFFERS = 256;
   private static final Map<Key, Long> renderPasses = new HashMap<>();
   private static final Map<Key, Long> framebuffers = new HashMap<>();
   private static final Map<Long, List<Key>> viewToFramebuffers = new HashMap<>();
   private static final Map<Long, Long> pendingFramebuffers = new HashMap<>();
   private static long lifecycleCounter;
   private static final ThreadLocal<RenderPassDescriptor> pending = new ThreadLocal<>();
   private static final Set<Key> recentlyUsed = new HashSet<>();
   private static boolean loggedTrim;
   private static long renderPassesCreated;
   private static long framebuffersCreated;
   private static long framebuffersEvicted;
   private static boolean loggedFirstPass;
   private static boolean warnedMissingDescriptor;
   private static final Set<String> pipelineFormats = new HashSet<>();
   private static final Set<String> reportedMismatches = new HashSet<>();

   private DynamicRenderingEmulation() {
   }

   public static long renderPassCount() {
      return renderPassesCreated;
   }

   public static long framebufferCount() {
      return framebuffersCreated;
   }

   public static boolean active() {
      return VulkanExtensionEmulator.config().emulateDynamicRendering
         && Emulation.isExtensionEmulated("VK_KHR_dynamic_rendering");
   }

   public static void init(VkDevice d) {
      device = d;
      VulkanExtensionEmulator.LOGGER.info(
         "[Vulkan Extension Emulator] Dynamic rendering emulation initialised (render passes + framebuffers, layout=GENERAL, no transitions)"
      );
   }

   public static void shutdown() {
      if (device != null) {
         try {
            VK10.vkDeviceWaitIdle(device);
         } catch (Throwable ignored) {
         }

         for (long framebuffer : framebuffers.values()) {
            try {
               VK10.vkDestroyFramebuffer(
                  device,
                  framebuffer,
                  (VkAllocationCallbacks) null
               );
            } catch (Throwable ignored) {
            }
         }

         for (long renderPass : renderPasses.values()) {
            try {
               VK10.vkDestroyRenderPass(
                  device,
                  renderPass,
                  (VkAllocationCallbacks) null
               );
            } catch (Throwable ignored) {
            }
         }

         for (long framebuffer : pendingFramebuffers.keySet()) {
            try {
               VK10.vkDestroyFramebuffer(
                  device,
                  framebuffer,
                  (VkAllocationCallbacks) null
               );
            } catch (Throwable ignored) {
            }
         }

         VulkanExtensionEmulator.LOGGER.info(
            "[Vulkan Extension Emulator] Dynamic rendering shutdown: {} render pass(es), {} framebuffer(s) created, {} evicted",
            renderPassesCreated,
            framebuffersCreated,
            framebuffersEvicted
         );

         framebuffers.clear();
         renderPasses.clear();
         viewToFramebuffers.clear();
         pendingFramebuffers.clear();
         recentlyUsed.clear();
         pipelineFormats.clear();
         reportedMismatches.clear();
         pending.remove();
         device = null;
      }
   }

   public static void stashDescriptor(RenderPassDescriptor descriptor) {
      if (active()) {
         pending.set(descriptor);
      }
   }

   public static void beginRendering(VkCommandBuffer cmd, VkRenderingInfo info) {
      VkDevice vkDevice = cmd != null ? cmd.getDevice() : null;

      if (!active() || vkDevice != null
         && vkDevice.getCapabilities().vkCmdBeginRenderingKHR != 0L) {
         KHRDynamicRendering.vkCmdBeginRenderingKHR(cmd, info);
      } else {
         RenderPassDescriptor descriptor = pending.get();
         pending.remove();

         if (descriptor == null && !warnedMissingDescriptor) {
            warnedMissingDescriptor = true;
            VulkanExtensionEmulator.LOGGER.error(
               "[Vulkan Extension Emulator] vkCmdBeginRendering with no stashed descriptor — attachment formats are unknown. Rendering will be wrong."
            );
         }

         if (device == null) {
            device = cmd.getDevice();
         }

         long startTime = System.nanoTime();

         try {
            begin0(cmd, info, descriptor);
         } catch (Throwable exception) {
            VulkanExtensionEmulator.LOGGER.error(
               "[Vulkan Extension Emulator] Dynamic rendering emulation failed at vkCmdBeginRendering; the frame will be wrong but the game should stay up",
               exception
            );
         } finally {
            FrameProfiler.addEmulation(startTime);
         }
      }
   }

   private static void begin0(
      VkCommandBuffer cmd,
      VkRenderingInfo info,
      RenderPassDescriptor descriptor
   ) {
      int colorCount = info.colorAttachmentCount();
      VkRenderingAttachmentInfo.Buffer colorAttachments =
         colorCount > 0 ? info.pColorAttachments() : null;
      VkRenderingAttachmentInfo depthAttachment = info.pDepthAttachment();

      int[] colorSlots = new int[colorCount];
      long[] views = new long[
         colorCount + (depthAttachment != null ? 1 : 0)
      ];

      int attachmentCount = 0;
      int[] signature = new int[1 + colorCount * 3 + 3];
      signature[0] = colorCount;

      for (int index = 0; index < colorCount; ++index) {
         VkRenderingAttachmentInfo attachment = colorAttachments.get(index);
         long view = attachment.imageView();

         if (view == 0L) {
            colorSlots[index] = -1;
         } else {
            int format = colorFormat(descriptor, index);
            colorSlots[index] = attachmentCount;
            views[attachmentCount] = view;
            signature[1 + index * 3] = format;
            signature[2 + index * 3] = attachment.loadOp();
            signature[3 + index * 3] = attachment.storeOp();
            ++attachmentCount;
         }
      }

      int depthSlot = -1;

      if (depthAttachment != null && depthAttachment.imageView() != 0L) {
         depthSlot = attachmentCount;
         views[attachmentCount] = depthAttachment.imageView();
         signature[1 + colorCount * 3] = depthFormat(descriptor);
         signature[2 + colorCount * 3] = depthAttachment.loadOp();
         signature[3 + colorCount * 3] = depthAttachment.storeOp();
         ++attachmentCount;
      }

      boolean hasDepth = depthSlot != -1;
      checkCompatibility(signature, colorCount);

      Key renderPassKey = new Key(signature);
      long renderPass = renderPasses.computeIfAbsent(
         renderPassKey,
         key -> createRenderPass(signature, colorSlots, hasDepth)
      );

      if (renderPass != 0L) {
         int width = 0;
         int height = 0;

         for (int index = 0; index < colorCount && width == 0; ++index) {
            GpuTextureView textureView = viewAt(descriptor, index);

            if (textureView != null) {
               width = textureView.getWidth(0);
               height = textureView.getHeight(0);
            }
         }

         if (width == 0 && descriptor != null
            && descriptor.depthAttachment() != null) {
            GpuTextureView textureView =
               descriptor.depthAttachment().textureView();
            width = textureView.getWidth(0);
            height = textureView.getHeight(0);
         }

         if (width == 0 || height == 0) {
            VkRect2D renderArea = info.renderArea();
            width = renderArea.offset().x() + renderArea.extent().width();
            height = renderArea.offset().y() + renderArea.extent().height();
         }

         long[] framebufferKeyData = new long[attachmentCount + 3];
         framebufferKeyData[0] = renderPass;
         System.arraycopy(
            views,
            0,
            framebufferKeyData,
            1,
            attachmentCount
         );
         framebufferKeyData[attachmentCount + 1] = width;
         framebufferKeyData[attachmentCount + 2] = height;

         Key framebufferKey = new Key(framebufferKeyData);
         Long cachedFramebuffer = framebuffers.get(framebufferKey);
         recentlyUsed.add(framebufferKey);

         long framebuffer;

         if (cachedFramebuffer != null) {
            framebuffer = cachedFramebuffer;
         } else {
            if (framebuffers.size() >= 64) {
               trimFramebuffers();
            }

            framebuffer = createFramebuffer(
               renderPass,
               views,
               attachmentCount,
               width,
               height
            );

            if (framebuffer == 0L) {
               return;
            }

            framebuffers.put(framebufferKey, framebuffer);

            for (int index = 0; index < attachmentCount; ++index) {
               viewToFramebuffers.computeIfAbsent(
                  views[index],
                  key -> new ArrayList<>()
               ).add(framebufferKey);
            }
         }

         try (MemoryStack stack = MemoryStack.stackPush()) {
            VkClearValue.Buffer clearValues =
               VkClearValue.calloc(Math.max(attachmentCount, 1), stack);

            for (int index = 0; index < colorCount; ++index) {
               if (colorSlots[index] != -1) {
                  VkRenderingAttachmentInfo attachment =
                     colorAttachments.get(index);

                  if (attachment.loadOp() == 1) {
                     MemoryUtil.memCopy(
                        attachment.clearValue().address(),
                        clearValues.get(colorSlots[index]).address(),
                        VkClearValue.SIZEOF
                     );
                  }
               }
            }

            if (depthSlot != -1 && depthAttachment.loadOp() == 1) {
               MemoryUtil.memCopy(
                  depthAttachment.clearValue().address(),
                  clearValues.get(depthSlot).address(),
                  VkClearValue.SIZEOF
               );
            }

            VkRenderPassBeginInfo beginInfo =
               VkRenderPassBeginInfo.calloc(stack)
                  .sType$Default()
                  .renderPass(renderPass)
                  .framebuffer(framebuffer)
                  .pClearValues(clearValues);

            beginInfo.renderArea().set(info.renderArea());
            beginInfo.clearValueCount(attachmentCount);

            if (!loggedFirstPass) {
               loggedFirstPass = true;

               VulkanExtensionEmulator.LOGGER.info(
                  "[Vulkan Extension Emulator] First emulated render pass: {} colour + {} depth, {}x{}, no layout transitions",
                  attachmentCount - (depthSlot != -1 ? 1 : 0),
                  depthSlot != -1 ? 1 : 0,
                  width,
                  height
               );
            }

            Trace.mark("vkCmdBeginRenderPass");
            VK10.vkCmdBeginRenderPass(cmd, beginInfo, 0);
         }
      }
   }

   public static void endRendering(VkCommandBuffer cmd) {
      VkDevice vkDevice = cmd != null ? cmd.getDevice() : null;

      if (active() && (vkDevice == null
         || vkDevice.getCapabilities().vkCmdEndRenderingKHR == 0L)) {
         Trace.mark("vkCmdEndRenderPass");
         VK10.vkCmdEndRenderPass(cmd);
      } else {
         KHRDynamicRendering.vkCmdEndRenderingKHR(cmd);
      }
   }

   public static int createGraphicsPipelines(
      VkDevice dev,
      long cache,
      VkGraphicsPipelineCreateInfo.Buffer infos,
      VkAllocationCallbacks allocator,
      LongBuffer out
   ) {
      long next;

      if (!active() || dev != null
         && dev.getCapabilities().vkCmdBeginRenderingKHR != 0L) {
         next = cache != 0L ? cache : PipelineCache.handle();
         return VK12.vkCreateGraphicsPipelines(
            dev,
            next,
            infos,
            allocator,
            out
         );
      }

      if (device == null) {
         device = dev;
      }

      next = infos.pNext();
      long renderingInfo = findPipelineRenderingCreateInfo(next);

      if (renderingInfo == 0L) {
         long pipelineCache = cache != 0L ? cache : PipelineCache.handle();
         return VK12.vkCreateGraphicsPipelines(
            dev,
            pipelineCache,
            infos,
            allocator,
            out
         );
      }

      long renderPass;

      try {
         renderPass = renderPassForPipeline(renderingInfo);
      } catch (Throwable exception) {
         VulkanExtensionEmulator.LOGGER.error(
            "[Vulkan Extension Emulator] Could not build a render pass for pipeline",
            exception
         );
         return -3;
      }

      if (renderPass == 0L) {
         return -3;
      }

      infos.pNext(0L);
      infos.renderPass(renderPass);
      infos.subpass(0);

      try {
         Trace.mark("vkCreateGraphicsPipelines (render pass substituted)");
         long pipelineCache = cache != 0L ? cache : PipelineCache.handle();

         return VK12.vkCreateGraphicsPipelines(
            dev,
            pipelineCache,
            infos,
            allocator,
            out
         );
      } finally {
         infos.pNext(next);
      }
   }

   private static long findPipelineRenderingCreateInfo(long head) {
      for (long address = head; address != 0L;
         address = MemoryUtil.memGetAddress(address + 8L)) {
         int structureType = MemoryUtil.memGetInt(address);

         if (structureType == ST_PIPELINE_RENDERING_CREATE_INFO) {
            return address;
         }
      }

      return 0L;
   }

   private static long renderPassForPipeline(long renderingInfo) {
      VkPipelineRenderingCreateInfoKHR info =
         VkPipelineRenderingCreateInfoKHR.create(renderingInfo);

      int colorCount = info.colorAttachmentCount();
      IntBuffer formats = info.pColorAttachmentFormats();
      int depthFormat = info.depthAttachmentFormat();

      int[] colorSlots = new int[colorCount];
      int[] signature = new int[1 + colorCount * 3 + 3];
      signature[0] = colorCount;

      int attachmentCount = 0;

      for (int index = 0; index < colorCount; ++index) {
         int format = formats == null ? 0 : formats.get(index);

         if (format == 0) {
            colorSlots[index] = -1;
         } else {
            colorSlots[index] = attachmentCount++;
            signature[1 + index * 3] = format;
            signature[2 + index * 3] = 0;
            signature[3 + index * 3] = 0;
         }
      }

      boolean hasDepth = depthFormat != 0;

      if (hasDepth) {
         signature[1 + colorCount * 3] = depthFormat;
         signature[2 + colorCount * 3] = 0;
         signature[3 + colorCount * 3] = 0;
      }

      pipelineFormats.add(formatSignature(signature, colorCount));

      return renderPasses.computeIfAbsent(
         new Key(signature),
         key -> createRenderPass(signature, colorSlots, hasDepth)
      );
   }

   private static String formatSignature(int[] signature, int colorCount) {
      StringBuilder builder = new StringBuilder();

      for (int index = 0; index < colorCount; ++index) {
         builder.append(signature[1 + index * 3]).append(',');
      }

      return builder.append('|')
         .append(signature[1 + colorCount * 3])
         .toString();
   }

   private static void checkCompatibility(int[] signature, int colorCount) {
      if (!pipelineFormats.isEmpty()) {
         String currentSignature = formatSignature(signature, colorCount);

         if (!pipelineFormats.contains(currentSignature)
            && reportedMismatches.add(currentSignature)) {
            VulkanExtensionEmulator.LOGGER.warn(
               "[Vulkan Extension Emulator] Render pass formats [{}] match no compiled pipeline {}. If you see corrupted or missing geometry, this is why.",
               currentSignature,
               pipelineFormats
            );
         }
      }
   }

   private static long createRenderPass(
      int[] signature,
      int[] colorSlots,
      boolean hasDepth
   ) {
      int colorCount = signature[0];
      int attachmentCount = 0;

      for (int slot : colorSlots) {
         if (slot != -1) {
            ++attachmentCount;
         }
      }

      if (hasDepth) {
         ++attachmentCount;
      }

      try (MemoryStack stack = MemoryStack.stackPush()) {
         VkAttachmentDescription.Buffer attachments =
            VkAttachmentDescription.calloc(
               Math.max(attachmentCount, 1),
               stack
            );

         VkAttachmentReference.Buffer colorReferences =
            VkAttachmentReference.calloc(
               Math.max(colorCount, 1),
               stack
            );

         for (int index = 0; index < colorCount; ++index) {
            int slot = colorSlots[index];

            colorReferences.get(index)
               .attachment(slot)
               .layout(slot == -1 ? 0 : LAYOUT_GENERAL);

            if (slot != -1) {
               attachments.get(slot)
                  .format(signature[1 + index * 3])
                  .samples(1)
                  .loadOp(signature[2 + index * 3])
                  .storeOp(signature[3 + index * 3])
                  .stencilLoadOp(2)
                  .stencilStoreOp(1)
                  .initialLayout(LAYOUT_GENERAL)
                  .finalLayout(LAYOUT_GENERAL);
            }
         }

         VkAttachmentReference depthReference = null;

         if (hasDepth) {
            int slot = attachmentCount - 1;

            attachments.get(slot)
               .format(signature[1 + colorCount * 3])
               .samples(1)
               .loadOp(signature[2 + colorCount * 3])
               .storeOp(signature[3 + colorCount * 3])
               .stencilLoadOp(2)
               .stencilStoreOp(1)
               .initialLayout(LAYOUT_GENERAL)
               .finalLayout(LAYOUT_GENERAL);

            depthReference = VkAttachmentReference.calloc(stack)
               .attachment(slot)
               .layout(LAYOUT_GENERAL);
         }

         VkSubpassDescription.Buffer subpasses =
            VkSubpassDescription.calloc(1, stack);

         subpasses.get(0)
            .pipelineBindPoint(0)
            .colorAttachmentCount(colorCount)
            .pColorAttachments(colorCount > 0 ? colorReferences : null)
            .pDepthStencilAttachment(depthReference);

         VkRenderPassCreateInfo createInfo =
            VkRenderPassCreateInfo.calloc(stack)
               .sType$Default()
               .pSubpasses(subpasses);

         if (attachmentCount > 0) {
            attachments.limit(attachmentCount);
            createInfo.pAttachments(attachments);
         }

         LongBuffer renderPassBuffer = stack.callocLong(1);

         int result = VK10.vkCreateRenderPass(
            device,
            createInfo,
            (VkAllocationCallbacks) null,
            renderPassBuffer
         );

         if (result != 0) {
            VulkanExtensionEmulator.LOGGER.error(
               "[Vulkan Extension Emulator] vkCreateRenderPass failed: {}",
               result
            );
            return 0L;
         }

         ++renderPassesCreated;
         return renderPassBuffer.get(0);
      } catch (Throwable exception) {
         VulkanExtensionEmulator.LOGGER.error(
            "[Vulkan Extension Emulator] Render pass creation failed",
            exception
         );
         return 0L;
      }
   }

   private static long createFramebuffer(
      long renderPass,
      long[] views,
      int count,
      int width,
      int height
   ) {
      try (MemoryStack stack = MemoryStack.stackPush()) {
         LongBuffer attachments = stack.callocLong(Math.max(count, 1));

         for (int index = 0; index < count; ++index) {
            attachments.put(index, views[index]);
         }

         attachments.limit(count);

         VkFramebufferCreateInfo createInfo =
            VkFramebufferCreateInfo.calloc(stack)
               .sType$Default()
               .renderPass(renderPass)
               .width(width)
               .height(height)
               .layers(1);

         if (count > 0) {
            createInfo.pAttachments(attachments);
         }

         LongBuffer framebufferBuffer = stack.callocLong(1);

         int result = VK10.vkCreateFramebuffer(
            device,
            createInfo,
            (VkAllocationCallbacks) null,
            framebufferBuffer
         );

         if (result != 0) {
            VulkanExtensionEmulator.LOGGER.error(
               "[Vulkan Extension Emulator] vkCreateFramebuffer failed: {} ({}x{}, {} attachment(s))",
               result,
               width,
               height,
               count
            );
            return 0L;
         }

         ++framebuffersCreated;
         return framebufferBuffer.get(0);
      } catch (Throwable exception) {
         VulkanExtensionEmulator.LOGGER.error(
            "[Vulkan Extension Emulator] Framebuffer creation failed",
            exception
         );
         return 0L;
      }
   }

   private static void trimFramebuffers() {
      if (device != null) {
         int initialSize = framebuffers.size();
         Iterator<Map.Entry<Key, Long>> iterator =
            framebuffers.entrySet().iterator();

         while (iterator.hasNext()) {
            Map.Entry<Key, Long> entry = iterator.next();

            if (recentlyUsed.contains(entry.getKey())) {
               continue;
            }

            pendingFramebuffers.put(
               entry.getValue(),
               lifecycleCounter + 3L
            );

            iterator.remove();

            for (List<Key> keys : viewToFramebuffers.values()) {
               keys.remove(entry.getKey());
            }
         }

         viewToFramebuffers.entrySet().removeIf(
            entry -> entry.getValue().isEmpty()
         );

         recentlyUsed.clear();

         if (initialSize != framebuffers.size() && !loggedTrim) {
            loggedTrim = true;

            VulkanExtensionEmulator.LOGGER.info(
               "[Vulkan Extension Emulator] Framebuffer cache trimmed {} -> {} (bounded at {}); further trims are silent",
               initialSize,
               framebuffers.size(),
               64
            );
         }
      }
   }

   public static void invalidateAll() {
      if (device != null) {
         for (long framebuffer : framebuffers.values()) {
            pendingFramebuffers.put(
               framebuffer,
               lifecycleCounter + 3L
            );
         }

         framebuffers.clear();
         viewToFramebuffers.clear();
         recentlyUsed.clear();
      }
   }

   public static void onImageViewDestroyed(long view) {
      if (device != null && view != 0L) {
         List<Key> keys = viewToFramebuffers.remove(view);

         if (keys != null) {
            for (Key key : keys) {
               Long framebuffer = framebuffers.remove(key);

               if (framebuffer == null) {
                  continue;
               }

               pendingFramebuffers.put(
                  framebuffer,
                  lifecycleCounter + 3L
               );

               Iterator<Map.Entry<Long, List<Key>>> iterator =
                  viewToFramebuffers.entrySet().iterator();

               while (iterator.hasNext()) {
                  Map.Entry<Long, List<Key>> entry = iterator.next();
                  entry.getValue().remove(key);

                  if (entry.getValue().isEmpty()) {
                     iterator.remove();
                  }
               }
            }
         }
      }
   }

   public static void drainPendingDestroys(long completedSubmitIndex) {
      if (device != null && !pendingFramebuffers.isEmpty()) {
         ++lifecycleCounter;
         long safe = lifecycleCounter;

         Iterator<Map.Entry<Long, Long>> iterator =
            pendingFramebuffers.entrySet().iterator();

         while (iterator.hasNext()) {
            Map.Entry<Long, Long> entry = iterator.next();

            if (entry.getValue() <= safe) {
               try {
                  VK10.vkDestroyFramebuffer(
                     device,
                     entry.getKey(),
                     (VkAllocationCallbacks) null
                  );
                  ++framebuffersEvicted;
               } catch (Throwable ignored) {
               }

               iterator.remove();
            }
         }
      }
   }


private static GpuTextureView viewAt(
   RenderPassDescriptor descriptor,
   int index
) {
   if (descriptor == null) {
      return null;
   }

   var attachments = descriptor.colorAttachments();

   if (index >= attachments.size()) {
      return null;
   }

   var attachment = attachments.get(index);

   return attachment == null ? null : attachment.textureView();
}

   private static int colorFormat(
      RenderPassDescriptor descriptor,
      int index
   ) {
      GpuTextureView textureView = viewAt(descriptor, index);

      return textureView == null
         ? 0
         : VulkanConst.toVk(textureView.texture().getFormat());
   }

   private static int depthFormat(RenderPassDescriptor descriptor) {
      return descriptor != null && descriptor.depthAttachment() != null
         ? VulkanConst.toVk(
            descriptor.depthAttachment().textureView().texture().getFormat()
         )
         : 0;
   }

   private static final class Key {
      private final int[] ints;
      private final long[] longs;
      private final int hash;

      Key(int[] ints) {
         this.ints = ints.clone();
         this.longs = null;
         this.hash = Arrays.hashCode(this.ints);
      }

      Key(long[] longs) {
         this.ints = null;
         this.longs = longs.clone();
         this.hash = Arrays.hashCode(this.longs);
      }

      @Override
      public boolean equals(Object object) {
         if (this == object) {
            return true;
         }

         if (!(object instanceof Key other) || other.hash != hash) {
            return false;
         }

         return Arrays.equals(ints, other.ints)
            && Arrays.equals(longs, other.longs);
      }

      @Override
      public int hashCode() {
         return hash;
      }
   }
}