package com.muhibiscr1t1kal.vulkanextensionemulator.vk;

import com.mojang.renderpearl.api.commands.RenderPassDescriptor;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import com.mojang.renderpearl.backend.vulkan.VulkanConst;
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
   private static final Map renderPasses = new HashMap();
   private static final Map framebuffers = new HashMap();
   private static final Map viewToFramebuffers = new HashMap();
   private static final Map pendingFramebuffers = new HashMap();
   private static long lifecycleCounter;
   private static final ThreadLocal pending = new ThreadLocal();
   private static final Set recentlyUsed = new HashSet();
   private static boolean loggedTrim;
   private static long currentSubmitIndex;
   private static long renderPassesCreated;
   private static long framebuffersCreated;
   private static long framebuffersEvicted;
   private static boolean loggedFirstPass;
   private static boolean warnedMissingDescriptor;
   private static final Set pipelineFormats = new HashSet();
   private static final Set reportedMismatches = new HashSet();

   private DynamicRenderingEmulation() {
   }

   public static long renderPassCount() {
      return renderPassesCreated;
   }

   public static long framebufferCount() {
      return framebuffersCreated;
   }

   public static boolean active() {
      return VulkanExtensionEmulator.config().emulateDynamicRendering && Emulation.isExtensionEmulated("VK_KHR_dynamic_rendering");
   }

   public static void init(VkDevice d) {
      device = d;
      VulkanExtensionEmulator.LOGGER.info("[Vulkan Extension Emulator] Dynamic rendering emulation initialised (render passes + framebuffers, layout=GENERAL, no transitions)");
   }

   public static void shutdown() {
      if (device != null) {
         try {
            VK10.vkDeviceWaitIdle(device);
         } catch (Throwable var7) {
         }

         Iterator var0 = framebuffers.values().iterator();

         long k;
         while(var0.hasNext()) {
            k = (Long)var0.next();

            try {
               VK10.vkDestroyFramebuffer(device, k, (VkAllocationCallbacks)null);
            } catch (Throwable var6) {
            }
         }

         var0 = renderPasses.values().iterator();

         while(var0.hasNext()) {
            k = (Long)var0.next();

            try {
               VK10.vkDestroyRenderPass(device, k, (VkAllocationCallbacks)null);
            } catch (Throwable var5) {
            }
         }

         var0 = pendingFramebuffers.keySet().iterator();

         while(var0.hasNext()) {
            k = (Long)var0.next();

            try {
               VK10.vkDestroyFramebuffer(device, k, (VkAllocationCallbacks)null);
            } catch (Throwable var4) {
            }
         }

         VulkanExtensionEmulator.LOGGER.info("[Vulkan Extension Emulator] Dynamic rendering shutdown: {} render pass(es), {} framebuffer(s) created, {} evicted", new Object[]{renderPassesCreated, framebuffersCreated, framebuffersEvicted});
         framebuffers.clear();
         renderPasses.clear();
         viewToFramebuffers.clear();
         pendingFramebuffers.clear();
         device = null;
      }

   }

   public static void stashDescriptor(RenderPassDescriptor descriptor) {
      if (active()) {
         pending.set(descriptor);
      }

   }

   public static void beginRendering(VkCommandBuffer cmd, VkRenderingInfo info) {
      if (active() && !NativeCaps.dynamicRendering) {
         RenderPassDescriptor renderpassdescriptor = (RenderPassDescriptor)pending.get();
         pending.remove();
         if (renderpassdescriptor == null && !warnedMissingDescriptor) {
            warnedMissingDescriptor = true;
            VulkanExtensionEmulator.LOGGER.error("[Vulkan Extension Emulator] vkCmdBeginRendering with no stashed descriptor — attachment formats are unknown. Rendering will be wrong.");
         }

         if (device == null) {
            device = cmd.getDevice();
         }

         long i = System.nanoTime();

         try {
            begin0(cmd, info, renderpassdescriptor);
         } catch (Throwable var9) {
            VulkanExtensionEmulator.LOGGER.error("[Vulkan Extension Emulator] Dynamic rendering emulation failed at vkCmdBeginRendering; the frame will be wrong but the game should stay up", var9);
         } finally {
            FrameProfiler.addEmulation(i);
         }
      } else {
         KHRDynamicRendering.vkCmdBeginRenderingKHR(cmd, info);
      }

   }

   private static void begin0(VkCommandBuffer cmd, VkRenderingInfo info, RenderPassDescriptor descriptor) {
      int i = info.colorAttachmentCount();
      VkRenderingAttachmentInfo.Buffer buffer = i > 0 ? info.pColorAttachments() : null;
      VkRenderingAttachmentInfo vkrenderingattachmentinfo = info.pDepthAttachment();
      int[] aint = new int[i];
      long[] along = new long[i + (vkrenderingattachmentinfo != null ? 1 : 0)];
      int j = 0;
      int[] aint1 = new int[1 + i * 3 + 3];
      aint1[0] = i;

      int i3;
      for(i3 = 0; i3 < i; ++i3) {
         VkRenderingAttachmentInfo vkrenderingattachmentinfo1 = (VkRenderingAttachmentInfo)buffer.get(i3);
         long l = vkrenderingattachmentinfo1.imageView();
         if (l == 0L) {
            aint[i3] = -1;
         } else {
            int j1 = colorFormat(descriptor, i3);
            aint[i3] = j;
            along[j] = l;
            aint1[1 + i3 * 3] = j1;
            aint1[2 + i3 * 3] = vkrenderingattachmentinfo1.loadOp();
            aint1[3 + i3 * 3] = vkrenderingattachmentinfo1.storeOp();
            ++j;
         }
      }

      i3 = -1;
      if (vkrenderingattachmentinfo != null && vkrenderingattachmentinfo.imageView() != 0L) {
         i3 = j;
         along[j] = vkrenderingattachmentinfo.imageView();
         aint1[1 + i * 3] = depthFormat(descriptor);
         aint1[2 + i * 3] = vkrenderingattachmentinfo.loadOp();
         aint1[3 + i * 3] = vkrenderingattachmentinfo.storeOp();
         ++j;
      }

      boolean flag = i3 != -1;
      checkCompatibility(aint1, i);
      Key dynamicrenderingemulation$key = new Key(aint1);
      long i1 = (Long)renderPasses.computeIfAbsent(dynamicrenderingemulation$key, (k) -> {
         return createRenderPass(aint1, aint, flag);
      });
      if (i1 != 0L) {
         int k1 = 0;
         int l1 = 0;

         for(int i2 = 0; i2 < i && k1 == 0; ++i2) {
            GpuTextureView gputextureview = viewAt(descriptor, i2);
            if (gputextureview != null) {
               k1 = gputextureview.getWidth(0);
               l1 = gputextureview.getHeight(0);
            }
         }

         if (k1 == 0 && descriptor != null && descriptor.depthAttachment() != null) {
            GpuTextureView gputextureview1 = descriptor.depthAttachment().textureView();
            k1 = gputextureview1.getWidth(0);
            l1 = gputextureview1.getHeight(0);
         }

         if (k1 == 0 || l1 == 0) {
            VkRect2D vkrect2d = info.renderArea();
            k1 = vkrect2d.offset().x() + vkrect2d.extent().width();
            l1 = vkrect2d.offset().y() + vkrect2d.extent().height();
         }

         long[] along1 = new long[j + 3];
         along1[0] = i1;
         System.arraycopy(along, 0, along1, 1, j);
         along1[j + 1] = (long)k1;
         along1[j + 2] = (long)l1;
         Key dynamicrenderingemulation$key1 = new Key(along1);
         Long olong = (Long)framebuffers.get(dynamicrenderingemulation$key1);
         recentlyUsed.add(dynamicrenderingemulation$key1);
         long j2;
         if (olong != null) {
            j2 = olong;
         } else {
            if (framebuffers.size() >= 256) {
               trimFramebuffers();
            }

            j2 = createFramebuffer(i1, along, j, k1, l1);
            if (j2 == 0L) {
               return;
            }

            framebuffers.put(dynamicrenderingemulation$key1, j2);

            for(int k2 = 0; k2 < j; ++k2) {
               ((List)viewToFramebuffers.computeIfAbsent(along[k2], (k) -> {
                  return new ArrayList();
               })).add(dynamicrenderingemulation$key1);
            }
         }

         MemoryStack memorystack = MemoryStack.stackPush();

         try {
            VkClearValue.Buffer buffer1 = VkClearValue.calloc(Math.max(j, 1), memorystack);
            int l2 = 0;

            while(true) {
               if (l2 >= i) {
                  if (i3 != -1 && vkrenderingattachmentinfo.loadOp() == 1) {
                     MemoryUtil.memCopy(vkrenderingattachmentinfo.clearValue().address(), ((VkClearValue)buffer1.get(i3)).address(), (long)VkClearValue.SIZEOF);
                  }

                  VkRenderPassBeginInfo vkrenderpassbegininfo = VkRenderPassBeginInfo.calloc(memorystack).sType$Default().renderPass(i1).framebuffer(j2).pClearValues(buffer1);
                  vkrenderpassbegininfo.renderArea().set(info.renderArea());
                  vkrenderpassbegininfo.clearValueCount(j);
                  if (!loggedFirstPass) {
                     loggedFirstPass = true;
                     VulkanExtensionEmulator.LOGGER.info("[Vulkan Extension Emulator] First emulated render pass: {} colour + {} depth, {}x{}, no layout transitions", new Object[]{j - (i3 != -1 ? 1 : 0), i3 != -1 ? 1 : 0, k1, l1});
                  }

                  Trace.mark("vkCmdBeginRenderPass");
                  VK10.vkCmdBeginRenderPass(cmd, vkrenderpassbegininfo, 0);
                  break;
               }

               if (aint[l2] != -1) {
                  VkRenderingAttachmentInfo vkrenderingattachmentinfo2 = (VkRenderingAttachmentInfo)buffer.get(l2);
                  if (vkrenderingattachmentinfo2.loadOp() == 1) {
                     MemoryUtil.memCopy(vkrenderingattachmentinfo2.clearValue().address(), ((VkClearValue)buffer1.get(aint[l2])).address(), (long)VkClearValue.SIZEOF);
                  }
               }

               ++l2;
            }
         } catch (Throwable var27) {
            if (memorystack != null) {
               try {
                  memorystack.close();
               } catch (Throwable var26) {
                  var27.addSuppressed(var26);
               }
            }

            throw var27;
         }

         if (memorystack != null) {
            memorystack.close();
         }
      }

   }

   public static void endRendering(VkCommandBuffer cmd) {
      if (active() && !NativeCaps.dynamicRendering) {
         Trace.mark("vkCmdEndRenderPass");
         VK10.vkCmdEndRenderPass(cmd);
      } else {
         KHRDynamicRendering.vkCmdEndRenderingKHR(cmd);
      }

   }

   public static int createGraphicsPipelines(VkDevice dev, long cache, VkGraphicsPipelineCreateInfo.Buffer infos, VkAllocationCallbacks allocator, LongBuffer out) {
      long i;
      if (active() && !NativeCaps.dynamicRendering) {
         if (device == null) {
            device = dev;
         }

         i = infos.pNext();
         long j = findPipelineRenderingCreateInfo(i);
         long k;
         if (j == 0L) {
            k = cache != 0L ? cache : PipelineCache.handle();
            return VK12.vkCreateGraphicsPipelines(dev, k, infos, allocator, out);
         } else {
            try {
               k = renderPassForPipeline(j);
            } catch (Throwable var19) {
               VulkanExtensionEmulator.LOGGER.error("[Vulkan Extension Emulator] Could not build a render pass for pipeline", var19);
               return -3;
            }

            if (k == 0L) {
               return -3;
            } else {
               infos.pNext(0L);
               infos.renderPass(k);
               infos.subpass(0);

               int var14;
               try {
                  Trace.mark("vkCreateGraphicsPipelines (render pass substituted)");
                  long l = cache != 0L ? cache : PipelineCache.handle();
                  var14 = VK12.vkCreateGraphicsPipelines(dev, l, infos, allocator, out);
               } finally {
                  infos.pNext(i);
               }

               return var14;
            }
         }
      } else {
         i = cache != 0L ? cache : PipelineCache.handle();
         return VK12.vkCreateGraphicsPipelines(dev, i, infos, allocator, out);
      }
   }

   private static long findPipelineRenderingCreateInfo(long head) {
      for(long i = head; i != 0L; i = MemoryUtil.memGetAddress(i + 8L)) {
         int j = MemoryUtil.memGetInt(i);
         if (j == 1000044002) {
            return i;
         }
      }

      return 0L;
   }

   private static long renderPassForPipeline(long renderingInfo) {
      VkPipelineRenderingCreateInfoKHR vkpipelinerenderingcreateinfokhr = VkPipelineRenderingCreateInfoKHR.create(renderingInfo);
      int i = vkpipelinerenderingcreateinfokhr.colorAttachmentCount();
      IntBuffer intbuffer = vkpipelinerenderingcreateinfokhr.pColorAttachmentFormats();
      int j = vkpipelinerenderingcreateinfokhr.depthAttachmentFormat();
      int[] aint = new int[i];
      int[] aint1 = new int[1 + i * 3 + 3];
      aint1[0] = i;
      int k = 0;

      for(int l = 0; l < i; ++l) {
         int i1 = intbuffer == null ? 0 : intbuffer.get(l);
         if (i1 == 0) {
            aint[l] = -1;
         } else {
            aint[l] = k++;
            aint1[1 + l * 3] = i1;
            aint1[2 + l * 3] = 0;
            aint1[3 + l * 3] = 0;
         }
      }

      boolean flag = j != 0;
      if (flag) {
         aint1[1 + i * 3] = j;
         aint1[2 + i * 3] = 0;
         aint1[3 + i * 3] = 0;
      }

      pipelineFormats.add(formatSignature(aint1, i));
      return (Long)renderPasses.computeIfAbsent(new Key(aint1), (kx) -> {
         return createRenderPass(aint1, aint, flag);
      });
   }

   private static String formatSignature(int[] sig, int colorCount) {
      StringBuilder stringbuilder = new StringBuilder();

      for(int i = 0; i < colorCount; ++i) {
         stringbuilder.append(sig[1 + i * 3]).append(',');
      }

      return stringbuilder.append('|').append(sig[1 + colorCount * 3]).toString();
   }

   private static void checkCompatibility(int[] sig, int colorCount) {
      if (!pipelineFormats.isEmpty()) {
         String s = formatSignature(sig, colorCount);
         if (!pipelineFormats.contains(s) && reportedMismatches.add(s)) {
            VulkanExtensionEmulator.LOGGER.warn("[Vulkan Extension Emulator] Render pass formats [{}] match no compiled pipeline {}. If you see corrupted or missing geometry, this is why.", s, pipelineFormats);
         }
      }

   }

   private static long createRenderPass(int[] sig, int[] colorSlot, boolean hasDepth) {
      int i = sig[0];
      int j = 0;
      int[] var5 = colorSlot;
      int var6 = colorSlot.length;

      for(int var7 = 0; var7 < var6; ++var7) {
         int k = var5[var7];
         if (k != -1) {
            ++j;
         }
      }

      if (hasDepth) {
         ++j;
      }

      MemoryStack memorystack = MemoryStack.stackPush();

      long l1;
      label95: {
         try {
            VkAttachmentDescription.Buffer buffer = VkAttachmentDescription.calloc(Math.max(j, 1), memorystack);
            VkAttachmentReference.Buffer buffer1 = VkAttachmentReference.calloc(Math.max(i, 1), memorystack);

            int k1;
            for(int j1 = 0; j1 < i; ++j1) {
               k1 = colorSlot[j1];
               ((VkAttachmentReference)buffer1.get(j1)).attachment(k1).layout(k1 == -1 ? 0 : 1);
               if (k1 != -1) {
                  ((VkAttachmentDescription)buffer.get(k1)).format(sig[1 + j1 * 3]).samples(1).loadOp(sig[2 + j1 * 3]).storeOp(sig[3 + j1 * 3]).stencilLoadOp(2).stencilStoreOp(1).initialLayout(1).finalLayout(1);
               }
            }

            VkAttachmentReference vkattachmentreference = null;
            if (hasDepth) {
               k1 = j - 1;
               ((VkAttachmentDescription)buffer.get(k1)).format(sig[1 + i * 3]).samples(1).loadOp(sig[2 + i * 3]).storeOp(sig[3 + i * 3]).stencilLoadOp(2).stencilStoreOp(1).initialLayout(1).finalLayout(1);
               vkattachmentreference = VkAttachmentReference.calloc(memorystack).attachment(k1).layout(1);
            }

            VkSubpassDescription.Buffer buffer2 = VkSubpassDescription.calloc(1, memorystack);
            ((VkSubpassDescription)buffer2.get(0)).pipelineBindPoint(0).colorAttachmentCount(i).pColorAttachments(i > 0 ? buffer1 : null).pDepthStencilAttachment(vkattachmentreference);
            VkRenderPassCreateInfo vkrenderpasscreateinfo = VkRenderPassCreateInfo.calloc(memorystack).sType$Default().pSubpasses(buffer2);
            if (j > 0) {
               buffer.limit(j);
               vkrenderpasscreateinfo.pAttachments(buffer);
            }

            LongBuffer longbuffer = memorystack.callocLong(1);
            int i1 = VK10.vkCreateRenderPass(device, vkrenderpasscreateinfo, (VkAllocationCallbacks)null, longbuffer);
            if (i1 != 0) {
               VulkanExtensionEmulator.LOGGER.error("[Vulkan Extension Emulator] vkCreateRenderPass failed: {}", i1);
               l1 = 0L;
               break label95;
            }

            ++renderPassesCreated;
            l1 = longbuffer.get(0);
         } catch (Throwable var16) {
            if (memorystack != null) {
               try {
                  memorystack.close();
               } catch (Throwable var15) {
                  var16.addSuppressed(var15);
               }
            }

            throw var16;
         }

         if (memorystack != null) {
            memorystack.close();
         }

         return l1;
      }

      if (memorystack != null) {
         memorystack.close();
      }

      return l1;
   }

   private static long createFramebuffer(long renderPass, long[] views, int count, int width, int height) {
      MemoryStack memorystack = MemoryStack.stackPush();

      long k;
      label57: {
         try {
            LongBuffer longbuffer = memorystack.callocLong(Math.max(count, 1));

            for(int i = 0; i < count; ++i) {
               longbuffer.put(i, views[i]);
            }

            longbuffer.limit(count);
            VkFramebufferCreateInfo vkframebuffercreateinfo = VkFramebufferCreateInfo.calloc(memorystack).sType$Default().renderPass(renderPass).width(width).height(height).layers(1);
            if (count > 0) {
               vkframebuffercreateinfo.pAttachments(longbuffer);
            }

            LongBuffer longbuffer1 = memorystack.callocLong(1);
            int j = VK10.vkCreateFramebuffer(device, vkframebuffercreateinfo, (VkAllocationCallbacks)null, longbuffer1);
            if (j != 0) {
               VulkanExtensionEmulator.LOGGER.error("[Vulkan Extension Emulator] vkCreateFramebuffer failed: {} ({}x{}, {} attachment(s))", new Object[]{j, width, height, count});
               k = 0L;
               break label57;
            }

            ++framebuffersCreated;
            k = longbuffer1.get(0);
         } catch (Throwable var14) {
            if (memorystack != null) {
               try {
                  memorystack.close();
               } catch (Throwable var13) {
                  var14.addSuppressed(var13);
               }
            }

            throw var14;
         }
      }

if (memorystack != null) {
    memorystack.close();
}

return k;
}

