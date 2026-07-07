package com.akamai.wsa.generator;

import com.akamai.wsa.generator.feed.IngestionFeeder;
import com.akamai.wsa.generator.generate.DatasetGenerator;
import com.akamai.wsa.generator.model.GeneratedEvent;
import com.akamai.wsa.generator.output.JsonEventWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@Component
public class GeneratorRunner implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(GeneratorRunner.class);

    private final GeneratorProperties properties;
    private final DatasetGenerator datasetGenerator;
    private final JsonEventWriter jsonEventWriter;
    private final IngestionFeeder ingestionFeeder;

    public GeneratorRunner(GeneratorProperties properties, DatasetGenerator datasetGenerator,
                           JsonEventWriter jsonEventWriter, IngestionFeeder ingestionFeeder) {
        this.properties = properties;
        this.datasetGenerator = datasetGenerator;
        this.jsonEventWriter = jsonEventWriter;
        this.ingestionFeeder = ingestionFeeder;
    }

    @Override
    public void run(String... arguments) throws Exception {
        List<GeneratedEvent> events = datasetGenerator.generate();
        logger.info("GeneratorRunner - generated {} events (mode={})", events.size(), properties.outputMode());
        switch (properties.outputMode()) {
            case STDOUT -> System.out.println(jsonEventWriter.toJsonArray(events));
            case JSON_FILE -> {
                Files.writeString(Path.of(properties.outputFile()), jsonEventWriter.toJsonArray(events));
                logger.info("GeneratorRunner - wrote {} events to {}", events.size(), properties.outputFile());
            }
            case HTTP -> {
                int accepted = ingestionFeeder.feed(events, properties.batchSize());
                logger.info("GeneratorRunner - fed events to gateway, accepted={} of {}", accepted, events.size());
            }
        }
    }
}
