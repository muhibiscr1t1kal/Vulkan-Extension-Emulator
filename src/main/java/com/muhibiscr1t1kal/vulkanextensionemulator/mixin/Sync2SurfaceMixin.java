package com.muhibiscr1t1kal.vulkanextensionemulator.mixin;

import com.mojang.blaze3d.vulkan.VulkanGpuSurface;
import com.muhibiscr1t1kal.vulkanextensionemulator.vk.Sync2Emulation;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkDependencyInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin({VulkanGpuSurface.class})
public abstract class Sync2SurfaceMixin {
   @Redirect(
      method = {"blitFromTexture"},
      at = @At(
   value = "INVOKE",
   target = "Lorg/lwjgl/vulkan/KHRSynchronization2;vkCmdPipelineBarrier2KHR(Lorg/lwjgl/vulkan/VkCommandBuffer;Lorg/lwjgl/vulkan/VkDependencyInfo;)V"
),
      require = 0
   )
   private void vulkanextensionemulator$surfaceBarrier(VkCommandBuffer cmd, VkDependencyInfo info) {
      Sync2Emulation.pipelineBarrier(cmd, info);
   }
}
