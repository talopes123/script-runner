package com.scriptrunner;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ScriptExecutor {
    
    private Process currentProcess;
    private ExecutorService executorService;
    private Path tempDir;
    
    public interface ExecutionCallback {
        void onOutput(String output);
        void onError(String error);
        void onComplete(int exitCode);
    }
    
    public ScriptExecutor() {
        this.executorService = Executors.newCachedThreadPool();
        try {
            this.tempDir = Files.createTempDirectory("script-runner");
        } catch (IOException e) {
            throw new RuntimeException("Failed to create temp directory", e);
        }
    }
    
    public void execute(String code, ScriptRunnerApp.ScriptLanguage language, ExecutionCallback callback) {
        CompletableFuture.runAsync(() -> {
            try {
                // Create temporary script file
                String fileName = "script." + language.getFileExtension();
                Path scriptFile = tempDir.resolve(fileName);
                Files.write(scriptFile, code.getBytes(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                
                // Build command
                String[] command;
                if (language == ScriptRunnerApp.ScriptLanguage.SWIFT) {
                    command = new String[]{"/usr/bin/env", "swift", scriptFile.toString()};
                } else if (language == ScriptRunnerApp.ScriptLanguage.KOTLIN) {
                    command = new String[]{"kotlinc", "-script", scriptFile.toString()};
                } else {
                    callback.onError("Unsupported language: " + language);
                    return;
                }
                
                // Create process
                ProcessBuilder pb = new ProcessBuilder(command);
                pb.redirectErrorStream(true); // Merge stderr into stdout
                
                synchronized (this) {
                    currentProcess = pb.start();
                }
                
                // Start output reader
                startOutputReader(currentProcess, callback);
                
                // Wait for process completion
                int exitCode = currentProcess.waitFor();
                
                synchronized (this) {
                    currentProcess = null;
                }
                
                callback.onComplete(exitCode);
                
            } catch (InterruptedException e) {
                callback.onError("Script execution was interrupted");
                Thread.currentThread().interrupt();
            } catch (IOException e) {
                callback.onError("Failed to execute script: " + e.getMessage());
            } catch (Exception e) {
                callback.onError("Unexpected error: " + e.getMessage());
            }
        }, executorService);
    }
    
    private void startOutputReader(Process process, ExecutionCallback callback) {
        // Read stdout/stderr in separate thread
        Thread outputReader = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    // Pass output directly - UI will handle clickable parsing
                    callback.onOutput(line + "\n");
                }
            } catch (IOException e) {
                if (!Thread.currentThread().isInterrupted()) {
                    callback.onError("Error reading output: " + e.getMessage());
                }
            }
        });
        
        outputReader.setDaemon(true);
        outputReader.start();
    }
    
    private boolean containsErrorLocation(String line) {
        // Check for Swift error pattern: filename:line:column: error/warning
        // Check for Kotlin error pattern: filename:line:column: error/warning
        return line.matches(".*\\w+\\.(swift|kts):\\d+:\\d+:\\s+(error|warning|note):.*");
    }
    
    private String makeErrorLocationClickable(String line) {
        // Add special markers that can be detected by the UI for clickable links
        // Pattern: filename:line:column: type: message
        if (line.matches(".*\\w+\\.(swift|kts):\\d+:\\d+:\\s+(error|warning|note):.*")) {
            // Extract the location part (filename:line:column)
            String[] parts = line.split(":", 4);
            if (parts.length >= 3) {
                try {
                    String filename = parts[0].trim();
                    int lineNum = Integer.parseInt(parts[1].trim());
                    int colNum = Integer.parseInt(parts[2].trim());
                    
                    // Store clickable info invisibly (could be stored in a map)
                    // For now, add minimal marker at start of line
                    return String.format("🔗 %s", line);
                } catch (NumberFormatException e) {
                    // If parsing fails, return original line
                    return line;
                }
            }
        }
        return line;
    }
    
    public synchronized void stop() {
        if (currentProcess != null && currentProcess.isAlive()) {
            currentProcess.destroyForcibly();
            currentProcess = null;
        }
    }
    
    public void shutdown() {
        stop();
        executorService.shutdown();
        
        // Cleanup temp directory
        try {
            Files.walk(tempDir)
                .sorted((p1, p2) -> -p1.compareTo(p2)) // Reverse order for deletion
                .forEach(path -> {
                    try {
                        Files.delete(path);
                    } catch (IOException e) {
                        // Ignore cleanup errors
                    }
                });
        } catch (IOException e) {
            // Ignore cleanup errors
        }
    }
}