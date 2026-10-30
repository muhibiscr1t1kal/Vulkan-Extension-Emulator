
package com.muhibiscr1t1kal.vulkanextensionemulator.vk;

import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VK11;
import org.lwjgl.vulkan.VkAllocationCallbacks;
import org.lwjgl.vulkan.VkApplicationInfo;
import org.lwjgl.vulkan.VkExtensionProperties;
import org.lwjgl.vulkan.VkInstance;
import org.lwjgl.vulkan.VkInstanceCreateInfo;
import org.lwjgl.vulkan.VkPhysicalDevice;
import org.lwjgl.vulkan.VkPhysicalDeviceFeatures;
import org.lwjgl.vulkan.VkPhysicalDeviceProperties;

public final class VulkanProbe {
   private static final String[] REQUIRED_EXTENSIONS = new String[]{"VK_KHR_swapchain", "VK_KHR_dynamic_rendering", "VK_KHR_push_descriptor", "VK_KHR_synchronization2", "VK_EXT_multi_draw", "VK_EXT_vertex_attribute_divisor"};
   private static final String[] HELPFUL_EXTENSIONS = new String[]{"VK_KHR_create_renderpass2", "VK_KHR_depth_stencil_resolve", "VK_KHR_imageless_framebuffer", "VK_KHR_maintenance2", "VK_KHR_timeline_semaphore", "VK_EXT_host_query_reset", "VK_KHR_get_physical_device_properties2"};

   private VulkanProbe() {
   }

