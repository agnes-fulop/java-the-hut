# Coding Challenge — Implementation Plan

## Context

We are building a REST API + React UI that lets clients register weather sensors, push metric readings, and query averaged metrics. Different user groups have different access levels. This is a coding challenge starting from a blank directory — we build everything from scratch with SOLID principles, clear layer separation, and test coverage at all levels.

---

## Database Decision: SQL (H2)

**SQL wins here because:**
- Data is relational and fixed-schema: `Sensor` (1) → (N) `MetricReading`
- Queries require `AVG() GROUP BY sensorId, metricType` — one line in SQL, a verbose aggregation pipeline in NoSQL
- The "no arbitrary metadata" constraint means document flexibility adds no value
- H2 in-memory is zero-setup for the challenge; the same JPA code runs on PostgreSQL unchanged

---

## Tech Stack

| Layer | Technology |
|---|---|
| Backend | Java 17, Spring Boot 3.x, Maven |
| Security | Spring Security 6, JWT (jjwt 0.12.x) |
| Persistence | Spring Data JPA, H2 in-memory |
| Validation | Spring Validation (Jakarta Bean Validation) |
| Utilities | Lombok |
| API Docs | springdoc-openapi 2.x (Swagger UI) |
| Testing | JUnit 5, Mockito, `@WebMvcTest`, `@DataJpaTest`, `@SpringBootTest` |
| Frontend | React 18, TypeScript, Vite, Axios, React Router v6 |

---

## User Personas & Access Control

| Role | Username | Permitted Actions |
|---|---|---|
| `ROLE_ADMIN` | `admin` | Register sensors, submit metrics, query metrics, view sensors |
| `ROLE_WEATHERMAN` | `weatherman` | Query metrics, view sensor by id |

**Credentials are externalized — no passwords in Java source code.** Values are set in `application.properties` and can be overridden at runtime via environment variables (e.g. `APP_SECURITY_USERS_ADMIN_PASSWORD=secret java -jar app.jar`).

```properties
# application.properties — set these values in your local environment
app.security.users.admin.username=admin
app.security.users.admin.password=<ADMIN_PASSWORD>
app.security.users.weatherman.username=weatherman
app.security.users.weatherman.password=<WEATHERMAN_PASSWORD>
```

```java
@ConfigurationProperties(prefix = "app.security.users")
public record SecurityUsersProperties(TestUser admin, TestUser weatherman) {
    public record TestUser(String username, String password) {}
}
```

**Endpoint security matrix:**

| Endpoint | ADMIN | WEATHERMAN | Unauthenticated |
|---|---|---|---|
| `POST /api/auth/login` | ✅ | ✅ | ✅ |
| `POST /api/sensors` | ✅ | ❌ 403 | ❌ 401 |
| `GET /api/sensors/{id}` | ✅ | ✅ | ❌ 401 |
| `POST /api/sensors/{id}/metrics` | ✅ | ❌ 403 | ❌ 401 |
| `POST /api/sensors/query` | ✅ | ✅ | ❌ 401 |
| `/swagger-ui/**`, `/h2-console/**` | ✅ | ✅ | ✅ (dev) |

---

## SOLID Principles Applied

| Principle | How it shows in this codebase |
|---|---|
| **S** — Single Responsibility | Controller handles HTTP; Service handles business logic; Repository handles persistence. `JwtTokenProvider` does only token operations. `GlobalExceptionHandler` is the single place all errors are translated. |
| **O** — Open/Closed | `MetricType` enum extends with new values without touching aggregation logic. Date defaulting is isolated in the service — new date strategies don't change the controller. |
| **L** — Liskov Substitution | `SensorService` and `MetricService` are interfaces; `SensorServiceImpl`/`MetricServiceImpl` are implementations. Tests swap real impls for mocks freely. |
| **I** — Interface Segregation | `SensorService` exposes only sensor operations; `MetricService` exposes only metric operations. No god-interfaces. |
| **D** — Dependency Inversion | Controllers depend on service interfaces, not concrete classes. Services depend on `JpaRepository` interfaces. Spring DI wires everything. |

---

## Package Structure

