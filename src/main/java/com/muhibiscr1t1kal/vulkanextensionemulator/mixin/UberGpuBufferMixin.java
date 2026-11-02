package com.muhibiscr1t1kal.vulkanextensionemulator.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.TlsfAllocator;
import com.mojang.blaze3d.vertex.UberGpuBuffer;
import com.mojang.datafixers.util.Pair;
import com.muhibiscr1t1kal.vulkanextensionemulator.VulkanExtensionEmulator;
import java.util.List;
import java.util.Locale;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({UberGpuBuffer.class})
public abstract class UberGpuBufferMixin {
   @Shadow
   private int bufferUsage;
   @Shadow
   private int heapSize;
   @Shadow
   private String name;
   @Shadow
   private List nodes;

   @Inject(
      method = {"<init>"},
      at = {@At("RETURN")},
      require = 0
   )
   private void vulkanextensionemulator$preCreateSolidHeap(CallbackInfo ci) {
      if (VulkanExtensionEmulator.config().enabled && VulkanExtensionEmulator.shouldPreallocateChunkHeaps() && !VulkanExtensionEmulator.isShaderModInstalled()) {
         if (this.name != null && this.name.equals("UberBuffer solid")) {
            if (RenderSystem.getDevice() != null) {
               try {
                  int i = VulkanExtensionEmulator.preallocateHeapCount();
                  if (i <= 0) {
                     return;
                  }

                  for(int j = 0; j < i; ++j) {
                     this.vulkanextensionemulator$addHeap();
                  }

                  VulkanExtensionEmulator.LOGGER.info("[Vulkan Extension Emulator] Pre-created {} GPU heap(s) of {} bytes at startup", i, this.heapSize);
               } catch (Throwable var4) {
                  VulkanExtensionEmulator.LOGGER.warn("[Vulkan Extension Emulator] Could not pre-create GPU heap; leaving lazy allocation ({} bytes)", this.heapSize, var4);
               }

            }
         }
      }
   }

   private void vulkanextensionemulator$addHeap() {
      String heapName = String.format(Locale.ROOT, "%s %d", new Object[]{this.name, this.nodes.size()});
      UberGpuBuffer.UberGpuBufferHeap heap = new UberGpuBuffer.UberGpuBufferHeap((long)this.heapSize, RenderSystem.getDevice(), this.bufferUsage, heapName);
      TlsfAllocator allocator = new TlsfAllocator(heap);
      this.nodes.add(new Pair(allocator, heap));
   }
}
