package com.scriptrunner;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.scene.paint.Color;
import javafx.scene.Cursor;
import javafx.stage.Stage;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.LineNumberFactory;

public class ScriptRunnerApp extends Application {
    
    private CodeArea codeEditor;
    private TextArea outputArea;
    private TextFlow outputFlow;
    private ScrollPane outputScrollPane;
    private Button runButton;
    private Button stopButton;
    private Label statusLabel;
    private Label exitCodeLabel;
    private ComboBox<ScriptLanguage> languageComboBox;
    private ScriptExecutor scriptExecutor;
    
    public enum ScriptLanguage {
        SWIFT("Swift", "swift", "/usr/bin/env swift"),
        KOTLIN("Kotlin", "kts", "kotlinc -script");
        
        private final String displayName;
        private final String fileExtension;
        private final String command;
        
        ScriptLanguage(String displayName, String fileExtension, String command) {
            this.displayName = displayName;
            this.fileExtension = fileExtension;
            this.command = command;
        }
        
        public String getDisplayName() { return displayName; }
        public String getFileExtension() { return fileExtension; }
        public String getCommand() { return command; }
        
        @Override
        public String toString() { return displayName; }
    }

    @Override
    public void start(Stage primaryStage) {
        initializeComponents();
        setupEventHandlers();
        
        BorderPane root = createLayout();
        
        Scene scene = new Scene(root, 1200, 800);
        // Add CSS styling
        try {
            String cssFile = getClass().getResource("/syntax-highlighting.css").toExternalForm();
            scene.getStylesheets().add(cssFile);
            System.out.println("CSS loaded successfully: " + cssFile);
        } catch (Exception e) {
            System.err.println("Failed to load CSS file: " + e.getMessage());
            // Fallback to inline styles
            try {
                scene.getStylesheets().add("data:text/css," + 
                    java.net.URLEncoder.encode(SyntaxHighlighter.getStyleSheet(), java.nio.charset.StandardCharsets.UTF_8));
                System.out.println("Using inline CSS as fallback");
            } catch (Exception ex) {
                System.err.println("All CSS methods failed: " + ex.getMessage());
            }
        }
        
        primaryStage.setTitle("Script Runner - Swift & Kotlin");
        primaryStage.setScene(scene);
        primaryStage.setOnCloseRequest(e -> {
            if (scriptExecutor != null) {
                scriptExecutor.stop();
            }
            Platform.exit();
        });
        primaryStage.show();
    }
    
    private void initializeComponents() {
        // Code editor with line numbers
        codeEditor = new CodeArea();
        codeEditor.setParagraphGraphicFactory(LineNumberFactory.get(codeEditor));
        codeEditor.setStyle("-fx-font-family: 'Courier New', monospace; -fx-font-size: 14px;");
        codeEditor.getStyleClass().add("code-area");
        
        // Output area - keep TextArea for now, add TextFlow as alternative
        outputArea = new TextArea();
        outputArea.setEditable(false);
        outputArea.setStyle("-fx-font-family: 'Courier New', monospace; -fx-font-size: 12px;");
        
        // Rich text output flow for clickable errors
        outputFlow = new TextFlow();
        outputFlow.setStyle("-fx-font-family: 'Courier New', monospace; -fx-font-size: 12px;");
        outputScrollPane = new ScrollPane(outputFlow);
        outputScrollPane.setFitToWidth(true);
        
        // Controls
        languageComboBox = new ComboBox<>();
        languageComboBox.getItems().addAll(ScriptLanguage.values());
        languageComboBox.setValue(ScriptLanguage.SWIFT);
        
        runButton = new Button("Run Script");
        runButton.setStyle("-fx-background-color: #4CAF50; -fx-text-fill: white;");
        
        stopButton = new Button("Stop");
        stopButton.setDisable(true);
        stopButton.setStyle("-fx-background-color: #f44336; -fx-text-fill: white;");
        
        statusLabel = new Label("Ready");
        statusLabel.setStyle("-fx-font-weight: bold;");
        
        exitCodeLabel = new Label("");
        
        // Initialize script executor
        scriptExecutor = new ScriptExecutor();
        
        // Clickable error navigation now handled directly in TextFlow
        
        // Apply initial syntax highlighting
        applySyntaxHighlighting();
    }
    
