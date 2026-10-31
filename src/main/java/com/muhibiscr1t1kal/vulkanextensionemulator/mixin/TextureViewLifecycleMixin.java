package com.muhibiscr1t1kal.vulkanextensionemulator.mixin;

import com.mojang.blaze3d.vulkan.VulkanConst;
import com.mojang.blaze3d.vulkan.VulkanGpuTexture;
import com.mojang.blaze3d.vulkan.VulkanGpuTextureView;
import com.muhibiscr1t1kal.vulkanextensionemulator.vk.DynamicRenderingEmulation;
import com.muhibiscr1t1kal.vulkanextensionemulator.vk.PushDescriptorEmulation;
import com.muhibiscr1t1kal.vulkanextensionemulator.vk.ViewRegistry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(VulkanGpuTextureView.class)
public abstract class TextureViewLifecycleMixin {

    @Shadow
    public abstract long vkImageView();

    @Shadow
    public abstract VulkanGpuTexture texture();

    @Inject(
        method = "<init>(Lcom/mojang/blaze3d/vulkan/VulkanDevice;Lcom/mojang/blaze3d/vulkan/VulkanGpuTexture;II)V",
        at = @At("TAIL")
    )
    private void vulkanextensionemulator$registerView(CallbackInfo ci) {
        ViewRegistry.register(
            vkImageView(),
            VulkanConst.toVk(texture().getFormat()),
            texture().getWidth(0),
            texture().getHeight(0)
        );
    }

    @Inject(
        method = "destroy",
        at = @At("HEAD"),
        require = 0
    )
    private void vulkanextensionemulator$evictFramebuffers(CallbackInfo ci) {
        long imageView = vkImageView();

        ViewRegistry.unregister(imageView);
        DynamicRenderingEmulation.onImageViewDestroyed(imageView);
        PushDescriptorEmulation.onResourceDestroyed(imageView);
    }
}