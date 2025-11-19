package ma.enset;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.admin.AdminClientConfig;

import java.util.Arrays;
import java.util.Properties;
import java.util.concurrent.ExecutionException;

public class KafkaTopicCreator {

    public static void main(String[] args) {

        Properties props = new Properties();
        props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");

        try (AdminClient adminClient = AdminClient.create(props)) {

            NewTopic textInput = new NewTopic("text-input", 3, (short) 1);
            NewTopic textClean = new NewTopic("text-clean", 3, (short) 1);
            NewTopic textDeadLetter = new NewTopic("text-dead-letter", 3, (short) 1);

            adminClient.createTopics(Arrays.asList(textInput, textClean, textDeadLetter))
                    .all()
                    .get();

            System.out.println("✅ Topics créés avec succès :");
            System.out.println("   - text-input");
            System.out.println("   - text-clean");
            System.out.println("   - text-dead-letter");

        } catch (ExecutionException | InterruptedException e) {
            if (e.getMessage().contains("TopicExistsException")) {
                System.out.println("⚠️ Les topics existent déjà.");
            } else {
                System.err.println("❌ Erreur lors de la création des topics : " + e.getMessage());
                e.printStackTrace();
            }
        }
    }
}