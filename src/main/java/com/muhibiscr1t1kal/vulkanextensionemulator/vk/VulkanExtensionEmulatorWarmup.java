package com.muhibiscr1t1kal.vulkanextensionemulator.vk;

public final class VulkanExtensionEmulatorWarmup {

    private static final String[] HOT_CLASSES = {
        "com.muhibiscr1t1kal.vulkanextensionemulator.VulkanExtensionEmulator",
        "com.muhibiscr1t1kal.vulkanextensionemulator.vk.Emulation",
        "com.muhibiscr1t1kal.vulkanextensionemulator.vk.NativeCaps",
        "com.muhibiscr1t1kal.vulkanextensionemulator.vk.BarrierCoalescer",
        "com.muhibiscr1t1kal.vulkanextensionemulator.vk.Core12Emulation",
        "com.muhibiscr1t1kal.vulkanextensionemulator.vk.MultiDrawEmulation",
        "com.muhibiscr1t1kal.vulkanextensionemulator.vk.PresentBarrierNarrowing",
        "com.muhibiscr1t1kal.vulkanextensionemulator.vk.PipelineCache",
        "com.muhibiscr1t1kal.vulkanextensionemulator.vk.PipelineDepth",
        "com.muhibiscr1t1kal.vulkanextensionemulator.vk.PushDescriptorEmulation",
        "com.muhibiscr1t1kal.vulkanextensionemulator.vk.DescriptorPoolManager",
        "com.muhibiscr1t1kal.vulkanextensionemulator.vk.DescriptorCache",
        "com.muhibiscr1t1kal.vulkanextensionemulator.vk.SubmitEmulation",
        "com.muhibiscr1t1kal.vulkanextensionemulator.vk.Sync2Emulation",
        "com.muhibiscr1t1kal.vulkanextensionemulator.vk.DynamicRenderingEmulation",
        "com.muhibiscr1t1kal.vulkanextensionemulator.vk.VulkanProbe",
        "com.muhibiscr1t1kal.vulkanextensionemulator.vk.FrameProfiler",
        "com.muhibiscr1t1kal.vulkanextensionemulator.vk.Metrics",
        "com.muhibiscr1t1kal.vulkanextensionemulator.vk.Trace",
        "com.muhibiscr1t1kal.vulkanextensionemulator.enhanced.TerrainRebuildEnhancement"
    };

    private VulkanExtensionEmulatorWarmup() {
    }

    public static void warm() {
        for (String className : HOT_CLASSES) {
            try {
                Class.forName(className);
            } catch (Throwable ignored) {
            }
        }
    }
}