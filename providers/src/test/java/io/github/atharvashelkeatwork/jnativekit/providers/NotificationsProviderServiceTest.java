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
import io.github.atharvashelkeatwork.jnativekit.spi.NotificationsProvider;

class NotificationsProviderServiceTest {

    @Test
    void serviceFileRegistersOneProviderPerSupportedPlatform() {
        List<NotificationsProvider> providers = new ArrayList<>();
        ServiceLoader.load(NotificationsProvider.class).forEach(providers::add);

        Map<String, NotificationsProvider> byId = providers.stream()
                .collect(Collectors.toMap(NotificationsProvider::id, Function.identity()));

        assertEquals(3, byId.size());
        assertTrue(byId.get("linux.notify-send").supports(Platform.LINUX));
        assertTrue(byId.get("windows.awt-balloon").supports(Platform.WINDOWS));
        assertTrue(byId.get("macos.osascript").supports(Platform.MACOS));
    }
}
