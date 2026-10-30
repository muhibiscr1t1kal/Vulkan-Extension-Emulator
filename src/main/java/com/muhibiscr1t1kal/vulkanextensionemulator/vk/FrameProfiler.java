package com.muhibiscr1t1kal.vulkanextensionemulator.vk;

import com.muhibiscr1t1kal.vulkanextensionemulator.VulkanExtensionEmulator;

public final class FrameProfiler {
   private static final long SLOW_FRAME_NS = 33000000L;
   private static final int MAX_REPORTS = 40;
   private static long frameStart;
   private static long emulationNs;
   private static long gpuWaitNs;
   private static long acquireNs;
   private static int reports;
   private static long slowFrames;
   private static long totalFrames;
   private static long sumEmulation;
   private static long sumGpuWait;
   private static long sumAcquire;
   private static long sumFrame;

   private FrameProfiler() {
   }

   public static boolean enabled() {
      return VulkanExtensionEmulator.config().profileFrames || VulkanExtensionEmulator.isEmulating();
   }

   public static void beginFrame() {
      if (enabled()) {
         frameStart = System.nanoTime();
         emulationNs = 0L;
         gpuWaitNs = 0L;
         acquireNs = 0L;
      }

   }

   public static void addEmulation(long startNs) {
      if (enabled()) {
         emulationNs += System.nanoTime() - startNs;
      }

   }

   public static void addGpuWait(long startNs) {
      if (enabled()) {
         gpuWaitNs += System.nanoTime() - startNs;
      }

   }

   public static void addAcquire(long startNs) {
      if (enabled()) {
         acquireNs += System.nanoTime() - startNs;
      }

   }

   public static void endFrame() {
      if (enabled() && frameStart != 0L) {
         long i = System.nanoTime() - frameStart;
         frameStart = 0L;
         ++totalFrames;
         sumFrame += i;
         sumEmulation += emulationNs;
         sumGpuWait += gpuWaitNs;
         sumAcquire += acquireNs;
         if (i >= 33000000L) {
            ++slowFrames;
            if (reports < 40) {
               ++reports;
               long j = i - emulationNs - gpuWaitNs - acquireNs;
               if (j < 0L) {
                  j = 0L;
               }

               VulkanExtensionEmulator.LOGGER.info("[Vulkan Extension Emulator] SLOW FRAME {}ms = emulation {}ms + gpuWait {}ms + acquire {}ms + other {}ms", new Object[]{ms(i), ms(emulationNs), ms(gpuWaitNs), ms(acquireNs), ms(j)});
               if (reports == 40) {
                  VulkanExtensionEmulator.LOGGER.info("[Vulkan Extension Emulator] (further slow frames counted but not logged)");
               }
            }
         }
      }

   }

   public static String summary() {
      return enabled() && totalFrames != 0L ? String.format("frame profile: %d frames, %d slow (%.1f%%) | avg %.1fms = emu %.2fms + gpuWait %.2fms + acquire %.2fms + other %.2fms", new Object[]{totalFrames, slowFrames, 100.0 * (double)slowFrames / (double)totalFrames, (double)sumFrame / 1000000.0 / (double)totalFrames, (double)sumEmulation / 1000000.0 / (double)totalFrames, (double)sumGpuWait / 1000000.0 / (double)totalFrames, (double)sumAcquire / 1000000.0 / (double)totalFrames, (double)Math.max(0L, sumFrame - sumEmulation - sumGpuWait - sumAcquire) / 1000000.0 / (double)totalFrames}) : "frame profile: disabled";
   }

   private static String ms(long ns) {
      return String.format("%.1f", new Object[]{(double)ns / 1000000.0});
   }
}
