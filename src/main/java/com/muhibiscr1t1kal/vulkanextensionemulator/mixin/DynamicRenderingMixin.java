package com.muhibiscr1t1kal.vulkanextensionemulator.mixin;

import com.mojang.blaze3d.systems.RenderPassDescriptor;
import com.mojang.blaze3d.vulkan.VulkanCommandEncoder;
import com.muhibiscr1t1kal.vulkanextensionemulator.vk.DynamicRenderingEmulation;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkRenderingInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({VulkanCommandEncoder.class})
public abstract class DynamicRenderingMixin {
   @Inject(
      method = {"createRenderPass"},
      at = @At("HEAD"),
      require = 0
   )
   private void vulkanextensionemulator$stashDescriptor(RenderPassDescriptor descriptor, CallbackInfoReturnable cir) {
      DynamicRenderingEmulation.stashDescriptor(descriptor);
   }

   @Redirect(
      method = {"createRenderPass"},
      at = @At(
   value = "INVOKE",
   target = "Lorg/lwjgl/vulkan/KHRDynamicRendering;vkCmdBeginRenderingKHR(Lorg/lwjgl/vulkan/VkCommandBuffer;Lorg/lwjgl/vulkan/VkRenderingInfo;)V"
),
      require = 0
   )
   private void vulkanextensionemulator$beginRendering(VkCommandBuffer cmd, VkRenderingInfo info) {
      DynamicRenderingEmulation.beginRendering(cmd, info);
   }

   @Redirect(
      method = {"submitRenderPass"},
      at = @At(
   value = "INVOKE",
   target = "Lorg/lwjgl/vulkan/KHRDynamicRendering;vkCmdEndRenderingKHR(Lorg/lwjgl/vulkan/VkCommandBuffer;)V"
),
      require = 0
   )
   private void vulkanextensionemulator$endRendering(VkCommandBuffer cmd) {
      DynamicRenderingEmulation.endRendering(cmd);
   }
}
