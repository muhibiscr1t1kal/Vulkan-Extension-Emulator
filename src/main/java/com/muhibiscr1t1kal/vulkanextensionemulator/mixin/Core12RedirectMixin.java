package com.muhibiscr1t1kal.vulkanextensionemulator.mixin;

import com.mojang.blaze3d.vulkan.VulkanCommandEncoder;
import com.muhibiscr1t1kal.vulkanextensionemulator.vk.Core12Emulation;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkSemaphoreWaitInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin({VulkanCommandEncoder.class})
public abstract class Core12RedirectMixin {
   @Redirect(
      method = {"awaitSubmitCompletion"},
      at = @At(
   value = "INVOKE",
   target = "Lorg/lwjgl/vulkan/VK12;vkWaitSemaphores(Lorg/lwjgl/vulkan/VkDevice;Lorg/lwjgl/vulkan/VkSemaphoreWaitInfo;J)I"
),
      require = 0
   )
   private int vulkanextensionemulator$waitSemaphores(VkDevice device, VkSemaphoreWaitInfo info, long timeout) {
      return Core12Emulation.waitSemaphores(device, info, timeout);
   }

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