    private void setupEventHandlers() {
        runButton.setOnAction(e -> runScript());
        stopButton.setOnAction(e -> stopScript());
        
        // Add syntax highlighting when language changes
        languageComboBox.setOnAction(e -> {
            applySyntaxHighlighting();
        });
        
        // Apply syntax highlighting as user types (with small delay to avoid excessive calls)
        codeEditor.textProperty().addListener((obs, oldText, newText) -> {
            Platform.runLater(this::applySyntaxHighlighting);
        });
    }
    
    private BorderPane createLayout() {
        BorderPane root = new BorderPane();
        
        // Top toolbar
        HBox toolbar = new HBox(10);
        toolbar.setPadding(new Insets(10));
        toolbar.getChildren().addAll(
            new Label("Language:"), languageComboBox,
            runButton, stopButton,
            new Separator(Orientation.VERTICAL),
            new Label("Status:"), statusLabel,
            exitCodeLabel
        );
        
        // Main content - split pane
        SplitPane splitPane = new SplitPane();
        splitPane.setOrientation(Orientation.HORIZONTAL);
        
        // Left side - editor
        VBox editorPane = new VBox(5);
        editorPane.setPadding(new Insets(10));
        Label editorLabel = new Label("Script Editor");
        editorLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");
        editorPane.getChildren().addAll(editorLabel, codeEditor);
        VBox.setVgrow(codeEditor, javafx.scene.layout.Priority.ALWAYS);
        
        // Right side - output
        VBox outputPane = new VBox(5);
        outputPane.setPadding(new Insets(10));
        Label outputLabel = new Label("Output");
        outputLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");
        outputPane.getChildren().addAll(outputLabel, outputScrollPane);
        VBox.setVgrow(outputScrollPane, javafx.scene.layout.Priority.ALWAYS);
        
        splitPane.getItems().addAll(editorPane, outputPane);
        splitPane.setDividerPositions(0.5);
        
        root.setTop(toolbar);
        root.setCenter(splitPane);
        
        return root;
    }
    
    private void runScript() {
        String code = codeEditor.getText().trim();
        if (code.isEmpty()) {
            showStatus("No script to run", false);
            return;
        }
        
        ScriptLanguage language = languageComboBox.getValue();
        clearOutput();
        setRunning(true);
        showStatus("Running...", false);
        exitCodeLabel.setText("");
        
        scriptExecutor.execute(code, language, new ScriptExecutor.ExecutionCallback() {
            @Override
            public void onOutput(String output) {
                Platform.runLater(() -> {
                    addOutputText(output);
                    // Keep TextArea updated for compatibility
                    outputArea.appendText(output);
                    outputArea.positionCaret(outputArea.getLength());
                });
            }
            
            @Override
            public void onError(String error) {
                Platform.runLater(() -> {
                    addOutputText("[ERROR] " + error);
                    // Keep TextArea updated for compatibility
                    outputArea.appendText("[ERROR] " + error);
                    outputArea.positionCaret(outputArea.getLength());
                });
            }
            
            @Override
            public void onComplete(int exitCode) {
                Platform.runLater(() -> {
                    setRunning(false);
                    if (exitCode == 0) {
                        showStatus("Completed successfully", false);
                        exitCodeLabel.setText("✓ Exit: 0");
                        exitCodeLabel.setStyle("-fx-text-fill: green; -fx-font-weight: bold;");
                    } else {
                        showStatus("Completed with errors", true);
                        exitCodeLabel.setText("✗ Exit: " + exitCode);
                        exitCodeLabel.setStyle("-fx-text-fill: red; -fx-font-weight: bold;");
                    }
                });
            }
        });
    }
    
    private void stopScript() {
        scriptExecutor.stop();
        setRunning(false);
        showStatus("Stopped", false);
        Platform.runLater(() -> {
            addOutputText("\n[PROCESS TERMINATED]\n");
            outputArea.appendText("\n[PROCESS TERMINATED]\n");
        });
    }
    
    private void setRunning(boolean running) {
        runButton.setDisable(running);
        stopButton.setDisable(!running);
        languageComboBox.setDisable(running);
    }
    
