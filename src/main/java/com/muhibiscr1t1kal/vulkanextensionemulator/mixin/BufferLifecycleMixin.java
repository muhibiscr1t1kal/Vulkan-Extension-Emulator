package com.muhibiscr1t1kal.vulkanextensionemulator.mixin;

import com.mojang.blaze3d.vulkan.VulkanGpuBuffer;
import com.muhibiscr1t1kal.vulkanextensionemulator.vk.PushDescriptorEmulation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(VulkanGpuBuffer.Direct.class)
public abstract class BufferLifecycleMixin {

    @Inject(
        method = "destroy",
        at = @At("HEAD"),
        require = 1
    )
    private void vulkanextensionemulator$evictDescriptors(CallbackInfo ci) {
        VulkanGpuBuffer buffer = (VulkanGpuBuffer) (Object) this;

        PushDescriptorEmulation.onResourceDestroyed(buffer.vkBuffer());
    }
}