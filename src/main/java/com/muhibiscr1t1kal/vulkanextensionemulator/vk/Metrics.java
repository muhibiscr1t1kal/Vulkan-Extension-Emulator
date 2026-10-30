package com.muhibiscr1t1kal.vulkanextensionemulator.vk;

import com.muhibiscr1t1kal.vulkanextensionemulator.VulkanExtensionEmulator;

public final class Metrics {
   private static final long INTERVAL_NS = 30000000000L;
   private static long lastReport;
   private static long frames;
   private static long prevPushes;
   private static long prevCacheHits;
   private static long prevCacheMisses;
   private static long prevRedundant;
   private static long prevBarriersIssued;
   private static long prevBarriersCoalesced;
   private static long prevUnrolledDraws;

   private Metrics() {
   }

   public static void frame() {
      if (VulkanExtensionEmulator.config().metrics || VulkanExtensionEmulator.isEmulating()) {
         ++frames;
         long i = System.nanoTime();
         if (lastReport == 0L) {
            lastReport = i;
         } else if (i - lastReport >= 30000000000L) {
            report(i - lastReport);
            lastReport = i;
            frames = 0L;
         }
      }

   }

   private static void report(long elapsedNs) {
      if (frames != 0L) {
         double d0 = (double)elapsedNs / 1.0E9;
         long i = PushDescriptorEmulation.pushCount();
         long j = PushDescriptorEmulation.cacheHits();
         long k = PushDescriptorEmulation.cacheMisses();
         long l = PushDescriptorEmulation.redundantBindsSkipped();
         long i1 = BarrierCoalescer.issuedCount();
         long j1 = BarrierCoalescer.coalescedCount();
         long k1 = MultiDrawEmulation.unrolledDrawCount();
         long l1 = i - prevPushes;
         long i2 = j - prevCacheHits;
         long j2 = k - prevCacheMisses;
         long k2 = l - prevRedundant;
         long l2 = i1 - prevBarriersIssued;
         long i3 = j1 - prevBarriersCoalesced;
         long j3 = k1 - prevUnrolledDraws;
         double d1 = i2 + j2 > 0L ? 100.0 * (double)i2 / (double)(i2 + j2) : 0.0;
         double d2 = l2 + i3 > 0L ? 100.0 * (double)i3 / (double)(l2 + i3) : 0.0;
         VulkanExtensionEmulator.LOGGER.info("[Vulkan Extension Emulator] {} fps | desc {}/frame ({}% cached, {} binds skipped) | barrier {}/frame ({}% coalesced) | unrolled {}/frame | rp={} fb={}", new Object[]{String.format("%.1f", new Object[]{(double)frames / d0}), per(l1), String.format("%.0f", new Object[]{d1}), per(k2), per(l2), String.format("%.0f", new Object[]{d2}), per(j3), DynamicRenderingEmulation.renderPassCount(), DynamicRenderingEmulation.framebufferCount()});
         VulkanExtensionEmulator.LOGGER.info("[Vulkan Extension Emulator] {}", FrameProfiler.summary());
         prevPushes = i;
         prevCacheHits = j;
         prevCacheMisses = k;
         prevRedundant = l;
         prevBarriersIssued = i1;
         prevBarriersCoalesced = j1;
         prevUnrolledDraws = k1;
      }

   }

   private static String per(long total) {
      return frames == 0L ? "0" : String.valueOf(total / frames);
   }
}
