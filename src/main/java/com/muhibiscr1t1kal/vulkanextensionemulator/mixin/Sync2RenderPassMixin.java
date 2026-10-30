package com.muhibiscr1t1kal.vulkanextensionemulator.mixin;

import com.mojang.blaze3d.vulkan.VulkanRenderPass;
import com.muhibiscr1t1kal.vulkanextensionemulator.vk.Sync2Emulation;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin({VulkanRenderPass.class})
public abstract class Sync2RenderPassMixin {
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
}
