package com.muhibiscr1t1kal.vulkanextensionemulator.mixin;

import com.mojang.blaze3d.vulkan.VulkanRenderPass;
import com.muhibiscr1t1kal.vulkanextensionemulator.vk.MultiDrawEmulation;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin({VulkanRenderPass.class})
public abstract class MultiDrawMixin {
   @Redirect(
      method = {"drawIndirect"},
      at = @At(
   value = "INVOKE",
   target = "Lorg/lwjgl/vulkan/VK12;vkCmdDrawIndirect(Lorg/lwjgl/vulkan/VkCommandBuffer;JJII)V"
),
      require = 0
   )
   private void vulkanextensionemulator$drawIndirect(VkCommandBuffer cmd, long buffer, long offset, int drawCount, int stride) {
      MultiDrawEmulation.drawIndirect(cmd, buffer, offset, drawCount, stride);
   }

   @Redirect(
      method = {"drawIndexedIndirect"},
      at = @At(
   value = "INVOKE",
   target = "Lorg/lwjgl/vulkan/VK12;vkCmdDrawIndexedIndirect(Lorg/lwjgl/vulkan/VkCommandBuffer;JJII)V"
),
      require = 0
   )
   private void vulkanextensionemulator$drawIndexedIndirect(VkCommandBuffer cmd, long buffer, long offset, int drawCount, int stride) {
      MultiDrawEmulation.drawIndexedIndirect(cmd, buffer, offset, drawCount, stride);
   }
}
