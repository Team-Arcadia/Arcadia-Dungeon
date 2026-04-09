package com.vyrriox.arcadiadungeon.util;

import com.mojang.logging.LogUtils;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;

public final class SparkUtil {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static MinecraftServer server = null;

    private SparkUtil() {}

    public static void setServer(MinecraftServer s) {
        server = s;
    }

    public static void clearServer() {
        server = null;
    }

    public static boolean startSection(String name) {
        if (!ModCompat.HAS_SPARK || server == null) return false;
        try {
            server.getProfiler().push(name);
            return true;
        } catch (Exception e) {
            LOGGER.error("SparkUtil: error starting section '{}'", name, e);
            return false;
        }
    }

    public static void endSection() {
        if (!ModCompat.HAS_SPARK || server == null) return;
        try {
            server.getProfiler().pop();
        } catch (Exception e) {
            LOGGER.error("SparkUtil: error ending section", e);
        }
    }
}
