package io.github.atharvashelkeatwork.jnativekit.internal;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.ToIntFunction;

import io.github.atharvashelkeatwork.jnativekit.Platform;

/**
 * Loads providers with {@link ServiceLoader} and selects one for a platform.
 *
 * <p>Selection is deterministic: only providers accepted by the supplied {@code supports} predicate
 * are considered, the highest priority wins, and ties are broken by the supplied identifier in
 * alphabetical order and then by provider class name.
 *
 * <p>Providers are loaded with the class loader that defines the provider interface, not with the
 * calling thread's context class loader. Discovery therefore does not change depending on which
 * thread touches {@code Desktop} first.
 *
 * <p>A broken provider never breaks resolution. A {@link ServiceConfigurationError} or a non-fatal
 * exception thrown by a provider's selection methods is logged at {@code WARNING} and that provider
 * is skipped.
 */
public final class ProviderResolver {

    private static final System.Logger LOG = System.getLogger(ProviderResolver.class.getName());

    /**
     * Upper bound on consecutive {@code hasNext()} failures. A hostile iterator must not spin
     * forever, while one broken entry must not hide every provider after it.
     */
    private static final int MAX_CONSECUTIVE_HAS_NEXT_FAILURES = 1024;

    private ProviderResolver() {
    }

    /**
     * Loads every provider of the given service type.
     *
     * <p>The provider interface's defining class loader is used so the result is independent of the
     * calling thread's context class loader.
     *
     * @param type the provider service interface to load
     * @param <P>  the provider type
     * @return the providers that could be instantiated; broken entries are skipped
     * @throws NullPointerException if {@code type} is {@code null}
     */
    public static <P> List<P> load(Class<P> type) {
        Objects.requireNonNull(type, "type");
        ClassLoader loader = type.getClassLoader();
        ServiceLoader<P> services = loader == null
                ? ServiceLoader.load(type)
                : ServiceLoader.load(type, loader);
        return loadAll(services);
    }

    /**
     * Iterates a provider source and keeps every provider it yields, skipping configuration
     * errors.
     *
     * <p>Package-private so tests can feed a hostile {@link Iterable} without changing the service
     * files of the module.
     *
     * <p>A {@link ServiceConfigurationError} from {@code hasNext()} is retried a bounded number of
     * times, because one broken service entry must not hide later providers.
     *
     * @param candidates the provider source
     * @param <P>        the provider type
     * @return the providers that could be iterated; never {@code null}
     */
    static <P> List<P> loadAll(Iterable<P> candidates) {
        Objects.requireNonNull(candidates, "candidates");
        List<P> result = new ArrayList<>();
        Iterator<P> iterator = candidates.iterator();
        int consecutiveHasNextFailures = 0;
        while (true) {
            boolean hasNext;
            try {
                hasNext = iterator.hasNext();
                consecutiveHasNextFailures = 0;
            } catch (ServiceConfigurationError e) {
                logBrokenProvider("while checking for more providers", e);
                consecutiveHasNextFailures++;
                if (consecutiveHasNextFailures >= MAX_CONSECUTIVE_HAS_NEXT_FAILURES) {
                    LOG.log(System.Logger.Level.WARNING,
                            "Giving up after " + consecutiveHasNextFailures
                                    + " consecutive hasNext() failures");
                    break;
                }
                continue;
            }
            if (!hasNext) {
                break;
            }
            try {
                P provider = iterator.next();
                if (provider != null) {
                    result.add(provider);
                }
            } catch (ServiceConfigurationError e) {
                logBrokenProvider("while instantiating a provider", e);
            }
        }
        return result;
    }

    /**
     * Selects the best provider for the given platform.
     *
     * <p>The provider's selection methods are passed as functions so this resolver serves all three
     * SPI interfaces without a shared supertype in the public SPI.
     *
     * @param platform  the platform to match; never {@code null}
     * @param providers the providers to choose from; may be empty
     * @param supports  the provider's support predicate
     * @param id        the provider's identifier
     * @param priority  the provider's selection priority
     * @param <P>       the provider type
     * @return the selected provider, or an empty optional when none matches
     * @throws NullPointerException if any argument is {@code null}
     */
    public static <P> Optional<P> select(
            Platform platform,
            Iterable<P> providers,
            BiPredicate<P, Platform> supports,
            Function<P, String> id,
            ToIntFunction<P> priority) {
        Objects.requireNonNull(platform, "platform");
        Objects.requireNonNull(providers, "providers");
        Objects.requireNonNull(supports, "supports");
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(priority, "priority");

        P best = null;
        String bestId = null;
        String bestClass = null;
        int bestPriority = Integer.MIN_VALUE;

        for (P provider : providers) {
            if (provider == null || !supports(provider, platform, supports)) {
                continue;
            }
            String providerId;
            int providerPriority;
            try {
                providerId = id.apply(provider);
                providerPriority = priority.applyAsInt(provider);
            } catch (RuntimeException e) {
                logSelectionFailure(provider, e);
                continue;
            } catch (Error e) {
                if (Errors.isFatal(e)) {
                    throw e;
                }
                logSelectionFailure(provider, e);
                continue;
            }
            if (providerId == null || providerId.isBlank()) {
                LOG.log(System.Logger.Level.WARNING,
                        "Skipping provider " + provider.getClass().getName()
                                + " because id() returned a null or blank id");
                continue;
            }

            String providerClass = provider.getClass().getName();
            if (best == null || isBetter(providerId, providerClass, providerPriority,
                    bestId, bestClass, bestPriority)) {
                best = provider;
                bestId = providerId;
                bestClass = providerClass;
                bestPriority = providerPriority;
            }
        }
        return Optional.ofNullable(best);
    }

    private static boolean isBetter(
            String providerId,
            String providerClass,
            int providerPriority,
            String bestId,
            String bestClass,
            int bestPriority) {
        if (providerPriority != bestPriority) {
            return providerPriority > bestPriority;
        }
        int byId = providerId.compareTo(bestId);
        if (byId != 0) {
            return byId < 0;
        }
        return providerClass.compareTo(bestClass) < 0;
    }

    private static <P> boolean supports(
            P provider, Platform platform, BiPredicate<P, Platform> supports) {
        try {
            return supports.test(provider, platform);
        } catch (RuntimeException e) {
            logSupportsFailure(provider, e);
            return false;
        } catch (Error e) {
            if (Errors.isFatal(e)) {
                throw e;
            }
            logSupportsFailure(provider, e);
            return false;
        }
    }

    private static void logSelectionFailure(Object provider, Throwable failure) {
        LOG.log(System.Logger.Level.WARNING,
                "Skipping provider " + provider.getClass().getName()
                        + " because id() or priority() failed",
                failure);
    }

    private static void logSupportsFailure(Object provider, Throwable failure) {
        LOG.log(System.Logger.Level.WARNING,
                "Skipping provider " + provider.getClass().getName()
                        + " because supports() failed",
                failure);
    }

    private static void logBrokenProvider(String phase, ServiceConfigurationError error) {
        LOG.log(System.Logger.Level.WARNING, "Skipping a broken provider " + phase, error);
    }
}
