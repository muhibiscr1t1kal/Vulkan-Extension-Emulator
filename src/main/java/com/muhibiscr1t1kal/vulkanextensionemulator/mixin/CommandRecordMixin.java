package com.muhibiscr1t1kal.vulkanextensionemulator.mixin;

import com.mojang.blaze3d.vulkan.VulkanCommandEncoder;
import com.muhibiscr1t1kal.vulkanextensionemulator.vk.BarrierCoalescer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({VulkanCommandEncoder.class})
public abstract class CommandRecordMixin {
   @Inject(
      method = {"writeToBuffer"},
      at = @At("HEAD"),
      require = 0
   )
   private void vulkanextensionemulator$w1(CallbackInfo ci) {
      BarrierCoalescer.commandRecorded();
   }

   @Inject(
      method = {"copyToBuffer"},
      at = @At("HEAD"),
      require = 0
   )
   private void vulkanextensionemulator$w2(CallbackInfo ci) {
      BarrierCoalescer.commandRecorded();
   }

   @Inject(
      method = {"writeToTexture"},
      at = @At("HEAD"),
      require = 0
   )
   private void vulkanextensionemulator$w3(CallbackInfo ci) {
      BarrierCoalescer.commandRecorded();
   }

   @Inject(
      method = {"copyBufferToTexture"},
      at = @At("HEAD"),
      require = 0
   )
   private void vulkanextensionemulator$w4(CallbackInfo ci) {
      BarrierCoalescer.commandRecorded();
   }

   @Inject(
      method = {"copyTextureToBuffer(Lcom/mojang/blaze3d/textures/GpuTexture;Lcom/mojang/blaze3d/buffers/GpuBuffer;JLjava/lang/Runnable;I)V"},
      at = @At("HEAD"),
      require = 0
   )
   private void vulkanextensionemulator$w5a(CallbackInfo ci) {
      BarrierCoalescer.commandRecorded();
   }

   @Inject(
      method = {"copyTextureToBuffer(Lcom/mojang/blaze3d/textures/GpuTexture;Lcom/mojang/blaze3d/buffers/GpuBuffer;JLjava/lang/Runnable;IIIII)V"},
      at = @At("HEAD"),
      require = 0
   )
   private void vulkanextensionemulator$w5b(CallbackInfo ci) {
      BarrierCoalescer.commandRecorded();
   }

   @Inject(
      method = {"copyTextureToTexture"},
      at = @At("HEAD"),
      require = 0
   )
   private void vulkanextensionemulator$w6(CallbackInfo ci) {
      BarrierCoalescer.commandRecorded();
   }

   @Inject(
      method = {"clearColorTexture"},
      at = @At("HEAD"),
      require = 0
   )
   private void vulkanextensionemulator$w7(CallbackInfo ci) {
      BarrierCoalescer.commandRecorded();
   }

   @Inject(
      method = {"clearDepthTexture"},
      at = @At("HEAD"),
      require = 0
   )
   private void vulkanextensionemulator$w8(CallbackInfo ci) {
      BarrierCoalescer.commandRecorded();
   }

   @Inject(
      method = {"clearColorAndDepthTextures(Lcom/mojang/blaze3d/textures/GpuTexture;Lorg/joml/Vector4fc;Lcom/mojang/blaze3d/textures/GpuTexture;D)V"},
      at = @At("HEAD"),
      require = 0
   )
   private void vulkanextensionemulator$w9a(CallbackInfo ci) {
      BarrierCoalescer.commandRecorded();
   }

   @Inject(
      method = {"clearColorAndDepthTextures(Lcom/mojang/blaze3d/textures/GpuTexture;Lorg/joml/Vector4fc;Lcom/mojang/blaze3d/textures/GpuTexture;DIIII)V"},
      at = @At("HEAD"),
      require = 0
   )
   private void vulkanextensionemulator$w9b(CallbackInfo ci) {
      BarrierCoalescer.commandRecorded();
   }

   @Inject(
      method = {"clearColorTextureUnsynced"},
      at = @At("HEAD"),
      require = 0
   )
   private void vulkanextensionemulator$w10(CallbackInfo ci) {
      BarrierCoalescer.commandRecorded();
   }

   @Inject(
      method = {"clearDepthTextureUnsynced"},
      at = @At("HEAD"),
      require = 0
   )
   private void vulkanextensionemulator$w11(CallbackInfo ci) {
      BarrierCoalescer.commandRecorded();
   }

   @Inject(
      method = {"createRenderPass"},
      at = @At("HEAD"),
      require = 0
   )
   private void vulkanextensionemulator$w12(CallbackInfoReturnable cir) {
      BarrierCoalescer.commandRecorded();
   }

   @Inject(
      method = {"submitRenderPass"},
      at = @At("HEAD"),
      require = 0
   )
   private void vulkanextensionemulator$w13(CallbackInfo ci) {
      BarrierCoalescer.commandRecorded();
   }

   @Inject(
      method = {"execute"},
      at = @At("HEAD"),
      require = 0
   )
   private void vulkanextensionemulator$w14(CallbackInfo ci) {
      BarrierCoalescer.commandRecorded();
   }

   @Inject(
      method = {"submit"},
      at = @At("HEAD"),
      require = 0
   )
   private void vulkanextensionemulator$reset(CallbackInfo ci) {
      BarrierCoalescer.reset();
   }
}
