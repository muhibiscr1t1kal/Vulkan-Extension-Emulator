package com.muhibiscr1t1kal.vulkanextensionemulator;

import com.mojang.blaze3d.vulkan.init.VulkanFeature;
import com.muhibiscr1t1kal.vulkanextensionemulator.vk.Emulation;
import com.muhibiscr1t1kal.vulkanextensionemulator.vk.VulkanProbe;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class VulkanExtensionEmulator implements ClientModInitializer {
    public static final String MOD_ID = "vulkanextensionemulator";
    public static final Logger LOGGER = LoggerFactory.getLogger("Vulkan Extension Emulator");

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
    private static final Set<String> claimedOnce = ConcurrentHashMap.newKeySet();
    private static final Set<String> blockedOnce = ConcurrentHashMap.newKeySet();

    @Override
    public void onInitializeClient() {
        config = loadConfig();

        LOGGER.info("[Vulkan Extension Emulator] Phase 5 — VK1.2 core routing + dynamic rendering");

        if (!config.enabled) {
            LOGGER.info("[Vulkan Extension Emulator] Disabled by config; leaving Vulkan gate untouched.");
        } else {
            try {
                probeResult = VulkanProbe.probe();

                if (config.verbose) {
                    for (String line : VulkanProbe.format(probeResult).split("\n")) {
                        LOGGER.info("[Vulkan Extension Emulator] {}", line);
                    }
                }
            } catch (Throwable error) {
                LOGGER.error("[Vulkan Extension Emulator] Probe failed", error);
            }

            LOGGER.info("[Vulkan Extension Emulator] Will emulate if missing: {}", EMULATABLE_EXTENSIONS);
            LOGGER.info("[Vulkan Extension Emulator] Phase 4 active: dynamic rendering -> VkRenderPass + VkFramebuffer");
            LOGGER.info("[Vulkan Extension Emulator] If the game crashes on launch, set enabled=false in");
            LOGGER.info("[Vulkan Extension Emulator] config/vulkanextensionemulator.properties to fall back to OpenGL.");
        }
    }

    public static boolean canEmulateExtension(String ext) {
        if (config != null && config.enabled) {
            return "VK_KHR_push_descriptor".equals(ext) && !config.allowPushDescriptor
                    ? false
                    : EMULATABLE_EXTENSIONS.contains(ext);
        }
        return false;
    }

    public static boolean canEmulateFeature(String feature) {
        return config != null && config.enabled && EMULATABLE_FEATURES.contains(feature);
    }

    public static boolean allRemainingGapsAreEmulatable() {
        return config != null && config.enabled && !Emulation.hasUnemulatableGap();
    }

    public static String featureName(VulkanFeature feature) {
        if (feature == null) {
            return null;
        }
        try {
            return feature.name();
        } catch (Throwable error) {
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
        return config != null && config.enabled
                && (!Emulation.extensions().isEmpty() || !Emulation.features().isEmpty());
    }

    private static Config loadConfig() {
        Config loadedConfig = new Config();
        Path path = FabricLoader.getInstance().getConfigDir()
                .resolve("vulkanextensionemulator.properties");

        if (Files.isReadable(path)) {
            Properties properties = new Properties();

            try (InputStream inputStream = Files.newInputStream(path)) {
                properties.load(inputStream);

                int version = 1;
                try {
                    version = Integer.parseInt(properties.getProperty("configVersion", "1").trim());
                } catch (NumberFormatException ignored) {
                }

                if (version >= 4) {
                    loadedConfig.enabled = Boolean.parseBoolean(properties.getProperty("enabled", String.valueOf(loadedConfig.enabled)).trim());
                    loadedConfig.bypassApiVersionCheck = Boolean.parseBoolean(properties.getProperty("bypassApiVersionCheck", String.valueOf(loadedConfig.bypassApiVersionCheck)).trim());
                    loadedConfig.verbose = Boolean.parseBoolean(properties.getProperty("verbose", String.valueOf(loadedConfig.verbose)).trim());
                    loadedConfig.trace = Boolean.parseBoolean(properties.getProperty("trace", String.valueOf(loadedConfig.trace)).trim());
                    loadedConfig.disableTimestamps = Boolean.parseBoolean(properties.getProperty("disableTimestamps", String.valueOf(loadedConfig.disableTimestamps)).trim());
                    loadedConfig.allowPushDescriptor = Boolean.parseBoolean(properties.getProperty("allowPushDescriptor", String.valueOf(loadedConfig.allowPushDescriptor)).trim());
                    loadedConfig.emulateDynamicRendering = Boolean.parseBoolean(properties.getProperty("emulateDynamicRendering", String.valueOf(loadedConfig.emulateDynamicRendering)).trim());
                    loadedConfig.coalesceBarriers = Boolean.parseBoolean(properties.getProperty("coalesceBarriers", String.valueOf(loadedConfig.coalesceBarriers)).trim());
                    loadedConfig.narrowPresentBarrier = Boolean.parseBoolean(properties.getProperty("narrowPresentBarrier", String.valueOf(loadedConfig.narrowPresentBarrier)).trim());
                    loadedConfig.usePipelineCache = Boolean.parseBoolean(properties.getProperty("usePipelineCache", String.valueOf(loadedConfig.usePipelineCache)).trim());
                    loadedConfig.metrics = Boolean.parseBoolean(properties.getProperty("metrics", String.valueOf(loadedConfig.metrics)).trim());
                    loadedConfig.cacheDescriptors = Boolean.parseBoolean(properties.getProperty("cacheDescriptors", String.valueOf(loadedConfig.cacheDescriptors)).trim());
                    loadedConfig.profileFrames = Boolean.parseBoolean(properties.getProperty("profileFrames", String.valueOf(loadedConfig.profileFrames)).trim());
                    loadedConfig.deeperPipeline = Boolean.parseBoolean(properties.getProperty("deeperPipeline", String.valueOf(loadedConfig.deeperPipeline)).trim());
                    loadedConfig.rebuildGateEmulationOnly = Boolean.parseBoolean(properties.getProperty("rebuildGateEmulationOnly", String.valueOf(loadedConfig.rebuildGateEmulationOnly)).trim());

                    try {
                        loadedConfig.extraFramesInFlight = Integer.parseInt(properties.getProperty("extraFramesInFlight", String.valueOf(loadedConfig.extraFramesInFlight)).trim());
                    } catch (NumberFormatException ignored) {
                    }

                    return loadedConfig;
                }

                Files.deleteIfExists(path);
                writeDefaultConfig(path);
                return loadedConfig;
            } catch (IOException error) {
                LOGGER.warn("[Vulkan Extension Emulator] Could not read config, using defaults", error);
            }

            return loadedConfig;
        }

        writeDefaultConfig(path);
        return loadedConfig;
    }

    private static void writeDefaultConfig(Path path) {
        String defaultConfig = """
                configVersion=4
                enabled=true
                bypassApiVersionCheck=true
                verbose=true
                trace=false
                disableTimestamps=true
                allowPushDescriptor=true
                emulateDynamicRendering=true
                coalesceBarriers=true
                narrowPresentBarrier=true
                usePipelineCache=false
                pipelineCacheAuto=true
                metrics=false
                cacheDescriptors=true
                profileFrames=false
                deeperPipeline=true
                extraFramesInFlight=1
                rebuildGateEmulationOnly=true
                """;

        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, defaultConfig);
        } catch (IOException error) {
            LOGGER.warn("[Vulkan Extension Emulator] Could not write config", error);
        }
    }

    public static final class Config {
        public int configVersion = 4;
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
        public boolean deeperPipeline = true;
        public int extraFramesInFlight = 1;
    }
}