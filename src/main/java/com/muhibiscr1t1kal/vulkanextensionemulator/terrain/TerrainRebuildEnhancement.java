package com.muhibiscr1t1kal.vulkanextensionemulator.terrain;

import com.muhibiscr1t1kal.vulkanextensionemulator.VulkanExtensionEmulator;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Proxy;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import net.fabricmc.api.ClientModInitializer;

public final class TerrainRebuildEnhancement implements ClientModInitializer {
   private static final String CLIENT_TICK_EVENTS = "net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents";
   private static final List<Long> REBUILD_TICKS = new ArrayList<>();
   private static long clientTick;
   private static Object lastLevel;
   private static String lastRenderSignature;
   private static boolean reflectionFailureLogged;
   private static boolean gateLogged;

   @Override
   public void onInitializeClient() {
      try {
         registerEndClientTick();
         System.out.println("[Vulkan Extension Emulator] Delayed terrain rebuild workaround active.");
      } catch (Throwable exception) {
         System.err.println("[Vulkan Extension Emulator] Terrain rebuild workaround disabled: " + String.valueOf(exception));
         exception.printStackTrace(System.err);
      }
   }

   private static void registerEndClientTick() throws Exception {
      Class<?> eventClass = Class.forName(CLIENT_TICK_EVENTS);
      Field field = eventClass.getField("END_CLIENT_TICK");
      Object event = field.get(null);

      if (event == null) {
         throw new IllegalStateException("ClientTickEvents.END_CLIENT_TICK is null");
      }

      Class<?> listenerClass = resolveListenerType(field, eventClass);

      InvocationHandler handler = (proxy, method, arguments) -> {
         if (method.getDeclaringClass() == Object.class) {
            return handleObjectMethod(proxy, method, arguments);
         }

         if (arguments != null && arguments.length == 1) {
            onEndClientTick(arguments[0]);
         }

         return null;
      };

      Object listener = Proxy.newProxyInstance(
         listenerClass.getClassLoader(),
         new Class<?>[]{listenerClass},
         handler
      );

      Method registerMethod = findSingleArgumentMethod(event.getClass(), "register");

      if (registerMethod == null) {
         throw new NoSuchMethodException(event.getClass().getName() + ".register(listener)");
      }

      registerMethod.setAccessible(true);
      registerMethod.invoke(event, listener);
   }

   private static Class<?> resolveListenerType(Field field, Class<?> eventClass)
      throws ClassNotFoundException {
      Type type = field.getGenericType();

      if (type instanceof ParameterizedType parameterizedType) {
         Type[] arguments = parameterizedType.getActualTypeArguments();

         if (arguments.length == 1) {
            Type listenerType = arguments[0];

            if (listenerType instanceof Class<?> listenerClass
               && listenerClass.isInterface()) {
               return listenerClass;
            }

            if (listenerType instanceof ParameterizedType parameterizedListener) {
               Type rawType = parameterizedListener.getRawType();

               if (rawType instanceof Class<?> listenerClass
                  && listenerClass.isInterface()) {
                  return listenerClass;
               }
            }
         }
      }

      for (Class<?> nestedClass : eventClass.getDeclaredClasses()) {
         if (nestedClass.isInterface()) {
            for (Method method : nestedClass.getMethods()) {
               if (method.getParameterCount() == 1
                  && (method.getName().equals("onEndTick")
                  || nestedClass.getSimpleName().toLowerCase().contains("endtick"))) {
                  return nestedClass;
               }
            }
         }
      }

      String[] candidates = {"EndTick", "EndTickEvent"};

      for (String candidate : candidates) {
         try {
            Class<?> listenerClass = Class.forName(eventClass.getName() + "$" + candidate);

            if (listenerClass.isInterface()) {
               return listenerClass;
            }
         } catch (ClassNotFoundException ignored) {
         }
      }

      throw new ClassNotFoundException("Unable to resolve END_CLIENT_TICK listener type");
   }

   private static Object handleObjectMethod(
      Object proxy,
      Method method,
      Object[] arguments
   ) {
      return switch (method.getName()) {
         case "toString" -> "[Vulkan Extension Emulator] end-client-tick listener";
         case "hashCode" -> System.identityHashCode(proxy);
         case "equals" -> arguments != null
            && arguments.length == 1
            && proxy == arguments[0];
         default -> null;
      };
   }

