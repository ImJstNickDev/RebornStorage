package net.gigabit101.rebornstorage.crafting;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.MinecartItem;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.CapabilityProvider;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.eventbus.ASMEventHandler;
import net.minecraftforge.eventbus.EventBus;
import net.minecraftforge.eventbus.ListenerList;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventListener;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.versions.forge.ForgeVersion;

/**
 * Recognizes capability hooks that cannot add state or act on a fresh plain output on this Forge
 * version.
 */
final class StackCapabilityPolicy {
  private static final StackCapabilityPolicy INSTANCE = load();
  private final ListenerList listeners;
  private final int bus;
  private final Field filter, handler;
  private final MethodHandle dispatcher, lazyData;
  private Class<?> curiosHandler, curiosWrapper, curioItem;

  private static final class Inspection {
    final IEventListener[] listeners;
    final boolean plain;

    Inspection(IEventListener[] listeners, boolean plain) {
      this.listeners = listeners;
      this.plain = plain;
    }
  }

  private volatile Inspection latest;

  private StackCapabilityPolicy() throws ReflectiveOperationException {
    dispatcher = capField("capabilities");
    lazyData = capField("lazyData");
    listeners = new AttachCapabilitiesEvent<>(ItemStack.class, ItemStack.EMPTY).getListenerList();
    bus = field(EventBus.class, "busID").getInt(MinecraftForge.EVENT_BUS);
    filter = field(ASMEventHandler.class, "filter");
    handler = field(ASMEventHandler.class, "handler");
    if (ModList.get()
        .getModContainerById("curios")
        .map(mod -> mod.getModInfo().getVersion().toString())
        .map(version -> version.equals("1.16.5-4.0.8.2") || version.equals("1.16.5-4.0.8.0"))
        .orElse(false)) {
      curiosHandler = Class.forName("top.theillusivec4.curios.common.event.CuriosEventHandler");
      curioItem = Class.forName("top.theillusivec4.curios.api.type.capability.ICurioItem");
      Method method =
          curiosHandler.getDeclaredMethod("attachStackCapabilities", AttachCapabilitiesEvent.class);
      Map<?, ?> wrappers = (Map<?, ?>) field(ASMEventHandler.class, "cache").get(null);
      curiosWrapper = (Class<?>) wrappers.get(method);
    }
  }

  private static Field field(Class<?> type, String name) throws ReflectiveOperationException {
    Field field = type.getDeclaredField(name);
    field.setAccessible(true);
    return field;
  }

  private static MethodHandle capField(String name) throws ReflectiveOperationException {
    return MethodHandles.lookup()
        .unreflectGetter(field(CapabilityProvider.class, name))
        .asType(MethodType.methodType(Object.class, ItemStack.class));
  }

  // Read the two possible sources of serializeCaps() without initializing a lazy recipe template.
  static boolean emptyPayload(ItemStack stack) {
    try {
      return (Object) INSTANCE.dispatcher.invokeExact(stack) == null
          && (Object) INSTANCE.lazyData.invokeExact(stack) == null;
    } catch (RuntimeException | Error e) {
      throw e;
    } catch (Throwable e) {
      throw new IllegalStateException(e);
    }
  }

  private static StackCapabilityPolicy load() {
    if (!"36.2.26".equals(ForgeVersion.getVersion())
        || MinecraftForge.EVENT_BUS.getClass() != EventBus.class) return null;
    try {
      return new StackCapabilityPolicy();
    } catch (ReflectiveOperationException | RuntimeException | LinkageError unsupported) {
      return null;
    }
  }

  static boolean supports(Item item) {
    Class<?> type = item.getClass();
    return INSTANCE != null
        && (type == Item.class || type == BlockItem.class || type == MinecartItem.class)
        && (INSTANCE.curioItem == null || !INSTANCE.curioItem.isInstance(item));
  }

  static boolean hooksArePlain() {
    if (INSTANCE == null) return false;
    // EventBus 4 publishes a new dispatch array on registration or removal, including parent
    // listeners.
    IEventListener[] current = INSTANCE.listeners.getListeners(INSTANCE.bus);
    Inspection known = INSTANCE.latest;
    if (known == null || current != known.listeners) {
      known = new Inspection(current, INSTANCE.inspect(current));
      INSTANCE.latest = known;
    }
    return known.plain;
  }

  private boolean inspect(IEventListener[] current) {
    try {
      for (IEventListener listener : current) {
        if (listener instanceof EventPriority) continue;
        if (listener.getClass() != ASMEventHandler.class) return false;
        Object type = filter.get(listener);
        if (type != null && type != ItemStack.class)
          continue; // Exact generic dispatch in EventBus 4.
        Object wrapped = handler.get(listener);
        if (wrapped == null
            || wrapped.getClass() != curiosWrapper
            || curiosWrapper.getField("instance").get(wrapped).getClass() != curiosHandler)
          return false;
      }
      return true;
    } catch (ReflectiveOperationException | RuntimeException unsupported) {
      return false;
    }
  }
}