```
com.weather.sensor
├── WeatherSensorApplication.java
├── config/
│   ├── OpenApiConfig.java               # Swagger + JWT bearer scheme
│   ├── SecurityConfig.java              # HTTP security rules, stateless sessions
│   ├── SecurityUsersProperties.java     # @ConfigurationProperties binding
│   ├── InMemoryUsersConfig.java         # InMemoryUserDetailsManager from config
│   └── WebConfig.java                   # Vite proxy makes CORS optional, kept for flexibility
├── controller/
│   ├── AuthController.java              # POST /api/auth/login
│   ├── SensorController.java            # sensor CRUD + query
│   └── MetricController.java            # metric ingestion
├── domain/
│   ├── Sensor.java                      # @Entity
│   ├── MetricReading.java               # @Entity
│   └── MetricType.java                  # enum TEMPERATURE | HUMIDITY | WIND_SPEED
├── dto/
│   ├── request/
│   │   ├── LoginRequest.java
│   │   ├── SensorRegistrationRequest.java
│   │   ├── MetricReadingRequest.java
│   │   ├── MetricSubmissionRequest.java
│   │   └── MetricQueryRequest.java
│   └── response/
│       ├── AuthResponse.java            # { token, role }
│       ├── ErrorResponse.java           # { status, error, message, timestamp }
│       ├── SensorResponse.java
│       ├── MetricQueryResponse.java
│       └── SensorMetricResult.java
├── repository/
│   ├── SensorRepository.java
│   └── MetricReadingRepository.java     # JPQL AVG projection query
├── service/
│   ├── SensorService.java               # interface (ISP/DIP)
│   ├── MetricService.java               # interface (ISP/DIP)
│   └── impl/
│       ├── SensorServiceImpl.java
│       └── MetricServiceImpl.java
├── security/
│   ├── JwtTokenProvider.java            # generate / validate / extract tokens
│   └── JwtAuthenticationFilter.java     # OncePerRequestFilter; populates SecurityContext
├── exception/
│   ├── SensorNotFoundException.java
│   ├── SensorAlreadyExistsException.java
│   └── GlobalExceptionHandler.java      # @RestControllerAdvice — all errors in one place
└── validation/
    ├── DateRangeValid.java              # constraint annotation
    └── DateRangeValidator.java          # ConstraintValidator implementation
```

**Frontend (`frontend/`):**
```
frontend/
├── src/
│   ├── api/
│   │   ├── client.ts                    # axios instance + JWT interceptor
│   │   ├── authApi.ts
│   │   ├── sensorApi.ts
│   │   └── metricApi.ts
│   ├── context/
│   │   └── AuthContext.tsx              # JWT + role state, login/logout
│   ├── components/
│   │   ├── ProtectedRoute.tsx
│   │   ├── RegisterSensorForm.tsx
│   │   ├── SubmitMetricsForm.tsx
│   │   └── QueryForm.tsx
│   ├── pages/
│   │   ├── LoginPage.tsx
│   │   ├── DashboardPage.tsx            # query — all authenticated users
│   │   └── AdminPage.tsx               # register + submit — ADMIN only
│   └── App.tsx
├── package.json
└── vite.config.ts                       # proxy /api → localhost:8080
```

---

## API Surface

| Method | Path | Role | Purpose |
|---|---|---|---|
| `POST` | `/api/auth/login` | all | Get JWT token |
| `POST` | `/api/sensors` | ADMIN | Register a sensor |
| `GET` | `/api/sensors/{id}` | ADMIN, WEATHERMAN | Get sensor by id |
| `POST` | `/api/sensors/{sensorId}/metrics` | ADMIN | Submit metric readings |
| `POST` | `/api/sensors/query` | ADMIN, WEATHERMAN | Query averaged metrics |

**Query request/response:**
```json
// Request
{
  "sensorIds": ["s1", "s2"],   // optional — omit for all sensors
  "metrics": ["TEMPERATURE", "HUMIDITY"],
  "from": "2026-04-01",        // optional LocalDate; both or neither
  "to":   "2026-04-30"         // span must be 1–31 days if provided
}

// Response
{
  "from": "2026-04-01",
  "to":   "2026-04-30",
  "results": [
    { "sensorId": "s1", "averages": { "TEMPERATURE": 22.5, "HUMIDITY": 65.0 } }
  ]
}
```

