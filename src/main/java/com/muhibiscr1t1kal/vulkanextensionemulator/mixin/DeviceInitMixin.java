package com.muhibiscr1t1kal.vulkanextensionemulator.mixin;

import com.mojang.blaze3d.vulkan.VulkanDevice;
import com.mojang.blaze3d.vulkan.VulkanQueue;
import com.mojang.blaze3d.vulkan.VulkanDevice;
import com.muhibiscr1t1kal.vulkanextensionemulator.vk.VulkanProbe;
import com.muhibiscr1t1kal.vulkanextensionemulator.VulkanExtensionEmulator;
import com.muhibiscr1t1kal.vulkanextensionemulator.vk.BarrierCoalescer;
import com.muhibiscr1t1kal.vulkanextensionemulator.vk.Core12Emulation;
import com.muhibiscr1t1kal.vulkanextensionemulator.vk.DynamicRenderingEmulation;
import com.muhibiscr1t1kal.vulkanextensionemulator.vk.MultiDrawEmulation;
import com.muhibiscr1t1kal.vulkanextensionemulator.vk.PipelineCache;
import com.muhibiscr1t1kal.vulkanextensionemulator.vk.PipelineDepth;
import com.muhibiscr1t1kal.vulkanextensionemulator.vk.PresentBarrierNarrowing;
import com.muhibiscr1t1kal.vulkanextensionemulator.vk.PushDescriptorEmulation;
import com.muhibiscr1t1kal.vulkanextensionemulator.vk.VulkanProbe.Result;
import org.lwjgl.vulkan.VkDevice;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(VulkanDevice.class)
public abstract class DeviceInitMixin {

    @Shadow
    public abstract VkDevice vkDevice();

    @Shadow
    public abstract VulkanQueue graphicsQueue();

    @Inject(
        method = "<init>",
        at = @At("RETURN"),
        require = 0
    )
    private void vulkanextensionemulator$initPools(CallbackInfo ci) {
        Core12Emulation.resolve(vkDevice(), graphicsQueue().vkQueue());
        initPipelineCache(vkDevice());
        MultiDrawEmulation.init(driverHasMultiDrawIndirect());
        PushDescriptorEmulation.init(vkDevice());

        if (DynamicRenderingEmulation.active()) {
            DynamicRenderingEmulation.init(vkDevice());
        }
    }

    @Inject(
        method = "close",
        at = @At("HEAD"),
        require = 0
    )
    private void vulkanextensionemulator$destroyPools(CallbackInfo ci) {
        PipelineCache.shutdown();
        PushDescriptorEmulation.shutdown();
        DynamicRenderingEmulation.shutdown();

        VulkanExtensionEmulator.LOGGER.info("[Vulkan Extension Emulator] {}", MultiDrawEmulation.stats());
        VulkanExtensionEmulator.LOGGER.info("[Vulkan Extension Emulator] {}", BarrierCoalescer.stats());
        VulkanExtensionEmulator.LOGGER.info("[Vulkan Extension Emulator] {}", PresentBarrierNarrowing.stats());
        VulkanExtensionEmulator.LOGGER.info("[Vulkan Extension Emulator] {}", PipelineDepth.stats());
    }

    private static void initPipelineCache(VkDevice device) {
        try {
            Result result = VulkanExtensionEmulator.probeResult();

            long deviceName = 0L;
            long apiVersion = 0L;
            int driverInfo = 0;

            if (result != null) {
                deviceName = result.deviceName() == null ? 0L : result.deviceName().hashCode();
                apiVersion = (long) result.apiMajor() << 32
                    | (long) result.apiMinor() << 16
                    | result.apiPatch();
                driverInfo = result.driverInfo() == null ? 0 : result.driverInfo().hashCode();
            }

            PipelineCache.init(device, deviceName, apiVersion, driverInfo);
        } catch (Throwable error) {
            VulkanExtensionEmulator.LOGGER.warn("[Vulkan Extension Emulator] Pipeline cache init failed", error);
        }
    }

    private static boolean driverHasMultiDrawIndirect() {
        Result result = VulkanExtensionEmulator.probeResult();
        return result != null && Boolean.TRUE.equals(result.features().get("multiDrawIndirect"));
    }
}
