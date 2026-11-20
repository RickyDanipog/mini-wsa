import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.StringJoiner;
import java.util.regex.Pattern;

class GenerateEvents {
    private static final String[] CATEGORIES = {
            "INJECTION", "XSS", "PROTOCOL_VIOLATION", "DATA_LEAKAGE", "BOT", "DOS", "RATE_LIMIT"
    };
    private static final String[] PATHS = {"/", "/cart", "/api/search", "/api/v1/login", "/admin/settings"};
    private static final String[] WAVE_PATHS = {"/api/v1/login", "/admin/settings"};
    private static final String[] SEVERITIES = {"CRITICAL", "HIGH", "MEDIUM", "LOW"};
    private static final String[] ACTIONS = {"DENY", "ALERT", "MONITOR"};
    private static final Pattern ACCEPTED_COUNT = Pattern.compile("\\{\\s*\"acceptedCount\"\\s*:\\s*(\\d+)\\s*}");
    private static final String USAGE = """
            java scripts/GenerateEvents.java [--count 1000] [--waves 3] [--seed 42]
              [--start ISO-8601] [--batch-size 200] [--output FILE | --post URL]
            Without an output option, writes JSON to stdout.
            """;

    public static void main(String[] args) {
        if (args.length == 1 && args[0].equals("--help")) {
            System.out.print(USAGE);
            return;
        }
        try {
            Options options = Options.parse(args);
            Dataset events = new Dataset(options);
            if (options.url != null) {
                post(events, options);
            } else {
                write(events, options);
            }
        } catch (IllegalArgumentException | DateTimeParseException | IOException error) {
            System.err.println(error.getMessage());
            System.exit(1);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            System.err.println("Ingestion was interrupted");
            System.exit(1);
        }
    }

    private static void write(Dataset events, Options options) throws IOException {
        try (var writer = options.output == null ? new BufferedWriter(new OutputStreamWriter(System.out))
                : Files.newBufferedWriter(options.output)) {
            writer.write("[\n");
            for (int index = 0; index < options.count; index++) {
                if (index > 0) writer.write(",\n");
                writer.write(events.next(index));
            }
            writer.write("\n]\n");
        }
    }

    private static void post(Dataset events, Options options) throws IOException, InterruptedException {
        try (var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()) {
            for (int index = 0; index < options.count;) {
                int count = Math.min(options.batchSize, options.count - index);
                var batch = new StringJoiner(",", "[", "]");
                for (int end = index + count; index < end; index++) {
                    batch.add(events.next(index));
                }
                var request = HttpRequest.newBuilder(options.url).timeout(Duration.ofSeconds(30))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(batch.toString())).build();
                var response = client.send(request, HttpResponse.BodyHandlers.ofString());
                var accepted = ACCEPTED_COUNT.matcher(response.body().strip());
                if (response.statusCode() != 201 || !accepted.matches() || Integer.parseInt(accepted.group(1)) != count) {
                    throw new IOException("Ingestion returned HTTP %d after %d confirmed events: %s"
                            .formatted(response.statusCode(), index - count, response.body()));
                }
            }
            System.out.printf(Locale.ROOT, "Accepted %d events%n", options.count);
        }
    }

    private static final class Dataset {
        private final Options options;
        private final SplittableRandom random;
        private final long runId;
        private int wave = 1;
        private int remaining;
        private String waveIp;
        private String wavePath;

        Dataset(Options options) {
            this.options = options;
            random = new SplittableRandom(options.seed);
            runId = random.nextLong();
        }

        String next(int index) {
            if (wave <= options.waves && index == waveStart(wave)) {
                remaining = Math.min(20, waveStart(wave + 1) - index);
                waveIp = "2001:db8::" + Integer.toHexString(wave);
                wavePath = pick(WAVE_PATHS);
                wave++;
            }

            String ip = "203.0.113." + random.nextInt(1, 255);
            String path = pick(PATHS);
            if (remaining > 0) {
                ip = waveIp;
                path = wavePath;
                remaining--;
            }

            int config = random.nextInt(14227, 14230);
            String category = pick(CATEGORIES);
            String action = pick(ACTIONS);
            return String.format(Locale.ROOT, """
                    {"eventId":"evt-java-%016x-%06d","timestamp":"%s","configId":%d,"policyId":"policy-%d",
                     "clientIp":"%s","hostname":"%s","path":"%s","method":"%s","statusCode":%d,"userAgent":"%s",
                     "rule":{"id":"rule-%s","name":"%s","message":"Synthetic %s event","severity":"%s","category":"%s"},
                     "action":"%s","geoLocation":{"country":"US","city":"New York"},
                     "requestSize":%d,"responseSize":%d}""",
                    runId, index, options.start.plusMillis(index * 20L), config, config, ip,
                    random.nextBoolean() ? "shop.example.com" : "api.example.com", path,
                    random.nextBoolean() ? "GET" : "POST", action.equals("DENY") ? 403 : 200,
                    random.nextBoolean() ? "Mozilla/5.0" : "SyntheticClient/1.0",
                    category.toLowerCase(Locale.ROOT), category, category, pick(SEVERITIES), category, action,
                    random.nextInt(128, 4097), random.nextInt(128, 4097));
        }

        private int waveStart(int position) {
            return (int) ((long) position * options.count / (options.waves + 1));
        }

        private String pick(String[] choices) {
            return choices[random.nextInt(choices.length)];
        }
    }

    private record Options(int count, int waves, long seed, Instant start, int batchSize, Path output, URI url) {
        private static final Set<String> FLAGS = Set.of(
                "--count", "--waves", "--seed", "--start", "--batch-size", "--output", "--post");

        static Options parse(String[] args) {
            Map<String, String> values = new HashMap<>();
            for (int index = 0; index < args.length; index += 2) {
                String flag = args[index];
                if (!FLAGS.contains(flag) || index + 1 == args.length || args[index + 1].startsWith("--")
                        || values.putIfAbsent(flag, args[index + 1]) != null) {
                    throw new IllegalArgumentException("Expected one value per option. See --help.");
                }
            }

            int count = Integer.parseInt(values.getOrDefault("--count", "1000"));
            int batchSize = Integer.parseInt(values.getOrDefault("--batch-size", "200"));
            if (count < 1 || batchSize < 1) {
                throw new IllegalArgumentException("Count and batch size must be positive");
            }
            int maxWaves = (count - 1) / 2;
            int waves = Integer.parseInt(values.getOrDefault("--waves", Integer.toString(Math.min(3, maxWaves))));
            if (waves < 0 || waves > maxWaves) {
                throw new IllegalArgumentException("Leave room for two events per wave and a baseline event");
            }
            if (values.containsKey("--output") && values.containsKey("--post")) {
                throw new IllegalArgumentException("Choose either --output or --post");
            }

            long seed = Long.parseLong(values.getOrDefault("--seed", "42"));
            Instant start = values.containsKey("--start")
                    ? OffsetDateTime.parse(values.get("--start")).toInstant()
                    : Instant.now().minusMillis((count - 1) * 20L);
            Path output = values.containsKey("--output") ? Path.of(values.get("--output")) : null;
            URI url = values.containsKey("--post") ? URI.create(values.get("--post")) : null;
            return new Options(count, waves, seed, start, batchSize, output, url);
        }
    }
}
