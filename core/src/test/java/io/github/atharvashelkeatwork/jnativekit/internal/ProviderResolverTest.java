package io.github.atharvashelkeatwork.jnativekit.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.ServiceConfigurationError;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import io.github.atharvashelkeatwork.jnativekit.Capability;
import io.github.atharvashelkeatwork.jnativekit.Notification;
import io.github.atharvashelkeatwork.jnativekit.Platform;
import io.github.atharvashelkeatwork.jnativekit.Reason;
import io.github.atharvashelkeatwork.jnativekit.Result;
import io.github.atharvashelkeatwork.jnativekit.spi.NotificationsProvider;

/**
 * Covers provider loading and the deterministic selection rules from
 * {@code docs/ARCHITECTURE.md}.
 */
class ProviderResolverTest {

    static Stream<Arguments> platformCases() {
        return Stream.of(
                Arguments.of(Platform.LINUX),
                Arguments.of(Platform.WINDOWS),
                Arguments.of(Platform.MACOS),
                Arguments.of(Platform.OTHER));
    }

    private static Optional<NotificationsProvider> resolve(
            Platform platform, Iterable<NotificationsProvider> providers) {
        return ProviderResolver.select(
                platform,
                providers,
                NotificationsProvider::supports,
                NotificationsProvider::id,
                NotificationsProvider::priority);
    }

    @ParameterizedTest(name = "no provider on {0}")
    @MethodSource("platformCases")
    void noProvidersMeansNoSelection(Platform platform) {
        assertTrue(resolve(platform, List.of()).isEmpty());
    }

    @Test
    void highestPriorityWins() {
        FakeProviders.NotificationsStub low = FakeProviders.notifications("linux.low", 1);
        FakeProviders.NotificationsStub high = FakeProviders.notifications("linux.high", 10);
        FakeProviders.NotificationsStub middle = FakeProviders.notifications("linux.middle", 5);

        Optional<NotificationsProvider> selected =
                resolve(Platform.LINUX, List.of(low, high, middle));

        assertSame(high, selected.orElseThrow());
    }

    @Test
    void priorityTieIsBrokenAlphabeticallyById() {
        FakeProviders.NotificationsStub zulu = FakeProviders.notifications("zulu", 5);
        FakeProviders.NotificationsStub alpha = FakeProviders.notifications("alpha", 5);
        FakeProviders.NotificationsStub mike = FakeProviders.notifications("mike", 5);

        Optional<NotificationsProvider> selected =
                resolve(Platform.LINUX, List.of(zulu, mike, alpha));

        assertSame(alpha, selected.orElseThrow());
    }

    @Test
    void equalIdAndPriorityTieBreaksByClassName() {
        Optional<NotificationsProvider> selected = resolve(
                Platform.LINUX, List.of(new ZuluNamedProvider(), new AlphaNamedProvider()));

        assertSame(AlphaNamedProvider.class, selected.map(Object::getClass).orElseThrow());
    }

    @Test
    void unsupportedProvidersAreFilteredOut() {
        FakeProviders.NotificationsStub wrongOs =
                FakeProviders.notifications("linux.nope", 100).supports(false);
        FakeProviders.NotificationsStub supported =
                FakeProviders.notifications("linux.yes", 1);

        Optional<NotificationsProvider> selected =
                resolve(Platform.LINUX, List.of(wrongOs, supported));

        assertSame(supported, selected.orElseThrow());
    }

    @Test
    void providerThrowingFromSupportsIsSkipped() {
        FakeProviders.NotificationsStub broken = FakeProviders.notifications("linux.broken", 100)
                .failSupports(new IllegalStateException("boom"));
        FakeProviders.NotificationsStub healthy = FakeProviders.notifications("linux.healthy", 1);

        Optional<NotificationsProvider> selected =
                resolve(Platform.LINUX, List.of(broken, healthy));

        assertSame(healthy, selected.orElseThrow());
    }

    @Test
    void providerThrowingFromIdOrPriorityIsSkipped() {
        FakeProviders.NotificationsStub broken = FakeProviders.notifications("linux.broken", 100)
                .failId(new IllegalStateException("boom"));
        FakeProviders.NotificationsStub healthy = FakeProviders.notifications("linux.healthy", 1);

        Optional<NotificationsProvider> selected =
                resolve(Platform.LINUX, List.of(broken, healthy));

        assertSame(healthy, selected.orElseThrow());
    }

