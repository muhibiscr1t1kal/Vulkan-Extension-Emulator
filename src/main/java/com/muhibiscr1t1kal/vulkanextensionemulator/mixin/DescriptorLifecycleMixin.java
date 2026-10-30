package com.muhibiscr1t1kal.vulkanextensionemulator.mixin;

import com.mojang.blaze3d.vulkan.VulkanCommandEncoder;
import com.muhibiscr1t1kal.vulkanextensionemulator.vk.DynamicRenderingEmulation;
import com.muhibiscr1t1kal.vulkanextensionemulator.vk.Emulation;
import com.muhibiscr1t1kal.vulkanextensionemulator.vk.PushDescriptorEmulation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({VulkanCommandEncoder.class})
public abstract class DescriptorLifecycleMixin {
   @Shadow
   private long currentSubmitIndex;
   @Shadow
   private long completedSubmitIndex;

   @Inject(
      method = {"submit"},
      at = @At("HEAD"),
      require = 0
   )
   private void vulkanextensionemulator$advancePools(CallbackInfo ci) {
      if (Emulation.isExtensionEmulated("VK_KHR_push_descriptor")) {
         PushDescriptorEmulation.beginSubmit(this.currentSubmitIndex, this.completedSubmitIndex);
      }

   }

   @Inject(
      method = {"submitRenderPass"},
      at = @At("HEAD"),
      require = 0
   )
   private void vulkanextensionemulator$advancePoolsRenderPass(CallbackInfo ci) {
      if (Emulation.isExtensionEmulated("VK_KHR_push_descriptor")) {
         PushDescriptorEmulation.beginSubmit(this.currentSubmitIndex, this.completedSubmitIndex);
      }

   }

   @Inject(
      method = {"submit"},
      at = @At("RETURN"),
      require = 0
   )
   private void vulkanextensionemulator$drainDeferredDestroys(CallbackInfo ci) {
      DynamicRenderingEmulation.drainPendingDestroys(this.completedSubmitIndex);
   }
}
