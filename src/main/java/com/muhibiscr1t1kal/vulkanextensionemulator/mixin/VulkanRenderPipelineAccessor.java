package com.muhibiscr1t1kal.vulkanextensionemulator.mixin;

import com.mojang.blaze3d.vulkan.VulkanRenderPipeline;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(VulkanRenderPipeline.class)
public interface VulkanRenderPipelineAccessor {
    @Accessor("pipelineLayout")
    long vulkanextensionemulator$descriptorSetLayout();
}