package ma.enset;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.kstream.KStream;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

public class TextProcessingKafkaStreams {

    private static final Set<String> FORBIDDEN_WORDS = new HashSet<>(
            Arrays.asList("HACK", "SPAM", "XXX")
    );

    private static final int MAX_LENGTH = 100;

    public static void main(String[] args) {

        Properties props = new Properties();
        props.put(StreamsConfig.APPLICATION_ID_CONFIG, "text-processing-app");
        props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        props.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String().getClass());
        props.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, Serdes.String().getClass());

        StreamsBuilder builder = new StreamsBuilder();

        KStream<String, String> inputStream = builder.stream("text-input");

        KStream<String, String> cleanedStream = inputStream
                .mapValues(value -> cleanText(value));

        KStream<String, String>[] branches = cleanedStream.branch(
                (key, value) -> isValidMessage(value),
                (key, value) -> true
        );

        KStream<String, String> validMessages = branches[0];

        validMessages.to("text-clean");

        inputStream
                .filter((key, value) -> !isValidMessage(cleanText(value)))
                .to("text-dead-letter");

        KafkaStreams streams = new KafkaStreams(builder.build(), props);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Arrêt de l'application...");
            streams.close();
            System.out.println("Application arrêtée proprement.");
        }));

        System.out.println("Démarrage de l'application Text Processing...");
        streams.start();
    }

    private static String cleanText(String text) {
        if (text == null) {
            return "";
        }

        String cleaned = text.trim();
        cleaned = cleaned.replaceAll("\\s+", " ");
        cleaned = cleaned.toUpperCase();

        return cleaned;
    }

    private static boolean isValidMessage(String text) {

        if (text == null || text.trim().isEmpty()) {
            return false;
        }

        for (String forbiddenWord : FORBIDDEN_WORDS) {
            if (text.contains(forbiddenWord)) {
                return false;
            }
        }

        if (text.length() > MAX_LENGTH) {
            return false;
        }

        return true;
    }
}