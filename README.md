# SmartMove Core Engine

A centralized backend system for managing shared urban mobility vehicles
(Bicycles, Electric Scooters, Mopeds) across multiple European cities.

## Architecture Overview

```
src/main/java/com/smartmove/
│
├── Main.java                          ← Entry point & demonstration
│
├── controller/
│   ├── SmartMoveCentralController.java  ← Central brain (state machine, policies, telemetry)
│   └── SmartMoveException.java
│
├── domain/                            ← Core business entities
│   ├── vehicle/
│   │   ├── Vehicle.java               ← Abstract base (state machine, thread-safe transitions)
│   │   ├── VehicleState.java          ← Enum: AVAILABLE, RESERVED, IN_USE, MAINTENANCE,
│   │   │                                       EMERGENCY_LOCK, RELOCATING
│   │   ├── Bicycle.java
│   │   ├── ElectricScooter.java
│   │   └── Moped.java                 ← + helmet sensor field
│   ├── City.java
│   ├── GeoCoordinate.java             ← Haversine distance calculation
│   ├── Zone.java                      ← Geofenced area with containment check
│   ├── TelemetryData.java             ← GPS + battery + temperature + helmetPresent
│   ├── Rental.java
│   ├── Payment.java
│   └── User.java
│
├── policy/                            ← Multi-tenant city policies
│   ├── CityPolicy.java                ← Interface
│   ├── LondonPolicy.java              ← Congestion charge, speed limits
│   ├── MilanPolicy.java               ← Helmet check for mopeds, ZTL zones
│   ├── RomePolicy.java                ← Archaeological zone restrictions for scooters
│   ├── PolicyFactory.java             ← City name → policy lookup
│   └── PolicyViolationException.java
│
├── telemetry/
│   └── TelemetryMonitor.java          ← Background thread, BlockingQueue, event callbacks
│
├── persistence/
│   ├── FileStorage.java               ← Interface: loadAll() / saveAll()
│   ├── CsvFileStorage.java            ← Abstract CSV base
│   ├── VehicleRepository.java         ← vehicles.csv
│   └── Repositories.java             ← users.csv, rentals.csv, payments.csv
│
├── audit/
│   ├── AuditEntry.java                ← Checksum-chained record (djb2 hash)
│   ├── AuditLog.java                  ← Append-only log, chain verification, rollback
│   └── AuditWriteException.java
│
└── util/
    └── DataSeeder.java                ← Populates demo fleet (13 vehicles, 5 users)
```

## Key Features Implemented

### 1. Vehicle State Machine
- 6 states: `AVAILABLE → RESERVED → IN_USE → MAINTENANCE / EMERGENCY_LOCK`
- All transitions validated through `SmartMoveCentralController.validateTransition()`
- Thread-safe via per-vehicle `synchronized` locks (no frameworks, no `java.util.concurrent.locks`)

### 2. Multi-Tenant City Policies
| City   | Policy                                                              |
|--------|---------------------------------------------------------------------|
| London | Congestion charge (£3.50) on every trip end                         |
| Milan  | Helmet sensor required before Moped unlock; ZTL zone emergency lock |
| Rome   | Scooters blocked from archaeological zones (Colosseum, Vatican, etc)|

### 3. Asynchronous Telemetry
- `TelemetryMonitor` runs on a dedicated daemon thread
- Uses `LinkedBlockingQueue<TelemetryUpdate>` as the stream buffer
- Triggers `EMERGENCY_LOCK` if temperature > 60°C or theft alarm (movement without rental)
- Triggers emergency rental termination if battery < 5% during active trip

### 4. Primitive Concurrency (no frameworks)
- `ConcurrentHashMap<String, Object>` for per-vehicle lock objects
- All state transitions use `synchronized(vehicleLock)` blocks
- Volatile fields on Vehicle for safe cross-thread reads
- `AtomicLong` for ID sequence generation and audit checksum tracking

### 5. High-Integrity Audit Trail
- Every state change and payment appended to `data/audit_log.csv`
- Each entry contains `seqId | timestamp | eventType | payload | prevChecksum | checksum`
- Checksum computed with djb2 hash over all fields including previous checksum
- `AuditLog.verifyChain()` detects any tampering
- Write-then-update pattern: file write must succeed before in-memory update
- Automatic rollback if audit write fails

### 6. File-Based Persistence (no DB)
All data stored in `data/` directory as CSV files:
- `data/vehicles.csv` — vehicle fleet state
- `data/users.csv` — registered users  
- `data/rentals.csv` — rental history
- `data/payments.csv` — payment records
- `data/audit_log.csv` — immutable audit trail

## Building and Running

### Requirements
- Java 17 or later (uses records, switch expressions)
- Maven 3.6+ (optional — can also compile manually)

### With Maven
```bash
# Compile and run
mvn compile exec:java

# Build fat jar
mvn package
java -jar target/smartmove-core-1.0.0-jar-with-dependencies.jar
```

### Manual Compilation
```bash
mkdir -p out data
find src -name "*.java" > sources.txt
javac -d out @sources.txt
java -cp out com.smartmove.Main
```

### Using the build script
```bash
chmod +x build.sh
./build.sh
```

## Demo Scenarios (Main.java)

1. **London** — Alice rents a scooter; congestion charge automatically added at trip end
2. **Milan** — Carlos attempts to start Moped without helmet (rejected), then succeeds after sensor confirms helmet
3. **Rome** — Bob's scooter enters Colosseum archaeological zone and is immediately emergency-locked
4. **Telemetry** — Vehicles with critical temperature and low battery trigger automatic responses
5. **Concurrency** — Two users race to reserve the same vehicle; only one succeeds
6. **Audit** — Full checksum chain printed and verified

## Data Persistence

After running, inspect the `data/` directory:
```
data/
├── vehicles.csv      ← 13 vehicles with states, batteries, GPS
├── users.csv         ← 5 users
├── rentals.csv       ← completed and active rentals
├── payments.csv      ← payments with surcharges
└── audit_log.csv     ← tamper-evident event log
```
