package com.muhibiscr1t1kal.vulkanextensionemulator.vk;

import com.muhibiscr1t1kal.vulkanextensionemulator.VulkanExtensionEmulator;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.LongBuffer;
import java.nio.file.CopyOption;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileAttribute;
import net.fabricmc.loader.api.FabricLoader;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkAllocationCallbacks;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkPipelineCacheCreateInfo;

public final class PipelineCache {
   private static final int FORMAT_VERSION = 1;
   private static final String FILE_NAME = "vulkanextensionemulator-pipeline-cache.bin";
   private static VkDevice device;
   private static long handle = 0L;
   private static Path cachePath;
   private static int loadedBytes;
   private static byte[] currentUuid = new byte[24];

   private PipelineCache() {
   }

   public static boolean enabled() {
      if (VulkanExtensionEmulator.config().usePipelineCache) {
         return true;
      } else if (!VulkanExtensionEmulator.config().pipelineCacheAuto) {
         return false;
      } else if (VulkanExtensionEmulator.isEmulating()) {
         return false;
      } else {
         VulkanProbe.Result vulkanprobe$result = VulkanExtensionEmulator.probeResult();
         return vulkanprobe$result != null && (vulkanprobe$result.apiMajor() > 1 || vulkanprobe$result.apiMajor() == 1 && vulkanprobe$result.apiMinor() >= 2);
      }
   }

   public static long handle() {
      return handle;
   }

   public static void init(VkDevice vkDevice, long deviceUuidLow, long deviceUuidHigh, int driverVersion) {
      if (enabled() && handle == 0L) {
         device = vkDevice;
         byte[] abyte = null;

         try {
            cachePath = FabricLoader.getInstance().getGameDir().resolve("vulkanextensionemulator").resolve("vulkanextensionemulator-pipeline-cache.bin");
            abyte = readSeed(cachePath, deviceUuidLow, deviceUuidHigh, driverVersion);
         } catch (Throwable var13) {
            VulkanExtensionEmulator.LOGGER.warn("[Vulkan Extension Emulator] Could not read pipeline cache; starting empty", var13);
         }

         try {
            MemoryStack memorystack = MemoryStack.stackPush();

            label90: {
               try {
                  VkPipelineCacheCreateInfo vkpipelinecachecreateinfo = VkPipelineCacheCreateInfo.calloc(memorystack).sType$Default();
                  ByteBuffer bytebuffer = null;
                  if (abyte != null && abyte.length > 0) {
                     bytebuffer = MemoryUtil.memAlloc(abyte.length);
                     bytebuffer.put(abyte).flip();
                     vkpipelinecachecreateinfo.pInitialData(bytebuffer);
                     loadedBytes = abyte.length;
                  }

                  LongBuffer longbuffer = memorystack.callocLong(1);
                  int i = VK10.vkCreatePipelineCache(device, vkpipelinecachecreateinfo, (VkAllocationCallbacks)null, longbuffer);
                  if (bytebuffer != null) {
                     MemoryUtil.memFree(bytebuffer);
                  }

                  if (i == 0) {
                     handle = longbuffer.get(0);
                     if (loadedBytes > 0) {
                        VulkanExtensionEmulator.LOGGER.info("[Vulkan Extension Emulator] Pipeline cache primed from disk ({} KiB) — shader compiles should not stall the first frames", loadedBytes / 1024);
                     } else {
                        VulkanExtensionEmulator.LOGGER.info("[Vulkan Extension Emulator] Pipeline cache created empty; it will be saved on exit and reused next launch");
                     }
                     break label90;
                  }

                  VulkanExtensionEmulator.LOGGER.warn("[Vulkan Extension Emulator] vkCreatePipelineCache failed ({}); pipelines will compile uncached", i);
                  handle = 0L;
               } catch (Throwable var14) {
                  if (memorystack != null) {
                     try {
                        memorystack.close();
                     } catch (Throwable var12) {
                        var14.addSuppressed(var12);
                     }
                  }

                  throw var14;
               }

               if (memorystack != null) {
                  memorystack.close();
               }

               return;
            }

            if (memorystack != null) {
               memorystack.close();
            }

            return;
         } catch (Throwable var15) {
            VulkanExtensionEmulator.LOGGER.warn("[Vulkan Extension Emulator] Pipeline cache unavailable", var15);
            handle = 0L;
         }
      }

   }

