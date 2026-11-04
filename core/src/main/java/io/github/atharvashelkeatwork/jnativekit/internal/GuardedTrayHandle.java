package io.github.atharvashelkeatwork.jnativekit.internal;

import io.github.atharvashelkeatwork.jnativekit.TrayHandle;

/**
 * Wraps a provider tray handle so its methods never throw.
 *
 * <p>Package-private implementation detail; created only by {@link GuardedTray}.
 */
final class GuardedTrayHandle implements TrayHandle {

    private static final System.Logger LOG = System.getLogger(GuardedTrayHandle.class.getName());

    private final String providerId;
    private final TrayHandle delegate;

    GuardedTrayHandle(String providerId, TrayHandle delegate) {
        this.providerId = providerId;
        this.delegate = delegate;
    }

    @Override
    public boolean isOpen() {
        try {
            return delegate.isOpen();
        } catch (RuntimeException e) {
            logFailed("isOpen", e);
            return false;
        } catch (Error e) {
            if (Errors.isFatal(e)) {
                throw e;
            }
            logFailed("isOpen", e);
            return false;
        }
    }

    @Override
    public void close() {
        try {
            delegate.close();
        } catch (RuntimeException e) {
            logFailed("close", e);
        } catch (Error e) {
            if (Errors.isFatal(e)) {
                throw e;
            }
            logFailed("close", e);
        }
    }

    private void logFailed(String action, Throwable failure) {
        LOG.log(System.Logger.Level.WARNING,
                "Tray handle from provider " + providerId + " threw during " + action
                        + "(); the failure is contained",
                failure);
    }
}
