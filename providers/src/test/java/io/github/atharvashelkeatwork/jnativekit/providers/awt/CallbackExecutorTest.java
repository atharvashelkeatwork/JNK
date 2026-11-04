package io.github.atharvashelkeatwork.jnativekit.providers.awt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

/**
 * Tier T2: the callback executor contract from {@code docs/API.md}.
 *
 * <p>It proves the thread name and daemon flag, not any tray behaviour.
 */
class CallbackExecutorTest {

    @Test
    void sharedExecutorUsesTheDocumentedDaemonThread() throws InterruptedException {
        CountDownLatch ran = new CountDownLatch(1);
        AtomicReference<Thread> thread = new AtomicReference<>();
        AtomicBoolean daemon = new AtomicBoolean();

        CallbackExecutor.shared().execute(() -> {
            thread.set(Thread.currentThread());
            daemon.set(Thread.currentThread().isDaemon());
            ran.countDown();
        });

        assertTrue(ran.await(5, TimeUnit.SECONDS), "the callback did not run in time");
        assertEquals("jnativekit-callbacks", thread.get().getName());
        assertTrue(daemon.get(), "the callback thread must be a daemon");
    }
}
