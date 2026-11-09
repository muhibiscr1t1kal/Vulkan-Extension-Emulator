package com.muhibiscr1t1kal.vulkanextensionemulator.terrain;

import com.muhibiscr1t1kal.vulkanextensionemulator.VulkanExtensionEmulator;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

@EventBusSubscriber(
   modid = "vulkanextensionemulator",
   value = Dist.CLIENT
)
public final class TerrainRebuildEnhancement {
   private static final String TAG = "[Vulkan Extension Emulator]";
   private static final List REBUILD_TICKS = new ArrayList();
   private static final int MAX_DEFER_TICKS = 1200;
   private static long clientTick;
   private static Object lastLevel;
   private static String lastRenderSignature;
   private static boolean reflectionFailureLogged;
   private static long lastTickWall;
   private static int settleQuietTicks;
   private static long lastChunkSample;
   private static int lastChunkCount;
   private static int chunkCountDelta = -1;
   private static final Map FIELD_CACHE = new ConcurrentHashMap();
   private static final Map METHOD_CACHE = new ConcurrentHashMap();
   private static boolean gateLogged;
   private static long lastRebuildTick = -1000L;

   @SubscribeEvent
   public static void onClientTick(ClientTickEvent.Post event) {
      onEndClientTick(Minecraft.getInstance());
   }

   private static void onEndClientTick(Object object) {
      ++clientTick;

      try {
         long now = System.currentTimeMillis();
         if (lastTickWall != 0L && now - lastTickWall > (long)VulkanExtensionEmulator.config().terrainRebuildSettleBusyMs) {
            settleQuietTicks = 0;
         } else if (lastTickWall != 0L) {
            ++settleQuietTicks;
         }

         lastTickWall = now;
         if (now - lastChunkSample >= 1000L) {
            lastChunkSample = now;
            int i = loadedChunkCount(object);
            chunkCountDelta = lastChunkCount == 0 ? -1 : i - lastChunkCount;
            lastChunkCount = i;
         }

         Object object2 = getFieldCached(object, "level", "net.minecraft.client.multiplayer.ClientLevel");
         if (object2 == null) {
            if (lastLevel != null) {
               System.out.println("[Vulkan Extension Emulator] World closed; clearing queued rebuilds.");
            }

            lastLevel = null;
            lastRenderSignature = null;
            lastTickWall = 0L;
            settleQuietTicks = 0;
            lastChunkCount = 0;
            chunkCountDelta = -1;
            REBUILD_TICKS.clear();
            return;
         }

         if (object2 != lastLevel) {
            lastLevel = object2;
            lastRenderSignature = null;
            lastTickWall = 0L;
            settleQuietTicks = 0;
            lastChunkCount = 0;
            chunkCountDelta = -1;
            REBUILD_TICKS.clear();
            VulkanExtensionEmulator.notifyWorldJoin();
            schedule("world joined", 400);
         }

         Object object3 = getFieldCached(object, "options", "net.minecraft.client.Options");
         String string = renderSignature(object3);
         if (lastRenderSignature != null && !Objects.equals(lastRenderSignature, string)) {
            schedule("render options changed: " + lastRenderSignature + " -> " + string, 15);
         }

         lastRenderSignature = string;
         boolean flag = false;
         Iterator iterator = REBUILD_TICKS.iterator();

         while(iterator.hasNext()) {
            long l = (Long)iterator.next();
            if (l <= clientTick) {
               if (settleReady()) {
                  iterator.remove();
                  if (!flag) {
                     rebuildAllChunks(object);
                     flag = true;
                  }
               } else if (clientTick - l > 1200L) {
                  iterator.remove();
                  System.out.println("[Vulkan Extension Emulator] Skipped delayed terrain rebuild (world never settled in 1200 ticks).");
               }
            }
         }
      } catch (Throwable var10) {
         if (!reflectionFailureLogged) {
            reflectionFailureLogged = true;
            System.err.println("[Vulkan Extension Emulator] Could not run terrain rebuild workaround: " + String.valueOf(var10));
            var10.printStackTrace(System.err);
         }
      }
   }

