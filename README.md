# Remote Executor Service
A cloud-native Spring Boot application written in Kotlin that accepts shell scripts via a REST API, dynamically allocates resources, executes the scripts on isolated remote infrastructure (Docker or Kubernetes), and tracks their lifecycle and output logs.

This project was built using a **Modular Monolith** and **Clean Architecture** (Ports and Adapters) approach, allowing the core business logic to remain completely agnostic of the underlying infrastructure.

## Key Features
- **Multi-Engine Support**: Switch between a local Docker Daemon and a Kubernetes Cluster simply by changing a Spring `@Profile`.
- **Event-Driven Lifecycle Monitoring**: Uses a Kubernetes `SharedIndexInformer` to monitor Pod state changes (Pending -> Running -> Succeeded/Failed) via WebSockets, eliminating thread-blocking polling.
- **Asynchronous Processing**: API requests are offloaded to a custom configured `@Async` thread pool, instantly returning a `201 Created` response to the client.
- **Defensive Edge-Case Handling**: Safely catches Kubernetes `ImagePullBackOff` states, prevents infinite execution loops, and securely sanitizes API inputs.
- **Automated Garbage Collection**: Configured Kubernetes native TTL controllers (`ttlSecondsAfterFinished`) to automatically clean up Jobs/Pods to prevent cluster resource exhaustion.
- **Integration Tested**: Backed by Testcontainers (for real PostgreSQL testing) and the Fabric8 Mock Server (to simulate the Kubernetes Control Plane in milliseconds).

## Tech Stack
- **Language**: Kotlin 2.2
- **Framework**: Spring Boot 4.0.5 (Web, Data JPA, Validation)
- **Database**: PostgreSQL + Flyway (Schema Migration)
- **Infrastructure Clients**: docker-java (Docker Desktop), fabric8 (Kubernetes)
- **Testing**: JUnit 5, Mockito, Testcontainers, Awaitility, Fabric8 Kubernetes Mock Server
- **Documentation**: OpenAPI 3 / Swagger UI


## The 5-Phase Development Journey
This service was built iteratively, proving the core logic locally before scaling to a cloud-native architecture.

### Phase 1: Bootstrapping & API Design
- Initialized the Kotlin Spring Boot project with Spring Data JPA and PostgreSQL.
- Created the core `Execution` domain entity with automated JPA Auditing (`@CreatedDate`, `@LastModifiedDate`).
- Designed robust REST controllers with standardized `@RestControllerAdvice` exception handling for validation and parameter errors.

### Phase 2: Local Executor Sandbox (Docker)
- Defined the `RemoteExecutorClient` interface (The "Port") to decouple business logic from infrastructure.
- Built the `DockerExecutorClientImpl` (The "Adapter") using the Docker Java API to spin up local Alpine containers.
- Implemented `@Async` execution with a bounded thread pool (`ThreadPoolTaskExecutor`) to prevent HTTP request blocking.

### Phase 3: The Kubernetes Integration
- Transitioned the execution engine to the cloud using the **Fabric8 Kubernetes Client**.
- Implemented the logic to programmatically construct Kubernetes `V1Job` objects, mapping the user's CPU requests to native K8s `requests/limits`.
- Utilized Spring `@Profile` to allow seamless switching between the Docker and Kubernetes engines.

### Phase 4: Security & Edge Cases
- Hardened the Kubernetes Jobs by disabling default ServiceAccount token mounting (`automountServiceAccountToken: false`).
- Delegated cluster cleanup to Kubernetes native controllers via `ttlSecondsAfterFinished`.
- Added log retrieval mechanisms to capture standard output and standard error from the executed containers and store them in PostgreSQL.

### Phase 5: Event-Driven Lifecycle Monitoring
- Upgraded from synchronous polling (`waitUntilCondition`) to an Event-Driven Push Architecture.
- Implemented a `SharedIndexInformer` to establish a persistent WebSocket connection to the Kubernetes API.
- The Informer now handles all state transitions (`QUEUED`, `IN_PROGRESS`, `FINISHED`, `FAILED`) in the background, gracefully catching edge cases like `ImagePullBackOff`.


## How to Run the Application

### Prerequisites
1. **Docker Desktop** (Required for the database and the Docker profile).
2. **Kubernetes** (Enable Kubernetes inside Docker Desktop settings, or use `minikube`/`kind`).

### 1. Start the Database
The project includes a `compose.yaml` file to spin up the required PostgreSQL database.
```
docker-compose up -d
```

### 2. Choose Your Execution Engine
Open `src/main/resources/application.yml` and set your active profile:
- `spring.profiles.active: kubernetes` (Uses your local `~/.kube/config` to talk to your K8s cluster).
- `spring.profiles.active: docker` (Uses your local Docker daemon).

### 3. Run the Application
```
./gradlew bootRun
```
*Flyway will automatically run the schema migrations on startup.*


## API Documentation & Usage
Once the application is running, you can access the interactive Swagger UI to test the endpoints: [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)

### Submit a Script for Execution
```bash
curl -X POST http://localhost:8080/api/executions \
-H "Content-Type: application/json" \
-d '{
      "script": "echo \"Starting work...\" && sleep 5 && echo \"Work complete!\"",
      "cpuCount": 0.5
    }'
```
**Response (201 Created):**
```JSON
{
  "id": "123e4567-e89b-12d3-a456-426614174000",
  "script": "echo \"Starting work...\" && sleep 5 && echo \"Work complete!\"",
  "cpuCount": 0.5,
  "status": "QUEUED",
  "createdAt": "2026-03-27T12:00:00Z",
  "updatedAt": "2026-03-27T12:00:00Z"
}
```

### Check Execution Status & Logs
```Bash
curl http://localhost:8080/api/executions/123e4567-e89b-12d3-a456-426614174000
```
**Response (200 OK):**
```JSON
{
  "id": "123e4567-e89b-12d3-a456-426614174000",
  "script": "echo \"Starting work...\" && sleep 5 && echo \"Work complete!\"",
  "cpuCount": 0.5,
  "status": "FINISHED",
  "output": "Starting work...\nWork complete!",
  "createdAt": "2026-03-27T12:00:00Z",
  "updatedAt": "2026-03-27T12:00:06Z"
}
```

### List All Executions (Paginated)
```Bash
curl http://localhost:8080/api/executions?page=0&size=10
```


## Testing Strategy
The project utilizes a robust testing strategy:
- **IntegrationBaseTest**: Spins up a real PostgreSQL database using `@ServiceConnection` and Testcontainers to guarantee parity with production.
- **Awaitility**: Safely tests asynchronous thread-pool logic without using brittle `Thread.sleep()`.
- **Fabric8 Mock Server**: Tests the Kubernetes Informer and Job creation logic by spinning up an in-memory K8s Control Plane, avoiding the need for a live cluster during CI/CD builds.

Run the test suite via:
```Bash
./gradlew test
```