---

## Slice 1 — Project Bootstrap

**Goal:** A compiling, running Spring Boot application with H2 connected and Swagger UI reachable. Nothing more — no security, no domain logic. The sole purpose is having a green build to build on.

**Files:**

| File | Purpose |
|---|---|
| `pom.xml` | All deps declared upfront: web, jpa, validation, h2, lombok, springdoc, jjwt, security, security-test |
| `application.properties` | Datasource, DDL auto, JWT config, credential property keys (values set locally) |
| `application-dev.properties` | H2 console enabled, show-sql |
| `WeatherSensorApplication.java` | `@SpringBootApplication` entry point |
| `config/OpenApiConfig.java` | Swagger title + version (JWT scheme added in Slice 2) |

**application.properties:**
```properties
spring.profiles.active=dev
spring.datasource.url=jdbc:h2:mem:weatherdb;DB_CLOSE_DELAY=-1
spring.datasource.driver-class-name=org.h2.Driver
spring.jpa.hibernate.ddl-auto=create-drop

app.jwt.secret=<JWT_SECRET_MIN_256_BITS>
app.jwt.expiration-ms=86400000

app.security.users.admin.username=admin
app.security.users.admin.password=<ADMIN_PASSWORD>
app.security.users.weatherman.username=weatherman
app.security.users.weatherman.password=<WEATHERMAN_PASSWORD>
```

**Acceptance criteria:**
- `mvn spring-boot:run` starts without errors
- `GET /swagger-ui/index.html` loads
- H2 console reachable at `/h2-console`

**Tests:** `WeatherSensorApplicationTests` — `@SpringBootTest` context load only

**Dependencies:** None

---

## Slice 2 — Security & Error Foundation

**Goal:** Add the security and error-handling infrastructure that every subsequent feature slice depends on. When this slice is done: all endpoints are secured by JWT, test users are loaded from config (not hardcoded), errors return a consistent envelope, and Swagger shows the bearer auth button. Feature slices from here onward ship already production-quality — they don't need to add any security or error scaffolding.

**Files:**

| File | Purpose |
|---|---|
| `config/SecurityUsersProperties.java` | `@ConfigurationProperties(prefix="app.security.users")` — binds credentials from `application.properties` |
| `config/InMemoryUsersConfig.java` | `InMemoryUserDetailsManager` + `BCryptPasswordEncoder` built from injected properties |
| `config/SecurityConfig.java` | Stateless sessions, `JwtAuthenticationFilter`, endpoint authorization rules |
| `config/WebConfig.java` | CORS `http://localhost:5173` (fallback; Vite proxy is primary) |
| `security/JwtTokenProvider.java` | `generateToken(Authentication)`, `validateToken(String)`, `getUsernameFromToken(String)` |
| `security/JwtAuthenticationFilter.java` | `OncePerRequestFilter` — extracts Bearer token, validates, populates `SecurityContextHolder` |
| `controller/AuthController.java` | `POST /api/auth/login` |
| `dto/request/LoginRequest.java` | `@NotBlank String username`, `@NotBlank String password` |
| `dto/response/AuthResponse.java` | `String token`, `String role` |
| `dto/response/ErrorResponse.java` | `int status`, `String error`, `String message`, `Instant timestamp` |
| `exception/GlobalExceptionHandler.java` | `@RestControllerAdvice` — single place all exceptions are translated to `ErrorResponse` |
| Update `config/OpenApiConfig.java` | Add JWT bearer security scheme |

**SecurityUsersProperties + InMemoryUsersConfig:**
```java
@ConfigurationProperties(prefix = "app.security.users")
public record SecurityUsersProperties(TestUser admin, TestUser weatherman) {
    public record TestUser(String username, String password) {}
}

@Configuration
@EnableConfigurationProperties(SecurityUsersProperties.class)
public class InMemoryUsersConfig {
    @Bean
    public UserDetailsService userDetailsService(
            SecurityUsersProperties props, PasswordEncoder encoder) {
        var admin = User.builder()
            .username(props.admin().username())
            .password(encoder.encode(props.admin().password()))
            .roles("ADMIN").build();
        var weatherman = User.builder()
            .username(props.weatherman().username())
            .password(encoder.encode(props.weatherman().password()))
            .roles("WEATHERMAN").build();
        return new InMemoryUserDetailsManager(admin, weatherman);
    }

    @Bean
    public PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }
}
```

