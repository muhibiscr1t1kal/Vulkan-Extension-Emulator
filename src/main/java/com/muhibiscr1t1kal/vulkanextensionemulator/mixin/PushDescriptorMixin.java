package com.muhibiscr1t1kal.vulkanextensionemulator.mixin;

import com.mojang.blaze3d.vulkan.VulkanRenderPass;
import com.mojang.blaze3d.vulkan.VulkanRenderPipeline;
import com.muhibiscr1t1kal.vulkanextensionemulator.vk.Emulation;
import com.muhibiscr1t1kal.vulkanextensionemulator.vk.PushDescriptorEmulation;
import org.lwjgl.vulkan.KHRPushDescriptor;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkWriteDescriptorSet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin({VulkanRenderPass.class})
public abstract class PushDescriptorMixin {
   @Shadow
   protected VulkanRenderPipeline pipeline;

   @Redirect(
      method = {"pushDescriptors"},
      at = @At(
   value = "INVOKE",
   target = "Lorg/lwjgl/vulkan/KHRPushDescriptor;vkCmdPushDescriptorSetKHR(Lorg/lwjgl/vulkan/VkCommandBuffer;IJILorg/lwjgl/vulkan/VkWriteDescriptorSet$Buffer;)V"
),
      require = 0
   )
   private void vulkanextensionemulator$pushDescriptors(VkCommandBuffer cmd, int bindPoint, long pipelineLayout, int set, VkWriteDescriptorSet.Buffer writes) {
      if (cmd != null && cmd.getDevice() != null && cmd.getDevice().getCapabilities().vkCmdPushDescriptorSetKHR != 0L) {
         KHRPushDescriptor.vkCmdPushDescriptorSetKHR(cmd, bindPoint, pipelineLayout, set, writes);
      } else if (!Emulation.isExtensionEmulated("VK_KHR_push_descriptor")) {
         KHRPushDescriptor.vkCmdPushDescriptorSetKHR(cmd, bindPoint, pipelineLayout, set, writes);
      } else {
         long i = 0L;
         if (this.pipeline != null && this.pipeline.layout() != null) {
            i = this.pipeline.layout().handle();
         }

         PushDescriptorEmulation.pushDescriptorSet(cmd, bindPoint, pipelineLayout, set, writes, i);
      }
   }
}
