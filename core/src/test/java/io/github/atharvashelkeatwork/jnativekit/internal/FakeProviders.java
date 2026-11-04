package io.github.atharvashelkeatwork.jnativekit.internal;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.atharvashelkeatwork.jnativekit.AutostartEntry;
import io.github.atharvashelkeatwork.jnativekit.Capability;
import io.github.atharvashelkeatwork.jnativekit.Notification;
import io.github.atharvashelkeatwork.jnativekit.Platform;
import io.github.atharvashelkeatwork.jnativekit.Reason;
import io.github.atharvashelkeatwork.jnativekit.Result;
import io.github.atharvashelkeatwork.jnativekit.TrayHandle;
import io.github.atharvashelkeatwork.jnativekit.TrayIcon;
import io.github.atharvashelkeatwork.jnativekit.spi.AutostartProvider;
import io.github.atharvashelkeatwork.jnativekit.spi.NotificationsProvider;
import io.github.atharvashelkeatwork.jnativekit.spi.TrayProvider;

/**
 * Configurable test providers used by the resolver, guard and {@code Desktop} tests.
 *
 * <p>Test helper only; it is not shipped.
 */
public final class FakeProviders {

    private FakeProviders() {
    }

    /**
     * Creates a notifications provider with the given id and priority.
     *
     * @param id       the provider id
     * @param priority the selection priority
     * @return a configurable stub
     */
    public static NotificationsStub notifications(String id, int priority) {
        return new NotificationsStub(id, priority);
    }

    /**
     * Creates an autostart provider with the given id and priority.
     *
     * @param id       the provider id
     * @param priority the selection priority
     * @return a configurable stub
     */
    public static AutostartStub autostart(String id, int priority) {
        return new AutostartStub(id, priority);
    }

    /**
     * Creates a tray provider with the given id and priority.
     *
     * @param id       the provider id
     * @param priority the selection priority
     * @return a configurable stub
     */
    public static TrayStub tray(String id, int priority) {
        return new TrayStub(id, priority);
    }

    /**
     * A notifications provider whose behaviour can be scripted.
     */
    public static final class NotificationsStub implements NotificationsProvider {

        private final String id;
        private final int priority;
        private boolean supports = true;
        private RuntimeException supportsFailure;
        private Error supportsError;
        private RuntimeException idFailure;
        private Error idError;
        private Capability capability;
        private RuntimeException capabilityFailure;
        private Error capabilityError;
        private Result<Void> showResult;
        private RuntimeException showFailure;
        private Error showError;
        private int idCalls;
        private int capabilityCalls;
        private int showCalls;
        private Notification lastNotification;

        private NotificationsStub(String id, int priority) {
            this.id = Objects.requireNonNull(id, "id");
            this.priority = priority;
            this.capability = new Capability(Capability.Status.AVAILABLE, Reason.NONE, "ok", null);
            this.showResult = new Result<>(
                    Result.Status.DISPATCHED_UNVERIFIED, null, id, Reason.NONE, "sent");
        }

        @Override
        public String id() {
            idCalls++;
            if (idFailure != null) {
                throw idFailure;
            }
            if (idError != null) {
                throw idError;
            }
            return id;
        }

        @Override
        public boolean supports(Platform platform) {
            if (supportsFailure != null) {
                throw supportsFailure;
            }
            if (supportsError != null) {
                throw supportsError;
            }
            return supports;
        }

        @Override
        public int priority() {
            return priority;
        }

        @Override
        public Capability capability() {
            capabilityCalls++;
            if (capabilityFailure != null) {
                throw capabilityFailure;
            }
            if (capabilityError != null) {
                throw capabilityError;
            }
            return capability;
        }

        @Override
        public Result<Void> show(Notification notification) {
            showCalls++;
            lastNotification = notification;
            if (showFailure != null) {
                throw showFailure;
            }
            if (showError != null) {
                throw showError;
            }
            return showResult;
        }

        /**
         * Sets whether {@link #supports(Platform)} returns true.
         *
         * @param value the new value
         * @return this stub
         */
        public NotificationsStub supports(boolean value) {
            this.supports = value;
            return this;
        }

