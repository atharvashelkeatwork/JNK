package io.github.atharvashelkeatwork.jnativekit.providers.awt;

import java.awt.Image;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Test double for {@link TrayBackend}.
 *
 * <p>Test helper only; it is not shipped. It records every added icon, can simulate a headless
 * environment, a missing system tray, a failing add and a failing remove, and lets a test fire a
 * menu entry the way a real click would.
 */
public final class FakeTrayBackend implements TrayBackend {

    /**
     * One icon handed to the backend.
     *
     * @param image   the decoded image
     * @param tooltip the tooltip text
     * @param menu    the menu entries
     * @param handle  the handle returned to the provider
     */
    public record Added(
            Image image,
            String tooltip,
            List<MenuEntry> menu,
            FakeIconHandle handle) {

        /**
         * Creates an immutable record of one added icon.
         *
         * @param image   the decoded image
         * @param tooltip the tooltip text
         * @param menu    the menu entries
         * @param handle  the handle returned to the provider
         */
        public Added {
            Objects.requireNonNull(image, "image");
            Objects.requireNonNull(tooltip, "tooltip");
            menu = List.copyOf(Objects.requireNonNull(menu, "menu"));
            Objects.requireNonNull(handle, "handle");
        }
    }

    private final List<Added> icons = new CopyOnWriteArrayList<>();
    private volatile boolean headless;
    private volatile boolean supported = true;
    private volatile RuntimeException addFailure;
    private volatile RuntimeException removeFailure;

    /**
     * Sets whether the fake reports a headless AWT environment.
     *
     * @param value whether AWT is reported as headless
     * @return this fake
     */
    public FakeTrayBackend headless(boolean value) {
        this.headless = value;
        return this;
    }

    /**
     * Sets whether the fake reports a supported system tray.
     *
     * @param value whether the tray is supported
     * @return this fake
     */
    public FakeTrayBackend supported(boolean value) {
        this.supported = value;
        return this;
    }

    /**
     * Makes the next and later adds throw.
     *
     * @param value the failure, or {@code null} to stop failing
     * @return this fake
     */
    public FakeTrayBackend addFailure(RuntimeException value) {
        this.addFailure = value;
        return this;
    }

    /**
     * Makes the handles of the next and later icons fail on remove.
     *
     * @param value the failure, or {@code null} to stop failing
     * @return this fake
     */
    public FakeTrayBackend removeFailure(RuntimeException value) {
        this.removeFailure = value;
        return this;
    }

    /**
     * Returns the recorded icons.
     *
     * @return an immutable snapshot of the added icons
     */
    public List<Added> icons() {
        return List.copyOf(icons);
    }

    /**
     * Returns one recorded icon.
     *
     * @param index the zero-based icon index
     * @return the recorded icon
     */
    public Added icon(int index) {
        return icons.get(index);
    }

    /**
     * Fires one menu entry the way the AWT event thread would.
     *
     * @param iconIndex the zero-based icon index
     * @param menuIndex the zero-based menu index; must not be a separator
     */
    public void click(int iconIndex, int menuIndex) {
        Runnable action = icon(iconIndex).menu().get(menuIndex).action();
        if (action == null) {
            throw new IllegalArgumentException("menu entry " + menuIndex + " is a separator");
        }
        action.run();
    }

    @Override
    public boolean isHeadless() {
        return headless;
    }

    @Override
    public boolean isSupported() {
        return supported;
    }

    @Override
    public IconHandle add(Image image, String tooltip, List<MenuEntry> menu) {
        FakeIconHandle handle = new FakeIconHandle(removeFailure);
        icons.add(new Added(image, tooltip, menu, handle));
        RuntimeException failure = addFailure;
        if (failure != null) {
            throw failure;
        }
        return handle;
    }

    /**
     * Test handle that counts removals and can fail once configured.
     */
    public static final class FakeIconHandle implements IconHandle {

        private final AtomicInteger removals = new AtomicInteger();
        private final RuntimeException failure;

        private FakeIconHandle(RuntimeException failure) {
            this.failure = failure;
        }

        @Override
        public void remove() {
            removals.incrementAndGet();
            if (failure != null) {
                throw failure;
            }
        }

        /**
         * Returns how often {@link #remove()} was called.
         *
         * @return the removal count
         */
        public int removals() {
            return removals.get();
        }
    }
}
