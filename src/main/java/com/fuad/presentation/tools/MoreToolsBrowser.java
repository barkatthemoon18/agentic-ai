package com.fuad.presentation.tools;

import com.fuad.enums.OsAction;
import com.fuad.interaction.*;

import java.util.*;

public final class MoreToolsBrowser {
    private static final String APP_PREFIX = "app:";
    private static final String NEXT = "nav:next";
    private static final String PREVIOUS = "nav:previous";
    private static final String CLOSE = "nav:close";

    private final InteractionService interactionService;
    private final ToolsActionHandler actionHandler;
    private final List<ToolApplication> applications;
    private final int pageSize;

    public MoreToolsBrowser(InteractionService interactionService, ToolsActionHandler actionHandler, ToolsWorkspaceConfig config) {
        Objects.requireNonNull(config);

        this.interactionService = Objects.requireNonNull(interactionService);
        this.actionHandler = Objects.requireNonNull(actionHandler);
        this.applications = config.moreApplications();
        this.pageSize = config.pageSize();
    }

    public boolean open() {
        if (applications.isEmpty()) {
            return false;
        }
        showPage(0);
        return true;
    }

    private void showPage(int page) {
        int pageCount = (applications.size() + pageSize - 1) / pageSize;

        if (page < 0 || page >= pageCount) {
            return;
        }
        int from = page * pageSize;
        int to = Math.min(from + pageSize, applications.size());
        List<ChoiceOption> options = new ArrayList<>();
        for (ToolApplication application : applications.subList(from, to)) {
            options.add(new ChoiceOption(APP_PREFIX + application.id(), application.displayName(),
                    Optional.of("APPLICATION"), List.of()));
        }
        if (page > 0) {
            options.add(new ChoiceOption(PREVIOUS, "Anterior", List.of()));
        }
        if (page + 1 < pageCount) {
            options.add(new ChoiceOption(NEXT, "Siguiente", List.of()));
        }
        if (options.size() == 1) {
            options.add(new ChoiceOption(CLOSE, "Cerrar", List.of()));
        }
        ChoiceRequest request = new ChoiceRequest(Optional.empty(),
                "MORE TOOLS // PAGE " + (page + 1) + " / " + pageCount, Set.of(InputModality.TOUCH),
                Optional.empty(), FocusRequirement.PASSIVE, options);
        interactionService.request(request).thenAccept(result -> {
            if (result.outcome() != InteractionOutcome.SUBMITTED) {
                return;
            }
            String selected = result.value().orElseThrow();
            handleSelection(selected, page, pageCount);
        });
    }

    private void handleSelection(String selected, int currentPage, int pageCount) {
        if (NEXT.equals(selected)) {
            if (currentPage + 1 < pageCount) {
                showPage(currentPage + 1);
            }
            return;
        }
        if (PREVIOUS.equals(selected)) {
            if (currentPage > 0) {
                showPage(currentPage - 1);
            }
            return;
        }
        if (CLOSE.equals(selected)) {
            return;
        }
        if (!selected.startsWith(APP_PREFIX)) {
            return;
        }
        String id = selected.substring(APP_PREFIX.length());
        applications.stream().filter(app -> app.id().equals(id)).findFirst()
                .ifPresent(app ->
                        actionHandler.submit(new ToolsActionRequest(OsAction.OPEN_APPLICATION, app.resolverTarget())));
    }
}
