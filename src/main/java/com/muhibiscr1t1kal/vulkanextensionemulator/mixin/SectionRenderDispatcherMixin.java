package com.muhibiscr1t1kal.vulkanextensionemulator.mixin;

import com.muhibiscr1t1kal.vulkanextensionemulator.VulkanExtensionEmulator;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Mixin({SectionRenderDispatcher.class})
public abstract class SectionRenderDispatcherMixin {
   private static final int STAGING_BYTES = 201326592;
   private static final int VERTEX_HEAP_BYTES = 268435456;
   private static final int INDEX_HEAP_BYTES = 67108864;
   private static final int BIG_STAGING_BYTES = 402653184;
   private static final int BIG_VERTEX_HEAP_BYTES = 536870912;
   private static final int BIG_INDEX_HEAP_BYTES = 134217728;

   private static boolean bigBuffers() {
      return VulkanExtensionEmulator.config().bufferAutoSize && VulkanExtensionEmulator.config().autoTunePreallocation && VulkanExtensionEmulator.deviceLocalBytes() >= 4294967296L;
   }

   @ModifyConstant(
      method = {"<init>"},
      constant = {@Constant(
   intValue = 102760448
)},
      require = 0
   )
   private static int vulkanextensionemulator$biggerStagingBuffer(int original) {
      if (!VulkanExtensionEmulator.config().enabled) {
         return original;
      } else {
         return bigBuffers() ? 402653184 : 201326592;
      }
   }

   @ModifyConstant(
      method = {"lambda$new$0"},
      constant = {@Constant(
   intValue = 134217728
)},
      require = 0
   )
   private static int vulkanextensionemulator$biggerVertexHeap(int original) {
      if (!VulkanExtensionEmulator.config().enabled) {
         return original;
      } else {
         return bigBuffers() ? 536870912 : 268435456;
      }
   }

   @ModifyConstant(
      method = {"lambda$new$0"},
      constant = {@Constant(
   intValue = 33554432
)},
      require = 0
   )
   private static int vulkanextensionemulator$biggerIndexHeap(int original) {
      if (!VulkanExtensionEmulator.config().enabled) {
         return original;
      } else {
         return bigBuffers() ? 134217728 : 67108864;
      }
   }
}