        /**
         * Makes {@link #supports(Platform)} throw.
         *
         * @param failure the failure to throw
         * @return this stub
         */
        public NotificationsStub failSupports(RuntimeException failure) {
            this.supportsFailure = failure;
            return this;
        }

        /**
         * Makes {@link #supports(Platform)} throw an {@link Error}.
         *
         * @param failure the error to throw
         * @return this stub
         */
        public NotificationsStub failSupports(Error failure) {
            this.supportsError = failure;
            return this;
        }

        /**
         * Makes {@link #id()} throw.
         *
         * @param failure the failure to throw
         * @return this stub
         */
        public NotificationsStub failId(RuntimeException failure) {
            this.idFailure = failure;
            return this;
        }

        /**
         * Makes {@link #id()} throw an {@link Error}.
         *
         * @param failure the error to throw
         * @return this stub
         */
        public NotificationsStub failId(Error failure) {
            this.idError = failure;
            return this;
        }

        /**
         * Sets the capability reported by {@link #capability()}.
         *
         * @param value the capability
         * @return this stub
         */
        public NotificationsStub capability(Capability value) {
            this.capability = value;
            return this;
        }

        /**
         * Makes {@link #capability()} throw.
         *
         * @param failure the failure to throw
         * @return this stub
         */
        public NotificationsStub failCapability(RuntimeException failure) {
            this.capabilityFailure = failure;
            return this;
        }

        /**
         * Makes {@link #capability()} throw an {@link Error}.
         *
         * @param failure the error to throw
         * @return this stub
         */
        public NotificationsStub failCapability(Error failure) {
            this.capabilityError = failure;
            return this;
        }

        /**
         * Sets the result returned by {@link #show(Notification)}.
         *
         * @param value the result
         * @return this stub
         */
        public NotificationsStub showResult(Result<Void> value) {
            this.showResult = value;
            return this;
        }

        /**
         * Makes {@link #show(Notification)} throw.
         *
         * @param failure the failure to throw
         * @return this stub
         */
        public NotificationsStub failShow(RuntimeException failure) {
            this.showFailure = failure;
            return this;
        }

        /**
         * Makes {@link #show(Notification)} throw an {@link Error}.
         *
         * @param failure the error to throw
         * @return this stub
         */
        public NotificationsStub failShow(Error failure) {
            this.showError = failure;
            return this;
        }

        /**
         * Returns how many times {@link #id()} was called.
         *
         * @return the call count
         */
        public int idCalls() {
            return idCalls;
        }

        /**
         * Returns how many times {@link #capability()} was called.
         *
         * @return the call count
         */
        public int capabilityCalls() {
            return capabilityCalls;
        }

        /**
         * Returns how many times {@link #show(Notification)} was called.
         *
         * @return the call count
         */
        public int showCalls() {
            return showCalls;
        }

        /**
         * Returns the last notification passed to {@link #show(Notification)}.
         *
         * @return the last notification, or {@code null}
         */
        public Notification lastNotification() {
            return lastNotification;
        }
    }

    /**
     * An autostart provider whose behaviour can be scripted.
     */
    public static final class AutostartStub implements AutostartProvider {

        private final String id;
        private final int priority;
        private boolean supports = true;
        private RuntimeException supportsFailure;
        private Capability capability;
        private RuntimeException capabilityFailure;
        private Error capabilityError;
        private Result<Void> enableResult;
        private RuntimeException enableFailure;
        private Error enableError;
        private Result<Void> disableResult;
        private RuntimeException disableFailure;
        private Error disableError;
        private Result<Boolean> isEnabledResult;
        private RuntimeException isEnabledFailure;
        private Error isEnabledError;
        private AutostartEntry lastEntry;
        private String lastAppId;
        private int enableCalls;
        private int disableCalls;
        private int isEnabledCalls;

