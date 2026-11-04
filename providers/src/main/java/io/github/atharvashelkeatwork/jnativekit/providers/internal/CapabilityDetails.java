package io.github.atharvashelkeatwork.jnativekit.providers.internal;

import io.github.atharvashelkeatwork.jnativekit.Capability;

/**
 * Builds human-readable text for capability-based outcomes.
 *
 * <p>Internal to the {@code providers} module. Result records have no separate remedy field, so an
 * unavailable detail combines the capability detail with its remedy. The text is for humans and
 * logs and is never parsed by code.
 */
public final class CapabilityDetails {

    private CapabilityDetails() {
    }

    /**
     * Builds a human-readable {@code UNAVAILABLE} detail that keeps the capability's remedy.
     *
     * @param capability the capability that made the action unavailable; never {@code null}
     * @return the detail to put in an {@code UNAVAILABLE} result
     * @throws NullPointerException if {@code capability} is {@code null}
     */
    public static String unavailableDetail(Capability capability) {
        if (capability == null) {
            throw new NullPointerException("capability");
        }
        String remedy = capability.remedy();
        return remedy == null ? capability.detail() : capability.detail() + "; " + remedy;
    }
}
