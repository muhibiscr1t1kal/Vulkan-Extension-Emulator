package com.muhibiscr1t1kal.vulkanextensionemulator.mixin;

import com.mojang.blaze3d.vulkan.VulkanGpuTextureView;
import com.muhibiscr1t1kal.vulkanextensionemulator.vk.DynamicRenderingEmulation;
import com.muhibiscr1t1kal.vulkanextensionemulator.vk.PushDescriptorEmulation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(VulkanGpuTextureView.class)
public abstract class TextureViewLifecycleMixin {

    @Shadow
    public abstract long vkImageView();

    @Inject(
        method = "destroy",
        at = @At("HEAD"),
        require = 0
    )
    private void vulkanextensionemulator$evictFramebuffers(CallbackInfo ci) {
        long imageView = vkImageView();

        DynamicRenderingEmulation.onImageViewDestroyed(imageView);
        PushDescriptorEmulation.onResourceDestroyed(imageView);
    }
}