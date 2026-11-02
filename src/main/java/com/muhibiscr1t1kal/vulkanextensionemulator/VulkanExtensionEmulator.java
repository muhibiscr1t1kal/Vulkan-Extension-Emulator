package com.muhibiscr1t1kal.vulkanextensionemulator;

import com.mojang.blaze3d.vulkan.init.VulkanFeature;
import com.muhibiscr1t1kal.vulkanextensionemulator.vk.Emulation;
import com.muhibiscr1t1kal.vulkanextensionemulator.vk.PipelineDepth;
import com.muhibiscr1t1kal.vulkanextensionemulator.vk.StutterLog;
import com.muhibiscr1t1kal.vulkanextensionemulator.vk.VulkanProbe;
import com.muhibiscr1t1kal.vulkanextensionemulator.vk.VulkanExtensionEmulatorWarmup;
import java.io.IOException;
import java.io.InputStream;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class VulkanExtensionEmulator implements ClientModInitializer {

    public static final String MOD_ID = "vulkanextensionemulator";
    public static final Logger LOGGER =
        LoggerFactory.getLogger("Vulkan Extension Emulator");

    private static final Set<String> EMULATABLE_EXTENSIONS = Set.of(
        "VK_KHR_dynamic_rendering",
        "VK_KHR_synchronization2",
        "VK_KHR_push_descriptor",
        "VK_EXT_vertex_attribute_divisor",
        "VK_EXT_multi_draw"
    );

    private static final Set<String> EMULATABLE_FEATURES = Set.of(
        "dynamicRendering",
        "synchronization2",
        "multiDraw",
        "multiDrawIndirect",
        "fillModeNonSolid",
        "vertexAttributeInstanceRateDivisor",
        "depthClamp",
        "shaderDrawParameters",
        "timelineSemaphore",
        "hostQueryReset"
    );

    private static Config config;
    private static VulkanProbe.Result probeResult;

    private static final Set<String> claimedOnce =
        ConcurrentHashMap.newKeySet();

    private static final Set<String> blockedOnce =
        ConcurrentHashMap.newKeySet();

    private static volatile Boolean desktopSafeProfile;
    private static volatile Long systemRamMbCached;
    private static volatile Boolean forcePush;
    private static volatile String shaderModLogged;

    @Override
    public void onInitializeClient() {
        config = loadConfig();

        VulkanExtensionEmulatorWarmup.warm();

        LOGGER.info("[Vulkan Extension Emulator] Phase 5 — VK1.2 core routing + dynamic rendering");

        LOGGER.info(
            "[Vulkan Extension Emulator] Device memory: {} MB device-local | NVIDIA-old push-fallback: {}",
            deviceLocalBytes() / 1048576L,
            forcePushEmulation()
        );

        if (!config.enabled) {
            LOGGER.info("[Vulkan Extension Emulator] Disabled by config; leaving Vulkan gate untouched.");
        } else {
            try {
                probeResult = VulkanProbe.probe();

                if (config.verbose) {
                    String[] lines = VulkanProbe.format(probeResult).split("\n");

                    for (String line : lines) {
                        LOGGER.info("[Vulkan Extension Emulator] {}", line);
                    }
                }
            } catch (Throwable exception) {
                LOGGER.error("[Vulkan Extension Emulator] Probe failed", exception);
            }

            long ram = systemRamMB();

            LOGGER.info(
                "[Vulkan Extension Emulator] Chunk heap pre-alloc: {} (device-local {} MB, system RAM {})",
                shouldPreallocateChunkHeaps() ? "ON" : "OFF",
                deviceLocalBytes() / 1048576L,
                ram > 0L ? ram + " MB" : "unknown"
            );

            LOGGER.info("[Vulkan Extension Emulator] Will emulate if missing: {}", EMULATABLE_EXTENSIONS);

            LOGGER.info(
                "[Vulkan Extension Emulator] Phase 4 active: dynamic rendering -> VkRenderPass + VkFramebuffer"
            );

            LOGGER.info("[Vulkan Extension Emulator] If the game crashes on launch, set enabled=false in");
            LOGGER.info("[Vulkan Extension Emulator] config/vulkanextensionemulator.properties to fall back to OpenGL.");
        }
    }

    public static boolean canEmulateExtension(String ext) {
        if (config != null && config.enabled) {
            return !"VK_KHR_push_descriptor".equals(ext)
                || config.allowPushDescriptor
                    ? EMULATABLE_EXTENSIONS.contains(ext)
                    : false;
        }

        return false;
    }

    public static boolean canEmulateFeature(String feature) {
        return config != null
            && config.enabled
            && EMULATABLE_FEATURES.contains(feature);
    }

    public static boolean allRemainingGapsAreEmulatable() {
        return config != null
            && config.enabled
            && !Emulation.hasUnemulatableGap();
    }

    public static String featureName(VulkanFeature feature) {
        if (feature == null) {
            return null;
        }

        try {
            return feature.name();
        } catch (Throwable exception) {
            return null;
        }
    }

    public static boolean markFirstClaim(String name) {
        return claimedOnce.add(name);
    }

    public static boolean markFirstBlock(String name) {
        return blockedOnce.add(name);
    }

    public static Config config() {
        return config == null ? new Config() : config;
    }

    public static VulkanProbe.Result probeResult() {
        return probeResult;
    }

    public static boolean isEmulating() {
        return config != null
            && config.enabled
            && (!Emulation.extensions().isEmpty()
                || !Emulation.features().isEmpty());
    }

    public static boolean isDesktopSafeProfile() {
        Boolean cached = desktopSafeProfile;

        if (cached != null) {
            return cached;
        }

        boolean flag = false;
        String detectedProfile = null;

        VulkanProbe.Result result = probeResult();

        if (result != null) {
            String deviceName = result.deviceName() == null
                ? ""
                : result.deviceName().toLowerCase();

            String driverInfo = result.driverInfo() == null
                ? ""
                : result.driverInfo().toLowerCase();

            long vendor = vendorId(result);

            if (vendor != 43627L
                && !deviceName.contains("apple")
                && !driverInfo.contains("moltenv")
                && !driverInfo.contains("honey")) {

                if (vendor == 4098L
                    && (driverInfo.contains("radv")
                        || deviceName.contains("amd")
                        || deviceName.contains("radeon"))) {

                    flag = true;
                    detectedProfile = "AMD RADV (Linux)";
                }
            } else {
                flag = true;
                detectedProfile = "Apple/Metal translation (MoltenVK/Honeykrisp)";
            }
        }

        if (flag) {
            LOGGER.warn(
                "[Vulkan Extension Emulator] Desktop/translation driver detected ({}): disabling deeper pipeline to reduce GPU timeouts. Set desktopSafeMode=false in config/vulkanextensionemulator.properties to re-enable.",
                detectedProfile
            );
        }

        desktopSafeProfile = flag;
        return flag;
    }

    private static long vendorId(VulkanProbe.Result result) {
        String driverInfo = result.driverInfo();

        if (driverInfo == null) {
            return 0L;
        }

        int index = driverInfo.indexOf("vendorID=0x");

        if (index < 0) {
            return 0L;
        }

        try {
            String value = driverInfo.substring(index + 11).trim();

            int end = 0;

            while (end < value.length()
                && Character.digit(value.charAt(end), 16) >= 0) {
                end++;
            }

            return Long.parseLong(value.substring(0, end), 16);
        } catch (NumberFormatException exception) {
            return 0L;
        }
    }

    public static long deviceLocalBytes() {
        VulkanProbe.Result result = probeResult();

        return result == null ? 0L : result.deviceLocalBytes();
    }

    public static long systemRamMB() {
        Long cached = systemRamMbCached;

        if (cached != null) {
            return cached;
        }

        long ram = 0L;

        try {
            Class<?> beanClass =
                Class.forName("com.sun.management.OperatingSystemMXBean");

            Object bean = ManagementFactory.getOperatingSystemMXBean();

            if (beanClass.isInstance(bean)) {
                Method method = beanClass.getMethod(
                    "getTotalMemorySize",
                    new Class[0]
                );

                ram = (Long) method.invoke(bean, new Object[0])
                    / 1048576L;
            }
        } catch (Throwable ignored) {
        }

        systemRamMbCached = ram;
        return ram;
    }

    public static boolean forcePushEmulation() {
        Boolean cached = forcePush;

        if (cached != null) {
            return cached;
        }

        boolean flag = false;
        VulkanProbe.Result result = probeResult();

        if (result != null && vendorId(result) == 4318L) {
            String deviceName = result.deviceName() == null
                ? ""
                : result.deviceName().toLowerCase();

            flag = deviceName.contains("gtx 10")
                || deviceName.contains("gtx 9")
                || deviceName.contains("gtx 7")
                || deviceName.contains("gtx 6")
                || deviceName.contains("gtx 5")
                || deviceName.contains("gt 1030")
                || deviceName.contains("gt 710")
                || deviceName.contains("gt 730")
                || deviceName.contains("gt 740")
                || deviceName.contains("quadro")
                || deviceName.contains("tesla")
                || deviceName.contains("nvs");
        }

        forcePush = flag;
        return flag;
    }

    public static boolean terrainRebuildActive() {
        if (isShaderModInstalled()) {
            return false;
        }

        if (!config().terrainRebuildAuto) {
            return config().terrainRebuildEnabled;
        }

        return config().terrainRebuildEnabled || isSodiumInstalled();
    }

    public static void notifyWorldJoin() {
        StutterLog.markWorldJoin();
        PipelineDepth.resetAdaptive();
    }

    public static boolean isSodiumInstalled() {
        try {
            return FabricLoader.getInstance().isModLoaded("sodium");
        } catch (Throwable exception) {
            return false;
        }
    }

    public static boolean isShaderModInstalled() {
        try {
            String[] mods = {
                "beryl_native",
                "sulkan",
                "iris",
                "aperture"
            };

            FabricLoader loader = FabricLoader.getInstance();

            for (String mod : mods) {
                if (loader.isModLoaded(mod)) {
                    if (!Objects.equals(shaderModLogged, mod)) {
                        shaderModLogged = mod;

                        LOGGER.warn(
                            "[Vulkan Extension Emulator] Shader/renderer mod detected ({}) — deeper pipeline, terrain rebuild and chunk-heap pre-alloc disabled (compat mode)",
                            mod
                        );
                    }

                    return true;
                }
            }
        } catch (Throwable ignored) {
        }

        return false;
    }

    public static boolean shouldPreallocateChunkHeaps() {
        if (!config.preallocateChunkHeaps) {
            return false;
        }

        if (!config.autoTunePreallocation) {
            return true;
        }

        if (deviceLocalBytes() < 2147483648L) {
            return false;
        }

        long ram = systemRamMB();

        return config.preallocateMinSystemRamMB <= 0
            || ram <= 0L
            || ram >= config.preallocateMinSystemRamMB;
    }

    public static int preallocateHeapCount() {
        if (!shouldPreallocateChunkHeaps()) {
            return 0;
        }

        int count = config.preallocateChunkHeapCount;

        if (count > 0) {
            return Math.min(count, 4);
        }

        return deviceLocalBytes() >= 6442450944L ? 2 : 1;
    }

    private static int readInt(
        Properties properties,
        String key,
        int def
    ) {
        try {
            return Integer.parseInt(
                properties.getProperty(key, String.valueOf(def)).trim()
            );
        } catch (NumberFormatException exception) {
            return def;
        }
    }

    private static Config loadConfig() {
        Config result = new Config();

        Path path = FabricLoader.getInstance()
            .getConfigDir()
            .resolve("vulkanextensionemulator.properties");

        if (!Files.isReadable(path)) {
            writeDefaultConfig(path);
            return result;
        }

        Properties properties = new Properties();

        try (InputStream input = Files.newInputStream(
            path,
            new OpenOption[0]
        )) {
            properties.load(input);

            int version = 1;

            try {
                version = Integer.parseInt(
                    properties.getProperty("configVersion", "1").trim()
                );
            } catch (NumberFormatException ignored) {
            }

            if (version < 10) {
                try {
                    Files.delete(path);
                } catch (IOException ignored) {
                }

                writeDefaultConfig(path);
                return result;
            }

            result.enabled = Boolean.parseBoolean(properties.getProperty(
                "enabled",
                String.valueOf(result.enabled)
            ).trim());

            result.bypassApiVersionCheck = Boolean.parseBoolean(properties.getProperty(
                "bypassApiVersionCheck",
                String.valueOf(result.bypassApiVersionCheck)
            ).trim());

            result.verbose = Boolean.parseBoolean(properties.getProperty(
                "verbose",
                String.valueOf(result.verbose)
            ).trim());

            result.trace = Boolean.parseBoolean(properties.getProperty(
                "trace",
                String.valueOf(result.trace)
            ).trim());

            result.disableTimestamps = Boolean.parseBoolean(properties.getProperty(
                "disableTimestamps",
                String.valueOf(result.disableTimestamps)
            ).trim());

            result.allowPushDescriptor = Boolean.parseBoolean(properties.getProperty(
                "allowPushDescriptor",
                String.valueOf(result.allowPushDescriptor)
            ).trim());

            result.emulateDynamicRendering = Boolean.parseBoolean(properties.getProperty(
                "emulateDynamicRendering",
                String.valueOf(result.emulateDynamicRendering)
            ).trim());

            result.coalesceBarriers = Boolean.parseBoolean(properties.getProperty(
                "coalesceBarriers",
                String.valueOf(result.coalesceBarriers)
            ).trim());

            result.narrowPresentBarrier = Boolean.parseBoolean(properties.getProperty(
                "narrowPresentBarrier",
                String.valueOf(result.narrowPresentBarrier)
            ).trim());

            result.usePipelineCache = Boolean.parseBoolean(properties.getProperty(
                "usePipelineCache",
                String.valueOf(result.usePipelineCache)
            ).trim());

            result.metrics = Boolean.parseBoolean(properties.getProperty(
                "metrics",
                String.valueOf(result.metrics)
            ).trim());

            result.cacheDescriptors = Boolean.parseBoolean(properties.getProperty(
                "cacheDescriptors",
                String.valueOf(result.cacheDescriptors)
            ).trim());

            result.profileFrames = Boolean.parseBoolean(properties.getProperty(
                "profileFrames",
                String.valueOf(result.profileFrames)
            ).trim());

            result.deeperPipeline = Boolean.parseBoolean(properties.getProperty(
                "deeperPipeline",
                String.valueOf(result.deeperPipeline)
            ).trim());

            result.preallocateChunkHeaps = Boolean.parseBoolean(properties.getProperty(
                "preallocateChunkHeaps",
                String.valueOf(result.preallocateChunkHeaps)
            ).trim());

            result.desktopSafeMode = Boolean.parseBoolean(properties.getProperty(
                "desktopSafeMode",
                String.valueOf(result.desktopSafeMode)
            ).trim());

            result.autoTunePreallocation = Boolean.parseBoolean(properties.getProperty(
                "autoTunePreallocation",
                String.valueOf(result.autoTunePreallocation)
            ).trim());

            result.terrainRebuildEnabled = Boolean.parseBoolean(properties.getProperty(
                "terrainRebuildEnabled",
                String.valueOf(result.terrainRebuildEnabled)
            ).trim());

            result.terrainRebuildAuto = Boolean.parseBoolean(properties.getProperty(
                "terrainRebuildAuto",
                String.valueOf(result.terrainRebuildAuto)
            ).trim());

            try {
                result.extraFramesInFlight = Integer.parseInt(
                    properties.getProperty(
                        "extraFramesInFlight",
                        String.valueOf(result.extraFramesInFlight)
                    ).trim()
                );
            } catch (NumberFormatException ignored) {
            }

            result.prewarmDescriptorPools = Boolean.parseBoolean(properties.getProperty(
                "prewarmDescriptorPools",
                String.valueOf(result.prewarmDescriptorPools)
            ).trim());

            result.stutterLog = Boolean.parseBoolean(properties.getProperty(
                "stutterLog",
                String.valueOf(result.stutterLog)
            ).trim());

            result.adaptiveDepth = Boolean.parseBoolean(properties.getProperty(
                "adaptiveDepth",
                String.valueOf(result.adaptiveDepth)
            ).trim());

            result.bufferAutoSize = Boolean.parseBoolean(properties.getProperty(
                "bufferAutoSize",
                String.valueOf(result.bufferAutoSize)
            ).trim());

            result.descriptorBlockSets = readInt(
                properties,
                "descriptorBlockSets",
                result.descriptorBlockSets
            );

            result.adaptiveDepthSlowMs = readInt(
                properties,
                "adaptiveDepthSlowMs",
                result.adaptiveDepthSlowMs
            );

            result.adaptiveDepthConsecutive = readInt(
                properties,
                "adaptiveDepthConsecutive",
                result.adaptiveDepthConsecutive
            );

            result.adaptiveDepthRecoverFrames = readInt(
                properties,
                "adaptiveDepthRecoverFrames",
                result.adaptiveDepthRecoverFrames
            );

            result.terrainRebuildSettleTicks = readInt(
                properties,
                "terrainRebuildSettleTicks",
                result.terrainRebuildSettleTicks
            );

            result.terrainRebuildSettleBusyMs = readInt(
                properties,
                "terrainRebuildSettleBusyMs",
                result.terrainRebuildSettleBusyMs
            );

            result.preallocateMinSystemRamMB = readInt(
                properties,
                "preallocateMinSystemRamMB",
                result.preallocateMinSystemRamMB
            );

            result.preallocateChunkHeapCount = readInt(
                properties,
                "preallocateChunkHeapCount",
                result.preallocateChunkHeapCount
            );

        } catch (IOException exception) {
            LOGGER.warn("[Vulkan Extension Emulator] Could not read config, using defaults", exception);
        }

        return result;
    }

    private static void writeDefaultConfig(Path path) {
        try {
            Files.createDirectories(path.getParent(), new FileAttribute[0]);

            Files.writeString(
                path,
                """
                # Vulkan Extension Emulator configuration
                configVersion=10

                # Master switch. false = stock behaviour (OpenGL fallback).
                enabled=true

                # Bypass the hard Vulkan 1.2 check.
                bypassApiVersionCheck=true

                # Log the full capability probe at startup.
                verbose=true

                # Diagnostics before emulated native calls.
                trace=false

                # Skip timestamp queries.
                disableTimestamps=true

                # Emulate VK_KHR_push_descriptor.
                allowPushDescriptor=true

                # Emulate VK_KHR_dynamic_rendering.
                emulateDynamicRendering=true

                # Collapse consecutive identical barriers.
                coalesceBarriers=true

                # Narrow the end-of-frame present transition.
                narrowPresentBarrier=true

                # Pipeline cache settings.
                usePipelineCache=false
                pipelineCacheAuto=true

                # Diagnostics and profiling.
                metrics=false
                cacheDescriptors=true
                profileFrames=false

                # Pipeline settings.
                deeperPipeline=true
                extraFramesInFlight=1

                # Chunk heap allocation.
                preallocateChunkHeaps=true
                desktopSafeMode=true
                autoTunePreallocation=true
                preallocateMinSystemRamMB=3072
                preallocateChunkHeapCount=0

                # Terrain rebuild workaround.
                terrainRebuildEnabled=false
                terrainRebuildAuto=true
                terrainRebuildSettleTicks=60
                terrainRebuildSettleBusyMs=150

                # Descriptor pool settings.
                prewarmDescriptorPools=true
                descriptorBlockSets=0

                # Stutter telemetry.
                stutterLog=false

                # Adaptive pipeline depth.
                adaptiveDepth=true
                adaptiveDepthSlowMs=150
                adaptiveDepthConsecutive=3
                adaptiveDepthRecoverFrames=600

                # Buffer auto sizing.
                bufferAutoSize=false
                """,
                new OpenOption[0]
            );

        } catch (IOException exception) {
            LOGGER.warn("[Vulkan Extension Emulator] Could not write config", exception);
        }
    }

    public static void saveConfig() {
        if (config == null) {
            return;
        }

        try {
            Path path = FabricLoader.getInstance()
                .getConfigDir()
                .resolve("vulkanextensionemulator.properties");

            Files.createDirectories(path.getParent(), new FileAttribute[0]);

            StringBuilder builder = new StringBuilder();

            builder.append("# Vulkan Extension Emulator configuration\n");
            builder.append("configVersion=").append(config.configVersion).append('\n');
            builder.append("enabled=").append(config.enabled).append('\n');
            builder.append("bypassApiVersionCheck=").append(config.bypassApiVersionCheck).append('\n');
            builder.append("verbose=").append(config.verbose).append('\n');
            builder.append("trace=").append(config.trace).append('\n');
            builder.append("disableTimestamps=").append(config.disableTimestamps).append('\n');
            builder.append("allowPushDescriptor=").append(config.allowPushDescriptor).append('\n');
            builder.append("emulateDynamicRendering=").append(config.emulateDynamicRendering).append('\n');
            builder.append("coalesceBarriers=").append(config.coalesceBarriers).append('\n');
            builder.append("narrowPresentBarrier=").append(config.narrowPresentBarrier).append('\n');
            builder.append("usePipelineCache=").append(config.usePipelineCache).append('\n');
            builder.append("pipelineCacheAuto=").append(config.pipelineCacheAuto).append('\n');
            builder.append("metrics=").append(config.metrics).append('\n');
            builder.append("cacheDescriptors=").append(config.cacheDescriptors).append('\n');
            builder.append("profileFrames=").append(config.profileFrames).append('\n');
            builder.append("rebuildGateEmulationOnly=").append(config.rebuildGateEmulationOnly).append('\n');
            builder.append("preallocateChunkHeaps=").append(config.preallocateChunkHeaps).append('\n');
            builder.append("desktopSafeMode=").append(config.desktopSafeMode).append('\n');
            builder.append("autoTunePreallocation=").append(config.autoTunePreallocation).append('\n');
            builder.append("terrainRebuildEnabled=").append(config.terrainRebuildEnabled).append('\n');
            builder.append("terrainRebuildAuto=").append(config.terrainRebuildAuto).append('\n');
            builder.append("deeperPipeline=").append(config.deeperPipeline).append('\n');
            builder.append("extraFramesInFlight=").append(config.extraFramesInFlight).append('\n');
            builder.append("prewarmDescriptorPools=").append(config.prewarmDescriptorPools).append('\n');
            builder.append("descriptorBlockSets=").append(config.descriptorBlockSets).append('\n');
            builder.append("stutterLog=").append(config.stutterLog).append('\n');
            builder.append("adaptiveDepth=").append(config.adaptiveDepth).append('\n');
            builder.append("adaptiveDepthSlowMs=").append(config.adaptiveDepthSlowMs).append('\n');
            builder.append("adaptiveDepthConsecutive=").append(config.adaptiveDepthConsecutive).append('\n');
            builder.append("adaptiveDepthRecoverFrames=").append(config.adaptiveDepthRecoverFrames).append('\n');
            builder.append("terrainRebuildSettleTicks=").append(config.terrainRebuildSettleTicks).append('\n');
            builder.append("terrainRebuildSettleBusyMs=").append(config.terrainRebuildSettleBusyMs).append('\n');
            builder.append("bufferAutoSize=").append(config.bufferAutoSize).append('\n');
            builder.append("preallocateMinSystemRamMB=").append(config.preallocateMinSystemRamMB).append('\n');
            builder.append("preallocateChunkHeapCount=").append(config.preallocateChunkHeapCount).append('\n');

            Files.writeString(path, builder.toString(), new OpenOption[0]);

        } catch (IOException exception) {
            LOGGER.warn("[Vulkan Extension Emulator] Could not write config", exception);
        }
    }

    public static final class Config {

        public int configVersion = 10;

        public boolean enabled = true;
        public boolean bypassApiVersionCheck = true;
        public boolean verbose = true;
        public boolean trace = false;
        public boolean disableTimestamps = true;
        public boolean allowPushDescriptor = true;
        public boolean emulateDynamicRendering = true;
        public boolean coalesceBarriers = true;
        public boolean narrowPresentBarrier = true;

        public boolean usePipelineCache = false;
        public boolean pipelineCacheAuto = true;
        public boolean metrics = false;
        public boolean cacheDescriptors = true;
        public boolean profileFrames = false;

        public boolean rebuildGateEmulationOnly = true;
        public boolean preallocateChunkHeaps = true;
        public boolean desktopSafeMode = true;
        public boolean autoTunePreallocation = true;

        public boolean terrainRebuildEnabled = false;
        public boolean terrainRebuildAuto = true;

        public boolean deeperPipeline = true;
        public int extraFramesInFlight = 1;

        public boolean prewarmDescriptorPools = true;
        public int descriptorBlockSets = 0;

        public boolean stutterLog = false;

        public boolean adaptiveDepth = true;
        public int adaptiveDepthSlowMs = 150;
        public int adaptiveDepthConsecutive = 3;
        public int adaptiveDepthRecoverFrames = 600;

        public int terrainRebuildSettleTicks = 60;
        public int terrainRebuildSettleBusyMs = 150;

        public boolean bufferAutoSize = false;

        public int preallocateMinSystemRamMB = 3072;
        public int preallocateChunkHeapCount = 0;
    }
}