**SecurityConfig endpoint rules:**
```java
.authorizeHttpRequests(auth -> auth
    .requestMatchers("/api/auth/**", "/swagger-ui/**", "/v3/api-docs/**", "/h2-console/**").permitAll()
    .requestMatchers(HttpMethod.POST, "/api/sensors").hasRole("ADMIN")
    .requestMatchers(HttpMethod.POST, "/api/sensors/*/metrics").hasRole("ADMIN")
    .requestMatchers(HttpMethod.GET, "/api/sensors/*").hasAnyRole("ADMIN", "WEATHERMAN")
    .requestMatchers(HttpMethod.POST, "/api/sensors/query").hasAnyRole("ADMIN", "WEATHERMAN")
    .anyRequest().authenticated()
)
```

**GlobalExceptionHandler mappings:**

| Exception | HTTP Status |
|---|---|
| `SensorNotFoundException` | 404 |
| `SensorAlreadyExistsException` | 409 |
| `MethodArgumentNotValidException` | 400 (flatten all field errors into one message) |
| `HttpMessageNotReadableException` | 400 (bad JSON / unknown enum value) |
| `BadCredentialsException` | 401 |
| `AccessDeniedException` | 403 |
| `Exception` catch-all | 500 (log stack trace; generic message to caller — no trace leakage) |

**Acceptance criteria:**
- `POST /api/auth/login` with valid credentials returns `{ token, role }`
- `POST /api/auth/login` with bad credentials returns `ErrorResponse` with status 401
- Any unauthenticated request to a protected endpoint returns 401 `ErrorResponse`
- Swagger UI shows JWT bearer auth button
- No Spring whiteboard pages — all errors return `ErrorResponse` shape

**Tests:**
- `JwtTokenProviderTest` — unit: generate token, validate valid, reject tampered/expired
- `AuthControllerTest` — `@WebMvcTest`: valid login → 200 + token, bad password → 401 `ErrorResponse`
- `GlobalExceptionHandlerTest` — `@WebMvcTest`: each exception type → correct status + `ErrorResponse` shape; catch-all 500 does not expose stack trace

**Dependencies:** Slice 1

---

## Slice 3 — Sensor Registration

**Goal:** Register and retrieve sensors, end-to-end: secured, validated, error-handled, tested.

**Files:**

| File | Purpose |
|---|---|
| `domain/MetricType.java` | `enum { TEMPERATURE, HUMIDITY, WIND_SPEED }` — defined here, used in Slice 4+ |
| `domain/Sensor.java` | `@Entity`: `@Id String id`, nullable `country`, `city`, `Instant registeredAt` via `@PrePersist` |
| `repository/SensorRepository.java` | `JpaRepository<Sensor, String>` |
| `dto/request/SensorRegistrationRequest.java` | `@NotBlank String id`, nullable `String country`, `String city` |
| `dto/response/SensorResponse.java` | Mirrors entity fields |
| `service/SensorService.java` | Interface: `registerSensor(SensorRegistrationRequest)`, `getSensor(String id)` |
| `service/impl/SensorServiceImpl.java` | `@Service` implementation |
| `controller/SensorController.java` | `POST /api/sensors` (201), `GET /api/sensors/{id}` (200) |
| `exception/SensorNotFoundException.java` | `RuntimeException` — mapped to 404 in `GlobalExceptionHandler` |
| `exception/SensorAlreadyExistsException.java` | `RuntimeException` — mapped to 409 in `GlobalExceptionHandler` |

**SensorService interface:**
```java
public interface SensorService {
    SensorResponse registerSensor(SensorRegistrationRequest request);
    SensorResponse getSensor(String id);
}
```

