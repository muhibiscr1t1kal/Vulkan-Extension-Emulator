package com.muhibiscr1t1kal.vulkanextensionemulator.mixin;

import com.muhibiscr1t1kal.vulkanextensionemulator.vk.SubmitEmulation;
import org.lwjgl.vulkan.VkQueue;
import org.lwjgl.vulkan.VkSubmitInfo2;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(
   targets = {"com.mojang.blaze3d.vulkan.VulkanQueue$Submission"}
)
public abstract class Sync2SubmitMixin {
   @Redirect(
      method = {"close"},
      at = @At(
   value = "INVOKE",
   target = "Lorg/lwjgl/vulkan/KHRSynchronization2;vkQueueSubmit2KHR(Lorg/lwjgl/vulkan/VkQueue;Lorg/lwjgl/vulkan/VkSubmitInfo2$Buffer;J)I"
),
      require = 0
   )
   private int vulkanextensionemulator$submit(VkQueue queue, VkSubmitInfo2.Buffer submits, long fence) {
      return SubmitEmulation.queueSubmit(queue, submits, fence);
   }
}
