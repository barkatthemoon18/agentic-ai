package com.fuad.assistant.skills.os;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

class CatalogSessionStoreTest {
    @Test
    void shouldSharePageAndFilterChangesWithObservers() throws Exception {
        CatalogSessionStore store = new CatalogSessionStore();
        List<ApplicationDefinition> applications = IntStream.range(0, 45)
                .mapToObj(index -> new ApplicationDefinition("app-" + index,
                        "Application " + String.format("%02d", index), List.of("open"), "app.exe"))
                .toList();
        ApplicationCatalogPayload first = store.create(applications, "");
        ApplicationCatalogPayload[] observed = new ApplicationCatalogPayload[1];
        AutoCloseable subscription = store.observe(first.sessionId(), payload -> observed[0] = payload);

        ApplicationCatalogPayload second = store.navigate(first.sessionId(), CatalogNavigation.NEXT).orElseThrow();
        assertEquals(1, second.pageIndex());
        assertEquals(second, observed[0]);

        ApplicationCatalogPayload filtered = store.filter(first.sessionId(), "Application 0").orElseThrow();
        assertEquals(0, filtered.pageIndex());
        assertEquals(10, filtered.totalCount());
        assertEquals(filtered, observed[0]);
        subscription.close();
    }

    @Test
    void navigationShouldClampAtCatalogBounds() {
        CatalogSessionStore store = new CatalogSessionStore();
        ApplicationCatalogPayload payload = store.create(List.of(
                new ApplicationDefinition("one", "One", List.of("open"), "one.exe")), "");

        assertEquals(0, store.navigate(payload.sessionId(), CatalogNavigation.PREVIOUS).orElseThrow().pageIndex());
        assertEquals(0, store.navigate(payload.sessionId(), CatalogNavigation.NEXT).orElseThrow().pageIndex());
    }
}
