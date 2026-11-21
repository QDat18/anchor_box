package com.smartbox.backend;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PostgreSQL từ chối "FOR UPDATE" khi câu truy vấn có LEFT JOIN (nullable side of an outer join),
 * nhưng H2 dùng trong test thì không, nên lỗi chỉ lộ ra trên production. Test này chặn mẫu SQL đó.
 */
class SqlLockingGuardTests {
    private static final Pattern LITERAL = Pattern.compile("\"((?:[^\"\\\\]|\\\\.)*)\"");

    @Test
    void noForUpdateCombinedWithOuterJoin() throws IOException {
        List<String> offenders = new ArrayList<>();
        try (Stream<Path> files = Files.walk(Path.of("src/main/java"))) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                List<String> lines = Files.readAllLines(file);
                for (int i = 0; i < lines.size(); i++) {
                    var matcher = LITERAL.matcher(lines.get(i));
                    while (matcher.find()) {
                        String sql = matcher.group(1).toUpperCase();
                        if (sql.matches(".*\\b(LEFT|RIGHT|FULL)( OUTER)? JOIN\\b.*") && sql.contains("FOR UPDATE") && !sql.contains("FOR UPDATE OF")) {
                            offenders.add(file + ":" + (i + 1));
                        }
                    }
                }
            }
        }
        assertTrue(offenders.isEmpty(), "FOR UPDATE với outer join sẽ lỗi trên PostgreSQL: " + offenders);
    }
}
