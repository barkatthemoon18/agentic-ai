package com.fuad.view.workspace.files;

import com.fuad.view.icon.HudIcon;
import com.fuad.view.icon.HudIconView;
import javafx.css.PseudoClass;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.util.*;

public class FilesWorkspaceView extends VBox {
    private static final String HOME_PATH = "HOME/Desktop/Proyectos Personales/AI";
    private static final PseudoClass ACTIVE_LOCATION = PseudoClass.getPseudoClass("active");
    private static final PseudoClass SELECTED = PseudoClass.getPseudoClass("selected");
    private final Deque<String> history = new ArrayDeque<>();
    private final Map<String, FilesWorkspaceSnapshot> mockFileSystem = createMockFileSystem();
    private final TextField searchField = new TextField();
    private final VBox fileList = new VBox(4.0);
    private final Label selectedName = new Label("NONE");
    private final Label selectedType = new Label("-");
    private final Label selectedSize = new Label("-");
    private final Label selectedModified = new Label("-");
    private final HBox breadcrumb = new HBox(5.0);
    private Button selectedRow;
    private Button activeLocation;
    private FilesWorkspaceSnapshot currentSnapshot;
    private String currentPath = HOME_PATH;

    public FilesWorkspaceView() {
        setSpacing(12.0);

        Label title = new Label("FILES // EXPLORER");
        Label provider = new Label("LOCAL FILESYSTEM // MOCK");

        title.getStyleClass().add("workspace-view-title");
        provider.getStyleClass().add("files-provider");

        VBox identity = new VBox(2.0, title, provider);
        HBox navigation = createNavigation();
        HBox explorer = createExplorer();
        VBox context = createContextPanel();
        VBox.setVgrow(explorer, Priority.ALWAYS);

        getChildren().addAll(identity, navigation, explorer, context);

        navigateTo(HOME_PATH, false);
    }

    private void navigateTo(String path, boolean pushHistory) {
        FilesWorkspaceSnapshot snapshot = mockFileSystem.get(path);

        if (snapshot == null) {
            return;
        }
        if (pushHistory && currentPath != null && !currentPath.equals(path)) {
            history.push(currentPath);
        }
        currentPath = path;
        update(snapshot);
        updateBreadcrumb(path);
    }

    public void update(FilesWorkspaceSnapshot snapshot) {
        currentSnapshot = snapshot;
        searchField.clear();
        renderEntries(snapshot.entries());
    }

    private HBox createNavigation() {
        Button back = navigationButton(HudIcon.BACK);
        Button up = navigationButton(HudIcon.UP);
        Button home = navigationButton(HudIcon.HOME);

        /* Tooltip */
        back.setTooltip(new Tooltip("Back"));
        up.setTooltip(new Tooltip("Up"));
        home.setTooltip(new Tooltip("Home"));

        /* Button actions */
        back.setOnAction(event -> {
            if (history.isEmpty()) {
                return;
            }
            String previous = history.pop();
            navigateTo(previous, false);
        });
        up.setOnAction(event -> {
            int separator = currentPath.lastIndexOf('/');
            if (separator <= 0) {
                return;
            }
            String parent = currentPath.substring(0, separator);
            navigateTo(parent, true);
        });
        home.setOnAction(event -> navigateTo(HOME_PATH, true));

        HBox breadcrumbView = createBreadcrumb();
        HBox.setHgrow(breadcrumbView, Priority.ALWAYS);

        searchField.setPromptText("SEARCH FILES");
        searchField.getStyleClass().add("files-search");
        searchField.setPrefWidth(165.0);
        searchField.textProperty().addListener((observable, oldValue, newValue) -> applySearch(newValue));

        HBox result = new HBox(7.0, back, up, home, breadcrumbView, searchField);
        result.setAlignment(Pos.CENTER_LEFT);
        return result;
    }

    private Button navigationButton(HudIcon icon) {
        Button button = new Button();

        button.setGraphic(new HudIconView(icon, 14.0));
        button.getStyleClass().add("files-nav-button");
        return button;
    }

    private HBox createBreadcrumb() {
        breadcrumb.setAlignment(Pos.CENTER_LEFT);
        breadcrumb.getStyleClass().add("files-breadcrumb");
        return breadcrumb;
    }

