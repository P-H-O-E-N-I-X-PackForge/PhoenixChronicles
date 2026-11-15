package net.phoenixvine.chronicles.common.filter;

import net.minecraft.nbt.*;

public final class NbtMatchUtil {

    private NbtMatchUtil() {}

    private static final java.util.Set<String> UNCHECKED_KEYS = java.util.Set.of("GT.Tool", "GT.Behaviours", "Damage");

    public static boolean matches(CompoundTag actual, CompoundTag filter) {
        if (filter == null || filter.isEmpty()) return true;
        if (actual == null) return false;

        return matchesCompound(actual, filter);
    }

    public static boolean matchesCompound(CompoundTag actual, CompoundTag filter) {
        if (filter == null || filter.isEmpty()) return true;
        if (actual == null) return false;

        for (String key : filter.getAllKeys()) {
            if (UNCHECKED_KEYS.contains(key)) continue;
            Tag filterValue = filter.get(key);
            if (!actual.contains(key)) {

                if (filterValue instanceof CompoundTag c && c.isEmpty()) continue;
                if (filterValue instanceof ListTag l && l.isEmpty()) continue;
                return false;
            }
            Tag actualValue = actual.get(key);
            if (!matchesTag(actualValue, filterValue)) {
                return false;
            }
        }
        return true;
    }

    public static boolean matchesTag(Tag actual, Tag filter) {
        if (filter == null) return true;
        if (actual == null) return false;

        if (filter instanceof CompoundTag filterCompound) {
            if (actual instanceof CompoundTag actualCompound) {
                return matchesCompound(actualCompound, filterCompound);
            }
            return false;
        }

        if (filter instanceof NumericTag filterNum) {
            if (actual instanceof NumericTag actualNum) {
                return matchesNumber(actualNum, filterNum);
            }
            return false;
        }

        if (filter instanceof ListTag filterList) {
            if (actual instanceof ListTag actualList) {
                return matchesList(actualList, filterList);
            }
            return false;
        }

        if (filter instanceof StringTag filterStr && actual instanceof StringTag actualStr) {
            return actualStr.getAsString().equals(filterStr.getAsString());
        }

        return actual.equals(filter);
    }

    private static boolean matchesNumber(NumericTag actual, NumericTag filter) {
        if (filter instanceof FloatTag || filter instanceof DoubleTag || actual instanceof FloatTag ||
                actual instanceof DoubleTag) {
            return Math.abs(actual.getAsDouble() - filter.getAsDouble()) < 1e-6;
        }
        return actual.getAsLong() == filter.getAsLong();
    }

    private static boolean matchesList(ListTag actual, ListTag filter) {
        if (filter.isEmpty()) return true;
        if (actual.isEmpty()) return false;

        for (Tag filterItem : filter) {
            boolean foundMatch = false;
            for (Tag actualItem : actual) {
                if (matchesTag(actualItem, filterItem)) {
                    foundMatch = true;
                    break;
                }
            }
            if (!foundMatch) return false;
        }
        return true;
    }

    public static String firstMismatch(Tag actual, Tag filter) {
        return mismatch(actual, filter, "");
    }

    private static String mismatch(Tag actual, Tag filter, String path) {
        String at = path.isEmpty() ? "(root)" : path;
        if (filter == null) return null;
        if (actual == null) return at + ": stack has no tag, filter wants " + filter;
        if (filter instanceof CompoundTag fc) {
            if (!(actual instanceof CompoundTag ac)) return at + ": stack has " + actual + ", filter wants a compound";
            for (String key : fc.getAllKeys()) {
                if (UNCHECKED_KEYS.contains(key)) continue;
                Tag fv = fc.get(key);
                if (!ac.contains(key)) {
                    if (fv instanceof CompoundTag c && c.isEmpty()) continue;
                    if (fv instanceof ListTag l && l.isEmpty()) continue;
                    return at + "/" + key + ": missing on the stack, filter wants " + fv;
                }
                String inner = mismatch(ac.get(key), fv, path.isEmpty() ? key : path + "/" + key);
                if (inner != null) return inner;
            }
            return null;
        }
        return matchesTag(actual, filter) ? null : at + ": stack has " + actual + ", filter wants " + filter;
    }
}
