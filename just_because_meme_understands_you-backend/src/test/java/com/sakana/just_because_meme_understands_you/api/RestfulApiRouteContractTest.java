package com.sakana.just_because_meme_understands_you.api;

import com.sakana.just_because_meme_understands_you.api.admin.controller.AdminMemeController;
import com.sakana.just_because_meme_understands_you.api.admin.controller.AdminAuthController;
import com.sakana.just_because_meme_understands_you.api.user.controller.AuthController;
import com.sakana.just_because_meme_understands_you.api.user.controller.AiSearchController;
import com.sakana.just_because_meme_understands_you.api.user.controller.HomeController;
import com.sakana.just_because_meme_understands_you.api.user.controller.MemeCommentController;
import com.sakana.just_because_meme_understands_you.api.user.controller.MemeController;
import com.sakana.just_because_meme_understands_you.api.user.controller.MemeCommentLikeController;
import com.sakana.just_because_meme_understands_you.api.user.controller.MemeFavoriteController;
import com.sakana.just_because_meme_understands_you.api.user.controller.NotificationController;
import com.sakana.just_because_meme_understands_you.api.user.controller.UserFollowController;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import com.sakana.just_because_meme_understands_you.modules.auth.infrastructure.config.SecurityConfig;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RestfulApiRouteContractTest {

    private static final String API_PACKAGE = "com.sakana.just_because_meme_understands_you.api.";

    @Test
    void everyUserAndAdminControllerUsesTheVersionedRouteSpace() throws Exception {
        assertControllersVersioned("user");
        assertControllersVersioned("admin");
    }

    @Test
    void canonicalRoutesUseResourceNamesAndRestfulMethods() {
        assertRoute(AuthController.class, "POST", "/v1/auth/sessions");
        assertRoute(AuthController.class, "POST", "/v1/auth/login");
        assertRoute(AuthController.class, "PUT", "/v1/auth/sessions/current");
        assertRoute(AuthController.class, "DELETE", "/v1/auth/sessions/current");
        assertRoute(HomeController.class, "GET", "/v1/memes/{memeId}");
        assertRoute(MemeController.class, "POST", "/v1/memes/{memeId}/restorations");
        assertRoute(MemeCommentController.class, "DELETE", "/v1/comments/{commentId}");
        assertRoute(MemeCommentLikeController.class, "GET", "/v1/comments/{commentId}/likes/me");
        assertRoute(MemeCommentLikeController.class, "PUT", "/v1/comments/{commentId}/likes/me");
        assertRoute(MemeCommentLikeController.class, "DELETE", "/v1/comments/{commentId}/likes/me");
        assertRoute(MemeFavoriteController.class, "PUT", "/v1/users/me/favorites/{memeId}");
        assertRoute(MemeFavoriteController.class, "PATCH", "/v1/users/me/favorites/{memeId}");
        assertRoute(UserFollowController.class, "PUT", "/v1/users/{userId}/followers/me");
        assertRoute(UserFollowController.class, "DELETE", "/v1/users/{userId}/followers/me");
        assertRoute(NotificationController.class, "PATCH", "/v1/notifications/{id}");
        assertRoute(AdminMemeController.class, "PATCH", "/admin/v1/memes/{id}");
        assertRoute(AdminMemeController.class, "DELETE", "/admin/v1/memes/{id}");
        assertRoute(AdminAuthController.class, "POST", "/admin/v1/auth/login");
        assertRoute(AiSearchController.class, "POST", "/v1/ai/searches");
        assertRoute(AiSearchController.class, "GET", "/v1/ai/searches/current");
        assertRoute(AiSearchController.class, "GET", "/v1/ai/searches/{taskId}/events");
        assertRoute(AiSearchController.class, "POST", "/v1/ai/searches/{taskId}/cancel");
    }

    @Test
    void everyAdminResourceControllerUsesMethodSecurityAndSpringMethodSecurityIsEnabled() throws Exception {
        assertTrue(SecurityConfig.class.isAnnotationPresent(EnableMethodSecurity.class),
                "Spring Security 方法授权必须启用");
        Path sourceDirectory = Path.of("src/main/java/com/sakana/just_because_meme_understands_you/api/admin/controller");
        try (var sourceFiles = Files.list(sourceDirectory)) {
            for (Path sourceFile : sourceFiles.filter(path -> path.toString().endsWith("Controller.java")).toList()) {
                String simpleName = sourceFile.getFileName().toString().replaceFirst("\\.java$", "");
                if (simpleName.equals("AdminAuthController")) continue;
                Class<?> controller = Class.forName(API_PACKAGE + "admin.controller." + simpleName);
                PreAuthorize authorization = controller.getDeclaredAnnotation(PreAuthorize.class);
                assertTrue(authorization != null, simpleName + " must require an admin role");
                assertTrue(authorization.value().contains("ADMIN") && authorization.value().contains("SUPER_ADMIN"),
                        simpleName + " must accept ADMIN and SUPER_ADMIN");
            }
        }
        assertTrue(AdminAuthController.class.getDeclaredMethod("logout",
                String.class, String.class, jakarta.servlet.http.HttpServletResponse.class)
                .isAnnotationPresent(PreAuthorize.class));
    }

    @Test
    void commentLikeStatusIsOptionalAuthWhileWritesStillRequireAuth() throws Exception {
        Path source = Path.of("src/main/java/com/sakana/just_because_meme_understands_you/modules/auth/infrastructure/config/WebMvcConfig.java");
        long optionalAndExcludedMappings = Files.readString(source).lines()
                .filter(line -> line.contains("\"/v1/comments/*/likes/me\""))
                .count();
        assertTrue(optionalAndExcludedMappings == 2,
                "comment-like route must be configured for optional auth and excluded from mandatory auth");
    }

    private void assertControllersVersioned(String module) throws Exception {
        Path sourceDirectory = Path.of("src/main/java/com/sakana/just_because_meme_understands_you/api", module, "controller");
        try (var sourceFiles = Files.list(sourceDirectory)) {
            List<Path> controllers = sourceFiles.filter(path -> path.toString().endsWith("Controller.java")).toList();
            assertTrue(!controllers.isEmpty(), "No controllers found under " + sourceDirectory);
            for (Path sourceFile : controllers) {
                String simpleName = sourceFile.getFileName().toString().replaceFirst("\\.java$", "");
                Class<?> controller = Class.forName(API_PACKAGE + module + ".controller." + simpleName);
                RequestMapping requestMapping = controller.getDeclaredAnnotation(RequestMapping.class);
                assertTrue(requestMapping != null, simpleName + " must declare a versioned class-level mapping");
                String[] paths = requestMapping.path().length == 0 ? requestMapping.value() : requestMapping.path();
                assertTrue(paths.length > 0, simpleName + " must declare a class-level path");
                for (String path : paths) {
                    boolean versioned = module.equals("admin")
                            ? path.equals("/admin/v1") || path.startsWith("/admin/v1/")
                            : path.equals("/v1") || path.startsWith("/v1/");
                    assertTrue(versioned, simpleName + " must be under its versioned route space, found " + path);
                }
            }
        }
    }

    private void assertRoute(Class<?> controller, String expectedMethod, String expectedPath) {
        assertTrue(routesFor(controller).contains(expectedMethod + " " + expectedPath),
                "Expected " + expectedMethod + " " + expectedPath + " but found " + routesFor(controller));
    }

    private void assertRouteAbsent(Class<?> controller, String method, String path) {
        assertFalse(routesFor(controller).contains(method + " " + path),
                "Did not expect " + method + " " + path + " but found " + routesFor(controller));
    }

    private Set<String> routesFor(Class<?> controller) {
        RequestMapping baseMapping = controller.getDeclaredAnnotation(RequestMapping.class);
        if (baseMapping == null) throw new AssertionError(controller.getSimpleName() + " must declare a class-level mapping");
        String[] basePaths = baseMapping.path().length == 0 ? baseMapping.value() : baseMapping.path();
        List<String> bases = basePaths.length == 0 ? List.of("") : List.of(basePaths);

        Set<String> actualRoutes = new java.util.HashSet<>();
        for (Method method : controller.getDeclaredMethods()) {
            for (Annotation annotation : method.getDeclaredAnnotations()) {
                String httpMethod = mappingMethod(annotation);
                if (httpMethod == null) continue;
                String[] paths = mappingPaths(annotation);
                if (paths.length == 0) paths = new String[]{""};
                for (String base : bases) {
                    for (String path : paths) {
                        actualRoutes.add(httpMethod + " " + join(base, path));
                    }
                }
            }
        }
        return actualRoutes;
    }

    private String mappingMethod(Annotation annotation) {
        if (annotation instanceof GetMapping) return "GET";
        if (annotation instanceof PostMapping) return "POST";
        if (annotation instanceof PutMapping) return "PUT";
        if (annotation instanceof PatchMapping) return "PATCH";
        if (annotation instanceof DeleteMapping) return "DELETE";
        return null;
    }

    private String[] mappingPaths(Annotation annotation) {
        if (annotation instanceof GetMapping mapping) return mapping.path().length == 0 ? mapping.value() : mapping.path();
        if (annotation instanceof PostMapping mapping) return mapping.path().length == 0 ? mapping.value() : mapping.path();
        if (annotation instanceof PutMapping mapping) return mapping.path().length == 0 ? mapping.value() : mapping.path();
        if (annotation instanceof PatchMapping mapping) return mapping.path().length == 0 ? mapping.value() : mapping.path();
        if (annotation instanceof DeleteMapping mapping) return mapping.path().length == 0 ? mapping.value() : mapping.path();
        return new String[0];
    }

    private String join(String base, String path) {
        return "/" + List.of(base, path).stream()
                .map(value -> value.replaceAll("^/+|/+$", ""))
                .filter(value -> !value.isEmpty())
                .collect(Collectors.joining("/"));
    }
}
