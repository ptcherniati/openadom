# Proposition de Dashboards Grafana pour OpenADOM

Suite à l'instrumentation de l'application, voici les dashboards recommandés pour couvrir les besoins de monitoring, d'optimisation et de scalabilité.

## 1. Dashboard "Vue d'ensemble Système" (System Overview)
**Objectif :** Surveiller la santé globale et repérer les points de blocage techniques immédiats.

*   **Santé & Ressources :**
    *   **Status** : Up/Down (basé sur `up`).
    *   **CPU Usage** : Système vs Processus Java (`system_cpu_usage`, `process_cpu_usage`).
    *   **Mémoire JVM** : Heap Used vs Max, Garbage Collection Time (`jvm_memory_used_bytes`, `jvm_gc_pause_seconds`).
*   **Serveur Web (Tomcat) :**
    *   **Threads** : Threads actifs vs Max (`tomcat_threads_current_threads`, `tomcat_threads_busy_threads`). *Indicateur clé de saturation.*
    *   **Requêtes HTTP** : Débit global (RPS) et Taux d'erreur (4xx, 5xx).
*   **Base de Données (HikariCP) :**
    *   **Connexions** : Actives, Idle, En attente (`hikaricp_connections_active`, `hikaricp_connections_pending`).
    *   **Temps d'acquisition** : Temps pour obtenir une connexion (`hikaricp_connection_acquire_seconds`). *Si ce temps augmente, la BDD est le goulot d'étranglement.*

## 2. Dashboard "Performance Métier" (Business Performance)
**Objectif :** Identifier les applications et types de données les plus utilisés pour optimiser les index et le paramétrage.

*   **Analyse par Application (`app_name`) :**
    *   **Top Applications (Trafic)** : `sum(rate(http_server_requests_seconds_count[5m])) by (app_name)`.
    *   **Latence par Application (p95)** : `histogram_quantile(0.95, sum(rate(http_server_requests_seconds_bucket[5m])) by (le, app_name))`. *Permet de voir quelle appli est lente.*
    *   **Taux d'erreur par Application** : Pourcentage d'erreurs par application.
*   **Analyse par Type de Données (`data_type`) :**
    *   **Top Types de Données** : Volume de requêtes par type de données. *Indique quelles tables sont les plus sollicitées.*
    *   **Latence Création/Lecture** : Focus sur les endpoints POST (création) vs GET (lecture) par type de données.

## 3. Dashboard "Base de Données & Scalabilité" (Database & Scalability)
**Objectif :** Pistes de scalabilité et optimisation fine de la BDD.

*   **Hibernate Statistics :**
    *   **Sessions** : Sessions ouvertes (`hibernate_sessions_open`).
    *   **Requêtes** : Nombre de requêtes exécutées, temps max d'exécution (`hibernate_query_execution_max`).
    *   **Cache** : Hit/Miss ratio du cache de second niveau (si activé).
*   **Indicateurs de Scalabilité :**
    *   **Saturation Pool JDBC** : Si `hikaricp_connections_active` est constamment proche de `max-pool-size`, il faut augmenter la taille du pool ou scaler horizontalement.
    *   **Saturation Threads Tomcat** : Si les threads busy sont proches du max, c'est un signe de besoin de scaling horizontal (plus d'instances).
    *   **Latence vs Charge** : Corrélation entre le nombre de requêtes et la latence. Si la latence explose avec la charge, l'application n'est pas scalable linéairement (probablement un verrou BDD ou CPU).

## Configuration Requise dans Grafana
Pour utiliser ces dashboards, assurez-vous que la source de données Prometheus est bien configurée et que les jobs de scraping pointent vers `/actuator/prometheus`.

Les tags `app_name` et `data_type` seront automatiquement disponibles dans les filtres Grafana grâce à l'instrumentation mise en place (`OreSiWebMvcTagsContributor`).