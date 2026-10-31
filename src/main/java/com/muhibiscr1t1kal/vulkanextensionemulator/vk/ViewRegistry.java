package com.muhibiscr1t1kal.vulkanextensionemulator.vk;

import java.util.HashMap;
import java.util.Map;

public final class ViewRegistry {

    private static final Map<Long, ViewInfo> VIEWS = new HashMap<>();

    private ViewRegistry() {
    }

    public static void register(
            long imageView,
            int format,
            int width,
            int height
    ) {
        if (imageView == 0L) {
            return;
        }

        VIEWS.put(
                imageView,
                new ViewInfo(format, width, height)
        );
    }

    public static void unregister(long imageView) {
        VIEWS.remove(imageView);
    }

    public static ViewInfo get(long imageView) {
        return VIEWS.get(imageView);
    }

    public static boolean contains(long imageView) {
        return VIEWS.containsKey(imageView);
    }

    public static void clear() {
        VIEWS.clear();
    }

    public static final class ViewInfo {
        public final int format;
        public final int width;
        public final int height;

        private ViewInfo(int format, int width, int height) {
            this.format = format;
            this.width = width;
            this.height = height;
        }
    }
}