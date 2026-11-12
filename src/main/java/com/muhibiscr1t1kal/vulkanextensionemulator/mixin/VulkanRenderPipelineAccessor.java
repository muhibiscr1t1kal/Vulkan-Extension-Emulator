package com.muhibiscr1t1kal.vulkanextensionemulator.mixin;

import com.mojang.renderpearl.backend.vulkan.VulkanRenderPipeline;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(VulkanRenderPipeline.class)
public interface VulkanRenderPipelineAccessor {
    @Accessor("descriptorSetLayout")
    long vulkanextensionemulator$descriptorSetLayout();
}