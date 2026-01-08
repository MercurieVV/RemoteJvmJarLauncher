package io.github.mercurievv.rjjl;


import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.UnauthorizedResponse;
import io.javalin.http.UploadedFile;
import io.javalin.http.staticfiles.Location;
import com.fasterxml.jackson.databind.ObjectMapper;

import static io.javalin.apibuilder.ApiBuilder.*;

import io.javalin.json.JavalinJackson;
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
        int internalPort = Integer.parseInt(System.getenv().getOrDefault("INTERNAL_HTTP_PORT", "8666"));
        int externalPort = Integer.parseInt(System.getenv().getOrDefault("EXTERNAL_HTTP_PORT", "8777"));

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

        Javalin internalServer = startHttpServer(internalPort, pluginsDir, pluginManager);
        Javalin externalServer = createAuth(
                startHttpServer(externalPort, pluginsDir, pluginManager),
                authToken
        );

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutting down, stopping plugins...");
            pluginManager.stopPlugins();
            pluginManager.unloadPlugins();
            internalServer.stop();
            externalServer.stop();
            log.info("RJJL Stopped");
        }));
    }

    private static Javalin startHttpServer(int port, Path pluginsDir, PluginManager pluginManager) {
        FileController files = new FileController(pluginManager, pluginsDir);
        ObjectMapper objectMapper = new ObjectMapper();

        Javalin app = Javalin.create(config -> {
            config.showJavalinBanner = false;
            config.staticFiles.add(staticCfg -> {
                staticCfg.hostedPath = "/";
                staticCfg.directory = "/public";
                staticCfg.location = Location.CLASSPATH;
                staticCfg.precompress = false;
            });
            config.router.apiBuilder(() -> {
                fileRoutes(files);
            });
            config.jsonMapper(new JavalinJackson(objectMapper, true));
        }).start(port);

        log.info("HTTP server started on port {}", port);
        return app;
    }

    private static Javalin createAuth(Javalin app, String authToken) {
        // ---- AUTH MIDDLEWARE (all routes on the external server) ----
        app.before(ctx -> {
            // public endpoints
            if (ctx.path().equals("/health")) return;

            if (authToken == null || authToken.isBlank()) {
                throw new UnauthorizedResponse("AUTH_TOKEN is not configured");
            }

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

        return app;
    }


    private static void fileRoutes(FileController files) {
        get("plugins", files::list);
        post("plugins", files::upload);
        delete("plugins/{pluginId}", files::delete);
        get("/health", ctx -> ctx.result("OK"));
    }

    public static class FileController {
        public PluginManager pluginManager;
        public Path pluginsDir;

        FileController(PluginManager pluginManager, Path pluginsDir) {
            this.pluginManager = pluginManager;
            this.pluginsDir = pluginsDir;
        }

        void list(Context ctx) throws Exception {
            var ids = pluginManager.getPlugins().stream()
                    .map(p -> p.getDescriptor().getPluginId() + ":" + p.getDescriptor().getVersion())
                    .toList();
            ctx.json(ids);
        }

        void upload(Context ctx) throws Exception {
            UploadedFile file = ctx.uploadedFile("file");
            if (file == null) {
                ctx.status(400).result("No file");
                return;
            }

            String filename = file.filename(); // <-- original filename
            InputStream content = file.content();
            Path target = pluginsDir.resolve(filename);
            log.info("Uploading plugin JAR to {}", target);

            Files.copy(content, target, StandardCopyOption.REPLACE_EXISTING);

            // Load and start the plugin
            String pluginId = pluginManager.loadPlugin(target);
            pluginManager.startPlugin(pluginId);

            log.info("Plugin loaded and started: {}", pluginId);
            ctx.result("Uploaded and started plugin: " + pluginId);
        }

        void delete(Context ctx) throws Exception {
            String pluginId = ctx.pathParam("pluginId").replaceFirst(":.+$", ""); // remove version if present
            pluginManager.stopPlugin(pluginId);
            pluginManager.deletePlugin(pluginId);
        }
        //void download(Context ctx) throws Exception;
    }
}
