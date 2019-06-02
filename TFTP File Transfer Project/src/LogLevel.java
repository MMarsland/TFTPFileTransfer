
public enum LogLevel {

    INFO(5),
    WARN(4),
    ERROR(3),
    FATAL(0);

    private Integer severity;

    LogLevel(int severity) {
        this.severity = severity;
    }

    public boolean shouldLog(LogLevel other) {
        return this.severity <= other.severity;
    }
}