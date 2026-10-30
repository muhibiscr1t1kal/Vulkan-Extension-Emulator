package com.muhibiscr1t1kal.vulkanextensionemulator.vk;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.lwjgl.vulkan.VkDescriptorBufferInfo;
import org.lwjgl.vulkan.VkDescriptorImageInfo;
import org.lwjgl.vulkan.VkWriteDescriptorSet;

public final class DescriptorCache {
   private static final int MAX_ENTRIES = 1024;
   private final Map sets = new HashMap();
   private final Map handleToKeys = new HashMap();
   private long hits;
   private long misses;
   private long evictions;

   public long lookup(long layout, VkWriteDescriptorSet.Buffer writes) {
      Key descriptorcache$key = DescriptorCache.Key.of(layout, writes);
      if (descriptorcache$key == null) {
         return 0L;
      } else {
         Long olong = (Long)this.sets.get(descriptorcache$key);
         if (olong != null) {
            ++this.hits;
            return olong;
         } else {
            ++this.misses;
            return 0L;
         }
      }
   }

   public void store(long layout, VkWriteDescriptorSet.Buffer writes, long set) {
      if (this.sets.size() >= 1024) {
         this.sets.clear();
         this.handleToKeys.clear();
         ++this.evictions;
      }

      Key descriptorcache$key = DescriptorCache.Key.of(layout, writes);
      if (descriptorcache$key != null) {
         this.sets.put(descriptorcache$key, set);

         for(int i = 0; i < writes.remaining(); ++i) {
            VkWriteDescriptorSet vkwritedescriptorset = (VkWriteDescriptorSet)writes.get(i);
            VkDescriptorBufferInfo.Buffer buffer = vkwritedescriptorset.pBufferInfo();
            if (buffer != null) {
               for(int j = 0; j < buffer.remaining(); ++j) {
                  this.track(((VkDescriptorBufferInfo)buffer.get(j)).buffer(), descriptorcache$key);
               }
            }

            VkDescriptorImageInfo.Buffer buffer1 = vkwritedescriptorset.pImageInfo();
            if (buffer1 != null) {
               for(int k = 0; k < buffer1.remaining(); ++k) {
                  this.track(((VkDescriptorImageInfo)buffer1.get(k)).imageView(), descriptorcache$key);
               }
            }
         }
      }

   }

   private void track(long handle, Key key) {
      if (handle != 0L) {
         ((List)this.handleToKeys.computeIfAbsent(handle, (k) -> {
            return new ArrayList();
         })).add(key);
      }

   }

   public void invalidateResource(long handle) {
      List list = (List)this.handleToKeys.remove(handle);
      if (list != null) {
         Iterator var4 = list.iterator();

         while(var4.hasNext()) {
            Key descriptorcache$key = (Key)var4.next();
            this.sets.remove(descriptorcache$key);
         }
      }

   }

   public void invalidate() {
      this.sets.clear();
      this.handleToKeys.clear();
   }

   public long hits() {
      return this.hits;
   }

   public long misses() {
      return this.misses;
   }

   public String stats() {
      long i = this.hits + this.misses;
      return i == 0L ? "descriptor cache: unused" : String.format("descriptor cache: %.1f%% hit rate (%d hits, %d misses, %d flushes)", new Object[]{100.0 * (double)this.hits / (double)i, this.hits, this.misses, this.evictions});
   }

   private static record Key(long layout, long[] words) {
      private Key(long layout, long[] words) {
         this.layout = layout;
         this.words = words;
      }

      static Key of(long layout, VkWriteDescriptorSet.Buffer writes) {
         int i = writes.remaining();
         if (i == 0) {
            return null;
         } else {
            long[] along = new long[1 + i * 8];
            int j = 0;
            along[j++] = (long)i;

            for(int k = 0; k < i; ++k) {
               VkWriteDescriptorSet vkwritedescriptorset = (VkWriteDescriptorSet)writes.get(k);
               int l = vkwritedescriptorset.descriptorType();
               int i1 = vkwritedescriptorset.descriptorCount();
               along[j++] = (long)vkwritedescriptorset.dstBinding() << 32 | (long)l & 4294967295L;
               along[j++] = (long)vkwritedescriptorset.dstArrayElement() << 32 | (long)i1 & 4294967295L;
               VkDescriptorBufferInfo.Buffer buffer = vkwritedescriptorset.pBufferInfo();
               if (buffer != null) {
                  for(int j1 = 0; j1 < buffer.remaining() && j + 3 < along.length; ++j1) {
                     VkDescriptorBufferInfo vkdescriptorbufferinfo = (VkDescriptorBufferInfo)buffer.get(j1);
                     along[j++] = vkdescriptorbufferinfo.buffer();
                     along[j++] = vkdescriptorbufferinfo.offset();
                     along[j++] = vkdescriptorbufferinfo.range();
                  }
               }

               VkDescriptorImageInfo.Buffer buffer1 = vkwritedescriptorset.pImageInfo();
               if (buffer1 != null) {
                  for(int k1 = 0; k1 < buffer1.remaining() && j + 3 < along.length; ++k1) {
                     VkDescriptorImageInfo vkdescriptorimageinfo = (VkDescriptorImageInfo)buffer1.get(k1);
                     along[j++] = vkdescriptorimageinfo.sampler();
                     along[j++] = vkdescriptorimageinfo.imageView();
                     along[j++] = (long)vkdescriptorimageinfo.imageLayout();
                  }
               }

               if (vkwritedescriptorset.pTexelBufferView() != null) {
                  return null;
               }
            }

            long[] along1 = new long[j];
            System.arraycopy(along, 0, along1, 0, j);
            return new Key(layout, along1);
         }
      }

      public boolean equals(Object o) {
         boolean var10000;
         if (o instanceof Key descriptorcache$key) {
            if (descriptorcache$key.layout == this.layout && Arrays.equals(descriptorcache$key.words, this.words)) {
               var10000 = true;
               return var10000;
            }
         }

         var10000 = false;
         return var10000;
      }

      public int hashCode() {
         return 31 * Long.hashCode(this.layout) + Arrays.hashCode(this.words);
      }

      public long layout() {
         return this.layout;
      }

      public long[] words() {
         return this.words;
      }
   }
}
