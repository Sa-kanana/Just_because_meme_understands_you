package com.sakana.just_because_meme_understands_you.architecture;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** 中文注释：验证 AI、梗图和用户模块通过公开契约交互，不越过领域与持久层边界。 */
class BusinessModuleBoundaryTests {

    private static final String BASE_PACKAGE =
            "com.sakana.just_because_meme_understands_you.modules.";

    @Test
    void aiMustNotImportMemePersistenceOrDomainPackages() throws Exception {
        assertNoForbiddenImports(
                "ai",
                Pattern.compile("\\b" + Pattern.quote(BASE_PACKAGE)
                        + "meme\\.(?:domain\\.entity|infrastructure(?:\\.|;))"));
    }

    @Test
    void memeMustNotImportAiImplementationPackages() throws Exception {
        assertNoForbiddenImports(
                "meme",
                Pattern.compile("\\b" + Pattern.quote(BASE_PACKAGE) + "ai\\."));
    }

    @Test
    void userMustNotImportMemePersistenceOrDomainPackages() throws Exception {
        assertNoForbiddenImports(
                "user",
                Pattern.compile("\\b" + Pattern.quote(BASE_PACKAGE)
                        + "meme\\.(?:domain\\.entity|infrastructure(?:\\.|;))"));
    }

    @Test
    void homeMustUseMemeContractInsteadOfMemePersistenceOrAiConfiguration() throws Exception {
        Pattern forbidden = Pattern.compile("\\b" + Pattern.quote(BASE_PACKAGE)
                + "(?:meme\\.(?:domain\\.entity|infrastructure(?:\\.|;))|ai\\.)");
        assertNoForbiddenImports("home", forbidden);
    }

    private void assertNoForbiddenImports(String ownerModule, Pattern forbiddenPackage)
            throws Exception {
        Path root = Path.of("src/main/java/com/sakana/just_because_meme_understands_you/modules",
                ownerModule);
        List<String> violations = new ArrayList<>();
        assertTrue(Files.isDirectory(root), "未找到业务模块目录: " + root);

        try (var paths = Files.walk(root)) {
            for (Path source : paths.filter(path -> path.toString().endsWith(".java")).toList()) {
                String sourceText = Files.readString(source);
                var imports = Pattern.compile("(?m)^\\s*import\\s+[^;]+;").matcher(sourceText);
                while (imports.find()) {
                    if (forbiddenPackage.matcher(imports.group()).find()) {
                        violations.add(root.relativize(source) + " -> " + imports.group().trim());
                    }
                }
            }
        }

        assertTrue(violations.isEmpty(), () -> ownerModule + " 模块存在跨模块实现依赖: " + violations);
    }
}
