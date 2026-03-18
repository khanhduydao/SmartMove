package com.smartmove.audit;

import com.smartmove.constants.SmartMoveConstants;
import com.smartmove.config.LoggerFactory;
import java.util.logging.Logger;

import java.io.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

public class AuditLog {
    private static final Logger logger = LoggerFactory.getLogger(AuditLog.class);
    private static final String LOG_FILE = SmartMoveConstants.AUDIT_LOG_CSV;
    private static final String HEADER = "seqId,timestamp,eventType,payload,prevChecksum,checksum";
    private static final String GENESIS_CHECKSUM = SmartMoveConstants.GENESIS_CHECKSUM;

    private final List<AuditEntry> inMemoryLog = new ArrayList<>();
    private final AtomicLong sequenceCounter = new AtomicLong(0);
    private final Object writeLock = new Object();

    public AuditLog() {
        loadFromFile();
    }

    public void append(AuditEntry entry) throws AuditWriteException {
        synchronized (writeLock) {
            boolean writeSuccess = writeEntryToFile(entry);
            if (!writeSuccess) {
                throw new AuditWriteException(
                        "Failed to persist audit entry seq=" + entry.getSeqId()
                                + ". In-memory state NOT updated to maintain consistency.");
            }
            inMemoryLog.add(entry);
        }
    }

    public AuditEntry createEntry(AuditEventType eventType, String payload) {
        long seq = sequenceCounter.incrementAndGet();
        String prevChecksum = inMemoryLog.isEmpty()
                ? GENESIS_CHECKSUM
                : inMemoryLog.get(inMemoryLog.size() - 1).getChecksum();
        return new AuditEntry(seq, Instant.now().toString(),
                eventType.getEventName(), payload, prevChecksum);
    }

    public boolean verifyChain() {
        synchronized (writeLock) {
            String prevChecksum = GENESIS_CHECKSUM;
            for (AuditEntry entry : inMemoryLog) {
                if (!entry.verifyIntegrity(prevChecksum)) {
                    logger.severe(() -> "[AuditLog] INTEGRITY VIOLATION at seq=" + entry.getSeqId());
                    return false;
                }
                prevChecksum = entry.getChecksum();
            }
            logger.info(() -> "[AuditLog] Chain integrity verified. Entries: " + inMemoryLog.size());
            return true;
        }
    }

    public long getLastStableSnapshotId() {
        synchronized (writeLock) {
            if (inMemoryLog.isEmpty()) return 0L;
            return inMemoryLog.get(inMemoryLog.size() - 1).getSeqId();
        }
    }

    public List<AuditEntry> getEntries() {
        synchronized (writeLock) {
            return new ArrayList<>(inMemoryLog);
        }
    }

    public void printLog() {
        synchronized (writeLock) {
            logger.info(() -> "=== AUDIT LOG (" + inMemoryLog.size() + " entries) ===");
            for (AuditEntry e : inMemoryLog) {
                logger.info(() -> String.format("  [%3d] %s | %s | %s | checksum=%s%n",
                        e.getSeqId(), e.getTimestamp(), e.getEventType(),
                        e.getPayload(), e.getChecksum().substring(0, 8)));
            }
            logger.info("=== END AUDIT LOG ===");
        }
    }

    private boolean writeEntryToFile(AuditEntry entry) {
        try {
            Path path = Paths.get(LOG_FILE);
            Files.createDirectories(path.getParent());

            boolean fileExists = Files.exists(path);
            try (BufferedWriter writer = Files.newBufferedWriter(path,
                    fileExists ? StandardOpenOption.APPEND : StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE)) {
                if (!fileExists) {
                    writer.write(HEADER);
                    writer.newLine();
                }
                writer.write(entry.toCsv());
                writer.newLine();
                writer.flush();
            }
            return true;
        } catch (IOException e) {
            logger.severe(() -> "[AuditLog] Write failed: " + e.getMessage());
            return false;
        }
    }

    private void loadFromFile() {
        Path path = Paths.get(LOG_FILE);
        if (!Files.exists(path)) {
            logger.info("[AuditLog] No existing log file. Starting fresh.");
            return;
        }
        try (BufferedReader reader = Files.newBufferedReader(path)) {
            String line;
            boolean firstLine = true;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                if (firstLine && line.startsWith("seqId")) { firstLine = false; continue; }
                firstLine = false;
                try {
                    AuditEntry entry = AuditEntry.fromCsv(line);
                    inMemoryLog.add(entry);
                    if (entry.getSeqId() > sequenceCounter.get()) {
                        sequenceCounter.set(entry.getSeqId());
                    }
                } catch (Exception e) {
                    final String malformedLine = line;
                    logger.severe(() -> "[AuditLog] Skipping malformed line: " + malformedLine);
                }
            }
            logger.info(() -> "[AuditLog] Loaded " + inMemoryLog.size() + " entries from file.");
        } catch (IOException e) {
            logger.severe(() -> "[AuditLog] Failed to load log: " + e.getMessage());
        }
    }
}
