package com.fuad.presentation.tools;

import com.fuad.enums.OsAction;
import com.fuad.interaction.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

class MoreToolsBrowserTest {
    private final RecordingInteraction interaction = new RecordingInteraction();
    private final List<ToolsActionRequest> actions = new ArrayList<>();

    @Test
    void emptyBrowserShouldNotCreateAnInteraction() {
        assertFalse(browser(0, 2).open());
        assertTrue(interaction.requests.isEmpty());
    }

    @Test
    void navigationShouldShowOnlyPageItemsAndDispatchTheResolverTarget() {
        MoreToolsBrowser browser = browser(5, 2);
        assertTrue(browser.open());
        assertEquals(List.of("app:tool-0", "app:tool-1", "nav:next"), ids());
        assertEquals(Set.of(InputModality.TOUCH), interaction.current().modalities());
        assertEquals(FocusRequirement.PASSIVE, interaction.current().focusRequirement());
        assertEquals("MORE TOOLS // PAGE 1 / 3", interaction.current().prompt());
        interaction.select("nav:next");
        assertEquals(List.of("app:tool-2", "app:tool-3", "nav:previous", "nav:next"), ids());
        interaction.select("nav:next");
        assertEquals(List.of("app:tool-4", "nav:previous"), ids());
        interaction.select("nav:previous");
        assertEquals("MORE TOOLS // PAGE 2 / 3", interaction.current().prompt());
        interaction.select("app:tool-3");
        assertEquals(List.of(new ToolsActionRequest(OsAction.OPEN_APPLICATION, "Resolver 3")), actions);
        assertEquals(4, interaction.requests.size());
    }

    @Test
    void featuredApplicationsShouldBeExcludedFromBrowser() {
        ToolsWorkspaceConfig config = new ToolsWorkspaceConfig(1, "TOOLS", 2, List.of("featured"),
                List.of(new ToolApplication("featured", "Featured", "Featured"),
                        new ToolApplication("other", "Other", "Other target")));
        MoreToolsBrowser browser = new MoreToolsBrowser(interaction, this::submit, config);
        assertTrue(browser.open());
        assertEquals(List.of("app:other", "nav:close"), ids());
    }

    @Test
    void singleRemainingApplicationShouldIncludeCloseAndRespectCancellation() {
        assertTrue(browser(1, 2).open());
        assertEquals(List.of("app:tool-0", "nav:close"), ids());
        interaction.select("nav:close");
        assertTrue(actions.isEmpty());
        assertEquals(1, interaction.requests.size());
    }

    @ParameterizedTest
    @EnumSource(value = InteractionOutcome.class, names = "SUBMITTED", mode = EnumSource.Mode.EXCLUDE)
    void terminalInteractionShouldNeverDispatchOrNavigate(InteractionOutcome outcome) {
        assertTrue(browser(3, 2).open());
        interaction.result.complete(InteractionResult.terminal(interaction.current(), outcome, null));
        assertTrue(actions.isEmpty());
        assertEquals(1, interaction.requests.size());
    }

    @ParameterizedTest
    @ValueSource(strings = {"app:unknown", "unexpected", "nav:previous"})
    void invalidSelectionOrNavigationBeforeFirstPageShouldHaveNoEffect(String selected) {
        assertTrue(browser(3, 2).open());
        interaction.select(selected);
        assertTrue(actions.isEmpty());
        assertEquals(1, interaction.requests.size());
    }

    @Test
    void deferredHandlersShouldRejectBeforeBindingAndForwardAfterBinding() {
        DeferredToolsActionHandler tools = new DeferredToolsActionHandler();
        DeferredMoreToolsHandler more = new DeferredMoreToolsHandler();
        ToolsActionRequest request = new ToolsActionRequest(OsAction.OPEN_APPLICATION, "Firefox");
        assertFalse(tools.submit(request));
        assertFalse(more.open());
        tools.bind(this::submit);
        more.bind(() -> true);
        assertTrue(tools.submit(request));
        assertEquals(List.of(request), actions);
        assertTrue(more.open());
        tools.bind(ignored -> false);
        assertFalse(tools.submit(request));
        assertThrows(NullPointerException.class, () -> tools.bind(null));
        assertThrows(NullPointerException.class, () -> more.bind(null));
    }

    private boolean submit(ToolsActionRequest request) {
        actions.add(request);
        return true;
    }

    private MoreToolsBrowser browser(int count, int pageSize) {
        List<ToolApplication> applications = IntStream.range(0, count)
                .mapToObj(i -> new ToolApplication("tool-" + i, "Display " + i, "Resolver " + i)).toList();
        return new MoreToolsBrowser(interaction, this::submit,
                new ToolsWorkspaceConfig(1, "TOOLS", pageSize, List.of(), applications));
    }

    private List<String> ids() {
        return interaction.current().options().stream().map(ChoiceOption::id).toList();
    }

    private static final class RecordingInteraction implements InteractionService {
        private final List<ChoiceRequest> requests = new ArrayList<>();
        private CompletableFuture<InteractionResult<String>> result;

        @Override
        @SuppressWarnings("unchecked")
        public <T> CompletionStage<InteractionResult<T>> request(InteractionRequest<T> request) {
            requests.add((ChoiceRequest) request);
            result = new CompletableFuture<>();
            return (CompletionStage<InteractionResult<T>>) (CompletionStage<?>) result;
        }

        ChoiceRequest current() { return requests.getLast(); }

        void select(String value) {
            result.complete(InteractionResult.submitted(current(), value, InputModality.TOUCH));
        }

        @Override public void close() { }
    }
}