    private void updateBreadcrumb(String path) {
        StringBuilder accumlatedPath;
        List<String> parts = Arrays.stream(path.split("/")).filter(part -> !part.isBlank()).toList();

        breadcrumb.getChildren().clear();
        if (parts.isEmpty()) {
            return;
        }
        breadcrumb.getChildren().add(breadcrumbPart(parts.getFirst(), parts.getFirst()));
        if (parts.size() > 4) {
            breadcrumb.getChildren().add(breadcrumbSeparator());
        }
        Label collapsed = new Label("...");
        collapsed.getStyleClass().add("files-breadcrumb-separator");
        breadcrumb.getChildren().add(collapsed);
        int start = Math.max(1, parts.size() - 3);
        accumlatedPath = new StringBuilder(parts.getFirst());
        for (int i = 1; i < parts.size(); i++) {
            accumlatedPath.append('/').append(parts.get(i));
            if (i < start) {
                continue;
            }
            breadcrumb.getChildren().add(breadcrumbPart(parts.get(i), accumlatedPath.toString()));
        }
    }

    private Button breadcrumbPart(String text, String targetPath) {
        Button button = new Button(text);
        button.getStyleClass().add("files-breadcrumb-part");
        button.setFocusTraversable(false);
        button.setOnAction(event -> {
            if (!targetPath.equalsIgnoreCase(currentPath)) {
                navigateTo(targetPath, true);
            }
        });
        return button;
    }

    private Label breadcrumbSeparator() {
        Label separator = new Label(">");
        separator.getStyleClass().add("files-breadcrumb-separator");
        return separator;
    }

    private HBox createExplorer() {
        VBox locations = createLocations();
        VBox content = createFileContent();

        HBox.setHgrow(content, Priority.ALWAYS);
        HBox result = new HBox(12.0, locations, content);
        result.setFillHeight(true);
        return result;
    }

    private VBox createLocations() {
        Label title = new Label("LOCATIONS");

        title.getStyleClass().add("files-section-title");

        VBox locations = new VBox(6.0, title, locationButton("PROJECTS", HOME_PATH), locationButton("DOWNLOADS", null),
                locationButton("DOCUMENTS", null), locationButton("DESKTOP", null), locationButton("RECENT", null));
        locations.setPadding(new Insets(12.0));
        locations.setPrefWidth(135.0);
        locations.setMinWidth(135.0);
        locations.setMaxWidth(135.0);
        locations.getStyleClass().add("files-locations");
        return locations;
    }

    private Button locationButton(String name, String targetPath) {
        Button button = new Button(name);

        button.setMaxWidth(Double.MAX_VALUE);
        button.getStyleClass().add("files-location");
        button.setOnAction(event -> {
            setActiveLocation(button);
            if (targetPath != null && mockFileSystem.containsKey(targetPath)) {
                navigateTo(targetPath, true);
            }
        });
        return button;
    }

    private void setActiveLocation(Button button) {
        if (activeLocation != null) {
            activeLocation.pseudoClassStateChanged(ACTIVE_LOCATION, false);
        }
        activeLocation = button;
        activeLocation.pseudoClassStateChanged(ACTIVE_LOCATION, true);
    }

    private VBox createFileContent() {
        Label name = new Label("NAME");
        Label type =  new Label("TYPE");
        Label size = new Label("SIZE");

        name.getStyleClass().add("files-column-header");
        type.getStyleClass().add("files-column-header");
        size.getStyleClass().add("files-column-header");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox header = new HBox(10.0, name, spacer, type, size);
        header.setPadding(new Insets(0, 12, 6, 12));

        ScrollPane scroll = new ScrollPane(fileList);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scroll.getStyleClass().add("files-scroll");

        VBox.setVgrow(scroll, Priority.ALWAYS);
        VBox content = new VBox(6.0, header, scroll);
        content.setPadding(new Insets(12.0));
        content.getStyleClass().add("files-content");
        return content;
    }

