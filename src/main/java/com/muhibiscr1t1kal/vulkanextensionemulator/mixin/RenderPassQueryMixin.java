package com.muhibiscr1t1kal.vulkanextensionemulator.mixin;

import com.mojang.blaze3d.vulkan.VulkanRenderPass;
import com.muhibiscr1t1kal.vulkanextensionemulator.vk.Core12Emulation;
import org.lwjgl.vulkan.VkDevice;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin({VulkanRenderPass.class})
public abstract class RenderPassQueryMixin {
   @Redirect(
      method = {"writeTimestamp"},
      at = @At(
   value = "INVOKE",
   target = "Lorg/lwjgl/vulkan/VK12;vkResetQueryPool(Lorg/lwjgl/vulkan/VkDevice;JII)V"
),
      require = 0
   )
   private void vulkanextensionemulator$resetQueryPool(VkDevice device, long pool, int first, int count) {
      Core12Emulation.resetQueryPool(device, pool, first, count);
   }
}
