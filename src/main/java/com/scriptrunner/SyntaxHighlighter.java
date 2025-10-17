package com.scriptrunner;

import javafx.concurrent.Task;
import javafx.application.Platform;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.model.StyleSpans;
import org.fxmisc.richtext.model.StyleSpansBuilder;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SyntaxHighlighter {
    
    // Swift keywords (limited to 10 most common)
    private static final Set<String> SWIFT_KEYWORDS = Set.of(
        "func", "var", "let", "if", "else", "for", "while", "class", "struct", "import"
    );
    
    // Kotlin keywords (limited to 10 most common)
    private static final Set<String> KOTLIN_KEYWORDS = Set.of(
        "fun", "var", "val", "if", "else", "for", "while", "class", "object", "import"
    );
    
    // Pattern for keywords
    private static final Pattern KEYWORD_PATTERN = Pattern.compile("\\b(" + String.join("|", getAllKeywords()) + ")\\b");
    
    // Pattern for strings
    private static final Pattern STRING_PATTERN = Pattern.compile("\"([^\"\\\\]|\\\\.)*\"");
    
    // Pattern for comments
    private static final Pattern COMMENT_PATTERN = Pattern.compile("//[^\n]*" + "|" + "/\\*(.|\\R)*?\\*/");
    
    // Combined pattern
    private static final Pattern PATTERN = Pattern.compile(
        "(?<KEYWORD>" + KEYWORD_PATTERN.pattern() + ")" +
        "|(?<STRING>" + STRING_PATTERN.pattern() + ")" +
        "|(?<COMMENT>" + COMMENT_PATTERN.pattern() + ")"
    );
    
    private static Set<String> getAllKeywords() {
        Set<String> allKeywords = new HashSet<>(SWIFT_KEYWORDS);
        allKeywords.addAll(KOTLIN_KEYWORDS);
        return allKeywords;
    }
    
    public static void highlight(CodeArea codeArea, ScriptRunnerApp.ScriptLanguage language) {
        Task<StyleSpans<Collection<String>>> task = new Task<StyleSpans<Collection<String>>>() {
            @Override
            protected StyleSpans<Collection<String>> call() throws Exception {
                return computeHighlighting(codeArea.getText(), language);
            }
        };
        
        task.setOnSucceeded(e -> {
            try {
                StyleSpans<Collection<String>> highlighting = task.getValue();
                System.out.println("Applying highlighting with " + highlighting.length() + " spans");
                codeArea.setStyleSpans(0, highlighting);
                System.out.println("Syntax highlighting applied successfully");
            } catch (Exception ex) {
                System.err.println("Failed to apply syntax highlighting: " + ex.getMessage());
                ex.printStackTrace();
            }
        });
        
        task.setOnFailed(e -> {
            Throwable exception = task.getException();
            System.err.println("Syntax highlighting task failed: " + exception.getMessage());
            exception.printStackTrace();
        });
        
        // Run highlighting in background thread to avoid blocking UI
        Thread highlightThread = new Thread(task);
        highlightThread.setDaemon(true);
        highlightThread.start();
    }
    
    private static StyleSpans<Collection<String>> computeHighlighting(String text, ScriptRunnerApp.ScriptLanguage language) {
        System.out.println("Computing highlighting for text: " + text.substring(0, Math.min(50, text.length())) + "...");
        System.out.println("Language: " + language);
        
        Matcher matcher = PATTERN.matcher(text);
        int lastKwEnd = 0;
        StyleSpansBuilder<Collection<String>> spansBuilder = new StyleSpansBuilder<>();
        
        Set<String> relevantKeywords = getKeywordsForLanguage(language);
        System.out.println("Relevant keywords: " + relevantKeywords);
        
        while (matcher.find()) {
            String styleClass = null;
            
            if (matcher.group("KEYWORD") != null) {
                // Only highlight if it's a keyword for the current language
                String keyword = matcher.group("KEYWORD");
                if (relevantKeywords.contains(keyword)) {
                    styleClass = "keyword";
                }
            } else if (matcher.group("STRING") != null) {
                styleClass = "string";
            } else if (matcher.group("COMMENT") != null) {
                styleClass = "comment";
            }
            
            if (styleClass != null) {
                spansBuilder.add(Collections.emptyList(), matcher.start() - lastKwEnd);
                spansBuilder.add(Collections.singleton(styleClass), matcher.end() - matcher.start());
                lastKwEnd = matcher.end();
            }
        }
        spansBuilder.add(Collections.emptyList(), text.length() - lastKwEnd);
        return spansBuilder.create();
    }
    
    private static Set<String> getKeywordsForLanguage(ScriptRunnerApp.ScriptLanguage language) {
        switch (language) {
            case SWIFT:
                return SWIFT_KEYWORDS;
            case KOTLIN:
                return KOTLIN_KEYWORDS;
            default:
                return Collections.emptySet();
        }
    }
    
    // CSS styles for syntax highlighting
    public static String getStyleSheet() {
        return """
            .code-area .keyword {
                -fx-fill: #0000ff;
                -fx-font-weight: bold;
            }
            
            .code-area .string {
                -fx-fill: #008000;
            }
            
            .code-area .comment {
                -fx-fill: #808080;
                -fx-font-style: italic;
            }
            
            .code-area .error-location {
                -fx-fill: #ff0000;
                -fx-underline: true;
                -fx-cursor: hand;
            }
            """;
    }
}