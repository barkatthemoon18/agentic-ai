package com.fuad.assistant.skills.os;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public class CatalogSessionStore {
    public static final int PAGE_SIZE = 20;
    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();

    public synchronized ApplicationCatalogPayload create(List<ApplicationDefinition> applications, String filter) {
        UUID id = UUID.randomUUID();
        Session session = new Session(id, applications);
        sessions.clear();
        sessions.put(id, session);
        return session.setFilter(filter);
    }

    public Optional<ApplicationCatalogPayload> current(UUID sessionId) {
        Session session = sessions.get(sessionId);
        return session == null ? Optional.empty() : Optional.of(session.snapshot());
    }

    public Optional<ApplicationCatalogPayload> navigate(UUID sessionId, CatalogNavigation navigation) {
        Session session = sessions.get(sessionId);
        return session == null ? Optional.empty() : Optional.of(session.navigate(navigation));
    }

    public Optional<ApplicationCatalogPayload> filter(UUID sessionId, String filter) {
        Session session = sessions.get(sessionId);
        return session == null ? Optional.empty() : Optional.of(session.setFilter(filter));
    }

    public AutoCloseable observe(UUID sessionId, Consumer<ApplicationCatalogPayload> observer) {
        Session session = sessions.get(sessionId);
        if (session == null) return () -> { };
        session.observers.add(observer);
        observer.accept(session.snapshot());
        return () -> session.observers.remove(observer);
    }

    public void remove(UUID sessionId) {
        sessions.remove(sessionId);
    }

    private static final class Session {
        private final UUID id;
        private final List<ApplicationDefinition> all;
        private final List<Consumer<ApplicationCatalogPayload>> observers = new CopyOnWriteArrayList<>();
        private String filter = "";
        private int pageIndex;

        private Session(UUID id, List<ApplicationDefinition> applications) {
            this.id = id;
            this.all = applications.stream()
                    .sorted(Comparator.comparing(ApplicationDefinition::getDisplayName, String.CASE_INSENSITIVE_ORDER))
                    .toList();
        }

        synchronized ApplicationCatalogPayload snapshot() {
            List<ApplicationDefinition> filtered = filtered();
            int totalPages = Math.max(1, (filtered.size() + PAGE_SIZE - 1) / PAGE_SIZE);
            pageIndex = Math.clamp(pageIndex, 0, totalPages - 1);
            int from = Math.min(pageIndex * PAGE_SIZE, filtered.size());
            int to = Math.min(from + PAGE_SIZE, filtered.size());
            List<ApplicationListItem> items = filtered.subList(from, to).stream()
                    .map(app -> new ApplicationListItem(app.getId(), app.getDisplayName())).toList();
            return new ApplicationCatalogPayload(id, filter, pageIndex, PAGE_SIZE,
                    filtered.size(), totalPages, items);
        }

        ApplicationCatalogPayload navigate(CatalogNavigation navigation) {
            ApplicationCatalogPayload payload;
            synchronized (this) {
                int last = Math.max(0, snapshot().totalPages() - 1);
                pageIndex = switch (navigation) {
                    case NEXT -> Math.min(last, pageIndex + 1);
                    case PREVIOUS -> Math.max(0, pageIndex - 1);
                    case FIRST -> 0;
                    case LAST -> last;
                };
                payload = snapshot();
            }
            publish(payload);
            return payload;
        }

        ApplicationCatalogPayload setFilter(String value) {
            ApplicationCatalogPayload payload;
            synchronized (this) {
                filter = value == null ? "" : value.trim();
                pageIndex = 0;
                payload = snapshot();
            }
            publish(payload);
            return payload;
        }

        private List<ApplicationDefinition> filtered() {
            String query = ApplicationNames.normalize(filter);
            if (query.isBlank()) return all;
            return all.stream().filter(app -> ApplicationNames.normalize(app.getDisplayName()).contains(query)
                    || app.getAliases().stream().anyMatch(alias -> ApplicationNames.normalize(alias).contains(query)))
                    .toList();
        }

        private void publish(ApplicationCatalogPayload payload) {
            observers.forEach(observer -> observer.accept(payload));
        }
    }
}
