package pl.aridlin.psychiatrykroles.clientperf;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Caches discovery, including absent fields/methods; never caches packet values. */
public final class SnapshotReflectionCache {
    private SnapshotReflectionCache() {}
    private static final Object[] NO_ARGUMENTS = new Object[0];

    private static final ClassValue<ConcurrentHashMap<String, Optional<Field>>> FIELDS = new ClassValue<>() {
        @Override protected ConcurrentHashMap<String, Optional<Field>> computeValue(Class<?> type) {
            return new ConcurrentHashMap<>();
        }
    };

    public static Object readField(Object target, String name) {
        // Original cache helper requires a non-null target.
        Optional<Field> field = FIELDS.get(target.getClass()).computeIfAbsent(name,
            key -> discoverField(target.getClass(), key));
        if (field.isEmpty()) return null;
        try { return field.get().get(target); }
        catch (ReflectiveOperationException | RuntimeException ignored) { return null; }
    }

    private static Optional<Field> discoverField(Class<?> type, String name) {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try {
                Field field = current.getDeclaredField(name);
                field.setAccessible(true);
                return Optional.of(field);
            } catch (NoSuchFieldException ignored) {
                // The original searches superclasses only when a field is absent.
            } catch (RuntimeException ignored) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    private record EntityAccess(List<Method> recordMethods, List<Method> fallbackMethods) {}
    private static final ClassValue<EntityAccess> ENTITIES = new ClassValue<>() {
        @Override protected EntityAccess computeValue(Class<?> type) {
            List<Method> records = new ArrayList<>();
            if (type.isRecord()) {
                for (RecordComponent component : type.getRecordComponents()) {
                    String name = component.getName().toLowerCase(Locale.ROOT);
                    if (name.contains("entity") && name.contains("id")) records.add(component.getAccessor());
                }
            }
            List<Method> methods = new ArrayList<>();
            for (String name : List.of("entityId", "getEntityId")) {
                try { methods.add(type.getMethod(name)); }
                catch (ReflectiveOperationException ignored) {}
            }
            return new EntityAccess(List.copyOf(records), List.copyOf(methods));
        }
    };

    /** Exactly the original record-first, entityId/getEntityId-second precedence. */
    public static Integer findEntityId(Object target) {
        EntityAccess access = ENTITIES.get(target.getClass());
        for (Method method : access.recordMethods()) {
            try {
                Object value = method.invoke(target, NO_ARGUMENTS);
                if (value instanceof Number number) return number.intValue();
            } catch (ReflectiveOperationException ignored) {
                break; // Original stops the record scan after a failed invocation.
            }
        }
        for (Method method : access.fallbackMethods()) {
            try {
                Object value = method.invoke(target, NO_ARGUMENTS);
                if (value instanceof Number number) return number.intValue();
            } catch (ReflectiveOperationException ignored) {}
        }
        return null;
    }
}