   public static void shutdown() {
      if (handle != 0L && device != null) {
         try {
            VK10.vkDeviceWaitIdle(device);
         } catch (Throwable var2) {
         }

         try {
            save();
         } catch (Throwable var1) {
            VulkanExtensionEmulator.LOGGER.warn("[Vulkan Extension Emulator] Could not save pipeline cache", var1);
         }

         VK10.vkDestroyPipelineCache(device, handle, (VkAllocationCallbacks)null);
         handle = 0L;
         device = null;
      }

   }

   private static void save() throws IOException {
      MemoryStack memorystack;
      label191: {
         label192: {
            label198: {
               if (cachePath != null) {
                  memorystack = MemoryStack.stackPush();

                  try {
                     PointerBuffer pointerbuffer = memorystack.mallocPointer(1);
                     if (VK10.vkGetPipelineCacheData(device, handle, pointerbuffer, (ByteBuffer)null) == 0) {
                        int i = (int)pointerbuffer.get(0);
                        if (i <= 0) {
                           break label191;
                        }

                        ByteBuffer bytebuffer = MemoryUtil.memAlloc(i);

                        try {
                           pointerbuffer.put(0, (long)i);
                           if (VK10.vkGetPipelineCacheData(device, handle, pointerbuffer, bytebuffer) != 0) {
                              break label192;
                           }

                           byte[] abyte = new byte[i];
                           bytebuffer.get(abyte);
                           Files.createDirectories(cachePath.getParent(), new FileAttribute[0]);
                           Path path = cachePath.resolveSibling("vulkanextensionemulator-pipeline-cache.bin.tmp");
                           OutputStream outputstream = Files.newOutputStream(path, new OpenOption[0]);

                           try {
                              outputstream.write(header());
                              outputstream.write(abyte);
                           } catch (Throwable var17) {
                              if (outputstream != null) {
                                 try {
                                    outputstream.close();
                                 } catch (Throwable var16) {
                                    var17.addSuppressed(var16);
                                 }
                              }

                              throw var17;
                           }

                           if (outputstream != null) {
                              outputstream.close();
                           }

                           Files.move(path, cachePath, new CopyOption[]{StandardCopyOption.REPLACE_EXISTING});
                           VulkanExtensionEmulator.LOGGER.info("[Vulkan Extension Emulator] Pipeline cache saved ({} KiB, was {} KiB at load)", i / 1024, loadedBytes / 1024);
                           break label198;
                        } finally {
                           MemoryUtil.memFree(bytebuffer);
                        }
                     }
                  } catch (Throwable var19) {
                     if (memorystack != null) {
                        try {
                           memorystack.close();
                        } catch (Throwable var15) {
                           var19.addSuppressed(var15);
                        }
                     }

                     throw var19;
                  }

                  if (memorystack != null) {
                     memorystack.close();
                  }
               }

               return;
            }

            if (memorystack != null) {
               memorystack.close();
            }

            return;
         }

         if (memorystack != null) {
            memorystack.close();
         }

         return;
      }

      if (memorystack != null) {
         memorystack.close();
      }

   }

   private static byte[] header() {
      return currentUuid;
   }

   private static byte[] readSeed(Path path, long uuidLow, long uuidHigh, int driverVersion) throws IOException {
      ByteBuffer bytebuffer = ByteBuffer.allocate(24);
      bytebuffer.putInt(1);
      bytebuffer.putInt(driverVersion);
      bytebuffer.putLong(uuidLow);
      bytebuffer.putLong(uuidHigh);
      currentUuid = bytebuffer.array();
      if (!Files.isReadable(path)) {
         return null;
      } else {
         byte[] abyte = Files.readAllBytes(path);
         if (abyte.length <= 24) {
            return null;
         } else {
            for(int i = 0; i < 24; ++i) {
               if (abyte[i] != currentUuid[i]) {
                  VulkanExtensionEmulator.LOGGER.info("[Vulkan Extension Emulator] Pipeline cache is from a different device or driver version; starting fresh");
                  return null;
               }
            }

            byte[] abyte1 = new byte[abyte.length - 24];
            System.arraycopy(abyte, 24, abyte1, 0, abyte1.length);
            return abyte1;
         }
      }
   }
}
