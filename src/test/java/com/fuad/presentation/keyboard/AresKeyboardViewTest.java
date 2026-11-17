package com.fuad.presentation.keyboard;

import com.fuad.presentation.JavaFxRuntime;
import javafx.event.ActionEvent;
import javafx.scene.AccessibleAction;
import javafx.scene.AccessibleAttribute;
import javafx.scene.AccessibleRole;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.text.Text;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class AresKeyboardViewTest {
    private static JavaFxRuntime runtime;

    @BeforeAll
    static void initToolkit() {
        runtime = new JavaFxRuntime();
    }

    @Test
    void changingContextPreservesTheDraft() {
        runtime.runAndWait(() -> {
            AresKeyboardView view = new AresKeyboardView();
            view.setQuery("Buscar archivos del proyecto");

            String[] actions = {"music", "file", "apps", "web"};
            String[] modes = {"MEDIA", "FILES", "SYSTEM", "QUERY"};
            for (int i = 0; i < actions.length; i++) {
                view.activate("action-" + actions[i]);
                assertEquals(modes[i], view.getMode());
                assertEquals("Buscar archivos del proyecto", view.getQuery());
            }
        });
    }

    @Test
    void contextualSuggestionFillsTheQuery() {
        runtime.runAndWait(() -> {
            AresKeyboardView view = new AresKeyboardView();
            view.setQuery("Borrador anterior");
            view.lookup("#suggestion-0").executeAccessibleAction(AccessibleAction.FIRE);

            assertEquals("QUERY", view.getMode());
            assertTrue(view.getQuery().contains("Google"));
            assertNotEquals("Borrador anterior", view.getQuery());
        });
    }

    @Test
    void executingAnEmptyQueryRequestsInput() {
        runtime.runAndWait(() -> {
            AresKeyboardView view = new AresKeyboardView();
            view.activate("execute");

            assertEquals("", view.getQuery());
            assertTrue(text(view, "keyboard-feedback").contains("Escribe"));
        });
    }

    @Test
    void playingAModelReleasesThePreviouslyActiveModel() {
        runtime.runAndWait(() -> {
            AresKeyboardView view = new AresKeyboardView();
            view.setQuery("Consulta pendiente");
            assertSelectedModel(view, 0);

            for (int model = 1; model < 4; model++) {
                view.activate("model-toggle-" + model);
                assertSelectedModel(view, model);
                assertEquals("Consulta pendiente", view.getQuery());
            }
        });
    }

    @Test
    void stoppingTheActiveModelLeavesAllModelsInStandbyAndAllowsRestarting() {
        runtime.runAndWait(() -> {
            AresKeyboardView view = new AresKeyboardView();
            for (int model = 0; model < 4; model++) {
                if (model > 0) view.activate("model-toggle-" + model);
                assertSelectedModel(view, model);

                view.activate("model-toggle-" + model);
                assertSelectedModel(view, -1);

                view.activate("model-toggle-" + model);
                assertSelectedModel(view, model);
            }
        });
    }

    @Test
    void unknownControlsAreRejectedWithoutChangingTheDraft() {
        runtime.runAndWait(() -> {
            AresKeyboardView view = new AresKeyboardView();
            view.setQuery("Consulta pendiente");

            assertThrows(IllegalArgumentException.class, () -> view.activate("missing-control"));
            assertEquals("Consulta pendiente", view.getQuery());
            assertEquals("QUERY", view.getMode());
        });
    }

    @Test
    void escapeClearsTheDraftWithoutChangingTheSelectedModel() {
        runtime.runAndWait(() -> {
            AresKeyboardView view = new AresKeyboardView();
            view.setQuery("Consulta pendiente");
            view.activate("model-toggle-1");

            escape(view);
            assertEquals("", view.getQuery());
            assertSelectedModel(view, 1);
        });
    }

    @Test
    void unmuteAndDeckInvokeTheirApplicationCallbacksWithoutLosingTheDraft() {
        runtime.runAndWait(() -> {
            AtomicInteger unmuteCalls = new AtomicInteger();
            AtomicInteger deckFocusCalls = new AtomicInteger();
            AresKeyboardView view = new AresKeyboardView(unmuteCalls::incrementAndGet, deckFocusCalls::incrementAndGet);
            view.setQuery("Consulta pendiente");

            view.activate("mcp-unmute");
            view.activate("mcp-unmute");
            view.activate("mcp-deck");

            assertEquals(2, unmuteCalls.get());
            assertEquals(1, deckFocusCalls.get());
            assertEquals("Consulta pendiente", view.getQuery());
        });
    }

    @Test
    void leftClusterContainsOnlyTheFiveRequestedActions() {
        runtime.runAndWait(() -> {
            AresKeyboardView view = new AresKeyboardView();
            String[] controls = {"mcp-unmute", "mcp-deck", "mcp-weather", "mcp-dj", "mcp-stub"};
            for (String id : controls) {
                Node control = view.lookup("#" + id);
                assertNotNull(control, id);
                assertFalse(containsText(control), id + " must display only its icon");
                assertNotNull(control.getAccessibleText(), id + " must retain an accessible name");
            }
            assertEquals(5, view.getChildren().stream()
                    .filter(node -> node.getId() != null && node.getId().startsWith("mcp-")).count());
            assertNull(view.lookup("#mode-query"));
            assertNull(view.lookup("#nav-windows"));
            assertNull(view.lookup("#nav-home"));
            assertNull(view.lookup("#nav-back"));
        });
    }

    @Test
    void contextualShortcutsPrepareASuitableQueryWhenTheInputIsEmpty() {
        runtime.runAndWait(() -> {
            AresKeyboardView view = new AresKeyboardView();
            String[] controls = {"mcp-weather", "mcp-dj", "mcp-stub"};
            String[] modes = {"QUERY", "MEDIA", "SYSTEM"};
            String[] queries = {"Clima en Talca", "Buscar en TIDAL", "Ver integraciones MCP"};
            for (int i = 0; i < controls.length; i++) {
                view.setQuery("");
                view.activate(controls[i]);
                assertEquals(modes[i], view.getMode());
                assertEquals(queries[i], view.getQuery());
            }
        });
    }

    @Test
    void djAndMcpSelectTheirContextWhilePreservingAnExistingDraft() {
        runtime.runAndWait(() -> {
            AresKeyboardView view = new AresKeyboardView();
            view.setQuery("Consulta pendiente");

            view.activate("mcp-dj");
            assertEquals("MEDIA", view.getMode());
            assertEquals("Consulta pendiente", view.getQuery());

            view.activate("mcp-stub");
            assertEquals("SYSTEM", view.getMode());
            assertEquals("Consulta pendiente", view.getQuery());
        });
    }

    @Test
    void localDemoCanUnmuteAndFocusDeckWithoutApplicationCallbacks() {
        runtime.runAndWait(() -> {
            AresKeyboardView view = new AresKeyboardView();
            assertDoesNotThrow(() -> view.activate("mcp-unmute"));
            assertDoesNotThrow(() -> view.activate("mcp-deck"));
            assertTrue(text(view, "keyboard-feedback").toLowerCase(java.util.Locale.ROOT).contains("deck"));
        });
    }

    @Test
    void externallyRemutingTheKeyboardRestoresItsStatusWithoutInvokingUnmute() {
        runtime.runAndWait(() -> {
            AtomicInteger unmuteCalls = new AtomicInteger();
            AresKeyboardView view = new AresKeyboardView(unmuteCalls::incrementAndGet, null);
            view.activate("mcp-unmute");
            assertEquals("VOICE // ON", text(view, "voice-status"));
            assertEquals(1, unmuteCalls.get());

            view.setVoiceMuted(true);
            assertEquals("VOICE // MUTED", text(view, "voice-status"));
            assertEquals(1, unmuteCalls.get());
            view.setVoiceMuted(false);
            assertEquals("VOICE // ON", text(view, "voice-status"));
            assertEquals(1, unmuteCalls.get());
        });
    }

    private static void escape(AresKeyboardView view) {
        Node input = view.lookup("#query-input");
        input.fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.ESCAPE, false, false, false, false));
    }

    @Test
    void submittingTheInputRunsTheSelectedActionWithItsCurrentText() {
        runtime.runAndWait(() -> {
            AresKeyboardView view = new AresKeyboardView();
            view.activate("action-music");
            TextField input = assertInstanceOf(TextField.class, view.lookup("#query-input"));
            input.setText("Jazz para trabajar");
            input.fireEvent(new ActionEvent(input, input));

            assertEquals("Jazz para trabajar", view.getQuery());
            assertTrue(text(view, "keyboard-feedback").startsWith("DEMO // MUSIC"));
            assertTrue(text(view, "keyboard-feedback").contains("Jazz para trabajar"));
        });
    }

    @Test
    void iconOnlyModelSwitchesExposeTheirNamesAndChangingStateToAccessibility() {
        runtime.runAndWait(() -> {
            AresKeyboardView view = new AresKeyboardView();
            String[] names = {"Qwen 2.5 14B", "Phi 4 14B", "Gemma 2 9B", "Llama 3.2 11B"};
            assertEquals("Estado LLMs", text(view, "llm-status-title"));
            for (int model = 0; model < names.length; model++) {
                assertEquals(names[model], text(view, "model-name-" + model));
                Node control = view.lookup("#model-toggle-" + model);
                assertNotNull(control);
                assertEquals(AccessibleRole.TOGGLE_BUTTON, control.getAccessibleRole());
                assertTrue(control.isFocusTraversable());
                assertFalse(containsText(control), "Model switches must display only icons");
                assertTrue(control.getAccessibleText().contains(names[model]));
                assertTrue(control.getAccessibleText().contains("demostración"));

                if (model > 0) control.executeAccessibleAction(AccessibleAction.FIRE);
                assertSelectedModel(view, model);
                Node active = view.lookup("#model-toggle-" + model);
                assertTrue(active.getAccessibleText().contains("Detener"));
                assertTrue(active.getAccessibleText().contains("activo"));

                active.executeAccessibleAction(AccessibleAction.FIRE);
                assertSelectedModel(view, -1);
                Node standby = view.lookup("#model-toggle-" + model);
                assertTrue(standby.getAccessibleText().contains("Activar"));
                assertTrue(standby.getAccessibleText().contains("standby"));
            }
        });
    }

    private static void assertSelectedModel(AresKeyboardView view, int selected) {
        for (int model = 0; model < 4; model++) {
            Node control = view.lookup("#model-toggle-" + model);
            assertNotNull(control);
            assertEquals(model == selected, control.queryAccessibleAttribute(AccessibleAttribute.SELECTED),
                    "Selection state for model " + model);
        }
    }

    private static boolean containsText(Node node) {
        return node instanceof Text || node instanceof Parent parent
                && parent.getChildrenUnmodifiable().stream().anyMatch(AresKeyboardViewTest::containsText);
    }

    private static String text(AresKeyboardView view, String id) {
        return assertInstanceOf(Text.class, view.lookup("#" + id)).getText();
    }
}
