package com.muhibiscr1t1kal.vulkanextensionemulator.mixin;

import com.mojang.blaze3d.vulkan.VulkanCommandEncoder;
import com.muhibiscr1t1kal.vulkanextensionemulator.vk.Sync2Emulation;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkDependencyInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin({VulkanCommandEncoder.class})
public abstract class Sync2RedirectMixin {
   @Redirect(
      method = {"memoryBarrier(Lorg/lwjgl/vulkan/VkCommandBuffer;Lorg/lwjgl/system/MemoryStack;)V"},
      at = @At(
   value = "INVOKE",
   target = "Lorg/lwjgl/vulkan/KHRSynchronization2;vkCmdPipelineBarrier2KHR(Lorg/lwjgl/vulkan/VkCommandBuffer;Lorg/lwjgl/vulkan/VkDependencyInfo;)V"
),
      require = 0
   )
   private static void vulkanextensionemulator$barrier(VkCommandBuffer cmd, VkDependencyInfo info) {
      Sync2Emulation.pipelineBarrier(cmd, info);
   }

   @Redirect(
      method = {"writeTimestamp"},
      at = @At(
   value = "INVOKE",
   target = "Lorg/lwjgl/vulkan/KHRSynchronization2;vkCmdWriteTimestamp2KHR(Lorg/lwjgl/vulkan/VkCommandBuffer;JJI)V"
),
      require = 0
   )
   private void vulkanextensionemulator$timestamp(VkCommandBuffer cmd, long stage, long pool, int query) {
      Sync2Emulation.writeTimestamp(cmd, stage, pool, query);
   }

   @Redirect(
      method = {"getTimestampNow"},
      at = @At(
   value = "INVOKE",
   target = "Lorg/lwjgl/vulkan/KHRSynchronization2;vkCmdWriteTimestamp2KHR(Lorg/lwjgl/vulkan/VkCommandBuffer;JJI)V"
),
      require = 0
   )
   private void vulkanextensionemulator$timestampNow(VkCommandBuffer cmd, long stage, long pool, int query) {
      Sync2Emulation.writeTimestamp(cmd, stage, pool, query);
   }
}
