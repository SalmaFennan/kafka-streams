package ma.enset;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.kstream.*;

import java.time.Duration;
import java.util.Properties;

/**
 * Kafka Streams app:
 * - lit weather-data (CSV: station,temperature,humidity)
 * - filtre temperature Celsius > 30
 * - convertit Celsius -> Fahrenheit
 * - agrège (moyenne température & humidité) par station
 * - publie dans station-averages (value: JSON string ou format lisible)
 */
public class WeatherProcessingApp {

    public static void main(String[] args) {
        Properties props = new Properties();
        props.put(StreamsConfig.APPLICATION_ID_CONFIG, "weather-processing-app");
        props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        props.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String().getClass());
        props.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, Serdes.String().getClass());

        StreamsBuilder builder = new StreamsBuilder();

        KStream<String, String> input = builder.stream("weather-data", Consumed.with(Serdes.String(), Serdes.String()));

        // 1) Parse CSV, filter > 30°C, convert to Fahrenheit and key by station
        KStream<String, WeatherReading> readingsStream = input
                .mapValues(value -> parseCsv(value))             // parse
                .filter((k, r) -> r != null)                     // remove parse failures
                .filter((k, r) -> r.getTemperatureC() > 30.0)    // keep only > 30°C
                .map((k, r) -> KeyValue.pair(r.getStation(), r.toFahrenheit())); // key by station and convert temp

        // 2) Group by station
        KGroupedStream<String, WeatherReading> grouped = readingsStream.groupByKey(Grouped.with(Serdes.String(), new JsonSerde<>(WeatherReading.class)));

        // 3) Aggregate: sumTempF, sumHumidity, count
        KTable<String, StationStats> aggregated = grouped.aggregate(
                StationStats::new, // initializer
                (station, reading, agg) -> {
                    agg.add(reading.getTemperatureF(), reading.getHumidity());
                    return agg;
                },
                Materialized.with(Serdes.String(), new JsonSerde<>(StationStats.class))
        );

        // 4) Map to average result and publish to topic as a readable string (or JSON)
        aggregated.toStream()
                .mapValues((station, stats) -> stats.toAverageResultString(station))
                .to("station-averages", Produced.with(Serdes.String(), Serdes.String()));

        KafkaStreams streams = new KafkaStreams(builder.build(), props);

        // Clean shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Stopping streams...");
            streams.close(Duration.ofSeconds(10));
            System.out.println("Streams stopped.");
        }));

        System.out.println("Starting Weather Processing Streams application...");
        streams.start();
    }

    // Parse CSV "station,temperature,humidity"
    private static WeatherReading parseCsv(String csv) {
        try {
            if (csv == null) return null;
            String[] parts = csv.split(",");
            if (parts.length < 3) return null;
            String station = parts[0].trim();
            double tempC = Double.parseDouble(parts[1].trim());
            double humidity = Double.parseDouble(parts[2].trim());
            return new WeatherReading(station, tempC, humidity);
        } catch (Exception e) {
            // optionally log parse error
            return null;
        }
    }

    // -------------------------
    // WeatherReading POJO
    // -------------------------
    public static class WeatherReading {
        private String station;
        private double temperatureC;
        private double humidity;
        // computed Fahrenheit (optional)
        private double temperatureF;

        public WeatherReading() {}
        public WeatherReading(String station, double temperatureC, double humidity) {
            this.station = station;
            this.temperatureC = temperatureC;
            this.humidity = humidity;
            this.temperatureF = cToF(temperatureC);
        }

        public String getStation() { return station; }
        public double getTemperatureC() { return temperatureC; }
        public double getHumidity() { return humidity; }
        public double getTemperatureF() { return temperatureF; }

        public WeatherReading toFahrenheit() {
            this.temperatureF = cToF(this.temperatureC);
            return this;
        }

        private static double cToF(double c) {
            return (c * 9.0/5.0) + 32.0;
        }
    }

    // -------------------------
    // StationStats POJO (aggregator state)
    // -------------------------
    public static class StationStats {
        private double sumTempF;
        private double sumHumidity;
        private long count;

        public StationStats() {
            this.sumTempF = 0.0;
            this.sumHumidity = 0.0;
            this.count = 0;
        }

        public void add(double tempF, double humidity) {
            this.sumTempF += tempF;
            this.sumHumidity += humidity;
            this.count += 1;
        }

        public double avgTempF() {
            return count == 0 ? 0.0 : (sumTempF / count);
        }

        public double avgHumidity() {
            return count == 0 ? 0.0 : (sumHumidity / count);
        }

        public String toAverageResultString(String station) {
            // Format with 2 decimals
            return String.format("%s : Température Moyenne = %.2f°F, Humidité Moyenne = %.2f%%", station, avgTempF(), avgHumidity());
        }

        // getters/setters nécessaires pour sérialisation JSON
        public double getSumTempF() { return sumTempF; }
        public void setSumTempF(double sumTempF) { this.sumTempF = sumTempF; }
        public double getSumHumidity() { return sumHumidity; }
        public void setSumHumidity(double sumHumidity) { this.sumHumidity = sumHumidity; }
        public long getCount() { return count; }
        public void setCount(long count) { this.count = count; }
    }

    // -------------------------
    // JsonSerde générique (Jackson)
    // -------------------------
    public static class JsonSerde<T> implements Serde<T> {
        private final Class<T> clazz;
        private final ObjectMapper mapper = new ObjectMapper();

        public JsonSerde(Class<T> clazz) {
            this.clazz = clazz;
        }

        @Override
        public org.apache.kafka.common.serialization.Serializer<T> serializer() {
            return (topic, data) -> {
                try {
                    return data == null ? null : mapper.writeValueAsBytes(data);
                } catch (JsonProcessingException e) {
                    throw new RuntimeException(e);
                }
            };
        }

        @Override
        public org.apache.kafka.common.serialization.Deserializer<T> deserializer() {
            return (topic, bytes) -> {
                try {
                    return bytes == null ? null : mapper.readValue(bytes, clazz);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            };
        }
    }
}