    private Button createFileRow(FileEntry entry) {
        Label icon = new Label(symbol(entry.entryType()));
        icon.getStyleClass().add("files-entry-icon");
        Label name = new Label(entry.name());
        name.getStyleClass().add("files-entry-name");
        Label type = new Label(typeLabel(entry.entryType()));
        type.getStyleClass().add("files-entry-meta");
        type.setMinWidth(72.0);
        Label size = new Label(entry.size());
        size.getStyleClass().add("files-entry-meta");
        size.setMinWidth(52.0);

        GridPane content = new GridPane();
        content.setHgap(10.0);

        ColumnConstraints iconColumn = new ColumnConstraints(30.0);
        ColumnConstraints nameColumn = new ColumnConstraints();
        nameColumn.setHgrow(Priority.ALWAYS);
        ColumnConstraints typeColumn = new ColumnConstraints(72.0);
        ColumnConstraints sizeColumn = new ColumnConstraints(52.0);

        content.getColumnConstraints().addAll(iconColumn, nameColumn, typeColumn, sizeColumn);
        content.add(icon, 0, 0);
        content.add(name, 1, 0);
        content.add(type, 2, 0);
        content.add(size, 3, 0);

        Button row = new Button();
        row.setGraphic(content);
        row.setMaxWidth(Double.MAX_VALUE);
        row.setMinHeight(44.0);
        row.setPrefHeight(44.0);
        row.getStyleClass().add("files-entry");
        row.setOnAction(event -> {
            if (entry.isDirectory()) {
                String childPath = currentPath + "/" + entry.name();
                navigateTo(childPath, true);
                return;
            }
            selectEntry(row, entry);
        });
        return row;
    }

    private void applySearch(String query) {
        if (currentSnapshot == null) {
            return;
        }
        String normalized = query == null ? "" : query.strip().toLowerCase(Locale.ROOT);

        if (normalized.isEmpty()) {
            renderEntries(currentSnapshot.entries());
            return;
        }
        List<FileEntry> filtered = currentSnapshot.entries().stream().filter(entry ->
                        entry.name().toLowerCase().contains(normalized) || typeLabel(entry.entryType()).toLowerCase().contains(normalized))
                .toList();
        renderEntries(filtered);
    }

    private void renderEntries(List<FileEntry> entries) {
        fileList.getChildren().clear();
        selectedRow = null;

        for (FileEntry entry : entries) {
            fileList.getChildren().add(createFileRow(entry));
        }
        clearSelection();
    }

    private void selectEntry(Button row, FileEntry entry) {
        if (selectedRow != null) {
            selectedRow.pseudoClassStateChanged(SELECTED, false);
        }
        selectedRow = row;
        selectedRow.pseudoClassStateChanged(SELECTED, true);
        selectedName.setText(entry.name());
        selectedType.setText(typeDescription(entry.entryType()));
        selectedSize.setText(entry.size());
        selectedModified.setText(entry.modified());
    }

    private void clearSelection() {
        selectedName.setText("NONE");
        selectedType.setText("-");
        selectedSize.setText("-");
        selectedModified.setText("-");
    }

    private VBox createContextPanel() {
        Label title = new Label("SELECTION // CONTEXT");
        title.getStyleClass().add("files-context-title");

        GridPane gridPane = new GridPane();
        gridPane.setHgap(18.0);
        gridPane.setVgap(6.0);

        addContextRow(gridPane, 0, "NAME", selectedName);
        addContextRow(gridPane, 1, "TYPE", selectedType);
        addContextRow(gridPane, 2, "SIZE", selectedSize);
        addContextRow(gridPane, 3, "MODIFIED", selectedModified);

        VBox context = new VBox(8.0, title, gridPane);
        context.setPadding(new Insets(12.0));
        context.getStyleClass().add("files-context");
        return context;
    }

    private static void addContextRow(GridPane gridPane, int row, String keyText, Label value) {
        Label key = new Label(keyText);

        key.getStyleClass().add("files-context-key");
        value.getStyleClass().add("files-context-value");

        gridPane.add(key, 0, row);
        gridPane.add(value, 1, row);
    }

    private static String symbol(FileEntryType type) {
        return switch (type) {
            case DIRECTORY -> "DIR";
            case JAVA -> "J";
            case CSS -> "CSS";
            case XML -> "XML";
            case TEXT -> "TXT";
            case JSON -> "{}";
            case IMAGE -> "IMG";
            case OTHER -> "FILE";
        };
    }