        private AutostartStub(String id, int priority) {
            this.id = Objects.requireNonNull(id, "id");
            this.priority = priority;
            this.capability = new Capability(Capability.Status.AVAILABLE, Reason.NONE, "ok", null);
            this.enableResult = new Result<>(Result.Status.CONFIRMED, null, id, Reason.NONE, "enabled");
            this.disableResult = new Result<>(Result.Status.CONFIRMED, null, id, Reason.NONE, "disabled");
            this.isEnabledResult =
                    new Result<>(Result.Status.CONFIRMED, Boolean.TRUE, id, Reason.NONE, "found");
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public boolean supports(Platform platform) {
            if (supportsFailure != null) {
                throw supportsFailure;
            }
            return supports;
        }

        @Override
        public int priority() {
            return priority;
        }

        @Override
        public Capability capability() {
            if (capabilityFailure != null) {
                throw capabilityFailure;
            }
            if (capabilityError != null) {
                throw capabilityError;
            }
            return capability;
        }

        @Override
        public Result<Void> enable(AutostartEntry entry) {
            enableCalls++;
            lastEntry = entry;
            if (enableFailure != null) {
                throw enableFailure;
            }
            if (enableError != null) {
                throw enableError;
            }
            return enableResult;
        }

        @Override
        public Result<Void> disable(String appId) {
            disableCalls++;
            lastAppId = appId;
            if (disableFailure != null) {
                throw disableFailure;
            }
            if (disableError != null) {
                throw disableError;
            }
            return disableResult;
        }

        @Override
        public Result<Boolean> isEnabled(String appId) {
            isEnabledCalls++;
            lastAppId = appId;
            if (isEnabledFailure != null) {
                throw isEnabledFailure;
            }
            if (isEnabledError != null) {
                throw isEnabledError;
            }
            return isEnabledResult;
        }

        /**
         * Sets whether {@link #supports(Platform)} returns true.
         *
         * @param value the new value
         * @return this stub
         */
        public AutostartStub supports(boolean value) {
            this.supports = value;
            return this;
        }

        /**
         * Sets the capability reported by {@link #capability()}.
         *
         * @param value the capability
         * @return this stub
         */
        public AutostartStub capability(Capability value) {
            this.capability = value;
            return this;
        }

        /**
         * Makes {@link #capability()} throw an {@link Error}.
         *
         * @param failure the error to throw
         * @return this stub
         */
        public AutostartStub failCapability(Error failure) {
            this.capabilityError = failure;
            return this;
        }

        /**
         * Makes {@link #enable(AutostartEntry)} throw.
         *
         * @param failure the failure to throw
         * @return this stub
         */
        public AutostartStub failEnable(RuntimeException failure) {
            this.enableFailure = failure;
            return this;
        }

        /**
         * Makes {@link #enable(AutostartEntry)} throw an {@link Error}.
         *
         * @param failure the error to throw
         * @return this stub
         */
        public AutostartStub failEnable(Error failure) {
            this.enableError = failure;
            return this;
        }

        /**
         * Makes {@link #disable(String)} throw.
         *
         * @param failure the failure to throw
         * @return this stub
         */
        public AutostartStub failDisable(RuntimeException failure) {
            this.disableFailure = failure;
            return this;
        }

        /**
         * Makes {@link #disable(String)} throw an {@link Error}.
         *
         * @param failure the error to throw
         * @return this stub
         */
        public AutostartStub failDisable(Error failure) {
            this.disableError = failure;
            return this;
        }

        /**
         * Makes {@link #isEnabled(String)} throw.
         *
         * @param failure the failure to throw
         * @return this stub
         */
        public AutostartStub failIsEnabled(RuntimeException failure) {
            this.isEnabledFailure = failure;
            return this;
        }

        /**
         * Makes {@link #isEnabled(String)} throw an {@link Error}.
         *
         * @param failure the error to throw
         * @return this stub
         */
        public AutostartStub failIsEnabled(Error failure) {
            this.isEnabledError = failure;
            return this;
        }

        /**
         * Sets the result returned by {@link #isEnabled(String)}.
         *
         * @param value the result
         * @return this stub
         */
        public AutostartStub isEnabledResult(Result<Boolean> value) {
            this.isEnabledResult = value;
            return this;
        }

        /**
         * Returns the last entry passed to {@link #enable(AutostartEntry)}.
         *
         * @return the last entry, or {@code null}
         */
        public AutostartEntry lastEntry() {
            return lastEntry;
        }

        /**
         * Returns the last app id passed to {@link #disable(String)} or {@link #isEnabled(String)}.
         *
         * @return the last app id, or {@code null}
         */
        public String lastAppId() {
            return lastAppId;
        }

        /**
         * Returns how many times each action was called.
         *
         * @return the number of {@code enable} calls
         */
        public int enableCalls() {
            return enableCalls;
        }

        /**
         * Returns how many times {@link #disable(String)} was called.
         *
         * @return the call count
         */
        public int disableCalls() {
            return disableCalls;
        }

        /**
         * Returns how many times {@link #isEnabled(String)} was called.
         *
         * @return the call count
         */
        public int isEnabledCalls() {
            return isEnabledCalls;
        }
    }