   public static Result probe() {
      List list = new ArrayList();
      Map map = new LinkedHashMap();
      Map map1 = new LinkedHashMap();
      Map map2 = new LinkedHashMap();
      VkInstance vkinstance = null;

      try {
         Result vulkanprobe$result2;
         try {
            MemoryStack memorystack = MemoryStack.stackPush();

            Result vulkanprobe$result1;
            label286: {
               Result count;
               label287: {
                  try {
                     VkApplicationInfo vkapplicationinfo = VkApplicationInfo.calloc(memorystack).sType$Default().pApplicationName(memorystack.UTF8("Vulkan Extension Emulator Probe")).apiVersion(VK11.VK_API_VERSION_1_1);
                     VkInstanceCreateInfo vkinstancecreateinfo = VkInstanceCreateInfo.calloc(memorystack).sType$Default().pApplicationInfo(vkapplicationinfo);
                     PointerBuffer pointerbuffer = memorystack.mallocPointer(1);
                     int i = VK11.vkCreateInstance(vkinstancecreateinfo, (VkAllocationCallbacks)null, pointerbuffer);
                     if (i == 0) {
                        vkinstance = new VkInstance(pointerbuffer.get(0), vkinstancecreateinfo);
                        IntBuffer intbuffer = memorystack.mallocInt(1);
                        VK11.vkEnumeratePhysicalDevices(vkinstance, intbuffer, (PointerBuffer)null);
                        if (intbuffer.get(0) == 0) {
                           list.add("No Vulkan physical devices found");
                           vulkanprobe$result1 = new Result(false, "-", "-", 0, 0, 0, map, map1, map2, list);
                           break label286;
                        }

                        PointerBuffer pointerbuffer1 = memorystack.mallocPointer(intbuffer.get(0));
                        VK11.vkEnumeratePhysicalDevices(vkinstance, intbuffer, pointerbuffer1);
                        VkPhysicalDevice vkphysicaldevice = new VkPhysicalDevice(pointerbuffer1.get(0), vkinstance);
                        VkPhysicalDeviceProperties vkphysicaldeviceproperties = VkPhysicalDeviceProperties.calloc(memorystack);
                        VK11.vkGetPhysicalDeviceProperties(vkphysicaldevice, vkphysicaldeviceproperties);
                        String s = vkphysicaldeviceproperties.deviceNameString();
                        int j = vkphysicaldeviceproperties.apiVersion();
                        int k = VK11.VK_VERSION_MAJOR(j);
                        int l = VK11.VK_VERSION_MINOR(j);
                        int i1 = VK11.VK_VERSION_PATCH(j);
                        int var10000 = vkphysicaldeviceproperties.driverVersion();
                        String s1 = "driverVersion=" + var10000 + " vendorID=0x" + Integer.toHexString(vkphysicaldeviceproperties.vendorID());
                        VK11.vkEnumerateDeviceExtensionProperties(vkphysicaldevice, (String)null, intbuffer, (VkExtensionProperties.Buffer)null);
                        VkExtensionProperties.Buffer buffer = VkExtensionProperties.calloc(intbuffer.get(0), memorystack);
                        VK11.vkEnumerateDeviceExtensionProperties(vkphysicaldevice, (String)null, intbuffer, buffer);
                        List list1 = new ArrayList();

                        for(int j1 = 0; j1 < buffer.capacity(); ++j1) {
                           list1.add(((VkExtensionProperties)buffer.get(j1)).extensionNameString());
                        }

                        String[] var46 = REQUIRED_EXTENSIONS;
                        int var26 = var46.length;

                        int var27;
                        String s3;
                        for(var27 = 0; var27 < var26; ++var27) {
                           s3 = var46[var27];
                           map.put(s3, list1.contains(s3));
                        }

                        var46 = HELPFUL_EXTENSIONS;
                        var26 = var46.length;

                        for(var27 = 0; var27 < var26; ++var27) {
                           s3 = var46[var27];
                           map.put(s3, list1.contains(s3));
                        }

                        VkPhysicalDeviceFeatures vkphysicaldevicefeatures = VkPhysicalDeviceFeatures.calloc(memorystack);
                        VK11.vkGetPhysicalDeviceFeatures(vkphysicaldevice, vkphysicaldevicefeatures);
                        map1.put("multiDrawIndirect", vkphysicaldevicefeatures.multiDrawIndirect());
                        map1.put("samplerAnisotropy", vkphysicaldevicefeatures.samplerAnisotropy());
                        map1.put("fillModeNonSolid", vkphysicaldevicefeatures.fillModeNonSolid());
                        map1.put("depthClamp", vkphysicaldevicefeatures.depthClamp());
                        map1.put("independentBlend", vkphysicaldevicefeatures.independentBlend());
                        map2.put("vkCreateRenderPass (VK1.0 core)", true);
                        map2.put("vkCreateFramebuffer (VK1.0 core)", true);
                        map2.put("vkCmdBeginRenderPass (VK1.0 core)", true);
                        map2.put("vkCmdPipelineBarrier (VK1.0 core)", true);
                        map2.put("vkQueueSubmit (VK1.0 core)", true);
                        map2.put("VK_KHR_create_renderpass2 (nicer, optional)", list1.contains("VK_KHR_create_renderpass2"));
                        list.add("Legacy render-pass path is Vulkan 1.0 core and always present.");
                        list.add("This is what dynamic rendering was built on top of in 2021.");
                        vulkanprobe$result2 = new Result(true, s, s1, k, l, i1, map, map1, map2, list);
                        break label287;
                     }

                     list.add("vkCreateInstance failed: " + i);
                     count = new Result(false, "-", "-", 0, 0, 0, map, map1, map2, list);
                  } catch (Throwable var42) {
                     if (memorystack != null) {
                        try {
                           memorystack.close();
                        } catch (Throwable var41) {
                           var42.addSuppressed(var41);
                        }
                     }

                     throw var42;
                  }

                  if (memorystack != null) {
                     memorystack.close();
                  }

                  Result var45 = count;
                  return var45;
               }

               if (memorystack != null) {
                  memorystack.close();
               }

               count = vulkanprobe$result2;
               return count;
            }

            if (memorystack != null) {
               memorystack.close();
            }

            return vulkanprobe$result1;
         } catch (Throwable var43) {
            list.add("Probe threw: " + String.valueOf(var43));
            vulkanprobe$result2 = new Result(false, "-", "-", 0, 0, 0, map, map1, map2, list);
            return vulkanprobe$result2;
         }
      } finally {
         if (vkinstance != null) {
            try {
               VK11.vkDestroyInstance(vkinstance, (VkAllocationCallbacks)null);
            } catch (Throwable var40) {
            }
         }
      }
   }

