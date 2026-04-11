package com.arcadia.dungeon.util;

import com.arcadia.core.profiling.ProfilerUtil;

public final class SparkUtil {
    private static final String PROFILER_NAMESPACE = "arcadia_dungeon";

    private SparkUtil() {}

    public static boolean startSection(String section) {
        if (!ModCompat.HAS_SPARK) return false;
        return ProfilerUtil.startSection(PROFILER_NAMESPACE, normalizeSection(section));
    }

    public static void endSection() {
        endSection(true);
    }

    public static void endSection(boolean started) {
        if (!ModCompat.HAS_SPARK) return;
        ProfilerUtil.endSection(started);
    }

    private static String normalizeSection(String section) {
        if (section == null || section.isBlank()) return "unknown";
        String prefix = PROFILER_NAMESPACE + ".";
        return section.startsWith(prefix) ? section.substring(prefix.length()) : section;
    }
}
