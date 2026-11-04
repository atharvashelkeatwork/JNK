package io.github.atharvashelkeatwork.jnativekit;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.ToIntFunction;

import io.github.atharvashelkeatwork.jnativekit.internal.Errors;
import io.github.atharvashelkeatwork.jnativekit.internal.GuardedAutostart;
import io.github.atharvashelkeatwork.jnativekit.internal.GuardedNotifications;
import io.github.atharvashelkeatwork.jnativekit.internal.GuardedTray;
import io.github.atharvashelkeatwork.jnativekit.internal.PlatformDetector;
import io.github.atharvashelkeatwork.jnativekit.internal.ProviderResolver;
import io.github.atharvashelkeatwork.jnativekit.internal.Unavailable;
import io.github.atharvashelkeatwork.jnativekit.spi.AutostartProvider;
import io.github.atharvashelkeatwork.jnativekit.spi.NotificationsProvider;
import io.github.atharvashelkeatwork.jnativekit.spi.TrayProvider;

/**
 * Entry point to the three desktop features.
 *
 * <p>Each accessor returns a process-wide singleton, resolved lazily and thread-safely from the
 * providers found by {@link java.util.ServiceLoader}. The accessors never return {@code null} and
 * never throw: when no provider matches, they return a facade whose capability is
 * {@link Capability.Status#UNSUPPORTED_OS} and whose actions return
 * {@link Result.Status#UNAVAILABLE}. A non-fatal failure while detecting the platform or resolving
 * providers is logged and also falls back to that facade.
 *
 * <p>Provider selection happens once per feature; {@link Feature#capability()} is re-evaluated on
 * every call, so the reported state can change while the process runs.
 */
public final class Desktop {

    private static final System.Logger LOG = System.getLogger(Desktop.class.getName());

    private static final Object LOCK = new Object();

    private static volatile Platform platform;
    private static volatile Notifications notifications;
    private static volatile Autostart autostart;
    private static volatile Tray tray;

    /** Test-only override; {@code null} means "detect from the operating system". */
    private static volatile Platform platformOverride;
    /** Test-only provider source; only used when {@link #providersInjected} is true. */
    private static volatile List<?> providerOverride;
    private static volatile boolean providersInjected;

    private Desktop() {
    }

    /**
     * Returns the notifications feature for this process.
     *
     * @return the notifications singleton; never {@code null}
     */
    public static Notifications notifications() {
        Notifications local = notifications;
        if (local == null) {
            synchronized (LOCK) {
                local = notifications;
                if (local == null) {
                    local = selectNotifications(platform());
                    notifications = local;
                }
            }
        }
        return local;
    }

    /**
     * Returns the autostart feature for this process.
     *
     * @return the autostart singleton; never {@code null}
     */
    public static Autostart autostart() {
        Autostart local = autostart;
        if (local == null) {
            synchronized (LOCK) {
                local = autostart;
                if (local == null) {
                    local = selectAutostart(platform());
                    autostart = local;
                }
            }
        }
        return local;
    }

    /**
     * Returns the tray feature for this process.
     *
     * @return the tray singleton; never {@code null}
     */
    public static Tray tray() {
        Tray local = tray;
        if (local == null) {
            synchronized (LOCK) {
                local = tray;
                if (local == null) {
                    local = selectTray(platform());
                    tray = local;
                }
            }
        }
        return local;
    }

    /**
     * Returns the platform of the running JVM.
     *
     * @return the detected platform; never {@code null}
     */
    public static Platform platform() {
        Platform local = platform;
        if (local == null) {
            synchronized (LOCK) {
                local = platform;
                if (local == null) {
                    Platform override = platformOverride;
                    local = override != null ? override : detectPlatform();
                    platform = local;
                }
            }
        }
        return local;
    }

    /**
     * Replaces the platform and provider source for the current test, then forgets every singleton
     * so the next accessor call resolves again.
     *
     * <p>Package-private on purpose: it is a test hook, not public API.
     *
     * @param platform  the platform to report, or {@code null} to detect from the OS
     * @param providers the providers to consider, or {@code null} to use {@link java.util.ServiceLoader}
     */
    static void resetForTests(Platform platform, List<?> providers) {
        synchronized (LOCK) {
            platformOverride = platform;
            providersInjected = providers != null;
            providerOverride = providers == null ? null : List.copyOf(Objects.requireNonNull(providers, "providers"));
            Desktop.platform = null;
            notifications = null;
            autostart = null;
            tray = null;
        }
    }

    /**
     * Restores production behaviour: detect the platform and load providers with
     * {@link java.util.ServiceLoader}.
     *
     * <p>Package-private on purpose: it is a test hook, not public API.
     */
    static void resetForTests() {
        resetForTests(null, null);
    }

    private static Notifications selectNotifications(Platform current) {
        return resolve(
                current,
                NotificationsProvider.class,
                NotificationsProvider::supports,
                NotificationsProvider::id,
                NotificationsProvider::priority,
                GuardedNotifications::new,
                Unavailable::notifications,
                "notifications");
    }

    private static Autostart selectAutostart(Platform current) {
        return resolve(
                current,
                AutostartProvider.class,
                AutostartProvider::supports,
                AutostartProvider::id,
                AutostartProvider::priority,
                GuardedAutostart::new,
                Unavailable::autostart,
                "autostart");
    }

    private static Tray selectTray(Platform current) {
        return resolve(
                current,
                TrayProvider.class,
                TrayProvider::supports,
                TrayProvider::id,
                TrayProvider::priority,
                GuardedTray::new,
                Unavailable::tray,
                "tray");
    }

    private static <P, F> F resolve(
            Platform current,
            Class<P> type,
            BiPredicate<P, Platform> supports,
            Function<P, String> id,
            ToIntFunction<P> priority,
            Function<P, F> guard,
            Function<Platform, F> unavailable,
            String feature) {
        try {
            Optional<P> selected = ProviderResolver.select(
                    current,
                    candidates(type),
                    supports,
                    id,
                    priority);
            return selected.map(guard).orElseGet(() -> unavailable.apply(current));
        } catch (RuntimeException e) {
            return selectionFailure(feature, current, unavailable, e);
        } catch (Error e) {
            if (Errors.isFatal(e)) {
                throw e;
            }
            return selectionFailure(feature, current, unavailable, e);
        }
    }

    private static <F> F selectionFailure(
            String feature,
            Platform current,
            Function<Platform, F> unavailable,
            Throwable failure) {
        LOG.log(System.Logger.Level.WARNING,
                "Could not resolve the " + feature + " provider; using the unavailable facade",
                failure);
        return unavailable.apply(current);
    }

    private static Platform detectPlatform() {
        try {
            return PlatformDetector.detect();
        } catch (RuntimeException e) {
            return platformFailure(e);
        } catch (Error e) {
            if (Errors.isFatal(e)) {
                throw e;
            }
            return platformFailure(e);
        }
    }

    private static Platform platformFailure(Throwable failure) {
        LOG.log(System.Logger.Level.WARNING,
                "Platform detection failed; reporting OTHER",
                failure);
        return Platform.OTHER;
    }

    private static <P> List<P> candidates(Class<P> type) {
        if (providersInjected) {
            List<?> source = providerOverride;
            if (source == null) {
                return List.of();
            }
            List<P> matching = new ArrayList<>();
            for (Object provider : source) {
                if (type.isInstance(provider)) {
                    matching.add(type.cast(provider));
                }
            }
            return matching;
        }
        return ProviderResolver.load(type);
    }
}
