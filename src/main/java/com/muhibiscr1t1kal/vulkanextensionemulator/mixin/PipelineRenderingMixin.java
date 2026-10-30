package com.muhibiscr1t1kal.vulkanextensionemulator.mixin;

import com.mojang.blaze3d.vulkan.VulkanRenderPipeline;
import com.muhibiscr1t1kal.vulkanextensionemulator.vk.DynamicRenderingEmulation;
import java.nio.LongBuffer;
import org.lwjgl.vulkan.VkAllocationCallbacks;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkGraphicsPipelineCreateInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin({VulkanRenderPipeline.class})
public abstract class PipelineRenderingMixin {
   @Redirect(
      method = {"compile"},
      at = @At(
   value = "INVOKE",
   target = "Lorg/lwjgl/vulkan/VK12;vkCreateGraphicsPipelines(Lorg/lwjgl/vulkan/VkDevice;JLorg/lwjgl/vulkan/VkGraphicsPipelineCreateInfo$Buffer;Lorg/lwjgl/vulkan/VkAllocationCallbacks;Ljava/nio/LongBuffer;)I"
),
      require = 0
   )
   private static int vulkanextensionemulator$createPipelines(VkDevice device, long cache, VkGraphicsPipelineCreateInfo.Buffer infos, VkAllocationCallbacks allocator, LongBuffer out) {
      return DynamicRenderingEmulation.createGraphicsPipelines(device, cache, infos, allocator, out);
   }
}
