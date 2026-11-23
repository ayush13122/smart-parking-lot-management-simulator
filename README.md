# Smart Parking Lot Management Simulator

A beginner-friendly, browser-based Java project for **CSM228 – Data Structures**. It simulates a 12-slot parking lot and demonstrates three core data structures through real operations.

## Data Structures Demonstrated

| Data Structure | Java Implementation | Purpose |
|---|---|---|
| HashMap | `HashMap<String, ParkingRecord>` | Maps vehicle registration numbers to parking records |
| HashSet | `HashSet<Integer>` | Tracks occupied parking slots |
| Queue (FIFO) | Custom ArrayList-based FIFO queue with a front index | Maintains the order of waiting vehicles |

## Features

- 12 visual parking slots
- Vehicle entry, exit, and search
- Vehicle types: Car, Bike, and Electric Car
- Automatic allocation of the first available slot
- FIFO waiting list when all slots are occupied
- Automatic allocation to the first waiting vehicle when a slot becomes free
- Demo data, reset, current status, and activity log
- Embedded browser interface served by the Java application

## Requirements

- JDK 17 or newer
- IntelliJ IDEA (or another Java IDE)
- A modern web browser

The project uses Java standard-library classes only. No database, external API, or third-party Java dependency is required.

## Run in IntelliJ IDEA

1. Open the project in IntelliJ IDEA.
2. Set the project SDK to JDK 17 or newer.
3. Open `src/SmartParkingLotSimulator.java`.
4. Run the `SmartParkingLotSimulator` main class.
5. The application starts a local HTTP server and opens the browser interface at:

   `http://127.0.0.1:8765/`

Keep the Java process running while using the browser interface. Stop the application from the IDE Run console when finished.

## Demo Scenario

Use **Load Demo** to add 14 sample vehicles:

- The first 12 vehicles occupy the 12 parking slots.
- The remaining 2 vehicles join the waiting queue.
- Exit vehicle `PB10AB1003`.
- The first waiting vehicle should receive the newly available Slot 3, while the second remains in the queue.

This scenario demonstrates FIFO queue behavior.

## Complexity Notes

- HashMap lookup: O(1) average
- HashSet add/remove/contains: O(1) average
- Custom ArrayList-based FIFO queue with a front index enqueue/dequeue: O(1)
- Finding the first available slot by scanning: O(n)
- Searching the waiting queue: O(n)

## Project Structure

```text
smart-parking-lot-management-simulator/
├── README.md
├── PROJECT_OVERVIEW.md
├── .gitignore
├── src/
│   └── SmartParkingLotSimulator.java
└── docs/
    ├── CSM228_CA_Instructions.docx
    ├── CSM228_Project_Report_Template.docx
    └── CSM228_Data_Structures_Syllabus.png
```

## Course Alignment

This project primarily applies the CSM228 syllabus topics **HashTables, HashMaps, and Queues**. It also provides a practical context for algorithmic complexity analysis.

## Academic Note

This is an individual educational project. Understand the Java implementation and be prepared to explain the data structures, operations, and complexity during the demonstration and viva.
