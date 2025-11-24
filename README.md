# Exercice: Traitement de Messages Texte avec Kafka Streams
# Description
Application Kafka Streams qui nettoie, filtre et route des messages texte selon des règles de validation. Les messages valides sont envoyés vers un topic "propre", tandis que les messages invalides sont routés vers un topic de "dead letter".

# Objectifs

✅ Lire des messages depuis un topic Kafka
✅ Nettoyer et normaliser le texte
✅ Filtrer selon des règles de validation
✅ Router vers différents topics selon la validité


🏗️ Architecture
┌─────────────────┐
│  Producer       │
│  (Manuel/Test)  │
└────────┬────────┘
│
▼
┌──────────┐
│  Topic   │
│text-input│
└────┬─────┘
│
▼
┌─────────────────────────┐
│  Kafka Streams App      │
│  - Nettoyage           │
│  - Filtrage            │
│  - Routage             │
└─────┬──────────┬────────┘
│          │
▼          ▼
┌──────────┐  ┌────────────────┐
│  Topic   │  │    Topic       │
│text-clean│  │text-dead-letter│
└──────────┘  └────────────────┘

📦 Topics Kafka
TopicDescriptionFormattext-inputMessages bruts en entréeStringtext-cleanMessages valides nettoyésString (UPPERCASE)text-dead-letterMessages rejetésString (original)

🔧 Traitements Appliqués
1. Nettoyage (sur tous les messages)

Supprimer les espaces avant/après (.trim())
Remplacer espaces multiples par un seul espace
Convertir en MAJUSCULES

2. Filtrage (critères de rejet)
   ❌ Rejeter si :

Message vide ou uniquement des espaces
Contient des mots interdits : HACK, SPAM, XXX
Longueur > 100 caractères

✅ Accepter :

Tous les autres messages (après nettoyage)

3. Routage
   Message valide → text-clean (version nettoyée)
   Message invalide → text-dead-letter (version originale)

🚀 Installation et Démarrage
Prérequis

Java 21
Maven 3.8+
Docker Desktop
Kafka en cours d'exécution

Étape 1 : Démarrer Kafka
bashcd kafka-infrastructure
docker-compose up -d
Étape 2 : Créer les topics
bash# Topic text-input
docker exec -it broker /opt/kafka/bin/kafka-topics.sh \
--create --topic text-input \
--bootstrap-server localhost:9092 \
--partitions 3 \
--replication-factor 1

# Topic text-clean
docker exec -it broker /opt/kafka/bin/kafka-topics.sh \
--create --topic text-clean \
--bootstrap-server localhost:9092 \
--partitions 3 \
--replication-factor 1

# Topic text-dead-letter
docker exec -it broker /opt/kafka/bin/kafka-topics.sh \
--create --topic text-dead-letter \
--bootstrap-server localhost:9092 \
--partitions 3 \
--replication-factor 1

# Vérifier
docker exec -it broker /opt/kafka/bin/kafka-topics.sh \
--list --bootstrap-server localhost:9092
Étape 3 : Lancer l'application
bashcd text-processor
mvn clean install
mvn spring-boot:run

🧪 Tests
Test 1 : Messages valides
Envoyer des messages :
bashdocker exec -it broker /opt/kafka/bin/kafka-console-producer.sh \
--topic text-input \
--bootstrap-server localhost:9092
### Tapez ces messages :
--> hello world
--> kafka   streams    example
-->   clean   text   
--> this is a valid message

### Vérifier text-clean :
bashdocker exec -it broker /opt/kafka/bin/kafka-console-consumer.sh \
--topic text-clean \
--bootstrap-server localhost:9092 \
--from-beginning
### Résultat attendu :
HELLO WORLD
KAFKA STREAMS EXAMPLE
CLEAN TEXT
THIS IS A VALID MESSAGE
Test 2 : Messages invalides
Envoyer :
>                    (message vide)
> This message contains HACK word
> This is SPAM content
> This message is XXX rated
> Ce message est beaucoup trop long pour être accepté car il dépasse largement la limite de 100 caractères fixée
Vérifier text-dead-letter :
bashdocker exec -it broker /opt/kafka/bin/kafka-console-consumer.sh \
--topic text-dead-letter \
--bootstrap-server localhost:9092 \
--from-beginning
### Résultat attendu :

This message contains HACK word
This is SPAM content
This message is XXX rated
Ce message est beaucoup trop long...
### Test 3 : Messages mixtes
Envoyer :
> valid message one
> invalid SPAM message
> another valid message
>      too    many     spaces    
> rejected HACK attempt
> final valid message
### Résultats :
![docker compose.png](screenshots/docker%20compose.png)
![list_topic.png](screenshots/list_topic.png)
![topic_text_clean.png](screenshots/topic_text_clean.png)
![topic_text_input.png](screenshots/topic_text_input.png)