private static void trimFramebuffers() {
    if (device != null) {
        int i = framebuffers.size();
        Iterator iterator = framebuffers.entrySet().iterator();

        while (true) {
            Map.Entry entry;
            do {
                if (!iterator.hasNext()) {
                    viewToFramebuffers.entrySet().removeIf((ex) -> {
                        return ((List)((Map.Entry)ex).getValue()).isEmpty();
                    });
                    recentlyUsed.clear();
                    if (i != framebuffers.size() && !loggedTrim) {
                        loggedTrim = true;
                        VulkanExtensionEmulator.LOGGER.info("[Vulkan Extension Emulator] Framebuffer cache trimmed {} -> {} (bounded at {}); further trims are silent", new Object[]{i, framebuffers.size(), 256});
                    }

                    return;
                }

                entry = (Map.Entry)iterator.next();
            } while (recentlyUsed.contains(entry.getKey()));

            pendingFramebuffers.put((Long)entry.getValue(), currentSubmitIndex + 3L);
            iterator.remove();
            StutterLog.noteFramebufferEvict();
            Iterator var3 = viewToFramebuffers.values().iterator();

            while(var3.hasNext()) {
               List list = (List)var3.next();
               list.remove(entry.getKey());
            }
         }
      }
   }

   public static void noteSubmit(long submitIndex) {
      currentSubmitIndex = submitIndex;
   }

   public static void invalidateAll() {
      if (device != null) {
         Iterator var0 = framebuffers.values().iterator();

         while(var0.hasNext()) {
            long i = (Long)var0.next();
            pendingFramebuffers.put(i, currentSubmitIndex + 3L);
         }

         framebuffers.clear();
         viewToFramebuffers.clear();
         recentlyUsed.clear();
      }

   }

   public static void onImageViewDestroyed(long view) {
      if (device != null && view != 0L) {
         List list = (List)viewToFramebuffers.remove(view);
         if (list != null) {
            Iterator var3 = list.iterator();

            while(true) {
               Key dynamicrenderingemulation$key;
               Long olong;
               do {
                  if (!var3.hasNext()) {
                     return;
                  }

                  dynamicrenderingemulation$key = (Key)var3.next();
                  olong = (Long)framebuffers.remove(dynamicrenderingemulation$key);
               } while(olong == null);

               pendingFramebuffers.put(olong, currentSubmitIndex + 3L);
               Iterator iterator = viewToFramebuffers.entrySet().iterator();

               while(iterator.hasNext()) {
                  Map.Entry entry = (Map.Entry)iterator.next();
                  ((List)entry.getValue()).remove(dynamicrenderingemulation$key);
                  if (((List)entry.getValue()).isEmpty()) {
                     iterator.remove();
                  }
               }
            }
         }
      }

   }

   public static void drainPendingDestroys(long completedSubmitIndex) {
      if (device != null && !pendingFramebuffers.isEmpty()) {
         long safe = completedSubmitIndex - 3L;
         Iterator iterator = pendingFramebuffers.entrySet().iterator();

         while(iterator.hasNext()) {
            Map.Entry entry = (Map.Entry)iterator.next();
            if ((Long)entry.getValue() <= safe) {
               try {
                  VK10.vkDestroyFramebuffer(device, (Long)entry.getKey(), (VkAllocationCallbacks)null);
                  ++framebuffersEvicted;
               } catch (Throwable var7) {
               }

               iterator.remove();
            }
         }

      }
   }

   private static GpuTextureView viewAt(RenderPassDescriptor descriptor, int index) {
      if (descriptor == null) {
         return null;
      } else {
         List list = descriptor.colorAttachments();
         if (index >= list.size()) {
            return null;
         } else {
            RenderPassDescriptor.Attachment attachment = (RenderPassDescriptor.Attachment)list.get(index);
            return attachment == null ? null : attachment.textureView();
         }
      }
   }

   private static int colorFormat(RenderPassDescriptor descriptor, int index) {
      GpuTextureView gputextureview = viewAt(descriptor, index);
      return gputextureview == null ? 0 : VulkanConst.toVk(gputextureview.texture().getFormat());
   }

   private static int depthFormat(RenderPassDescriptor descriptor) {
      return descriptor != null && descriptor.depthAttachment() != null ? VulkanConst.toVk(descriptor.depthAttachment().textureView().texture().getFormat()) : 0;
   }

   private static final class Key {
      private final int[] ints;
      private final long[] longs;
      private final int hash;

      Key(int[] ints) {
         this.ints = ints;
         this.longs = null;
         this.hash = Arrays.hashCode(ints);
      }

      Key(long[] longs) {
         this.ints = null;
         this.longs = longs;
         this.hash = Arrays.hashCode(longs);
      }

      public boolean equals(Object o) {
         if (this == o) {
            return true;
         } else {
            boolean var10000;
            if (o instanceof Key) {
               Key dynamicrenderingemulation$key = (Key)o;
               if (dynamicrenderingemulation$key.hash == this.hash) {
                  var10000 = Arrays.equals(this.ints, dynamicrenderingemulation$key.ints) && Arrays.equals(this.longs, dynamicrenderingemulation$key.longs);
                  return var10000;
               }
            }

            var10000 = false;
            return var10000;
         }
      }

      public int hashCode() {
         return this.hash;
      }
   }
}