   public static String format(Result r) {
      StringBuilder stringbuilder = new StringBuilder();
      stringbuilder.append("=== Vulkan Extension Emulator Vulkan Probe ===\n");
      if (!r.vulkanAvailable()) {
         stringbuilder.append("Vulkan NOT available\n");
         r.notes().forEach((n) -> {
            stringbuilder.append("  ! ").append(n).append('\n');
         });
         return stringbuilder.toString();
      } else {
         stringbuilder.append("Device : ").append(r.deviceName()).append('\n');
         stringbuilder.append("API    : ").append(r.apiMajor()).append('.').append(r.apiMinor()).append('.').append(r.apiPatch()).append('\n');
         stringbuilder.append("Driver : ").append(r.driverInfo()).append('\n');
         stringbuilder.append("\n-- Required by Minecraft 26.2 --\n");
         String[] var2 = REQUIRED_EXTENSIONS;
         int var3 = var2.length;

         int var4;
         String s1;
         for(var4 = 0; var4 < var3; ++var4) {
            s1 = var2[var4];
            stringbuilder.append((Boolean)r.extensions().getOrDefault(s1, false) ? "  [YES] " : "  [NO ] ").append(s1).append('\n');
         }

         stringbuilder.append("\n-- Helpful for emulation --\n");
         var2 = HELPFUL_EXTENSIONS;
         var3 = var2.length;

         for(var4 = 0; var4 < var3; ++var4) {
            s1 = var2[var4];
            stringbuilder.append((Boolean)r.extensions().getOrDefault(s1, false) ? "  [YES] " : "  [NO ] ").append(s1).append('\n');
         }

         stringbuilder.append("\n-- Core features --\n");
         r.features().forEach((k, v) -> {
            stringbuilder.append(Boolean.TRUE.equals(v) ? "  [YES] " : "  [NO ] ").append(k).append('\n');
         });

         stringbuilder.append("\n-- Emulation target path --\n");
         r.legacyPath().forEach((k, v) -> {
            stringbuilder.append(Boolean.TRUE.equals(v) ? "  [YES] " : "  [NO ] ").append(k).append('\n');
         });

         stringbuilder.append('\n');
         r.notes().forEach((n) -> {
            stringbuilder.append("  * ").append(n).append('\n');
         });
         return stringbuilder.toString();
      }
   }

   public static record Result(boolean vulkanAvailable, String deviceName, String driverInfo, int apiMajor, int apiMinor, int apiPatch, Map extensions, Map features, Map legacyPath, List notes) {
      public Result(boolean vulkanAvailable, String deviceName, String driverInfo, int apiMajor, int apiMinor, int apiPatch, Map extensions, Map features, Map legacyPath, List notes) {
         this.vulkanAvailable = vulkanAvailable;
         this.deviceName = deviceName;
         this.driverInfo = driverInfo;
         this.apiMajor = apiMajor;
         this.apiMinor = apiMinor;
         this.apiPatch = apiPatch;
         this.extensions = extensions;
         this.features = features;
         this.legacyPath = legacyPath;
         this.notes = notes;
      }

      public boolean vulkanAvailable() {
         return this.vulkanAvailable;
      }

      public String deviceName() {
         return this.deviceName;
      }

      public String driverInfo() {
         return this.driverInfo;
      }

      public int apiMajor() {
         return this.apiMajor;
      }

      public int apiMinor() {
         return this.apiMinor;
      }

      public int apiPatch() {
         return this.apiPatch;
      }

      public Map extensions() {
         return this.extensions;
      }

      public Map features() {
         return this.features;
      }

      public Map legacyPath() {
         return this.legacyPath;
      }

      public List notes() {
         return this.notes;
      }
   }
}