   private static void onEndClientTick(Object minecraft) {
      ++clientTick;

      try {
         Object level = getFieldValue(
            minecraft,
            "level",
            "net.minecraft.client.multiplayer.ClientLevel"
         );

         if (level == null) {
            if (lastLevel != null) {
               System.out.println("[Vulkan Extension Emulator] World closed; clearing queued rebuilds.");
            }

            lastLevel = null;
            lastRenderSignature = null;
            REBUILD_TICKS.clear();
            return;
         }

         if (level != lastLevel) {
            lastLevel = level;
            lastRenderSignature = null;
            REBUILD_TICKS.clear();
            schedule("world joined", 24, 60);
         }

         Object options = getFieldValue(
            minecraft,
            "options",
            "net.minecraft.client.Options"
         );

         String signature = renderSignature(options);

         if (lastRenderSignature != null
            && !Objects.equals(lastRenderSignature, signature)) {
            schedule(
               "render options changed: " + lastRenderSignature + " -> " + signature,
               6,
               22
            );
         }

         lastRenderSignature = signature;

         boolean rebuilt = false;
         Iterator<Long> iterator = REBUILD_TICKS.iterator();

         while (iterator.hasNext()) {
            long scheduledTick = iterator.next();

            if (scheduledTick <= clientTick) {
               iterator.remove();

               if (!rebuilt) {
                  rebuildAllChunks(minecraft);
                  rebuilt = true;
               }
            }
         }
      } catch (Throwable exception) {
         if (!reflectionFailureLogged) {
            reflectionFailureLogged = true;
            System.err.println("[Vulkan Extension Emulator] Could not run terrain rebuild workaround: " + String.valueOf(exception));
            exception.printStackTrace(System.err);
         }
      }
   }

   private static void schedule(String reason, int... delays) {
      if (VulkanExtensionEmulator.config().rebuildGateEmulationOnly
         && !VulkanExtensionEmulator.isEmulating()) {
         if (!gateLogged) {
            gateLogged = true;
            System.out.println("[Vulkan Extension Emulator] Terrain rebuild workaround skipped: device is not emulating (native rendering active).");
         }

         return;
      }

      for (int delay : delays) {
         long scheduledTick = clientTick + delay;

         if (!REBUILD_TICKS.contains(scheduledTick)) {
            REBUILD_TICKS.add(scheduledTick);
         }
      }

      System.out.println("[Vulkan Extension Emulator] Scheduled delayed terrain rebuilds for " + reason + ".");
   }

   private static void rebuildAllChunks(Object minecraft) throws Exception {
      Object levelRenderer = getFieldValue(
         minecraft,
         "levelRenderer",
         "net.minecraft.client.renderer.LevelRenderer"
      );

      if (levelRenderer == null) {
         return;
      }

      Class<?> levelClass = Class.forName("net.minecraft.client.multiplayer.ClientLevel");
      Class<?> optionsClass = Class.forName("net.minecraft.client.Options");
      Class<?> cameraClass = Class.forName("net.minecraft.client.Camera");
      Class<?> blockColorsClass = Class.forName("net.minecraft.client.color.block.BlockColors");

      Method invalidateMethod = findMethod(
         levelRenderer.getClass(),
         "invalidateCompiledGeometry",
         levelClass,
         optionsClass,
         cameraClass,
         blockColorsClass
      );

      if (invalidateMethod != null) {
         Object level = getFieldValue(
            minecraft,
            "level",
            "net.minecraft.client.multiplayer.ClientLevel"
         );
         Object options = getFieldValue(
            minecraft,
            "options",
            "net.minecraft.client.Options"
         );
         Object gameRenderer = getFieldValue(
            minecraft,
            "gameRenderer",
            "net.minecraft.client.renderer.GameRenderer"
         );
         Object camera = invokeNoArg(gameRenderer, "mainCamera");
         Object blockColors = invokeNoArg(minecraft, "getBlockColors");

         invalidateMethod.setAccessible(true);
         invalidateMethod.invoke(
            levelRenderer,
            level,
            options,
            camera,
            blockColors
         );

         System.out.println("[Vulkan Extension Emulator] Rebuilt terrain (invalidateCompiledGeometry) at client tick " + clientTick + ".");
      } else {
         Method allChangedMethod = findNoArgMethod(levelRenderer.getClass(), "allChanged");

         if (allChangedMethod != null) {
            allChangedMethod.setAccessible(true);
            allChangedMethod.invoke(levelRenderer);

            System.out.println("[Vulkan Extension Emulator] Rebuilt terrain (allChanged) at client tick " + clientTick + ".");
         } else {
            throw new NoSuchMethodException(
               levelRenderer.getClass().getName() + ".invalidateCompiledGeometry()"
            );
         }
      }
   }

