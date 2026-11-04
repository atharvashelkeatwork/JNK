package io.github.atharvashelkeatwork.jnativekit.providers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import io.github.atharvashelkeatwork.jnativekit.Platform;
import io.github.atharvashelkeatwork.jnativekit.spi.TrayProvider;

class TrayProviderServiceTest {

    @Test
    void serviceFileRegistersTheAwtTrayProvider() {
        List<TrayProvider> providers = new ArrayList<>();
        ServiceLoader.load(TrayProvider.class).forEach(providers::add);

        Map<String, TrayProvider> byId = providers.stream()
                .collect(Collectors.toMap(TrayProvider::id, Function.identity()));

        assertEquals(1, byId.size());
        TrayProvider provider = byId.get("awt.systemtray");
        assertTrue(provider.supports(Platform.LINUX));
        assertTrue(provider.supports(Platform.WINDOWS));
        assertTrue(provider.supports(Platform.MACOS));
        assertTrue(provider.supports(Platform.OTHER));
    }
}