**SensorServiceImpl logic:**
- `registerSensor`: `existsById` → throw `SensorAlreadyExistsException`; map → `save` → return DTO
- `getSensor`: `findById` or throw `SensorNotFoundException`

**Acceptance criteria:**
- `POST /api/sensors` by ADMIN with valid body → 201, body is `SensorResponse` with `registeredAt`
- Blank `id` → 400 `ErrorResponse`
- Duplicate `id` → 409 `ErrorResponse`
- `GET /api/sensors/{id}` → 200 or 404 `ErrorResponse`
- WEATHERMAN calling `POST /api/sensors` → 403 `ErrorResponse`
- Unauthenticated → 401 `ErrorResponse`

**Tests:**
- `SensorServiceImplTest` — unit, Mockito: register success, duplicate throws, getSensor not-found throws
- `SensorControllerTest` — `@WebMvcTest` + `@WithMockUser(roles="ADMIN")`: 201, 409, 400, 200, 404
- Add one `@WithMockUser(roles="WEATHERMAN")` case: POST → 403

**Dependencies:** Slice 2

---

## Slice 4 — Metric Ingestion

**Goal:** Accept one or more metric readings for a registered sensor in one atomic call. Secured, validated, error-handled, tested.

**Files:**

| File | Purpose |
|---|---|
| `domain/MetricReading.java` | `@Entity`: auto Long id, `@ManyToOne(fetch=LAZY) Sensor`, `@Enumerated(STRING) MetricType`, `Double value`, `Instant recordedAt` |
| `repository/MetricReadingRepository.java` | `JpaRepository<MetricReading, Long>` — JPQL query added in Slice 5 |
| `dto/request/MetricReadingRequest.java` | `@NotNull MetricType metricType`, `@NotNull Double value`, optional `Instant recordedAt` |
| `dto/request/MetricSubmissionRequest.java` | `@NotEmpty List<MetricReadingRequest> readings` |
| `service/MetricService.java` | Interface: `submitReadings(String sensorId, MetricSubmissionRequest)` |
| `service/impl/MetricServiceImpl.java` | `@Service @Transactional` |
| `controller/MetricController.java` | `POST /api/sensors/{sensorId}/metrics` (201) |

**MetricServiceImpl.submitReadings logic:**
1. `sensorService.getSensor(sensorId)` — propagates 404 if sensor unknown
2. Default `recordedAt` to `Instant.now()` if null — enables backfill while defaulting sensibly for live sensors
3. Map list → `saveAll` in one transaction

**Acceptance criteria:**
- ADMIN submits valid readings → 201
- Unknown `sensorId` → 404 `ErrorResponse`
- Empty `readings` list → 400 `ErrorResponse`
- Null `metricType` or `value` → 400 `ErrorResponse`
- Unknown enum value for `metricType` → 400 `ErrorResponse`
- Multiple readings in one call persist atomically
- WEATHERMAN calling this endpoint → 403 `ErrorResponse`

**Tests:**
- `MetricServiceImplTest` — unit: unknown sensor propagates 404, empty list throws, multiple readings saved via `saveAll`
- `MetricControllerTest` — `@WebMvcTest` + `@WithMockUser(roles="ADMIN")`: 201, 404, 400 (empty list), 400 (null value), 403 for WEATHERMAN

**Dependencies:** Slice 3

---

## Slice 5 — Query API

**Goal:** `POST /api/sensors/query` returns averaged metrics per sensor for a configurable time window. Secured for both roles, custom date range validation, end-to-end tested.

**Files:**

| File | Purpose |
|---|---|
| `validation/DateRangeValid.java` | `@Constraint` annotation targeting `MetricQueryRequest` class |
| `validation/DateRangeValidator.java` | `ConstraintValidator`: both bounds or neither; span 1–31 days |
| `dto/request/MetricQueryRequest.java` | `@DateRangeValid`; `List<String> sensorIds` (nullable); `@NotEmpty List<MetricType> metrics`; `LocalDate from`/`to` (nullable) |
| `dto/response/SensorMetricResult.java` | `String sensorId`, `Map<MetricType, Double> averages` |
| `dto/response/MetricQueryResponse.java` | `LocalDate from`, `LocalDate to`, `List<SensorMetricResult> results` |
| Add to `MetricReadingRepository.java` | JPQL `AVG()` query + `MetricAverageProjection` interface |
| Add to `MetricService.java` | `queryMetrics(MetricQueryRequest)` |
| Add to `MetricServiceImpl.java` | Date defaulting logic + projection grouping |
| Add to `SensorController.java` | `POST /api/sensors/query` (200) |

