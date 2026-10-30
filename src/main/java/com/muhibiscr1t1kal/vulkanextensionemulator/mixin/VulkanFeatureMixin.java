package com.muhibiscr1t1kal.vulkanextensionemulator.mixin;

import com.mojang.blaze3d.vulkan.init.VulkanFeature;
import com.muhibiscr1t1kal.vulkanextensionemulator.VulkanExtensionEmulator;
import com.muhibiscr1t1kal.vulkanextensionemulator.vk.Emulation;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkPhysicalDeviceFeatures2;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(VulkanFeature.class)
public abstract class VulkanFeatureMixin {

    @Shadow
    public abstract String name();

    @Inject(
        method = "get(Lorg/lwjgl/vulkan/VkPhysicalDeviceFeatures2;)Z",
        at = @At("RETURN"),
        cancellable = true,
        require = 0
    )
    private void vulkanextensionemulator$claimFeatureSupported(
        VkPhysicalDeviceFeatures2 features,
        CallbackInfoReturnable<Boolean> cir
    ) {
        if (!cir.getReturnValueZ()) {
            String s = this.vulkanextensionemulator$name();

            if (s != null && VulkanExtensionEmulator.canEmulateFeature(s)) {
                Emulation.markFeature(s);

                if (VulkanExtensionEmulator.markFirstClaim(s)) {
                    VulkanExtensionEmulator.LOGGER.info(
                        "[Vulkan Extension Emulator] Feature [{}] reported as supported (emulated)",
                        s
                    );
                }

                cir.setReturnValue(true);
            }
        }
    }

    @Inject(
        method = "get(J)Z",
        at = @At("RETURN"),
        cancellable = true,
        require = 0
    )
    private void vulkanextensionemulator$claimFeatureSupportedRaw(
        long address,
        CallbackInfoReturnable<Boolean> cir
    ) {
        if (!cir.getReturnValueZ()) {
            String s = this.vulkanextensionemulator$name();

            if (s != null && VulkanExtensionEmulator.canEmulateFeature(s)) {
                cir.setReturnValue(true);
            }
        }
    }

    @Inject(
        method = "set(Lorg/lwjgl/vulkan/VkPhysicalDeviceFeatures2;ZLorg/lwjgl/system/MemoryStack;)V",
        at = @At("HEAD"),
        cancellable = true,
        require = 0
    )
    private void vulkanextensionemulator$blockFeatureEnableStack(
        VkPhysicalDeviceFeatures2 features,
        boolean value,
        MemoryStack stack,
        CallbackInfo ci
    ) {
        if (value) {
            String s = this.vulkanextensionemulator$name();

            if (s != null && VulkanExtensionEmulator.canEmulateFeature(s)) {
                if (VulkanExtensionEmulator.markFirstBlock(s)) {
                    VulkanExtensionEmulator.LOGGER.info(
                        "[Vulkan Extension Emulator] Feature [{}] NOT enabled on device (emulated in software)",
                        s
                    );
                }

                ci.cancel();
            }
        }
    }

    @Inject(
        method = "set(JZLorg/lwjgl/system/MemoryStack;)V",
        at = @At("HEAD"),
        cancellable = true,
        require = 0
    )
    private void vulkanextensionemulator$blockFeatureEnableRaw(
        long address,
        boolean value,
        MemoryStack stack,
        CallbackInfo ci
    ) {
        if (value) {
            String s = this.vulkanextensionemulator$name();

            if (s != null && VulkanExtensionEmulator.canEmulateFeature(s)) {
                ci.cancel();
            }
        }
    }

    @Inject(
        method = "set(Lorg/lwjgl/vulkan/VkPhysicalDeviceFeatures2;Z)Z",
        at = @At("HEAD"),
        cancellable = true,
        require = 0
    )
    private void vulkanextensionemulator$blockFeatureEnablePlain(
        VkPhysicalDeviceFeatures2 features,
        boolean value,
        CallbackInfoReturnable<Boolean> cir
    ) {
        if (value) {
            String s = this.vulkanextensionemulator$name();

            if (s != null && VulkanExtensionEmulator.canEmulateFeature(s)) {
                cir.setReturnValue(false);
            }
        }
    }

    @Inject(
        method = "set(JZ)Z",
        at = @At("HEAD"),
        cancellable = true,
        require = 0
    )
    private void vulkanextensionemulator$blockFeatureEnableRawPlain(
        long address,
        boolean value,
        CallbackInfoReturnable<Boolean> cir
    ) {
        if (value) {
            String s = this.vulkanextensionemulator$name();

            if (s != null && VulkanExtensionEmulator.canEmulateFeature(s)) {
                cir.setReturnValue(false);
            }
        }
    }

    private String vulkanextensionemulator$name() {
        return this.name();
    }
}