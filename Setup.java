import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Duration;
import java.util.*;
import java.util.regex.*;

/** One-time local bootstrap, JDK 25 only. Runs in a disposable container; reads qBittorrent logs from stdin. */
class Setup {
    private static final Path ENV = Path.of(".env");
    private static final Path JACKETT_CONFIG = Path.of("data/jackett/Jackett/ServerConfig.json");
    private static final URI QBIT = URI.create("http://127.0.0.1:8081");
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(4))
            .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
            .build();

    public static void main(String[] args) {
        try {
            if (!Files.exists(ENV)) Files.copy(Path.of(".env.example"), ENV);
            restrictEnv();
            await("http://127.0.0.1:9117/UI/Dashboard");
            await("http://127.0.0.1:8081/api/v2/app/version");

            String config = awaitConfig();
            Matcher keyMatcher = Pattern.compile("\"APIKey\"\\s*:\\s*\"([A-Za-z0-9_-]+)\"").matcher(config);
            if (!keyMatcher.find()) throw new IllegalStateException("No se encontró APIKey en ServerConfig.json");
            setEnv("JACKETT_API_KEY", keyMatcher.group(1));
            System.out.println("[setup] Jackett: API key importada");

            Map<String, String> env = readEnv();
            String existing = env.getOrDefault("QBIT_PASSWORD", "");
            if (!existing.isBlank()) {
                login(existing);
                System.out.println("[setup] qBittorrent: contraseña existente verificada");
            } else {
                String logs = new String(System.in.readAllBytes(), StandardCharsets.UTF_8);
                Matcher password = Pattern.compile("temporary password is provided for this session:\\s*(\\S+)",
                        Pattern.CASE_INSENSITIVE).matcher(logs);
                String temporary = null;
                while (password.find()) temporary = password.group(1);
                if (temporary == null) throw new IllegalStateException(
                        "No se encontró contraseña temporal en logs. Configura QBIT_PASSWORD si ya existe.");
                login(temporary);
                String generated = Base64.getUrlEncoder().withoutPadding()
                        .encodeToString(java.security.SecureRandom.getSeed(36));
                String json = "{\"web_ui_password\":\"" + generated + "\"}";
                request("POST", QBIT.resolve("/api/v2/app/setPreferences"),
                        "json=" + URLEncoder.encode(json, StandardCharsets.UTF_8));
                login(generated);
                setEnv("QBIT_USER", "admin");
                setEnv("QBIT_PASSWORD", generated);
                System.out.println("[setup] qBittorrent: contraseña aleatoria generada y verificada");
            }
            System.out.println("[setup] Preparado. Configura el bot de Telegram y al menos un indexador en Jackett.");
        } catch (Exception e) {
            System.err.println("[setup] ERROR: " + e.getMessage());
            System.exit(1);
        }
    }

    private static void login(String password) throws Exception {
        String form = "username=admin&password=" + URLEncoder.encode(password, StandardCharsets.UTF_8);
        String response = request("POST", QBIT.resolve("/api/v2/auth/login"), form);
        if (!response.trim().equals("Ok.")) throw new IllegalStateException("Credenciales qBittorrent incorrectas");
    }

    private static String request(String method, URI uri, String form) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(8))
                .header("Referer", QBIT + "/").header("Origin", QBIT.toString());
        if (method.equals("POST")) builder.header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form));
        else builder.GET();
        HttpResponse<String> response = HTTP.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 400) throw new IllegalStateException(
                "HTTP " + response.statusCode() + " en " + uri.getPath());
        return response.body();
    }

    private static void await(String url) throws Exception {
        long end = System.nanoTime() + Duration.ofSeconds(120).toNanos();
        while (System.nanoTime() < end) {
            try {
                HTTP.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(3))
                        .GET().build(), HttpResponse.BodyHandlers.discarding());
                return;
            } catch (IOException | InterruptedException e) {
                if (e instanceof InterruptedException) throw e;
                Thread.sleep(2000);
            }
        }
        throw new IllegalStateException("Tiempo agotado esperando " + url);
    }

    private static String awaitConfig() throws Exception {
        long end = System.nanoTime() + Duration.ofSeconds(120).toNanos();
        while (System.nanoTime() < end) {
            if (Files.isReadable(JACKETT_CONFIG)) {
                String config = Files.readString(JACKETT_CONFIG);
                if (config.contains("\"APIKey\"")) return config;
            }
            Thread.sleep(2000);
        }
        throw new IllegalStateException("No se puede leer la configuración de Jackett: " + JACKETT_CONFIG);
    }

    private static Map<String, String> readEnv() throws IOException {
        Map<String, String> env = new LinkedHashMap<>();
        for (String line : Files.readAllLines(ENV)) {
            if (line.isBlank() || line.stripLeading().startsWith("#")) continue;
            int index = line.indexOf('=');
            if (index > 0) {
                String value = line.substring(index + 1).strip();
                if (value.length() >= 2 && (value.startsWith("'") && value.endsWith("'")
                        || value.startsWith("\"") && value.endsWith("\""))) {
                    value = value.substring(1, value.length() - 1);
                }
                env.put(line.substring(0, index), value);
            }
        }
        return env;
    }

    private static void setEnv(String key, String value) throws IOException {
        if (!value.matches("[A-Za-z0-9_-]+")) throw new IllegalArgumentException("Formato no permitido para " + key);
        List<String> lines = new ArrayList<>(Files.readAllLines(ENV));
        boolean replaced = false;
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).startsWith(key + "=")) {
                lines.set(i, key + "=" + value);
                replaced = true;
            }
        }
        if (!replaced) lines.add(key + "=" + value);
        Path temp = Files.createTempFile(ENV.toAbsolutePath().getParent(), ".env-", ".tmp");
        try {
            Files.write(temp, lines, StandardCharsets.UTF_8);
            restrict(temp);
            Files.move(temp, ENV, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    private static void restrictEnv() throws IOException { restrict(ENV); }
    private static void restrict(Path file) throws IOException {
        try {
            Files.setPosixFilePermissions(file, EnumSet.of(
                    PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
        } catch (UnsupportedOperationException ignored) { }
    }

    private static void run(String... cmd) throws Exception {
        Process process = new ProcessBuilder(cmd).inheritIO().start();
        if (process.waitFor() != 0) throw new IllegalStateException("Falló: " + String.join(" ", cmd));
    }

    private static String capture(String... cmd) throws Exception {
        Process process = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (process.waitFor() != 0) throw new IllegalStateException("Falló: " + String.join(" ", cmd));
        return output;
    }
}