**Repository JPQL (aggregation stays in DB, not Java):**
```java
public interface MetricAverageProjection {
    String getSensorId();
    MetricType getMetricType();
    Double getAverage();
}

@Query("""
    SELECT m.sensor.id AS sensorId, m.metricType AS metricType, AVG(m.value) AS average
    FROM MetricReading m
    WHERE (:#{#sensorIds == null} = true OR m.sensor.id IN :sensorIds)
      AND m.metricType IN :metricTypes
      AND m.recordedAt >= :from
      AND m.recordedAt < :to
    GROUP BY m.sensor.id, m.metricType
    """)
List<MetricAverageProjection> findAverages(
    @Param("sensorIds") List<String> sensorIds,
    @Param("metricTypes") List<MetricType> metricTypes,
    @Param("from") Instant from,
    @Param("to") Instant to
);
```

**Date defaulting in MetricServiceImpl.queryMetrics:**
- No dates provided → `to = Instant.now()`, `from = to.minus(24, HOURS)`
- Dates provided → `from = from.atStartOfDay(UTC)`, `to = (to + 1 day).atStartOfDay(UTC)`

**Result grouping in Java (after DB aggregation):**
```java
Map<String, Map<MetricType, Double>> grouped = rows.stream()
    .collect(Collectors.groupingBy(
        MetricAverageProjection::getSensorId,
        Collectors.toMap(MetricAverageProjection::getMetricType,
                         MetricAverageProjection::getAverage)
    ));
```

**Acceptance criteria:**
- No `sensorIds` → averages for all sensors with data in the window
- Specific `sensorIds` → only those sensors in results
- No date range → defaults to last 24 hours
- Date range outside 1–31 days → 400 `ErrorResponse`
- Only one of `from`/`to` provided → 400 `ErrorResponse`
- Empty `metrics` → 400 `ErrorResponse`
- Invalid metric string → 400 `ErrorResponse`
- Both ADMIN and WEATHERMAN get 200; unauthenticated → 401

**Tests:**
- `MetricServiceQueryTest` — unit, Mockito: default window, explicit range, sensor filter, grouping
- `MetricReadingRepositoryTest` — `@DataJpaTest`: seed 2 sensors × 2 metrics × multiple readings; assert JPQL returns correct averages
- `QueryControllerTest` — `@WebMvcTest` + `@WithMockUser(roles="WEATHERMAN")`: 200 valid, 400 date violations, 400 empty metrics

**Dependencies:** Slices 3 and 4

---

## Slice 6 — React UI

**Goal:** Minimal React SPA that showcases all API functionality. Vite proxy routes `/api` → Spring Boot, so no CORS configuration is needed.

**Setup:**
```bash
npm create vite@latest frontend -- --template react-ts
cd frontend && npm install axios react-router-dom
```

**vite.config.ts proxy:**
```ts
server: { proxy: { '/api': 'http://localhost:8080' } }
```

**Pages and access:**

| Page | Route | Accessible by |
|---|---|---|
| Login | `/login` | All |
| Dashboard (Query) | `/dashboard` | ADMIN, WEATHERMAN |
| Admin Panel | `/admin` | ADMIN only — WEATHERMAN redirected to `/dashboard` |

**Key components:**
- `AuthContext.tsx` — stores JWT + role in state; persists to `sessionStorage`; provides `login()` / `logout()`
- `ProtectedRoute.tsx` — redirects to `/login` if unauthenticated; redirects WEATHERMAN away from `/admin`
- `LoginPage.tsx` — calls `POST /api/auth/login`; stores token; navigates to `/dashboard`
- `DashboardPage.tsx` — wraps `QueryForm` + nav
- `AdminPage.tsx` — tabs: "Register Sensor" / "Submit Metrics"
- `QueryForm.tsx` — sensor ids (comma-separated optional), metric checkboxes, optional date range; displays results in table
- `RegisterSensorForm.tsx` — id, country, city; inline success/error feedback
- `SubmitMetricsForm.tsx` — sensor id, dynamic rows of metric type + value; submit → feedback

