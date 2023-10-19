package com.android.tools.build.bundletool.model.utils;


import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;

public class MapUtils {

  public static int optInt(@Nullable Object map, int defaultValue, String... path) {
    Object obj = getObject(map, path);
    if (obj instanceof Number) {
      return ((Number) obj).intValue();
    } else {
      return defaultValue;
    }
  }

  public static float optFloat(@Nullable Object map, float defaultValue, String... path) {
    Object obj = getObject(map, path);
    if (obj instanceof Number) {
      return ((Number) obj).floatValue();
    } else {
      return defaultValue;
    }
  }

  public static boolean optBoolean(@Nullable Object map, boolean defaultValue, String... path) {
    Object obj = getObject(map, path);
    if (obj instanceof Boolean) {
      return (boolean) obj;
    } else {
      return defaultValue;
    }
  }

  public static String optString(@Nullable Object map, String defaultValue, String... path) {
    Object obj = getObject(map, path);
    if (obj instanceof String) {
      return (String) obj;
    } else {
      return defaultValue;
    }
  }

  public static List<?> optList(@Nullable Object map, List<?> defaultValue, String... path) {
    Object obj = getObject(map, path);
    if (obj instanceof List) {
      return (List<?>) obj;
    } else {
      return defaultValue;
    }
  }

  public static Map<?, ?> optMap(@Nullable Object map, Map<String, ?> defaultValue, String... path) {
    Object obj = getObject(map, path);
    if (obj instanceof Map) {
      return (Map<?, ?>) obj;
    } else {
      return defaultValue;
    }
  }

  public static Object getObject(@Nullable Object map, String... path) {
    if (map == null || path == null || path.length == 0) {
      return null;
    }


    try {
      for (int i = 0; i < path.length - 1; i++) {
        Object object = ((Map<?, ?>) map).get(path[i]);
        if (object instanceof Map) {
          map = object;
          continue;
        }
        return null;
      }
      return ((Map<?, ?>) map).get(path[path.length - 1]);
    } catch (Throwable e) {
      return null;
    }
  }
}