    /**
     * A tray provider whose behaviour can be scripted.
     */
    public static final class TrayStub implements TrayProvider {

        private final String id;
        private final int priority;
        private boolean supports = true;
        private Capability capability;
        private RuntimeException capabilityFailure;
        private Error capabilityError;
        private Result<TrayHandle> openResult;
        private RuntimeException openFailure;
        private Error openError;
        private TrayIcon lastIcon;

        private TrayStub(String id, int priority) {
            this.id = Objects.requireNonNull(id, "id");
            this.priority = priority;
            this.capability = new Capability(Capability.Status.AVAILABLE, Reason.NONE, "ok", null);
            this.openResult = new Result<>(
                    Result.Status.CONFIRMED, new FakeTrayHandle(), id, Reason.NONE, "opened");
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public boolean supports(Platform platform) {
            return supports;
        }

        @Override
        public int priority() {
            return priority;
        }

        @Override
        public Capability capability() {
            if (capabilityFailure != null) {
                throw capabilityFailure;
            }
            if (capabilityError != null) {
                throw capabilityError;
            }
            return capability;
        }

        @Override
        public Result<TrayHandle> open(TrayIcon icon) {
            lastIcon = icon;
            if (openFailure != null) {
                throw openFailure;
            }
            if (openError != null) {
                throw openError;
            }
            return openResult;
        }

        /**
         * Sets whether {@link #supports(Platform)} returns true.
         *
         * @param value the new value
         * @return this stub
         */
        public TrayStub supports(boolean value) {
            this.supports = value;
            return this;
        }

        /**
         * Sets the capability reported by {@link #capability()}.
         *
         * @param value the capability
         * @return this stub
         */
        public TrayStub capability(Capability value) {
            this.capability = value;
            return this;
        }

        /**
         * Makes {@link #capability()} throw an {@link Error}.
         *
         * @param failure the error to throw
         * @return this stub
         */
        public TrayStub failCapability(Error failure) {
            this.capabilityError = failure;
            return this;
        }

        /**
         * Sets the result returned by {@link #open(TrayIcon)}.
         *
         * @param value the result
         * @return this stub
         */
        public TrayStub openResult(Result<TrayHandle> value) {
            this.openResult = value;
            return this;
        }

        /**
         * Makes {@link #open(TrayIcon)} throw.
         *
         * @param failure the failure to throw
         * @return this stub
         */
        public TrayStub failOpen(RuntimeException failure) {
            this.openFailure = failure;
            return this;
        }

        /**
         * Makes {@link #open(TrayIcon)} throw an {@link Error}.
         *
         * @param failure the error to throw
         * @return this stub
         */
        public TrayStub failOpen(Error failure) {
            this.openError = failure;
            return this;
        }

        /**
         * Returns the last icon passed to {@link #open(TrayIcon)}.
         *
         * @return the last icon, or {@code null}
         */
        public TrayIcon lastIcon() {
            return lastIcon;
        }
    }

    /**
     * A tray handle whose open state can be inspected.
     */
    public static final class FakeTrayHandle implements TrayHandle {

        private final AtomicBoolean open = new AtomicBoolean(true);

        @Override
        public boolean isOpen() {
            return open.get();
        }

        @Override
        public void close() {
            open.set(false);
        }
    }
}