    @Test
    void providerThrowingErrorFromIdOrPriorityIsSkipped() {
        FakeProviders.NotificationsStub broken = FakeProviders.notifications("linux.broken", 100)
                .failId(new AssertionError("backend gone"));
        FakeProviders.NotificationsStub healthy = FakeProviders.notifications("linux.healthy", 1);

        Optional<NotificationsProvider> selected =
                resolve(Platform.LINUX, List.of(broken, healthy));

        assertSame(healthy, selected.orElseThrow());
    }

    @Test
    void blankIdProviderIsSkipped() {
        FakeProviders.NotificationsStub blank = FakeProviders.notifications("  ", 100);
        FakeProviders.NotificationsStub healthy = FakeProviders.notifications("linux.healthy", 1);

        Optional<NotificationsProvider> selected =
                resolve(Platform.LINUX, List.of(blank, healthy));

        assertSame(healthy, selected.orElseThrow());
    }

    @Test
    void nullProvidersAreIgnored() {
        FakeProviders.NotificationsStub healthy = FakeProviders.notifications("linux.healthy", 1);
        List<NotificationsProvider> providers = new ArrayList<>();
        providers.add(null);
        providers.add(healthy);

        assertSame(healthy, resolve(Platform.LINUX, providers).orElseThrow());
    }

    @Test
    void brokenServiceConfigurationEntryDoesNotStopLoading() {
        FakeProviders.NotificationsStub healthy = FakeProviders.notifications("linux.healthy", 1);
        Iterable<NotificationsProvider> hostile = () -> new Iterator<>() {
            private int state;

            @Override
            public boolean hasNext() {
                return state < 2;
            }

            @Override
            public NotificationsProvider next() {
                if (state++ == 0) {
                    throw new ServiceConfigurationError("provider class cannot be created");
                }
                return healthy;
            }
        };

        List<NotificationsProvider> loaded = ProviderResolver.loadAll(hostile);

        assertEquals(List.of(healthy), loaded);
    }

    @Test
    void brokenHasNextRecoversOnNextCall() {
        FakeProviders.NotificationsStub healthy = FakeProviders.notifications("linux.healthy", 1);
        Iterable<NotificationsProvider> hostile = () -> new Iterator<>() {
            private int calls;

            @Override
            public boolean hasNext() {
                calls++;
                if (calls == 2) {
                    throw new ServiceConfigurationError("transient configuration error");
                }
                return calls == 1;
            }

            @Override
            public NotificationsProvider next() {
                return healthy;
            }
        };

        List<NotificationsProvider> loaded = ProviderResolver.loadAll(hostile);

        assertEquals(1, loaded.size());
        assertSame(healthy, loaded.get(0));
    }

    @Test
    void brokenHasNextStopsLoadingWithoutThrowing() {
        Iterable<NotificationsProvider> hostile = () -> new Iterator<>() {
            @Override
            public boolean hasNext() {
                throw new ServiceConfigurationError("broken service file");
            }

            @Override
            public NotificationsProvider next() {
                throw new AssertionError("next() must not be called");
            }
        };

        assertTrue(ProviderResolver.loadAll(hostile).isEmpty());
    }

    /**
     * Provider with an empty class name suffix, to exercise the class-name tie-break.
     */
    private static final class AlphaNamedProvider implements NotificationsProvider {

        @Override
        public String id() {
            return "same.id";
        }

        @Override
        public boolean supports(Platform platform) {
            return true;
        }

        @Override
        public int priority() {
            return 7;
        }

        @Override
        public Capability capability() {
            return new Capability(Capability.Status.AVAILABLE, Reason.NONE, "ok", null);
        }

        @Override
        public Result<Void> show(Notification notification) {
            return new Result<>(
                    Result.Status.DISPATCHED_UNVERIFIED, null, id(), Reason.NONE, "sent");
        }
    }

    private static final class ZuluNamedProvider implements NotificationsProvider {

        @Override
        public String id() {
            return "same.id";
        }

        @Override
        public boolean supports(Platform platform) {
            return true;
        }

        @Override
        public int priority() {
            return 7;
        }

        @Override
        public Capability capability() {
            return new Capability(Capability.Status.AVAILABLE, Reason.NONE, "ok", null);
        }

        @Override
        public Result<Void> show(Notification notification) {
            return new Result<>(
                    Result.Status.DISPATCHED_UNVERIFIED, null, id(), Reason.NONE, "sent");
        }
    }
}
