package com.muhibiscr1t1kal.vulkanextensionemulator.vk;

import com.muhibiscr1t1kal.vulkanextensionemulator.VulkanExtensionEmulator;
import java.util.LinkedHashSet;
import java.util.Set;

public final class Emulation {
   private static final Set emulatedExtensions = new LinkedHashSet();
   private static final Set emulatedFeatures = new LinkedHashSet();
   private static boolean gateOpened;
   private static volatile boolean unemulatableGap;

   private Emulation() {
   }

   public static void markExtension(String name) {
      emulatedExtensions.add(name);
   }

   public static void markUnemulatable() {
      unemulatableGap = true;
   }

   public static boolean hasUnemulatableGap() {
      return unemulatableGap;
   }

   public static void markFeature(String name) {
      emulatedFeatures.add(name);
   }

   public static boolean isExtensionEmulated(String name) {
      return emulatedExtensions.contains(name);
   }

   public static boolean isFeatureEmulated(String name) {
      return emulatedFeatures.contains(name);
   }

   public static Set extensions() {
      return Set.copyOf(emulatedExtensions);
   }

   public static Set features() {
      return Set.copyOf(emulatedFeatures);
   }

   public static void setGateOpened(boolean v) {
      gateOpened = v;
   }

   public static boolean gateOpened() {
      return gateOpened;
   }

   public static boolean mustStripFromDeviceCreate(String extension) {
      return emulatedExtensions.contains(extension);
   }

   public static Set substituteExtensions() {
      VulkanProbe.Result vulkanprobe$result = VulkanExtensionEmulator.probeResult();
      if (vulkanprobe$result == null) {
         return Set.of();
      } else {
         Set set = new LinkedHashSet();
         String[] var2 = new String[]{"VK_EXT_host_query_reset", "VK_KHR_timeline_semaphore"};
         int var3 = var2.length;

         for(int var4 = 0; var4 < var3; ++var4) {
            String s = var2[var4];
            if (Boolean.TRUE.equals(vulkanprobe$result.extensions().get(s))) {
               set.add(s);
            }
         }

         return set;
      }
   }

   public static String describe() {
      return emulatedExtensions.isEmpty() && emulatedFeatures.isEmpty() ? "nothing emulated (driver supports everything required)" : "extensions=" + String.valueOf(emulatedExtensions) + " features=" + String.valueOf(emulatedFeatures);
   }
}
