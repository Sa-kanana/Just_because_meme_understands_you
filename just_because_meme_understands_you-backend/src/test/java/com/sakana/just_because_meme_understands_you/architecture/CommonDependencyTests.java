package com.sakana.just_because_meme_understands_you.architecture;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 公共组件不能反向依赖业务代码，无需引入额外测试依赖。 */
class CommonDependencyTests {

    @Test
    void commonMustNotReferenceOtherProjectPackages() throws Exception {
        Path common = Path.of("src/main/java/com/sakana/just_because_meme_understands_you/common");
        Pattern forbidden = Pattern.compile(
                "com\\.sakana\\.just_because_meme_understands_you\\.(?!common(?:\\.|\\b))[\\w.]+");
        var violations = new ArrayList<String>();
        try (var paths = Files.walk(common)) {
            var sources = paths.filter(path -> path.toString().endsWith(".java")).toList();
            assertFalse(sources.isEmpty(), "未找到公共组件源码，不能跳过依赖检查");
            for (Path source : sources) {
                var matcher = forbidden.matcher(Files.readString(source));
                while (matcher.find()) {
                    violations.add(source.getFileName() + " -> " + matcher.group());
                }
            }
        }
        assertTrue(violations.isEmpty(), () -> "公共组件引用了业务层：" + violations);
    }
}
