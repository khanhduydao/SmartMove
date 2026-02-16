package com.smartmove.audit;

public class AuditEntry {
    private final long seqId;
    private final String timestamp;
    private final String eventType;
    private final String payload;
    private final String prevChecksum;
    private final String checksum;

    public AuditEntry(long seqId, String timestamp, String eventType,
                      String payload, String prevChecksum) {
        this.seqId = seqId;
        this.timestamp = timestamp;
        this.eventType = eventType;
        this.payload = payload;
        this.prevChecksum = prevChecksum;
        this.checksum = computeChecksum(seqId, timestamp, eventType, payload, prevChecksum);
    }

    // Constructor for loading existing entries from file
    public AuditEntry(long seqId, String timestamp, String eventType,
                      String payload, String prevChecksum, String checksum) {
        this.seqId = seqId;
        this.timestamp = timestamp;
        this.eventType = eventType;
        this.payload = payload;
        this.prevChecksum = prevChecksum;
        this.checksum = checksum;
    }

    private static String computeChecksum(long seqId, String timestamp,
                                           String eventType, String payload, String prev) {
        String data = seqId + "|" + timestamp + "|" + eventType + "|" + payload + "|" + prev;
        // Simple but deterministic checksum using djb2 hash
        long hash = 5381L;
        for (char c : data.toCharArray()) {
            hash = ((hash << 5) + hash) + c;
        }
        return Long.toHexString(Math.abs(hash));
    }

    public long getSeqId() { return seqId; }
    public String getTimestamp() { return timestamp; }
    public String getEventType() { return eventType; }
    public String getPayload() { return payload; }
    public String getPrevChecksum() { return prevChecksum; }
    public String getChecksum() { return checksum; }

    public boolean verifyIntegrity(String expectedPrevChecksum) {
        if (!prevChecksum.equals(expectedPrevChecksum)) return false;
        String recomputed = computeChecksum(seqId, timestamp, eventType, payload, prevChecksum);
        return checksum.equals(recomputed);
    }

    // CSV representation
    public String toCsv() {
        return seqId + "," + timestamp + "," + eventType + ","
                + escapeCsv(payload) + "," + prevChecksum + "," + checksum;
    }

    private static String escapeCsv(String s) {
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }

    public static AuditEntry fromCsv(String line) {
        // Simple CSV parse (handles quoted payload)
        String[] parts = splitCsv(line);
        if (parts.length < 6) throw new IllegalArgumentException("Invalid audit CSV: " + line);
        return new AuditEntry(
                Long.parseLong(parts[0].trim()),
                parts[1].trim(),
                parts[2].trim(),
                parts[3].trim(),
                parts[4].trim(),
                parts[5].trim()
        );
    }

    private static String[] splitCsv(String line) {
        java.util.List<String> result = new java.util.ArrayList<>();
        boolean inQuotes = false;
        StringBuilder current = new StringBuilder();
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (c == ',' && !inQuotes) {
                result.add(current.toString());
                current = new StringBuilder();
            } else {
                current.append(c);
            }
        }
        result.add(current.toString());
        return result.toArray(new String[0]);
    }

    @Override
    public String toString() {
        return String.format("AuditEntry[seq=%d, type=%s, checksum=%s]", seqId, eventType, checksum);
    }
}