    private static String typeLabel(FileEntryType type) {
        return switch (type) {
            case DIRECTORY -> "FOLDER";
            case JAVA -> "JAVA";
            case CSS -> "CSS";
            case XML -> "XML";
            case TEXT -> "TEXT";
            case JSON -> "JSON";
            case IMAGE -> "IMAGE";
            case OTHER -> "FILE";
        };
    }

    private static String typeDescription(FileEntryType type) {
        return switch (type) {
            case DIRECTORY -> "DIRECTORY";
            case JAVA -> "JAVA SOURCE";
            case CSS -> "STYLESHEET";
            case XML -> "XML DOCUMENT";
            case TEXT -> "TEXT DOCUMENT";
            case JSON -> "JSON DOCUMENT";
            case IMAGE -> "IMAGE";
            case OTHER -> "FILE";
        };
    }

    private static FilesWorkspaceSnapshot mockSnapshot() {
        List<FileEntry> entries = List.of(
                        new FileEntry(
                                "agentic-ai",
                                FileEntryType.DIRECTORY,
                                "—",
                                "18 SEP 2026 15:42"
                        ),
                        new FileEntry(
                                "Android",
                                FileEntryType.DIRECTORY,
                                "—",
                                "16 SEP 2026 22:10"
                        ),
                        new FileEntry(
                                "Main.java",
                                FileEntryType.JAVA,
                                "15 KB",
                                "18 SEP 2026 14:32"
                        ),
                        new FileEntry(
                                "core-visual.css",
                                FileEntryType.CSS,
                                "9 KB",
                                "18 SEP 2026 06:58"
                        ),
                        new FileEntry(
                                "pom.xml",
                                FileEntryType.XML,
                                "4 KB",
                                "17 SEP 2026 23:14"
                        ),
                        new FileEntry(
                                "os-applications.json",
                                FileEntryType.JSON,
                                "1 KB",
                                "18 SEP 2026 01:20"
                        ),
                        new FileEntry(
                                "ares-source.txt",
                                FileEntryType.TEXT,
                                "245 KB",
                                "08 SEP 2026 22:17"
                        )
                );
        return new FilesWorkspaceSnapshot("C:\\Users\\FUAD NAZAL\\Desktop\\Proyectos Personales\\AI", entries);
    }

    private static Map<String, FilesWorkspaceSnapshot> createMockFileSystem() {
        final Map<String, FilesWorkspaceSnapshot> results = new LinkedHashMap<>();

        results.put("HOME/Desktop/Proyectos Personales/AI", new FilesWorkspaceSnapshot("HOME/Desktop/Proyectos Personales/AI",
                List.of(
                        new FileEntry(
                                "agentic-ai",
                                FileEntryType.DIRECTORY,
                                "—",
                                "20 SEP 2026 03:40"
                        ),
                        new FileEntry(
                                "Android",
                                FileEntryType.DIRECTORY,
                                "—",
                                "18 SEP 2026 22:10"
                        ),
                        new FileEntry(
                                "Main.java",
                                FileEntryType.JAVA,
                                "15 KB",
                                "20 SEP 2026 03:18"
                        )
                )
        ));
        results.put("HOME/Desktop/Proyectos Personales/AI/agentic-ai", new FilesWorkspaceSnapshot("HOME/Desktop/Proyectos Personales/AI/agentic-ai",
                List.of(
                        new FileEntry(
                                "src",
                                FileEntryType.DIRECTORY,
                                "—",
                                "20 SEP 2026 03:41"
                        ),
                        new FileEntry(
                                "pom.xml",
                                FileEntryType.XML,
                                "4 KB",
                                "19 SEP 2026 21:10"
                        ),
                        new FileEntry(
                                "README.md",
                                FileEntryType.TEXT,
                                "7 KB",
                                "19 SEP 2026 20:32"
                        )
                )
        ));
        results.put("HOME/Desktop/Proyectos Personales/AI/agentic-ai/src", new FilesWorkspaceSnapshot("HOME/Desktop/Proyectos Personales/AI/agentic-ai/src",
                List.of(
                        new FileEntry(
                                "main",
                                FileEntryType.DIRECTORY,
                                "—",
                                "20 SEP 2026 03:42"
                        ),
                        new FileEntry(
                                "test",
                                FileEntryType.DIRECTORY,
                                "—",
                                "20 SEP 2026 03:42"
                        )
                )
        ));
        return results;
    }
}
