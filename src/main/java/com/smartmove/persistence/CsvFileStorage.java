package com.smartmove.persistence;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * Generic CSV file storage.
 * Subclasses implement toCsv() and fromCsv() for their specific type.
 */
public abstract class CsvFileStorage<T> implements FileStorage<T> {

    protected final String filePath;
    protected final String header;

    protected CsvFileStorage(String filePath, String header) {
        this.filePath = filePath;
        this.header = header;
    }

    @Override
    public List<T> loadAll() {
        List<T> results = new ArrayList<>();
        Path path = Paths.get(filePath);
        if (!Files.exists(path)) return results;

        try (BufferedReader reader = Files.newBufferedReader(path)) {
            String line;
            boolean firstLine = true;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                if (firstLine) { firstLine = false; continue; } // skip header
                try {
                    T item = fromCsv(line);
                    if (item != null) results.add(item);
                } catch (Exception e) {
                    System.err.println("[CsvStorage] Failed to parse line in " + filePath + ": " + line);
                }
            }
        } catch (IOException e) {
            System.err.println("[CsvStorage] Failed to load " + filePath + ": " + e.getMessage());
        }
        return results;
    }

    @Override
    public void saveAll(List<T> items) {
        try {
            Path path = Paths.get(filePath);
            Files.createDirectories(path.getParent());
            try (BufferedWriter writer = Files.newBufferedWriter(path,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
                writer.write(header);
                writer.newLine();
                for (T item : items) {
                    writer.write(toCsv(item));
                    writer.newLine();
                }
                writer.flush();
            }
        } catch (IOException e) {
            System.err.println("[CsvStorage] Failed to save " + filePath + ": " + e.getMessage());
        }
    }

    protected abstract T fromCsv(String line);
    protected abstract String toCsv(T item);

    protected static String[] splitCsv(String line) {
        List<String> result = new ArrayList<>();
        boolean inQuotes = false;
        StringBuilder current = new StringBuilder();
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"'); i++;
                } else { inQuotes = !inQuotes; }
            } else if (c == ',' && !inQuotes) {
                result.add(current.toString()); current = new StringBuilder();
            } else { current.append(c); }
        }
        result.add(current.toString());
        return result.toArray(new String[0]);
    }

    protected static String escapeCsv(String s) {
        if (s == null) return "";
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }
}
