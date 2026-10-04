package com.sakana.just_because_meme_understands_you.architecture;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** API Controller 不应越过应用服务直接依赖实体或持久层 Mapper。 */
class ApiControllerDependencyTests {

    private static final Pattern FORBIDDEN_IMPORT = Pattern.compile(
            "(?m)^\\s*import\\s+(?:static\\s+)?"
                    + "com\\.sakana\\.just_because_meme_understands_you\\.modules\\.[\\w.]+"
                    + "\\.(?:domain\\.entity|infrastructure\\.persistence\\.mapper)"
                    + "(?:\\.[\\w]+)?\\s*;");

    @Test
    void apiControllersMustNotImportDomainEntitiesOrPersistenceMappers() throws Exception {
        List<Path> roots = List.of(
                Path.of("src/main/java/com/sakana/just_because_meme_understands_you/api/user/controller"),
                Path.of("src/main/java/com/sakana/just_because_meme_understands_you/api/admin/controller")
        );
        List<String> violations = new ArrayList<>();
        int controllerCount = 0;

        for (Path root : roots) {
            assertTrue(Files.isDirectory(root), "未找到 API Controller 目录: " + root);
            try (var paths = Files.walk(root)) {
                for (Path source : paths.filter(path -> path.toString().endsWith("Controller.java")).toList()) {
                    controllerCount++;
                    String sourceText = Files.readString(source);
                    var imports = FORBIDDEN_IMPORT.matcher(sourceText);
                    while (imports.find()) {
                        violations.add(root.relativize(source) + " -> " + imports.group().trim());
                    }
                }
            }
        }

        assertFalse(controllerCount == 0, "未找到待检查的 API Controller 源码");
        assertTrue(violations.isEmpty(), () -> "API Controller 存在越层依赖: " + violations);
    }
}