    private void showStatus(String message, boolean isError) {
        statusLabel.setText(message);
        if (isError) {
            statusLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: red;");
        } else {
            statusLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: black;");
        }
    }
    
    private void applySyntaxHighlighting() {
        ScriptLanguage language = languageComboBox.getValue();
        if (language != null) {
            System.out.println("Applying syntax highlighting for: " + language);
            SyntaxHighlighter.highlight(codeEditor, language);
        }
    }
    
    private void clearOutput() {
        outputFlow.getChildren().clear();
        // Keep TextArea for compatibility during transition
        outputArea.clear();
    }
    
    private void addOutputText(String text) {
        // Parse for error patterns and create clickable links
        String[] lines = text.split("\n");
        
        for (String line : lines) {
            if (containsErrorLocation(line)) {
                addClickableErrorLine(line);
            } else {
                addNormalText(line + "\n");
            }
        }
        
        // Scroll to bottom
        Platform.runLater(() -> {
            outputScrollPane.setVvalue(1.0);
        });
    }
    
    private boolean containsErrorLocation(String line) {
        // Check for compiler error patterns
        return line.matches(".*(\\w+\\.(swift|kts)):(\\d+):(\\d+):\\s+(error|warning|note):.*");
    }
    
    private void addClickableErrorLine(String line) {
        // Parse error line: filename:line:column: type: message
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
            "(.*?)(\\w+\\.(swift|kts)):(\\d+):(\\d+):(\\s+(error|warning|note):.*)"
        );
        java.util.regex.Matcher matcher = pattern.matcher(line);
        
        if (matcher.find()) {
            String prefix = matcher.group(1);
            String filename = matcher.group(2);
            int lineNum = Integer.parseInt(matcher.group(4));
            int colNum = Integer.parseInt(matcher.group(5));
            String suffix = matcher.group(6);
            
            // Add prefix text
            if (!prefix.isEmpty()) {
                addNormalText(prefix);
            }
            
            // Add clickable filename:line:column part
            Text clickableText = new Text(filename + ":" + lineNum + ":" + colNum);
            clickableText.setFill(Color.BLUE);
            clickableText.setUnderline(true);
            clickableText.setCursor(Cursor.HAND);
            clickableText.setOnMouseClicked(e -> {
                System.out.println("Clicked error location: line=" + lineNum + ", col=" + colNum);
                navigateToLocation(lineNum, colNum);
            });
            
            // Add suffix (error message)
            Text suffixText = new Text(suffix);
            suffixText.setFill(Color.DARKRED);
            
            outputFlow.getChildren().addAll(clickableText, suffixText);
            addNormalText("\n");
        } else {
            addNormalText(line + "\n");
        }
    }
    
    private void addNormalText(String text) {
        Text normalText = new Text(text);
        normalText.setFill(Color.BLACK);
        outputFlow.getChildren().add(normalText);
    }
    
    private void navigateToLocation(int line, int column) {
        try {
            // Convert to 0-based indexing
            int targetLine = Math.max(0, line - 1);
            int targetColumn = Math.max(0, column - 1);
            
            System.out.println("Navigating to line " + line + ", column " + column);
            
            // Get the total number of lines
            int totalLines = codeEditor.getParagraphs().size();
            if (targetLine >= totalLines) {
                targetLine = totalLines - 1;
            }
            
            // Calculate the absolute position
            int position = 0;
            for (int i = 0; i < targetLine; i++) {
                position += codeEditor.getParagraph(i).length() + 1; // +1 for newline
            }
            
            // Add column offset, but don't exceed line length
            int lineLength = codeEditor.getParagraph(targetLine).length();
            position += Math.min(targetColumn, lineLength);
            
            // Move caret and scroll to position
            codeEditor.moveTo(position);
            codeEditor.requestFollowCaret();
            codeEditor.requestFocus();
            
            // Highlight the error location
            if (targetColumn < lineLength) {
                codeEditor.selectRange(position, position + Math.min(3, lineLength - targetColumn));
            }
            
        } catch (Exception e) {
            System.err.println("Navigation failed: " + e.getMessage());
        }
    }
    
    public static void main(String[] args) {
        launch(args);
    }
}