package dev.xerohero.protracker;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import dev.xerohero.protracker.Issue;
import dev.xerohero.protracker.SavedFilter;
import javafx.application.Application;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.print.PrinterJob;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.input.*;
import javafx.scene.layout.*;
import javafx.scene.transform.Scale;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;
import java.util.Optional;

public class ProjectTrackerApp extends Application {

    private static final DataFormat ISSUE_FORMAT = new DataFormat("application/x-issue-id");

    private final ObservableList<Issue> masterIssueList = FXCollections.observableArrayList();
    private FilteredList<Issue> filteredIssueList;

    private final ObservableList<SavedFilter> customFilters = FXCollections.observableArrayList();
    private final TableView<Issue> table = new TableView<>();
    private final ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    private File currentFile = null;
    private Label statusLabel;

    // KanBan Column Containers
    private VBox openColumnBox;
    private VBox inProgressColumnBox;
    private VBox closedColumnBox;
    private Node kanbanViewNode;

    // Filter UI Components
    private TextField searchField;
    private ComboBox<String> filterStatusCombo;
    private ComboBox<String> filterPriorityCombo;
    private ComboBox<SavedFilter> savedFiltersCombo;

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("JavaFX Project Tracker");

        filteredIssueList = new FilteredList<>(masterIssueList, p -> true);
        masterIssueList.addListener((ListChangeListener<Issue>) c -> refreshKanbanBoard());

        // --- Menu Bar Setup ---
        MenuBar menuBar = new MenuBar();
        Menu fileMenu = new Menu("File");
        MenuItem newProject = new MenuItem("New Project");
        MenuItem openFile = new MenuItem("Open Project JSON...");
        MenuItem saveFile = new MenuItem("Save Project");
        MenuItem saveAsFile = new MenuItem("Save As...");
        MenuItem exportPrintItem = new MenuItem("Export / Print...");

        newProject.setOnAction(e -> clearProject());
        openFile.setOnAction(e -> loadProjectFromFile(primaryStage));
        saveFile.setOnAction(e -> saveProject(primaryStage, false));
        saveAsFile.setOnAction(e -> saveProject(primaryStage, true));
        exportPrintItem.setOnAction(e -> showExportDialog(primaryStage));

        fileMenu.getItems().addAll(newProject, openFile, saveFile, saveAsFile, new SeparatorMenuItem(), exportPrintItem);
        menuBar.getMenus().add(fileMenu);

        // --- Table View Setup ---
        TableColumn<Issue, String> titleCol = new TableColumn<>("Title");
        titleCol.setCellValueFactory(new PropertyValueFactory<>("title"));
        titleCol.setPrefWidth(220);

        TableColumn<Issue, String> milestoneCol = new TableColumn<>("Milestone");
        milestoneCol.setCellValueFactory(new PropertyValueFactory<>("milestone"));
        milestoneCol.setPrefWidth(120);

        TableColumn<Issue, String> priorityCol = new TableColumn<>("Priority");
        priorityCol.setCellValueFactory(new PropertyValueFactory<>("priority"));
        priorityCol.setPrefWidth(90);

        TableColumn<Issue, String> statusCol = new TableColumn<>("Status");
        statusCol.setCellValueFactory(new PropertyValueFactory<>("status"));
        statusCol.setPrefWidth(100);

        table.getColumns().addAll(titleCol, milestoneCol, priorityCol, statusCol);
        table.setItems(filteredIssueList);

