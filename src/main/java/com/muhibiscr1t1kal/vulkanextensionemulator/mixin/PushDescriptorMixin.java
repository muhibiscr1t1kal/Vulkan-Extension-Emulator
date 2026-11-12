package com.muhibiscr1t1kal.vulkanextensionemulator.mixin;

import com.mojang.renderpearl.backend.vulkan.VulkanRenderPass;
import com.mojang.renderpearl.backend.vulkan.VulkanRenderPipeline;
import com.muhibiscr1t1kal.vulkanextensionemulator.VulkanExtensionEmulator;
import com.muhibiscr1t1kal.vulkanextensionemulator.mixin.VulkanRenderPipelineAccessor;
import com.muhibiscr1t1kal.vulkanextensionemulator.vk.Emulation;
import com.muhibiscr1t1kal.vulkanextensionemulator.vk.NativeCaps;
import com.muhibiscr1t1kal.vulkanextensionemulator.vk.PushDescriptorEmulation;
import org.lwjgl.vulkan.KHRPushDescriptor;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkWriteDescriptorSet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin({VulkanRenderPass.class})
public abstract class PushDescriptorMixin {
    @Shadow
    protected VulkanRenderPipeline pipeline;

    @Redirect(
        method = {"pushDescriptors"},
        at = @At(
            value = "INVOKE",
            target = "Lorg/lwjgl/vulkan/KHRPushDescriptor;vkCmdPushDescriptorSetKHR(Lorg/lwjgl/vulkan/VkCommandBuffer;IJILorg/lwjgl/vulkan/VkWriteDescriptorSet$Buffer;)V"
        ),
        require = 0
    )
    private void vulkanextensionemulator$pushDescriptors(
        VkCommandBuffer cmd,
        int bindPoint,
        long pipelineLayout,
        int set,
        VkWriteDescriptorSet.Buffer writes
    ) {
        if (VulkanExtensionEmulator.forcePushEmulation()) {
            Emulation.markExtension("VK_KHR_push_descriptor");
            this.vulkanextensionemulator$emulate(
                cmd, bindPoint, pipelineLayout, set, writes
            );
        } else if (NativeCaps.pushDescriptor) {
            KHRPushDescriptor.vkCmdPushDescriptorSetKHR(
                cmd, bindPoint, pipelineLayout, set, writes
            );
        } else if (!Emulation.isExtensionEmulated("VK_KHR_push_descriptor")) {
            KHRPushDescriptor.vkCmdPushDescriptorSetKHR(
                cmd, bindPoint, pipelineLayout, set, writes
            );
        } else {
            this.vulkanextensionemulator$emulate(
                cmd, bindPoint, pipelineLayout, set, writes
            );
        }
    }

    private void vulkanextensionemulator$emulate(
        VkCommandBuffer cmd,
        int bindPoint,
        long pipelineLayout,
        int set,
        VkWriteDescriptorSet.Buffer writes
    ) {
        long descriptorSetLayout = 0L;

        if (this.pipeline != null) {
            descriptorSetLayout =
                ((VulkanRenderPipelineAccessor) (Object) this.pipeline)
                    .vulkanextensionemulator$descriptorSetLayout();
        }

        PushDescriptorEmulation.pushDescriptorSet(
            cmd,
            bindPoint,
            pipelineLayout,
            set,
            writes,
            descriptorSetLayout
        );
    }
}