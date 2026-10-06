package mn.suld.api.worldbuild;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Result of a compile-time or in-world validation run. */
public record ValidationReport(List<Issue> issues, Map<String, Object> stats) {

    public ValidationReport {
        issues = List.copyOf(issues);
        stats = new LinkedHashMap<>(stats);
    }

    public boolean passed() {
        return issues.stream().noneMatch(i -> i.severity() == Issue.Severity.ERROR);
    }

    public long errors() {
        return issues.stream().filter(i -> i.severity() == Issue.Severity.ERROR).count();
    }

    public long warnings() {
        return issues.size() - errors();
    }

    public String summary() {
        StringBuilder sb = new StringBuilder();
        sb.append(passed() ? "PASS" : "FAIL").append(" — ").append(errors()).append(" error(s), ")
                .append(warnings()).append(" warning(s)\n");
        stats.forEach((k, v) -> sb.append("  ").append(k).append(": ").append(v).append('\n'));
        for (Issue i : issues) sb.append("  ").append(i).append('\n');
        return sb.toString();
    }
}
