package com.fuad.view.workspace.web;

import javafx.animation.Animation;
import javafx.animation.FadeTransition;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.css.PseudoClass;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.util.Duration;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class ResearchWorkspaceView extends VBox {
    private static final DateTimeFormatter RESEARCH_TIME =  DateTimeFormatter.ofPattern("HH:mm");
    private static final PseudoClass SELECTED_SOURCE = PseudoClass.getPseudoClass("selected");
    private static final PseudoClass STATUS_COMPLETE = PseudoClass.getPseudoClass("complete");
    private static final PseudoClass STATUS_RESEARCHING = PseudoClass.getPseudoClass("researching");
    private static final PseudoClass STATUS_PARTIAL = PseudoClass.getPseudoClass("partial");
    private static final PseudoClass STATUS_FAILED =  PseudoClass.getPseudoClass("failed");
    private final Label providerLabel = new Label();
    private final Label statusLabel = new Label();
    private final Label ttsStatusLabel = new Label();
    private final Label sourceCountLabel = new Label();
    private final Label completedAtLabel = new Label();
    private final Label queryLabel = new Label();
    private final Label summaryLabel = new Label();
    private final Label selectedSourceDomain = new Label("HOME");
    private final Label selectedSourceTitle = new Label("NO SOURCE SELECTED");
    private final Label selectedSourceExcerpt = new Label("-");
    private final Label findingsCaption = subsectionTitle("KEY FINDINGS");
    private final Label visualsCaption = subsectionTitle("VISUAL // EVIDENCE");
    private final VBox findingsBox = new VBox(7.0);
    private final VBox sourcesList = new VBox(6.0);
    private final HBox visualsBox = new HBox(8.0);
    private final ResearchLifecycleListener lifecycleListener;
    private Button selectedSourceButton;
    private Timeline mockLifecycle;
    private FadeTransition statusPulse;

    public ResearchWorkspaceView() {
        this(ResearchLifecycleListener.noop());
    }

    public ResearchWorkspaceView(ResearchLifecycleListener lifecycleListener) {
        this.lifecycleListener = lifecycleListener != null ? lifecycleListener : ResearchLifecycleListener.noop();

        setSpacing(10.0);

        Label title = new Label("WEB // RESEARCH");
        title.getStyleClass().add("workspace-view-title");
        providerLabel.getStyleClass().add("research-provider");

        VBox identity = new VBox(2.0, title, providerLabel);
        HBox statusBar = createStatusBar();
        VBox query = createQueryPanel();
        GridPane body = createBody();
        VBox.setVgrow(body, Priority.ALWAYS);

        getChildren().addAll(identity, statusBar, query, body);

        startMockLifecycle();
    }

    public void update(ResearchWorkspaceSnapshot snapshot) {
        providerLabel.setText("ENGINE //  " + snapshot.provider().toUpperCase() + " // MOCK");
        updateState(snapshot.state());
        ttsStatusLabel.setText("TTS // " + snapshot.ttsState().name());
        sourceCountLabel.setText("SOURCES // %02d".formatted(snapshot.sources().size()));
        completedAtLabel.setText("UPDATED // " + snapshot.completedAt());
        queryLabel.setText(snapshot.query());
        summaryLabel.setText(snapshot.summary());
        renderFindings(snapshot.findings());
        renderVisuals(snapshot.visuals());
        renderSources(snapshot.sources(), snapshot.state());
        updateSectionVisibility(snapshot);
        lifecycleListener.onResearchUpdated(snapshot);
    }

    private HBox createStatusBar() {
        statusLabel.getStyleClass().add("research-status");
        ttsStatusLabel.getStyleClass().add("research-meta");
        sourceCountLabel.getStyleClass().add("research-meta");
        completedAtLabel.getStyleClass().add("research-meta");

        Region spacer =  new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox bar = new HBox(14.0, statusLabel, spacer, ttsStatusLabel, sourceCountLabel, completedAtLabel);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.getStyleClass().add("research-status-bar");
        return bar;
    }

    private VBox createQueryPanel() {
        Label caption = new Label("CURRENT_RESEARCH // QUERY");

        caption.getStyleClass().add("research-caption");
        queryLabel.setWrapText(true);
        queryLabel.getStyleClass().add("research-query");

        VBox panel = new VBox(5.0, caption, queryLabel);
        panel.setPadding(new Insets(10.0, 12.0, 10.0, 12.0));
        panel.getStyleClass().add("research-query-panel");
        return panel;
    }

    private GridPane createBody() {
        GridPane gridPane = new GridPane();
        gridPane.setHgap(10.0);

        ColumnConstraints briefColumn = new ColumnConstraints();
        briefColumn.setPercentWidth(58.0);
        briefColumn.setHgrow(Priority.ALWAYS);

        ColumnConstraints evidenceColumn = new ColumnConstraints();
        evidenceColumn.setPercentWidth(42.0);
        evidenceColumn.setHgrow(Priority.ALWAYS);

        RowConstraints row = new RowConstraints();
        row.setPercentHeight(100.0);
        row.setVgrow(Priority.ALWAYS);

        gridPane.getColumnConstraints().addAll(briefColumn, evidenceColumn);
        gridPane.getRowConstraints().add(row);

        VBox brief = createBriefPanel();
        VBox evidence = createEvidencePanel();

        gridPane.add(brief, 0, 0);
        gridPane.add(evidence, 1, 0);

        makeFill(brief);
        makeFill(evidence);
        return gridPane;
    }

    private VBox createBriefPanel() {
        Label title = sectionTitle("RESEARCH // BRIEF");
        Label summaryCaption = subsectionTitle("EXECUTIVE SUMMARY");

        summaryLabel.setWrapText(true);
        summaryLabel.getStyleClass().add("research-summary");

        VBox panel = new VBox(10.0, title, summaryCaption, summaryLabel, findingsCaption, findingsBox, visualsCaption, visualsBox);
        panel.setPadding(new Insets(13.0));
        panel.getStyleClass().add("research-card");
        return panel;
    }

    private VBox createEvidencePanel() {
        Label title = sectionTitle("SOURCES // EVIDENCE");

        ScrollPane scrollPane = new ScrollPane(sourcesList);
        scrollPane.setFitToWidth(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scrollPane.getStyleClass().add("research-scroll");

        VBox.setVgrow(scrollPane, Priority.ALWAYS);
        VBox sourceContext = createSourceContext();
        VBox panel = new VBox(10.0, title, scrollPane, sourceContext);
        panel.setPadding(new Insets(13.0));
        panel.getStyleClass().add("research-card");
        return panel;
    }

    private VBox createSourceContext() {
        Label title = new Label("SOURCE // CONTEXT");
        title.getStyleClass().add("research-context-title");

        selectedSourceDomain.getStyleClass().add("research-source-domain-active");

        selectedSourceTitle.setWrapText(true);
        selectedSourceTitle.setMaxWidth(Double.MAX_VALUE);
        selectedSourceTitle.getStyleClass().add("research-source-context-title");

        selectedSourceExcerpt.setWrapText(true);
        selectedSourceExcerpt.setMaxWidth(Double.MAX_VALUE);
        selectedSourceExcerpt.getStyleClass().add("research-source-excerpt");

        VBox context = new VBox(5.0, title, selectedSourceDomain, selectedSourceTitle, selectedSourceExcerpt);
        context.setPadding(new Insets(10.0));
        context.setMinHeight(118.0);
        context.setPrefHeight(118.0);
        context.setMaxHeight(118.0);
        context.getStyleClass().add("research-source-context");
        return context;
    }

    private Button createSourceRow(ResearchSource source) {
        Label id = new Label(source.id());
        Label domain =  new Label(source.domain());
        Label title = new Label(source.title());

        id.getStyleClass().add("research-source-id");
        domain.getStyleClass().add("research-source-domain");
        title.setWrapText(true);
        title.getStyleClass().add("research-source-title");

        VBox text = new VBox(2.0, domain, title);
        HBox.setHgrow(text, Priority.ALWAYS);
        HBox content = new HBox(9.0, id, text);
        content.setAlignment(Pos.CENTER_LEFT);
        content.setMaxWidth(Double.MAX_VALUE);
        text.setMaxWidth(Double.MAX_VALUE);
        title.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(text, Priority.ALWAYS);

        Button row = new Button();
        row.setGraphic(content);
        row.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setMaxWidth(Double.MAX_VALUE);
        row.setMinHeight(64.0);
        row.setPrefHeight(64.0);
        row.setMaxHeight(72.0);
        row.getStyleClass().add("research-source");
        row.setOnAction(event -> selectSource(row, source));
        return row;
    }

    private void selectSource(Button button, ResearchSource source) {
        if (selectedSourceButton != null) {
            selectedSourceButton.pseudoClassStateChanged(SELECTED_SOURCE, false);
        }
        selectedSourceButton = button;
        button.pseudoClassStateChanged(SELECTED_SOURCE, true);
        selectedSourceDomain.setText(source.domain());
        selectedSourceTitle.setText(source.title());
        selectedSourceExcerpt.setText(source.excerpt());
    }

    private void renderFindings(List<String> findings) {
        findingsBox.getChildren().clear();

        for (int i = 0; i < findings.size(); i++) {
            Label index = new Label("%02d".formatted(i + 1));
            index.getStyleClass().add("research-finding-index");

            Label text = new Label(findings.get(i));
            text.setWrapText(true);
            text.getStyleClass().add("research-finding-text");

            HBox.setHgrow(text, Priority.ALWAYS);
            HBox row = new HBox(9.0, index, text);
            row.setAlignment(Pos.TOP_LEFT);
            row.getStyleClass().add("research-finding-row");

            findingsBox.getChildren().add(row);

            animateArrival(row, i * 70.0);
        }
    }

    private void renderVisuals(List<ResearchVisual> visuals) {
        int i = 0;

        visualsBox.getChildren().clear();

        for (ResearchVisual visual : visuals) {
            VBox card = createVisualCard(visual);
            HBox.setHgrow(card, Priority.ALWAYS);
            visualsBox.getChildren().add(card);
            animateArrival(card, i++ * 110.0);
        }
    }

    private VBox createVisualCard(ResearchVisual visual) {
        Label placeholder = new Label(visual.label());
        placeholder.getStyleClass().add("research-visual-placeholder");

        StackPane imageArea = new StackPane(placeholder);
        imageArea.setMinHeight(85.0);
        imageArea.setPrefHeight(85.0);
        imageArea.getStyleClass().add("research-visual-image");

        Label caption = new Label(visual.caption());
        caption.setWrapText(true);
        caption.getStyleClass().add("research-visual-caption");

        VBox card = new VBox(6.0, imageArea, caption);
        card.setMaxWidth(Double.MAX_VALUE);
        card.getStyleClass().add("research-visual-card");
        return card;
    }

    private void updateState(ResearchState researchState) {
        statusLabel.pseudoClassStateChanged(STATUS_COMPLETE, false);
        statusLabel.pseudoClassStateChanged(STATUS_RESEARCHING, false);
        statusLabel.pseudoClassStateChanged(STATUS_PARTIAL, false);
        statusLabel.pseudoClassStateChanged(STATUS_FAILED, false);

        switch (researchState) {
            case COMPLETE -> statusLabel.pseudoClassStateChanged(STATUS_COMPLETE, true);
            case RESEARCHING ->  statusLabel.pseudoClassStateChanged(STATUS_RESEARCHING, true);
            case PARTIAL -> statusLabel.pseudoClassStateChanged(STATUS_PARTIAL, true);
            case FAILED -> statusLabel.pseudoClassStateChanged(STATUS_FAILED, true);
            case IDLE -> { /* Empty */ }
        }
        statusLabel.setText("● " + researchState.name());
        if (researchState == ResearchState.RESEARCHING) {
            startResearchingPulse();
        }
        else {
            stopResearchingPulse();
        }
    }

    private void renderSources(List<ResearchSource> sources, ResearchState state) {
        sourcesList.getChildren().clear();

        selectedSourceButton = null;

        if (sources.isEmpty()) {
            selectedSourceDomain.setText(state == ResearchState.RESEARCHING ? "ACQUIRING // SOURCES" : "NONE");
            selectedSourceTitle.setText(state == ResearchState.RESEARCHING ? "SEARCHING FOR EVIDENCE" : "NO SOURCE SELECTED");
            selectedSourceExcerpt.setText(state == ResearchState.RESEARCHING ? "Resolving and validating available sources..." : "-");
            return;
        }

        Button firstButton = null;
        ResearchSource firstSource = null;
        int i = 0;

        for (ResearchSource source : sources) {
            Button row = createSourceRow(source);
            sourcesList.getChildren().add(row);
            animateArrival(row, i++ * 70.0);
            if (firstButton == null) {
                firstButton = row;
                firstSource = source;
            }
        }
        if (firstButton != null) {
            selectSource(firstButton, firstSource);
        }
    }

    private static void makeFill(Region region) {
        region.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        GridPane.setHgrow(region, Priority.ALWAYS);
        GridPane.setVgrow(region, Priority.ALWAYS);
        GridPane.setFillWidth(region, true);
        GridPane.setFillHeight(region, true);
    }

    private void startMockLifecycle() {
        if (mockLifecycle != null) {
            mockLifecycle.stop();
        }
        mockLifecycle = new Timeline(new KeyFrame(Duration.ZERO, event -> update(researchingSnapshot())),
                        new KeyFrame(Duration.seconds(2.2), event -> update(partialSnapshot())),
                        new KeyFrame(Duration.seconds(4.7), event -> update(speakingSnapshot())),
                        new KeyFrame(Duration.seconds(7.0), event -> update(completeSnapshot())));
        mockLifecycle.setCycleCount(1);
        mockLifecycle.play();
    }

    private void updateSectionVisibility(ResearchWorkspaceSnapshot snapshot) {
        boolean hasFindings = !snapshot.findings().isEmpty();

        findingsCaption.setManaged(hasFindings);
        findingsCaption.setVisible(hasFindings);
        findingsBox.setManaged(hasFindings);
        findingsBox.setVisible(hasFindings);

        boolean hasVisuals = !snapshot.visuals().isEmpty();

        visualsCaption.setManaged(hasVisuals);
        visualsCaption.setVisible(hasVisuals);
        visualsBox.setManaged(hasVisuals);
        visualsBox.setVisible(hasVisuals);
    }

    private void animateArrival(Node node, double delayMillis) {
        node.setOpacity(0.0);

        FadeTransition fade = new FadeTransition(Duration.millis(240), node);
        fade.setDelay(Duration.millis(delayMillis));
        fade.setFromValue(0.0);
        fade.setToValue(1.0);
        fade.play();
    }

    private void startResearchingPulse() {
        if (statusPulse != null) {
            statusPulse.stop();
        }
        statusPulse = new FadeTransition(Duration.millis(650), statusLabel);
        statusPulse.setFromValue(1.0);
        statusPulse.setToValue(0.65);
        statusPulse.setAutoReverse(true);
        statusPulse.setCycleCount(Animation.INDEFINITE);
        statusPulse.play();
    }

    private void stopResearchingPulse() {
        if (statusPulse != null) {
            statusPulse.stop();
            statusPulse = null;
        }
        statusLabel.setOpacity(1.0);
    }

    private static Label sectionTitle(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("research-section-title");
        return label;
    }

    private static Label subsectionTitle(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("research-subsection-title");
        return label;
    }

    private static ResearchWorkspaceSnapshot researchingSnapshot() {
        return new ResearchWorkspaceSnapshot(
                ResearchState.RESEARCHING,
                "GPT API + BROWSER",
                "¿Qué modelos locales son adecuados para una RTX 4070 "
                        + "de 12 GB evitando CPU off-load?",
                "Acquiring sources, resolving relevant documents "
                        + "and validating available evidence...",
                List.of(),
                List.of(),
                List.of(),
                ResearchTtsState.PENDING,
                "--:--"
        );
    }

    private static ResearchWorkspaceSnapshot partialSnapshot() {
        return new ResearchWorkspaceSnapshot(
                ResearchState.PARTIAL,
                "GPT API + BROWSER",
                "¿Qué modelos locales son adecuados para una RTX 4070 "
                        + "de 12 GB evitando CPU off-load?",
                "La evidencia preliminar favorece configuraciones que "
                        + "mantengan el modelo completamente dentro de VRAM "
                        + "y permitan validar localmente latencia y estabilidad.",
                List.of(
                        "El ajuste completo dentro de VRAM debe ser una "
                                + "restricción explícita del despliegue.",
                        "La cuantización modifica el consumo de memoria "
                                + "y debe evaluarse junto con la calidad."
                ),
                List.of(
                        new ResearchSource(
                                "01",
                                "NVIDIA // DOCUMENTATION",
                                "GPU memory and execution constraints",
                                "Referencia técnica utilizada para contextualizar "
                                        + "los límites de ejecución sobre GPU."
                        ),
                        new ResearchSource(
                                "02",
                                "LM STUDIO // RUNTIME",
                                "Local model runtime characteristics",
                                "Información de runtime utilizada para contrastar "
                                        + "carga, memoria y ejecución local."
                        )
                ),
                List.of(),
                ResearchTtsState.PENDING,
                "--:--"
        );
    }

    private static ResearchWorkspaceSnapshot speakingSnapshot() {
        return new ResearchWorkspaceSnapshot(
                ResearchState.COMPLETE,
                "GPT API + BROWSER",
                "¿Qué modelos locales son adecuados para una RTX 4070 "
                        + "de 12 GB evitando CPU off-load?",
                "Se compararon alternativas locales priorizando ajuste "
                        + "completo en VRAM, latencia interactiva y "
                        + "estabilidad de ejecución. La evidencia reunida "
                        + "queda resumida en los hallazgos siguientes.",
                List.of(
                        "El ajuste completo dentro de VRAM debe ser una "
                                + "restricción explícita del despliegue.",
                        "La cuantización modifica el consumo de memoria "
                                + "y debe evaluarse junto con la calidad.",
                        "Las mediciones locales de latencia y estabilidad "
                                + "son necesarias antes de fijar el modelo."
                ),
                List.of(
                        new ResearchSource(
                                "01",
                                "NVIDIA // DOCUMENTATION",
                                "GPU memory and execution constraints",
                                "Referencia técnica utilizada para contextualizar "
                                        + "los límites de ejecución sobre GPU."
                        ),
                        new ResearchSource(
                                "02",
                                "LM STUDIO // RUNTIME",
                                "Local model runtime characteristics",
                                "Información de runtime utilizada para contrastar "
                                        + "carga, memoria y ejecución local."
                        ),
                        new ResearchSource(
                                "03",
                                "MODEL CARD // REFERENCE",
                                "Model architecture and quantization",
                                "Ficha del modelo utilizada para contrastar "
                                        + "arquitectura, contexto y variantes."
                        ),
                        new ResearchSource(
                                "04",
                                "LOCAL BENCHMARK // ARES",
                                "Interactive latency measurements",
                                "Resultados locales utilizados para comparar "
                                        + "latencia y estabilidad."
                        )
                ),
                List.of(
                        new ResearchVisual(
                                "01",
                                "VISUAL // 01",
                                "VRAM envelope"
                        ),
                        new ResearchVisual(
                                "02",
                                "VISUAL // 02",
                                "Latency profile"
                        )
                ),
                ResearchTtsState.SPEAKING,
                LocalTime.now().format(
                        RESEARCH_TIME
                )
        );
    }

    private static ResearchWorkspaceSnapshot completeSnapshot() {
        return new ResearchWorkspaceSnapshot(
                ResearchState.COMPLETE,
                "GPT API + BROWSER",
                "¿Qué modelos locales son adecuados para una RTX 4070 "
                        + "de 12 GB evitando CPU off-load?",
                "Se compararon alternativas locales priorizando ajuste "
                        + "completo en VRAM, latencia interactiva y "
                        + "estabilidad de ejecución. La evidencia reunida "
                        + "queda resumida en los hallazgos siguientes.",
                List.of(
                        "El ajuste completo dentro de VRAM debe ser una "
                                + "restricción explícita del despliegue.",
                        "La cuantización modifica el consumo de memoria "
                                + "y debe evaluarse junto con la calidad.",
                        "Las mediciones locales de latencia y estabilidad "
                                + "son necesarias antes de fijar el modelo."
                ),
                List.of(
                        new ResearchSource(
                                "01",
                                "NVIDIA // DOCUMENTATION",
                                "GPU memory and execution constraints",
                                "Referencia técnica utilizada para contextualizar "
                                        + "los límites de ejecución sobre GPU."
                        ),
                        new ResearchSource(
                                "02",
                                "LM STUDIO // RUNTIME",
                                "Local model runtime characteristics",
                                "Información de runtime utilizada para contrastar "
                                        + "carga, memoria y ejecución local."
                        ),
                        new ResearchSource(
                                "03",
                                "MODEL CARD // REFERENCE",
                                "Model architecture and quantization",
                                "Ficha del modelo utilizada para contrastar "
                                        + "arquitectura, contexto y variantes."
                        ),
                        new ResearchSource(
                                "04",
                                "LOCAL BENCHMARK // ARES",
                                "Interactive latency measurements",
                                "Resultados locales utilizados para comparar "
                                        + "latencia y estabilidad."
                        )
                ),
                List.of(
                        new ResearchVisual(
                                "01",
                                "VISUAL // 01",
                                "VRAM envelope"
                        ),
                        new ResearchVisual(
                                "02",
                                "VISUAL // 02",
                                "Latency profile"
                        )
                ),
                ResearchTtsState.DELIVERED,
                LocalTime.now().format(
                        RESEARCH_TIME
                )
        );
    }
}
