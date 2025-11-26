package io.github.mercurievv.rjjl;


import io.javalin.Javalin;
import io.javalin.http.UnauthorizedResponse;
import org.pf4j.DefaultPluginManager;
import org.pf4j.PluginManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.nio.file.*;

public class Main {
    private static final Logger log = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) throws Exception {
        // Config: PLUGINS_DIR env var or default ./plugins
        String pluginsDirEnv = System.getenv().getOrDefault("PLUGINS_DIR", "./plugins");
        Path pluginsDir = Paths.get(pluginsDirEnv).toAbsolutePath();
        int port = Integer.parseInt(System.getenv().getOrDefault("HTTP_PORT", "8080"));

        Files.createDirectories(pluginsDir);
        log.info("Using plugins directory: {}", pluginsDir);

        // -------- read auth token from env --------
        String authToken = System.getenv("AUTH_TOKEN");
        if (authToken == null || authToken.isBlank()) {
            log.warn("AUTH_TOKEN is not set — authentication will reject all protected requests");
        }


        // PF4J plugin manager
        PluginManager pluginManager = new DefaultPluginManager(pluginsDir);

        // Initial scan (load already-present plugins)
        pluginManager.loadPlugins();
        pluginManager.startPlugins();
        log.info("Initial plugins loaded and started.");

        Javalin httpServer = startHttpServer(port, authToken, pluginsDir, pluginManager);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutting down, stopping plugins...");
            pluginManager.stopPlugins();
            pluginManager.unloadPlugins();
            httpServer.stop();
            log.info("RJJL Stopped");
        }));
    }

    private static Javalin startHttpServer(int port, String authToken, Path pluginsDir, PluginManager pluginManager) {
        // Javalin HTTP server
        Javalin app = Javalin.create(config -> {
            config.showJavalinBanner = false;
        }).start(port);

        log.info("HTTP server started on port {}", port);

        // ---- AUTH MIDDLEWARE ----
        app.before(ctx -> {
            String path = ctx.path();

            // public endpoints
            if (path.equals("/health")) return;
            //if (ctx.method().equals("GET") && path.equals("/plugins")) return;

            // protected endpoints (upload, future admin calls)
            String header = ctx.header("Authorization");
            if (header == null || !header.startsWith("Bearer ")) {
                throw new UnauthorizedResponse("Missing Authorization header");
            }

            String provided = header.substring("Bearer ".length());
            if (!provided.equals(authToken)) {
                log.warn("Unauthorized access attempt");
                throw new UnauthorizedResponse("Invalid token");
            }
        });

        // ---- END AUTH ----

        // Health check
        app.get("/health", ctx -> ctx.result("OK"));

        // Upload endpoint:
        //   POST /plugins/upload/{fileName}
        //   Body: binary JAR bytes
        app.post("/plugins/upload/{fileName}", ctx -> {
            String fileName = ctx.pathParam("fileName");
            if (!fileName.endsWith(".jar")) {
                fileName = fileName + ".jar";
            }

            Path target = pluginsDir.resolve(fileName);
            log.info("Uploading plugin JAR to {}", target);

            try (InputStream in = ctx.bodyInputStream()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }

            // Load and start the plugin
            String pluginId = pluginManager.loadPlugin(target);
            pluginManager.startPlugin(pluginId);

            log.info("Plugin loaded and started: {}", pluginId);
            ctx.result("Uploaded and started plugin: " + pluginId);
        });

        // List plugins
        app.get("/plugins", ctx -> {
            var ids = pluginManager.getPlugins().stream()
                    .map(p -> p.getDescriptor().getPluginId() + ":" + p.getDescriptor().getVersion())
                    .toList();
            ctx.json(ids);
        });
        return app;
    }
}
