package com.muhibiscr1t1kal.vulkanextensionemulator.mixin;

import com.mojang.blaze3d.vulkan.Destroyable;
import com.mojang.blaze3d.vulkan.DestructionQueue;
import com.mojang.blaze3d.vulkan.VulkanCommandEncoder;
import com.mojang.blaze3d.vulkan.VulkanCommandPool;
import com.mojang.blaze3d.vulkan.VulkanDevice;
import com.muhibiscr1t1kal.vulkanextensionemulator.VulkanExtensionEmulator;
import com.muhibiscr1t1kal.vulkanextensionemulator.vk.PipelineDepth;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({VulkanCommandEncoder.class})
public abstract class PipelineDepthMixin {
   @Shadow
   @Final
   private VulkanDevice device;
   @Shadow
   @Final
   @Mutable
   private VulkanCommandPool[] commandPools;
   @Shadow
   @Final
   @Mutable
   private DestructionQueue destroyQueue;
   @Shadow
   private long currentSubmitIndex;

   @Inject(
      method = {"<init>"},
      at = @At("RETURN"),
      require = 0
   )
   private void vulkanextensionemulator$deepenRings(VulkanDevice device, CallbackInfo ci) {
      int i = PipelineDepth.requestedExtra();
      if (i != 0) {
         try {
            int j = 2 + i;
            VulkanCommandPool[] avulkancommandpool = new VulkanCommandPool[j];
            System.arraycopy(this.commandPools, 0, avulkancommandpool, 0, this.commandPools.length);

            int l;
            for(l = this.commandPools.length; l < j; ++l) {
               avulkancommandpool[l] = new VulkanCommandPool(device, device.graphicsQueue());
            }

            this.commandPools = avulkancommandpool;
            this.destroyQueue = new DestructionQueue(j, (o) -> {
               ((Destroyable)o).destroy();
            });
            if (this.commandPools == null || this.commandPools.length != j) {
               String var10002 = String.valueOf(this.commandPools == null ? "null" : this.commandPools.length);
               throw new IllegalStateException("commandPools is " + var10002 + ", expected " + j);
            }

            for(l = 0; l < j; ++l) {
               if (this.commandPools[l] == null) {
                  throw new IllegalStateException("commandPools[" + l + "] is null");
               }
            }

            if (this.destroyQueue == null) {
               throw new IllegalStateException("destroyQueue is null after resize");
            }

            PipelineDepth.armRings(i);
            VulkanExtensionEmulator.LOGGER.info("[Vulkan Extension Emulator] Deeper pipeline armed: {} frames in flight ({} command pool(s))", PipelineDepth.poolCount(), j);
         } catch (Throwable var7) {
            VulkanExtensionEmulator.LOGGER.error("[Vulkan Extension Emulator] Could not resize submit rings -- keeping stock pipeline depth (deeperPipeline has no effect)", var7);
         }
      }

   }

   @Inject(
      method = {"currentCommandPool"},
      at = @At("RETURN"),
      cancellable = true,
      require = 0
   )
   private void vulkanextensionemulator$ringIndex(CallbackInfoReturnable cir) {
      if (PipelineDepth.armed() && this.commandPools.length > 2) {
         cir.setReturnValue(this.commandPools[(int)(this.currentSubmitIndex % (long)this.commandPools.length)]);
      }

   }

   @Inject(
      method = {"destroy"},
      at = @At("RETURN"),
      require = 0
   )
   private void vulkanextensionemulator$destroyExtraPools(CallbackInfo ci) {
      if (PipelineDepth.armed()) {
         for(int i = 2; i < this.commandPools.length; ++i) {
            VulkanCommandPool vulkancommandpool = this.commandPools[i];
            if (vulkancommandpool != null) {
               try {
                  vulkancommandpool.destroy();
               } catch (Throwable var5) {
                  VulkanExtensionEmulator.LOGGER.warn("[Vulkan Extension Emulator] Failed to destroy extra command pool {}", i, var5);
               }
            }
         }
      }

   }

   @ModifyArg(
      method = {"submit"},
      at = @At(
   value = "INVOKE",
   target = "Lcom/mojang/blaze3d/vulkan/VulkanCommandEncoder;awaitSubmitCompletion(JJ)Z"
),
      index = 0,
      require = 0
   )
   private long vulkanextensionemulator$deepenPipeline(long waitIndex) {
      return PipelineDepth.adjustWaitTarget(waitIndex, this.currentSubmitIndex);
   }
}
