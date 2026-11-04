package io.github.atharvashelkeatwork.jnativekit.providers.awt;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Test double for {@link BalloonBackend}.
 *
 * <p>Test helper only; it is not shipped. It records every displayed message and can simulate a
 * headless environment, a missing system tray or a failing backend.
 */
public final class FakeBalloonBackend implements BalloonBackend {

    /**
     * One displayed message.
     *
     * @param title the title as received
     * @param body  the body as received
     */
    public record Message(String title, String body) {
    }

    private final List<Message> messages = new CopyOnWriteArrayList<>();
    private volatile boolean headless;
    private volatile boolean supported = true;
    private volatile RuntimeException failure;

    /**
     * Sets whether the fake is headless.
     *
     * @param value whether AWT is reported as headless
     * @return this fake
     */
    public FakeBalloonBackend headless(boolean value) {
        this.headless = value;
        return this;
    }

    /**
     * Sets whether the fake reports a supported system tray.
     *
     * @param value whether the tray is supported
     * @return this fake
     */
    public FakeBalloonBackend supported(boolean value) {
        this.supported = value;
        return this;
    }

    /**
     * Makes the next and later displays throw.
     *
     * @param value the failure, or {@code null} to stop failing
     * @return this fake
     */
    public FakeBalloonBackend failure(RuntimeException value) {
        this.failure = value;
        return this;
    }

    /**
     * Returns the recorded messages.
     *
     * @return an immutable snapshot of the displayed messages
     */
    public List<Message> messages() {
        return List.copyOf(messages);
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
    public void display(String title, String body) {
        messages.add(new Message(
                Objects.requireNonNull(title, "title"),
                Objects.requireNonNull(body, "body")));
        RuntimeException current = failure;
        if (current != null) {
            throw current;
        }
    }
}
