package com.carconnect.app.model;

/**
 * 日志条目模型
 */
public class LogEntry {
    public static final int LEVEL_DEBUG = 0;
    public static final int LEVEL_INFO = 1;
    public static final int LEVEL_WARN = 2;
    public static final int LEVEL_ERROR = 3;

    private long timestamp;
    private int level;
    private String tag;
    private String message;

    public LogEntry(int level, String tag, String message) {
        this.timestamp = System.currentTimeMillis();
        this.level = level;
        this.tag = tag;
        this.message = message;
    }

    public long getTimestamp() { return timestamp; }
    public int getLevel() { return level; }
    public String getTag() { return tag; }
    public String getMessage() { return message; }

    public String getLevelStr() {
        switch (level) {
            case LEVEL_DEBUG: return "D";
            case LEVEL_INFO:  return "I";
            case LEVEL_WARN:  return "W";
            case LEVEL_ERROR: return "E";
            default: return "I";
        }
    }
}