   private static boolean settleReady() {
      if (settleQuietTicks < VulkanExtensionEmulator.config().terrainRebuildSettleTicks) {
         return false;
      } else {
         return chunkCountDelta < 0 || chunkCountDelta < 32;
      }
   }

   private static int loadedChunkCount(Object minecraft) {
      try {
         Object object = getFieldCached(minecraft, "level", "net.minecraft.client.multiplayer.ClientLevel");
         Object object2 = invokeCached(object, "getChunkSource");
         Object object3 = invokeCached(object2, "getLoadedChunksCount");
         return object3 instanceof Integer ? (Integer)object3 : 0;
      } catch (Throwable var4) {
         return 0;
      }
   }

   private static void schedule(String string, int... aint) {
      if (VulkanExtensionEmulator.terrainRebuildActive()) {
         if (VulkanExtensionEmulator.config().rebuildGateEmulationOnly && !VulkanExtensionEmulator.isEmulating()) {
            if (!gateLogged) {
               gateLogged = true;
               System.out.println("[Vulkan Extension Emulator] Terrain rebuild workaround skipped: device is not emulating (native rendering active).");
            }
         } else {
            int[] var2 = aint;
            int var3 = aint.length;

            for(int var4 = 0; var4 < var3; ++var4) {
               int i = var2[var4];
               long l = clientTick + (long)i;
               if (!REBUILD_TICKS.contains(l)) {
                  REBUILD_TICKS.add(l);
               }
            }

            System.out.println("[Vulkan Extension Emulator] Scheduled delayed terrain rebuilds for " + string + ".");
         }
      }
   }

   private static void rebuildAllChunks(Object minecraft) throws Exception {
      if (clientTick - lastRebuildTick >= 100L) {
         lastRebuildTick = clientTick;
         Object object2 = getFieldCached(minecraft, "levelRenderer", "net.minecraft.client.renderer.LevelRenderer");
         if (object2 != null) {
            Class oclass1 = Class.forName("net.minecraft.client.multiplayer.ClientLevel");
            Class oclass2 = Class.forName("net.minecraft.client.Options");
            Class oclass3 = Class.forName("net.minecraft.client.Camera");
            Class oclass4 = Class.forName("net.minecraft.client.color.block.BlockColors");
            Method method = findMethodCached(object2.getClass(), "invalidateCompiledGeometry", oclass1, oclass2, oclass3, oclass4);
            if (method != null) {
               Object level = getFieldCached(minecraft, "level", "net.minecraft.client.multiplayer.ClientLevel");
               Object options = getFieldCached(minecraft, "options", "net.minecraft.client.Options");
               Object gameRenderer = getFieldCached(minecraft, "gameRenderer", "net.minecraft.client.renderer.GameRenderer");
               Object camera = invokeCached(gameRenderer, "mainCamera");
               Object blockColors = invokeCached(minecraft, "getBlockColors");
               method.setAccessible(true);
               method.invoke(object2, new Object[]{level, options, camera, blockColors});
               System.out.println("[Vulkan Extension Emulator] Rebuilt terrain (invalidateCompiledGeometry) at client tick " + clientTick + ".");
            } else {
               Method method2 = findMethodCached(object2.getClass(), "allChanged");
               if (method2 != null) {
                  method2.setAccessible(true);
                  method2.invoke(object2, new Object[0]);
                  System.out.println("[Vulkan Extension Emulator] Rebuilt terrain (allChanged) at client tick " + clientTick + ".");
               } else {
                  throw new NoSuchMethodException(object2.getClass().getName() + ".invalidateCompiledGeometry()");
               }
            }
         }
      }
   }

   private static String renderSignature(Object object) {
      if (object == null) {
         return "options=<null>";
      } else {
         Object object2 = readOptionField(object, "biomeBlendRadius");
         Object object3 = readOptionMethod(object, "graphicsPreset");
         Object object4 = readOptionMethod(object, "cutoutLeaves");
         Object object5 = readOptionMethod(object, "improvedTransparency");
         Object object6 = readOptionMethod(object, "ambientOcclusion");
         String var10000 = String.valueOf(valueOfOption(object2));
         return "blend=" + var10000 + ",preset=" + String.valueOf(valueOfOption(object3)) + ",cutoutLeaves=" + String.valueOf(valueOfOption(object4)) + ",transparency=" + String.valueOf(valueOfOption(object5)) + ",ao=" + String.valueOf(valueOfOption(object6));
      }
   }