        table.setRowFactory(tv -> {
            TableRow<Issue> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && (!row.isEmpty())) {
                    showEditDialog(row.getItem());
                }
            });
            return row;
        });

        table.getSelectionModel().selectedItemProperty().addListener((obs, oldSel, newSel) -> {
            if (oldSel != null && !oldSel.equals(newSel)) autosave();
        });

        table.focusedProperty().addListener((obs, wasFocused, isFocused) -> {
            if (!isFocused) autosave();
        });

        // --- Filter Bar Setup ---
        searchField = new TextField();
        searchField.setPromptText("Search title/desc...");
        searchField.textProperty().addListener((obs, oldV, newV) -> applyFilter());

        filterStatusCombo = new ComboBox<>(FXCollections.observableArrayList("ALL", "OPEN", "IN_PROGRESS", "CLOSED"));
        filterStatusCombo.setValue("ALL");
        filterStatusCombo.setOnAction(e -> applyFilter());

        filterPriorityCombo = new ComboBox<>(FXCollections.observableArrayList("ALL", "LOW", "MEDIUM", "HIGH"));
        filterPriorityCombo.setValue("ALL");
        filterPriorityCombo.setOnAction(e -> applyFilter());

        Button btnAllOpen = new Button("Open Issues");
        btnAllOpen.setOnAction(e -> {
            filterStatusCombo.setValue("OPEN");
            filterPriorityCombo.setValue("ALL");
            searchField.clear();
        });

        Button btnHighPriority = new Button("High Priority");
        btnHighPriority.setOnAction(e -> {
            filterStatusCombo.setValue("ALL");
            filterPriorityCombo.setValue("HIGH");
            searchField.clear();
        });

        savedFiltersCombo = new ComboBox<>(customFilters);
        savedFiltersCombo.setPromptText("Select Saved Filter");
        savedFiltersCombo.setOnAction(e -> {
            SavedFilter selected = savedFiltersCombo.getValue();
            if (selected != null) {
                filterStatusCombo.setValue(selected.getStatus());
                filterPriorityCombo.setValue(selected.getPriority());
                searchField.setText(selected.getTextSearch() != null ? selected.getTextSearch() : "");
            }
        });

        Button saveFilterBtn = new Button("Save Current Filter");
        saveFilterBtn.setOnAction(e -> promptSaveFilter());

        HBox filterBar1 = new HBox(10, new Label("Search:"), searchField, new Label("Status:"), filterStatusCombo, new Label("Priority:"), filterPriorityCombo);
        filterBar1.setAlignment(Pos.CENTER_LEFT);

        HBox filterBar2 = new HBox(10, new Label("Quick Views:"), btnAllOpen, btnHighPriority, new Label("| Saved:"), savedFiltersCombo, saveFilterBtn);
        filterBar2.setAlignment(Pos.CENTER_LEFT);

        VBox filterContainer = new VBox(8, filterBar1, filterBar2);
        filterContainer.setPadding(new Insets(10));
        filterContainer.setStyle("-fx-background-color: #eaecee; -fx-background-radius: 5;");

        // --- Form Inputs ---
        TextField titleField = new TextField();
        titleField.setPromptText("Issue Title");

        TextField milestoneField = new TextField();
        milestoneField.setPromptText("Milestone");

        ComboBox<String> priorityCombo = new ComboBox<>(FXCollections.observableArrayList("LOW", "MEDIUM", "HIGH"));
        priorityCombo.setValue("MEDIUM");

        ComboBox<String> statusCombo = new ComboBox<>(FXCollections.observableArrayList("OPEN", "IN_PROGRESS", "CLOSED"));
        statusCombo.setValue("OPEN");

        Button addButton = new Button("Add Ticket");
        addButton.setStyle("-fx-background-color: #27ae60; -fx-text-fill: white; -fx-font-weight: bold;");
        addButton.setOnAction(e -> {
            if (!titleField.getText().isBlank()) {
                masterIssueList.add(new Issue(
                        titleField.getText().trim(),
                        milestoneField.getText().isBlank() ? "Unassigned" : milestoneField.getText().trim(),
                        statusCombo.getValue(),
                        priorityCombo.getValue(),
                        ""
                ));
                titleField.clear();
                milestoneField.clear();
                autosave();
            }
        });

        Button deleteButton = new Button("Delete Selected");
        deleteButton.setStyle("-fx-background-color: #e74c3c; -fx-text-fill: white; -fx-font-weight: bold;");
        deleteButton.setOnAction(e -> {
            Issue selected = table.getSelectionModel().getSelectedItem();
            if (selected != null) {
                masterIssueList.remove(selected);
                autosave();
            }
        });

        GridPane formGrid = new GridPane();
        formGrid.setHgap(10);
        formGrid.setVgap(10);
        formGrid.add(new Label("Title:"), 0, 0);
        formGrid.add(titleField, 1, 0);
        formGrid.add(new Label("Milestone:"), 0, 1);
        formGrid.add(milestoneField, 1, 1);
        formGrid.add(new Label("Priority:"), 2, 0);
        formGrid.add(priorityCombo, 3, 0);
        formGrid.add(new Label("Status:"), 2, 1);
        formGrid.add(statusCombo, 3, 1);

        HBox actionBox = new HBox(10, addButton, deleteButton);
        actionBox.setAlignment(Pos.CENTER_RIGHT);

        // --- KanBan View Setup ---
        kanbanViewNode = buildKanbanView();

        // --- TabPane Layout ---
        TabPane tabPane = new TabPane();

        VBox tableContent = new VBox(10, table);
        Tab tableTab = new Tab("List View", tableContent);
        tableTab.setClosable(false);

        Tab kanbanTab = new Tab("KanBan Board", kanbanViewNode);
        kanbanTab.setClosable(false);

        tabPane.getTabs().addAll(tableTab, kanbanTab);

        statusLabel = new Label("New Unsaved Project");
        statusLabel.setStyle("-fx-text-fill: #7f8c8d; -fx-font-style: italic;");

        VBox mainLayout = new VBox(12, menuBar, filterContainer, formGrid, actionBox, tabPane, statusLabel);
        mainLayout.setPadding(new Insets(0, 15, 15, 15));

        Scene scene = new Scene(mainLayout, 840, 640);
        primaryStage.setScene(scene);
        primaryStage.show();

        refreshKanbanBoard();
    }

    // --- Export & Printing Logic ---

    private void showExportDialog(Stage ownerStage) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Export / Print Project");
        dialog.setHeaderText("Select export options");

        ButtonType exportBtnType = new ButtonType("Export / Print", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(exportBtnType, ButtonType.CANCEL);

        ComboBox<String> formatCombo = new ComboBox<>(FXCollections.observableArrayList(
                "Direct Print / PDF", "HTML Report", "CSV File"
        ));
        formatCombo.setValue("Direct Print / PDF");

        RadioButton currentFilterRadio = new RadioButton("Filtered Issues Only (" + filteredIssueList.size() + ")");
        RadioButton allIssuesRadio = new RadioButton("All Project Issues (" + masterIssueList.size() + ")");
        ToggleGroup scopeGroup = new ToggleGroup();
        currentFilterRadio.setToggleGroup(scopeGroup);
        allIssuesRadio.setToggleGroup(scopeGroup);
        currentFilterRadio.setSelected(true);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(12);
        grid.setPadding(new Insets(20));

        grid.add(new Label("Format:"), 0, 0);
        grid.add(formatCombo, 1, 0);
        grid.add(new Label("Scope:"), 0, 1);
        grid.add(new VBox(5, currentFilterRadio, allIssuesRadio), 1, 1);

        dialog.getDialogPane().setContent(grid);

        dialog.showAndWait().ifPresent(type -> {
            if (type == exportBtnType) {
                List<Issue> targetList = currentFilterRadio.isSelected() ? filteredIssueList : masterIssueList;
                String format = formatCombo.getValue();

                switch (format) {
                    case "Direct Print / PDF" -> printNode(kanbanViewNode, ownerStage);
                    case "HTML Report" -> exportHtml(targetList, ownerStage);
                    case "CSV File" -> exportCsv(targetList, ownerStage);
                }
            }
        });
    }

    private void printNode(Node node, Stage stage) {
        PrinterJob job = PrinterJob.createPrinterJob();
        if (job != null && job.showPrintDialog(stage)) {
            // Use getJobSettings() to access the page layout and dimensions
            double pageWidth = job.getJobSettings().getPageLayout().getPrintableWidth();
            double pageHeight = job.getJobSettings().getPageLayout().getPrintableHeight();

            double scaleX = pageWidth / node.getBoundsInParent().getWidth();
            double scaleY = pageHeight / node.getBoundsInParent().getHeight();
            double scale = Math.min(scaleX, scaleY);

            Scale transform = new Scale(scale, scale);
            node.getTransforms().add(transform);

            boolean success = job.printPage(node);
            node.getTransforms().remove(transform);

            if (success) {
                job.endJob();
                statusLabel.setText("Printed successfully.");
            } else {
                showError("Printing failed.");
            }
        }
    }


    private void exportHtml(List<Issue> issues, Stage stage) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save HTML Report");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("HTML Files", "*.html"));
        File file = chooser.showSaveDialog(stage);

        if (file != null) {
            try (PrintWriter writer = new PrintWriter(new FileWriter(file))) {
                writer.println("<!DOCTYPE html><html><head><title>Project Report</title>");
                writer.println("<style>");
                writer.println("body { font-family: Arial, sans-serif; margin: 20px; }");
                writer.println("table { border-collapse: collapse; width: 100%; margin-top: 15px; }");
                writer.println("th, td { border: 1px solid #ccc; padding: 8px 12px; text-align: left; }");
                writer.println("th { background-color: #34495e; color: white; }");
                writer.println("tr:nth-child(even) { background-color: #f2f2f2; }");
                writer.println(".HIGH { color: #e74c3c; font-weight: bold; }");
                writer.println(".MEDIUM { color: #e67e22; font-weight: bold; }");
                writer.println(".LOW { color: #27ae60; font-weight: bold; }");
                writer.println("</style></head><body>");

                writer.println("<h2>Project Issue Report</h2>");
                writer.println("<table><thead><tr><th>Title</th><th>Milestone</th><th>Priority</th><th>Status</th><th>Description</th></tr></thead><tbody>");

                for (Issue issue : issues) {
                    writer.printf("<tr><td>%s</td><td>%s</td><td class='%s'>%s</td><td>%s</td><td>%s</td></tr>%n",
                            escapeHtml(issue.getTitle()),
                            escapeHtml(issue.getMilestone()),
                            issue.getPriority(),
                            issue.getStatus(),
                            escapeHtml(issue.getDescription()));
                }

                writer.println("</tbody></table></body></html>");
                statusLabel.setText("Exported HTML: " + file.getName());
            } catch (IOException ex) {
                showError("HTML export failed: " + ex.getMessage());
            }
        }
    }

    private void exportCsv(List<Issue> issues, Stage stage) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save CSV Summary");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV Files", "*.csv"));
        File file = chooser.showSaveDialog(stage);

        if (file != null) {
            try (PrintWriter writer = new PrintWriter(new FileWriter(file))) {
                writer.println("Title,Milestone,Priority,Status,Description");
                for (Issue issue : issues) {
                    writer.printf("\"%s\",\"%s\",\"%s\",\"%s\",\"%s\"%n",
                            sanitizeCsv(issue.getTitle()),
                            sanitizeCsv(issue.getMilestone()),
                            sanitizeCsv(issue.getPriority()),
                            sanitizeCsv(issue.getStatus()),
                            sanitizeCsv(issue.getDescription()));
                }
                statusLabel.setText("Exported CSV: " + file.getName());
            } catch (IOException ex) {
                showError("CSV export failed: " + ex.getMessage());
            }
        }
    }

    private String sanitizeCsv(String input) {
        if (input == null) return "";
        return input.replace("\"", "\"\"");
    }

    private String escapeHtml(String input) {
        if (input == null) return "";
        return input.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    // --- KanBan Board Construction & Drag-And-Drop ---

    private Node buildKanbanView() {
        HBox kanbanContainer = new HBox(15);
        kanbanContainer.setPadding(new Insets(10));
        kanbanContainer.setAlignment(Pos.TOP_CENTER);

        openColumnBox = createKanbanColumn("OPEN", "#ebf5fb");
        inProgressColumnBox = createKanbanColumn("IN_PROGRESS", "#fef9e7");
        closedColumnBox = createKanbanColumn("CLOSED", "#eaf2f8");

        kanbanContainer.getChildren().addAll(
                buildColumnPanel("Open", openColumnBox, "#3498db"),
                buildColumnPanel("In Progress", inProgressColumnBox, "#f39c12"),
                buildColumnPanel("Closed", closedColumnBox, "#27ae60")
        );

        ScrollPane scrollPane = new ScrollPane(kanbanContainer);
        scrollPane.setFitToWidth(true);
        scrollPane.setFitToHeight(true);
        scrollPane.setStyle("-fx-background-color: transparent;");

        return scrollPane;
    }

    private VBox buildColumnPanel(String title, VBox cardContainer, String headerColor) {
        Label header = new Label(title);
        header.setStyle("-fx-font-weight: bold; -fx-font-size: 14px; -fx-text-fill: white;");

        HBox headerBox = new HBox(header);
        headerBox.setPadding(new Insets(8));
        headerBox.setAlignment(Pos.CENTER);
        headerBox.setStyle("-fx-background-color: " + headerColor + "; -fx-background-radius: 5 5 0 0;");

        VBox columnLayout = new VBox(0, headerBox, cardContainer);
        HBox.setHgrow(columnLayout, Priority.ALWAYS);
        columnLayout.setStyle("-fx-background-color: #f2f4f4; -fx-background-radius: 5; -fx-border-color: #bdc3c7; -fx-border-radius: 5;");

        return columnLayout;
    }

    private VBox createKanbanColumn(String targetStatus, String bgColor) {
        VBox col = new VBox(8);
        col.setPadding(new Insets(10));
        col.setPrefHeight(350);
        col.setStyle("-fx-background-color: " + bgColor + ";");

        col.setOnDragOver(event -> {
            if (event.getGestureSource() != col && event.getDragboard().hasContent(ISSUE_FORMAT)) {
                event.acceptTransferModes(TransferMode.MOVE);
            }
            event.consume();
        });

        col.setOnDragDropped(event -> {
            Dragboard db = event.getDragboard();
            boolean success = false;
            if (db.hasContent(ISSUE_FORMAT)) {
                int issueIndex = (Integer) db.getContent(ISSUE_FORMAT);
                if (issueIndex >= 0 && issueIndex < masterIssueList.size()) {
                    Issue issue = masterIssueList.get(issueIndex);
                    issue.setStatus(targetStatus);
                    table.refresh();
                    applyFilter();
                    refreshKanbanBoard();
                    autosave();
                    success = true;
                }
            }
            event.setDropCompleted(success);
            event.consume();
        });

        return col;
    }

    private void refreshKanbanBoard() {
        if (openColumnBox == null || inProgressColumnBox == null || closedColumnBox == null) return;

        openColumnBox.getChildren().clear();
        inProgressColumnBox.getChildren().clear();
        closedColumnBox.getChildren().clear();

        for (int i = 0; i < filteredIssueList.size(); i++) {
            Issue issue = filteredIssueList.get(i);
            int masterIndex = masterIssueList.indexOf(issue);

            VBox card = createKanbanCard(issue, masterIndex);

            switch (issue.getStatus().toUpperCase()) {
                case "OPEN" -> openColumnBox.getChildren().add(card);
                case "IN_PROGRESS" -> inProgressColumnBox.getChildren().add(card);
                case "CLOSED" -> closedColumnBox.getChildren().add(card);
                default -> openColumnBox.getChildren().add(card);
            }
        }
    }

    private VBox createKanbanCard(Issue issue, int masterIndex) {
        Label titleLabel = new Label(issue.getTitle());
        titleLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 12px;");

        Label milestoneLabel = new Label("Milestone: " + issue.getMilestone());
        milestoneLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: #555555;");

        Label priorityLabel = new Label(issue.getPriority());
        priorityLabel.setStyle("-fx-font-size: 10px; -fx-font-weight: bold; -fx-text-fill: " + getPriorityColor(issue.getPriority()) + ";");

        HBox footer = new HBox(10, milestoneLabel, priorityLabel);
        footer.setAlignment(Pos.CENTER_LEFT);

        VBox card = new VBox(5, titleLabel, footer);
        card.setPadding(new Insets(8));
        card.setStyle("-fx-background-color: white; -fx-border-color: #d5dbdb; -fx-border-radius: 4; -fx-background-radius: 4;");

        card.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                showEditDialog(issue);
            }
        });

        card.setOnDragDetected(event -> {
            Dragboard db = card.startDragAndDrop(TransferMode.MOVE);
            ClipboardContent content = new ClipboardContent();
            content.put(ISSUE_FORMAT, masterIndex);
            db.setContent(content);
            event.consume();
        });

        return card;
    }

    private String getPriorityColor(String priority) {
        if (priority == null) return "#2c3e50";
        return switch (priority.toUpperCase()) {
            case "HIGH" -> "#e74c3c";
            case "MEDIUM" -> "#e67e22";
            case "LOW" -> "#27ae60";
            default -> "#2c3e50";
        };
    }

    // --- Core Operations & Logic ---

    private void applyFilter() {
        String searchText = searchField.getText() == null ? "" : searchField.getText().toLowerCase().trim();
        String selectedStatus = filterStatusCombo.getValue();
        String selectedPriority = filterPriorityCombo.getValue();

        filteredIssueList.setPredicate(issue -> {
            if (selectedStatus != null && !selectedStatus.equals("ALL")) {
                if (!issue.getStatus().equalsIgnoreCase(selectedStatus)) return false;
            }

            if (selectedPriority != null && !selectedPriority.equals("ALL")) {
                if (!issue.getPriority().equalsIgnoreCase(selectedPriority)) return false;
            }

            if (!searchText.isEmpty()) {
                boolean matchesTitle = issue.getTitle().toLowerCase().contains(searchText);
                boolean matchesDesc = issue.getDescription() != null && issue.getDescription().toLowerCase().contains(searchText);
                return matchesTitle || matchesDesc;
            }

            return true;
        });

        refreshKanbanBoard();
    }

    private void promptSaveFilter() {
        TextInputDialog dialog = new TextInputDialog("My Filter");
        dialog.setTitle("Save Filter Preset");
        dialog.setHeaderText("Save current filter criteria for reuse");
        dialog.setContentText("Filter Name:");

        Optional<String> result = dialog.showAndWait();
        result.ifPresent(name -> {
            if (!name.isBlank()) {
                SavedFilter sf = new SavedFilter(
                        name.trim(),
                        filterStatusCombo.getValue(),
                        filterPriorityCombo.getValue(),
                        "ALL",
                        searchField.getText()
                );
                customFilters.add(sf);
                savedFiltersCombo.setValue(sf);
                autosave();
            }
        });
    }

    private void showEditDialog(Issue issue) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Edit Issue Details");
        dialog.setHeaderText("Editing: " + issue.getTitle());

        ButtonType saveButtonType = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveButtonType, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));

        TextField titleEdit = new TextField(issue.getTitle());
        TextField milestoneEdit = new TextField(issue.getMilestone());

        ComboBox<String> priorityEdit = new ComboBox<>(FXCollections.observableArrayList("LOW", "MEDIUM", "HIGH"));
        priorityEdit.setValue(issue.getPriority());

        ComboBox<String> statusEdit = new ComboBox<>(FXCollections.observableArrayList("OPEN", "IN_PROGRESS", "CLOSED"));
        statusEdit.setValue(issue.getStatus());

        TextArea descEdit = new TextArea(issue.getDescription() != null ? issue.getDescription() : "");
        descEdit.setPromptText("Enter detailed description...");
        descEdit.setPrefRowCount(5);
        descEdit.setWrapText(true);

        grid.add(new Label("Title:"), 0, 0);
        grid.add(titleEdit, 1, 0);
        grid.add(new Label("Milestone:"), 0, 1);
        grid.add(milestoneEdit, 1, 1);
        grid.add(new Label("Priority:"), 0, 2);
        grid.add(priorityEdit, 1, 2);
        grid.add(new Label("Status:"), 0, 3);
        grid.add(statusEdit, 1, 3);
        grid.add(new Label("Description:"), 0, 4);
        grid.add(descEdit, 1, 4);

        dialog.getDialogPane().setContent(grid);

        dialog.showAndWait().ifPresent(response -> {
            if (response == saveButtonType) {
                issue.setTitle(titleEdit.getText().trim());
                issue.setMilestone(milestoneEdit.getText().trim());
                issue.setPriority(priorityEdit.getValue());
                issue.setStatus(statusEdit.getValue());
                issue.setDescription(descEdit.getText());
                table.refresh();
                applyFilter();
                refreshKanbanBoard();
                autosave();
            }
        });
    }

    private void autosave() {
        if (currentFile != null) {
            try {
                Project proj = new Project(currentFile.getName().replace(".json", ""));
                proj.setIssues(new java.util.ArrayList<>(masterIssueList));
                proj.setSavedFilters(new java.util.ArrayList<>(customFilters));
                mapper.writeValue(currentFile, proj);
                statusLabel.setText("Autosaved to: " + currentFile.getName());
            } catch (IOException ex) {
                statusLabel.setText("Autosave failed: " + ex.getMessage());
            }
        }
    }

    private void clearProject() {
        masterIssueList.clear();
        customFilters.clear();
        currentFile = null;
        statusLabel.setText("New Unsaved Project");
        refreshKanbanBoard();
    }

    private void loadProjectFromFile(Stage stage) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Open Project File");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("JSON Files", "*.json"));
        File file = chooser.showOpenDialog(stage);

        if (file != null) {
            try {
                Project proj = mapper.readValue(file, Project.class);
                masterIssueList.setAll(proj.getIssues());
                customFilters.setAll(proj.getSavedFilters());
                currentFile = file;
                statusLabel.setText("Loaded: " + file.getName());
                refreshKanbanBoard();
            } catch (IOException ex) {
                showError("Failed to load project: " + ex.getMessage());
            }
        }
    }

    private void saveProject(Stage stage, boolean forceSaveAs) {
        if (currentFile == null || forceSaveAs) {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Save Project File");
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("JSON Files", "*.json"));
            chooser.setInitialFileName("project.json");
            File file = chooser.showSaveDialog(stage);
            if (file != null) currentFile = file;
            else return;
        }

        try {
            Project proj = new Project(currentFile.getName().replace(".json", ""));
            proj.setIssues(new java.util.ArrayList<>(masterIssueList));
            proj.setSavedFilters(new java.util.ArrayList<>(customFilters));
            mapper.writeValue(currentFile, proj);
            statusLabel.setText("Saved to: " + currentFile.getName());
        } catch (IOException ex) {
            showError("Failed to save project: " + ex.getMessage());
        }
    }

    private void showError(String msg) {
        Alert alert = new Alert(Alert.AlertType.ERROR, msg, ButtonType.OK);
        alert.showAndWait();
    }

    public static void main(String[] args) { launch(args); }
}
