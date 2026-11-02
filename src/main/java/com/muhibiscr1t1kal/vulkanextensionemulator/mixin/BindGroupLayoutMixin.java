package com.muhibiscr1t1kal.vulkanextensionemulator.mixin;

import com.mojang.blaze3d.vulkan.VulkanRenderPipeline;
import com.muhibiscr1t1kal.vulkanextensionemulator.VulkanExtensionEmulator;
import com.muhibiscr1t1kal.vulkanextensionemulator.vk.Emulation;
import com.muhibiscr1t1kal.vulkanextensionemulator.vk.PushDescriptorEmulation;
import org.lwjgl.vulkan.VkDescriptorSetLayoutCreateInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin({VulkanRenderPipeline.class})
public abstract class BindGroupLayoutMixin {
   private static int vulkanextensionemulator$stripCount;

   @Redirect(
      method = {"compile"},
      at = @At(
   value = "INVOKE",
   target = "Lorg/lwjgl/vulkan/VkDescriptorSetLayoutCreateInfo;flags(I)Lorg/lwjgl/vulkan/VkDescriptorSetLayoutCreateInfo;"
),
      require = 0
   )
   private static VkDescriptorSetLayoutCreateInfo vulkanextensionemulator$stripPushDescriptorFlag(VkDescriptorSetLayoutCreateInfo info, int flags) {
      if (Emulation.isExtensionEmulated("VK_KHR_push_descriptor") && !PushDescriptorEmulation.nativePushDescriptorAvailable()) {
         int i = flags & -2;
         if (i != flags) {
            ++vulkanextensionemulator$stripCount;
            if (vulkanextensionemulator$stripCount == 1) {
               VulkanExtensionEmulator.LOGGER.info("[Vulkan Extension Emulator] Stripping PUSH_DESCRIPTOR_BIT from descriptor set layouts (flags 0x{} -> 0x{}); further occurrences counted silently", Integer.toHexString(flags), Integer.toHexString(i));
            }
         }

         return info.flags(i);
      } else {
         return info.flags(flags);
      }
   }
}
