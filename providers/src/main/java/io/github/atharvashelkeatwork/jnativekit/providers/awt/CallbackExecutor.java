package io.github.atharvashelkeatwork.jnativekit.providers.awt;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * The process-wide executor for tray menu callbacks.
 *
 * <p>Internal to the {@code providers} module. It owns one daemon thread named
 * {@code jnativekit-callbacks}, created lazily on the first callback. Because the thread is a
 * daemon it never keeps the JVM alive, and because there is exactly one it serializes callbacks
 * across all open tray icons.
 */
final class CallbackExecutor {

    /** The exact thread name promised by {@code docs/API.md}. */
    static final String THREAD_NAME = "jnativekit-callbacks";

    private CallbackExecutor() {
    }

    /**
     * Returns the shared callback executor.
     *
     * @return the process-wide executor; never {@code null}
     */
    static Executor shared() {
        return Holder.INSTANCE;
    }

    /**
     * Holds the executor so the thread is only created when a callback is first submitted.
     */
    private static final class Holder {

        private static final Executor INSTANCE = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, THREAD_NAME);
            thread.setDaemon(true);
            return thread;
        });
    }
}