**Axios client with JWT interceptor:**
```ts
const client = axios.create({ baseURL: '' }); // empty — Vite proxy handles routing
client.interceptors.request.use(config => {
  const token = sessionStorage.getItem('jwt');
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});
```

**Acceptance criteria:**
- Login as admin → Dashboard + Admin Panel tab visible
- Login as weatherman → Dashboard only; navigating to `/admin` redirects
- Admin can register a sensor and submit readings via UI
- Both users can run a query and see averaged results in a table
- Logout clears token and redirects to `/login`
- API errors surface as inline messages (not silent failures)

**Tests:** Manual smoke-test via browser (see Verification section)

**Dependencies:** Slices 1–5

---

## Testing Strategy

| Test Type | Tools | Covers |
|---|---|---|
| Unit | JUnit 5 + Mockito | Service business logic, error paths, date defaulting, JWT generation/validation |
| Controller slice | `@WebMvcTest` + `@WithMockUser` | Request validation, HTTP status codes, role-based 403, error envelope shape |
| Repository | `@DataJpaTest` + H2 | JPQL averaging projection with seeded data |
| Integration | `@SpringBootTest` + `@AutoConfigureMockMvc` | End-to-end JWT login → use token → authorized call; full role access matrix |

**Security integration test coverage (required):**
- 401 for unauthenticated requests to all protected endpoints
- 403 when WEATHERMAN calls ADMIN-only endpoints
- Full happy path: `POST /api/auth/login` → extract token → use in `Authorization` header → 201 on sensor registration
- Expired/tampered token → 401

---

## Slice Summary

| # | Deliverable | New Endpoints | Key Tests |
|---|---|---|---|
| 1 | Project bootstrap — running app, H2, Swagger | — | `WeatherSensorApplicationTests` |
| 2 | Security & error foundation — JWT, roles from config, login, error envelope | `POST /api/auth/login` | `JwtTokenProviderTest`, `AuthControllerTest`, `GlobalExceptionHandlerTest` |
| 3 | Sensor registration — secured + error-handled | `POST /api/sensors`, `GET /api/sensors/{id}` | `SensorServiceImplTest`, `SensorControllerTest` |
| 4 | Metric ingestion — secured + error-handled | `POST /api/sensors/{id}/metrics` | `MetricServiceImplTest`, `MetricControllerTest` |
| 5 | Query + averaging — secured + date validation | `POST /api/sensors/query` | `MetricServiceQueryTest`, `MetricReadingRepositoryTest`, `QueryControllerTest` |
| 6 | React UI (Login, Dashboard, Admin) | — | Manual smoke tests |

---

## Verification

### Backend
1. `mvn spring-boot:run`
2. `POST /api/auth/login` `{"username":"admin","password":"<your-admin-password>"}` → JWT token
3. `POST /api/sensors` `{"id":"s1","country":"Hungary","city":"Budapest"}` (with `Authorization: Bearer <token>`) → 201
4. `POST /api/sensors/s1/metrics` `{"readings":[{"metricType":"TEMPERATURE","value":21.5},{"metricType":"HUMIDITY","value":60.0}]}` → 201
5. `POST /api/sensors/query` `{"metrics":["TEMPERATURE","HUMIDITY"]}` → 200 with averages
6. Repeat with weatherman token: step 3 → 403, step 5 → 200
7. `mvn test` — all tests pass

### React UI smoke test
1. `cd frontend && npm run dev` → open `http://localhost:5173`
2. Login as admin → Dashboard; Admin Panel tab visible
3. Register sensor `s1` (Hungary, Budapest) → success message
4. Submit temperature 21.5 + humidity 60.0 for `s1` → success message
5. Query (no filters) → table shows `s1` with averages
6. Logout → `/login`
7. Login as weatherman → no Admin Panel tab; `/admin` redirects to Dashboard
8. Query → results visible