   private static Method findMethod(
      Class<?> clazz,
      String name,
      Class<?>... parameterTypes
   ) {
      Class<?> current = clazz;

      while (current != null) {
         try {
            return current.getDeclaredMethod(name, parameterTypes);
         } catch (NoSuchMethodException ignored) {
            current = current.getSuperclass();
         }
      }

      return null;
   }

   private static Object invokeNoArg(Object object, String name) throws Exception {
      if (object == null) {
         return null;
      }

      Method method = findNoArgMethod(object.getClass(), name);

      if (method == null) {
         return null;
      }

      method.setAccessible(true);
      return method.invoke(object);
   }

   private static String renderSignature(Object options) {
      if (options == null) {
         return "options=<null>";
      }

      Object blendRadius = readOptionField(options, "biomeBlendRadius");
      Object graphicsPreset = readOptionMethod(options, "graphicsPreset");
      Object cutoutLeaves = readOptionMethod(options, "cutoutLeaves");
      Object improvedTransparency = readOptionMethod(options, "improvedTransparency");
      Object ambientOcclusion = readOptionMethod(options, "ambientOcclusion");

      return "blend=" + valueOfOption(blendRadius)
         + ",preset=" + valueOfOption(graphicsPreset)
         + ",cutoutLeaves=" + valueOfOption(cutoutLeaves)
         + ",transparency=" + valueOfOption(improvedTransparency)
         + ",ao=" + valueOfOption(ambientOcclusion);
   }

   private static Object valueOfOption(Object option) {
      if (option == null) {
         return "?";
      }

      try {
         Method getMethod = findNoArgMethod(option.getClass(), "get");

         if (getMethod == null) {
            return option;
         }

         getMethod.setAccessible(true);
         Object value = getMethod.invoke(option);
         return value == null ? "null" : value.toString();
      } catch (Throwable ignored) {
         return option.toString();
      }
   }

   private static Object readOptionField(Object object, String name) {
      try {
         Field field = findField(object.getClass(), name, null);

         if (field == null) {
            return null;
         }

         field.setAccessible(true);
         return field.get(object);
      } catch (Throwable ignored) {
         return null;
      }
   }

   private static Object readOptionMethod(Object object, String name) {
      try {
         Method method = findNoArgMethod(object.getClass(), name);

         if (method == null) {
            return null;
         }

         method.setAccessible(true);
         return method.invoke(object);
      } catch (Throwable ignored) {
         return null;
      }
   }

   private static Object getFieldValue(
      Object object,
      String name,
      String expectedType
   ) throws Exception {
      if (object == null) {
         return null;
      }

      Field field = findField(object.getClass(), name, expectedType);

      if (field == null) {
         throw new NoSuchFieldException(object.getClass().getName() + "." + name);
      }

      field.setAccessible(true);
      return field.get(object);
   }

   private static Field findField(
      Class<?> clazz,
      String name,
      String expectedType
   ) {
      Class<?> current = clazz;

      while (current != null) {
         try {
            return current.getDeclaredField(name);
         } catch (NoSuchFieldException ignored) {
            if (expectedType != null) {
               for (Field field : current.getDeclaredFields()) {
                  if (field.getType().getName().equals(expectedType)) {
                     return field;
                  }
               }
            }

            current = current.getSuperclass();
         }
      }

      return null;
   }

   private static Method findNoArgMethod(Class<?> clazz, String name) {
      Class<?> current = clazz;

      while (current != null) {
         try {
            return current.getDeclaredMethod(name);
         } catch (NoSuchMethodException ignored) {
            current = current.getSuperclass();
         }
      }

      return null;
   }

   private static Method findSingleArgumentMethod(Class<?> clazz, String name) {
      for (Method method : clazz.getMethods()) {
         if (method.getName().equals(name) && method.getParameterCount() == 1) {
            return method;
         }
      }

      for (Class<?> current = clazz; current != null; current = current.getSuperclass()) {
         for (Method method : current.getDeclaredMethods()) {
            if (method.getName().equals(name) && method.getParameterCount() == 1) {
               return method;
            }
         }
      }

      return null;
   }
}