   private static Object valueOfOption(Object object) {
      if (object == null) {
         return "?";
      } else {
         try {
            Method method = findMethodCached(object.getClass(), "get");
            if (method == null) {
               return object;
            } else {
               method.setAccessible(true);
               Object object2 = method.invoke(object, new Object[0]);
               return object2 == null ? "null" : object2.toString();
            }
         } catch (Throwable var3) {
            return object.toString();
         }
      }
   }

   private static Object readOptionField(Object object, String string) {
      try {
         Field field = findFieldCached(object.getClass(), string, (String)null);
         if (field == null) {
            return null;
         } else {
            field.setAccessible(true);
            return field.get(object);
         }
      } catch (Throwable var3) {
         return null;
      }
   }

   private static Object readOptionMethod(Object object, String string) {
      try {
         Method method = findMethodCached(object.getClass(), string);
         if (method == null) {
            return null;
         } else {
            method.setAccessible(true);
            return method.invoke(object, new Object[0]);
         }
      } catch (Throwable var3) {
         return null;
      }
   }

   private static Object getFieldCached(Object object, String string, String string2) throws Exception {
      if (object == null) {
         return null;
      } else {
         Field field = findFieldCached(object.getClass(), string, string2);
         if (field == null) {
            String var10002 = object.getClass().getName();
            throw new NoSuchFieldException(var10002 + "." + string);
         } else {
            return field.get(object);
         }
      }
   }

   private static Object invokeCached(Object object, String string) throws Exception {
      if (object == null) {
         return null;
      } else {
         Method method = findMethodCached(object.getClass(), string);
         return method == null ? null : method.invoke(object, new Object[0]);
      }
   }

   private static Field findFieldCached(Class clazz, String string, String string2) {
      Map map = (Map)FIELD_CACHE.computeIfAbsent(clazz, (k) -> {
         return new ConcurrentHashMap();
      });
      String key = string + ":" + string2;
      Field field = (Field)map.get(key);
      if (field == null) {
         field = findField(clazz, string, string2);
         if (field != null) {
            map.put(key, field);
         }
      }

      return field;
   }

   private static Method findMethodCached(Class clazz, String string, Class... aclass) {
      Map map = (Map)METHOD_CACHE.computeIfAbsent(clazz, (k) -> {
         return new ConcurrentHashMap();
      });
      String key = string + ":" + Arrays.toString(aclass);
      Method method = (Method)map.get(key);
      if (method == null) {
         method = aclass.length == 0 ? findNoArgMethod(clazz, string) : findMethod(clazz, string, aclass);
         if (method != null) {
            map.put(key, method);
         }
      }

      return method;
   }

   private static Field findField(Class clazz, String string, String string2) {
      Class oclass = clazz;

      while(oclass != null) {
         try {
            return oclass.getDeclaredField(string);
         } catch (NoSuchFieldException var9) {
            if (string2 != null) {
               Field[] var5 = oclass.getDeclaredFields();
               int var6 = var5.length;

               for(int var7 = 0; var7 < var6; ++var7) {
                  Field field = var5[var7];
                  if (field.getType().getName().equals(string2)) {
                     return field;
                  }
               }
            }

            oclass = oclass.getSuperclass();
         }
      }

      return null;
   }

   private static Method findMethod(Class clazz, String string, Class... aclass) {
      Class oclass = clazz;

      while(oclass != null) {
         try {
            return oclass.getDeclaredMethod(string, aclass);
         } catch (NoSuchMethodException var5) {
            oclass = oclass.getSuperclass();
         }
      }

      return null;
   }

   private static Method findNoArgMethod(Class clazz, String string) {
      Class oclass = clazz;

      while(oclass != null) {
         try {
            return oclass.getDeclaredMethod(string, new Class[0]);
         } catch (NoSuchMethodException var4) {
            oclass = oclass.getSuperclass();
         }
      }

      return null;
   }
}