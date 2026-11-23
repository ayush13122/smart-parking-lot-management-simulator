import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.awt.Desktop;
import java.awt.GraphicsEnvironment;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

/**
 * Smart Parking Lot Management Simulator
 * CSM228 - Data Structures Project
 *
 * Run this file from IntelliJ IDEA. It starts a small local web server
 * and opens the simulation in the default browser.
 */
public class SmartParkingLotSimulator {

    private static final int PORT = 8765;
    private static final ParkingLot PARKING_LOT = new ParkingLot(12);

    public static void main(String[] args) {
        try {
            HttpServer server = HttpServer.create(
                    new InetSocketAddress("127.0.0.1", PORT), 0
            );

            // Show the HTML page in the browser.
            server.createContext("/", exchange -> {
                String path = exchange.getRequestURI().getPath();
                if ((path.equals("/") || path.equals("/index.html"))
                        && exchange.getRequestMethod().equalsIgnoreCase("GET")) {
                    sendResponse(exchange, 200, "text/html; charset=UTF-8", HTML_PAGE);
                } else {
                    sendJson(exchange, 404, "{\"ok\":false,\"message\":\"Page not found.\"}");
                }
            });

            // API used by the browser to communicate with the Java code.
            server.createContext("/api/state", exchange -> {
                if (!requireMethod(exchange, "GET")) return;
                sendJson(exchange, 200, PARKING_LOT.getStateJson());
            });

            server.createContext("/api/entry", exchange -> handlePost(exchange, form ->
                    PARKING_LOT.addVehicle(
                            form.getOrDefault("registration", ""),
                            form.getOrDefault("type", "Car")
                    )
            ));

            server.createContext("/api/exit", exchange -> handlePost(exchange, form ->
                    PARKING_LOT.exitVehicle(form.getOrDefault("registration", ""))
            ));

            server.createContext("/api/search", exchange -> handlePost(exchange, form ->
                    PARKING_LOT.searchVehicle(form.getOrDefault("registration", ""))
            ));

            server.createContext("/api/reset", exchange -> handlePost(exchange, form -> {
                PARKING_LOT.reset();
                return new ApiResult(true, "Simulation has been reset.");
            }));

            server.createContext("/api/demo", exchange -> handlePost(exchange, form ->
                    PARKING_LOT.loadDemoData()
            ));

            server.setExecutor(Executors.newCachedThreadPool());
            server.start();

            String appUrl = "http://127.0.0.1:" + PORT + "/";
            System.out.println("Smart Parking Lot Simulator is running.");
            System.out.println("Open this address in your browser: " + appUrl);
            openBrowser(appUrl);

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("Stopping Smart Parking Lot Simulator...");
                server.stop(0);
            }));

        } catch (IOException e) {
            System.out.println("Could not start the local server on port " + PORT + ".");
            System.out.println("Close another application using this port, then run again.");
            e.printStackTrace();
        }
    }

    private static void openBrowser(String url) {
        try {
            if (!GraphicsEnvironment.isHeadless() && Desktop.isDesktopSupported()) {
                Desktop.getDesktop().browse(URI.create(url));
            } else {
                System.out.println("Automatic browser opening is not available in this environment.");
                System.out.println("Copy this URL into your browser: " + url);
            }
        } catch (Exception | java.awt.AWTError e) {
            System.out.println("The browser could not be opened automatically.");
            System.out.println("Copy this URL into your browser: " + url);
        }
    }

    private static void handlePost(HttpExchange exchange,
                                   java.util.function.Function<Map<String, String>, ApiResult> action)
            throws IOException {
        if (!requireMethod(exchange, "POST")) return;

        try {
            Map<String, String> form = readFormData(exchange);
            ApiResult result = action.apply(form);
            sendJson(exchange, 200, result.toJson());
        } catch (Exception e) {
            sendJson(exchange, 400,
                    new ApiResult(false, "Request could not be processed. Please check the entered details.").toJson());
        }
    }

    private static boolean requireMethod(HttpExchange exchange, String expectedMethod) throws IOException {
        if (!exchange.getRequestMethod().equalsIgnoreCase(expectedMethod)) {
            sendJson(exchange, 405,
                    new ApiResult(false, "This request method is not allowed.").toJson());
            return false;
        }
        return true;
    }

    private static Map<String, String> readFormData(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        Map<String, String> values = new HashMap<>();

        if (body.isBlank()) return values;

        String[] pairs = body.split("&");
        for (String pair : pairs) {
            String[] parts = pair.split("=", 2);
            String key = URLDecoder.decode(parts[0], StandardCharsets.UTF_8);
            String value = parts.length > 1
                    ? URLDecoder.decode(parts[1], StandardCharsets.UTF_8)
                    : "";
            values.put(key, value);
        }
        return values;
    }

    private static void sendJson(HttpExchange exchange, int statusCode, String json) throws IOException {
        sendResponse(exchange, statusCode, "application/json; charset=UTF-8", json);
    }

    private static void sendResponse(HttpExchange exchange, int statusCode,
                                     String contentType, String response) throws IOException {
        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    // -----------------------------------------------------------------
    // Data Structures: Vehicle, ParkingQueue, and ParkingLot
    // -----------------------------------------------------------------

    private static class Vehicle {
        private final String registrationNumber;
        private final String type;

        Vehicle(String registrationNumber, String type) {
            this.registrationNumber = registrationNumber;
            this.type = type;
        }
    }

    private static class ParkingRecord {
        private final Vehicle vehicle;
        private final int slotNumber;

        ParkingRecord(Vehicle vehicle, int slotNumber) {
            this.vehicle = vehicle;
            this.slotNumber = slotNumber;
        }
    }

    /**
     * A simple FIFO queue made with an ArrayList and a front index.
     * New vehicles are added at the end. Dequeue moves the front index,
     * so it does not shift every remaining item on each removal.
     */
    private static class ParkingQueue {
        private final ArrayList<Vehicle> vehicles = new ArrayList<>();
        private int frontIndex = 0;

        public void enqueue(Vehicle vehicle) {
            vehicles.add(vehicle);
        }

        public Vehicle dequeue() {
            if (isEmpty()) {
                return null;
            }

            Vehicle nextVehicle = vehicles.get(frontIndex);
            frontIndex++;

            // Clear old items when the queue becomes empty.
            if (frontIndex == vehicles.size()) {
                vehicles.clear();
                frontIndex = 0;
            }

            return nextVehicle;
        }

        public boolean isEmpty() {
            return frontIndex >= vehicles.size();
        }

        public int size() {
            return vehicles.size() - frontIndex;
        }

        public List<Vehicle> toList() {
            return new ArrayList<>(vehicles.subList(frontIndex, vehicles.size()));
        }

        public boolean containsRegistration(String registrationNumber) {
            for (int i = frontIndex; i < vehicles.size(); i++) {
                if (vehicles.get(i).registrationNumber.equals(registrationNumber)) {
                    return true;
                }
            }
            return false;
        }
    }

    private static class ParkingLot {
        private final int totalSlots;

        // HashMap: vehicle registration number -> parking record.
        private final HashMap<String, ParkingRecord> vehicleToSlot = new HashMap<>();

        // HashSet: stores the slot numbers that are currently occupied.
        private final HashSet<Integer> occupiedSlots = new HashSet<>();

        // Custom FIFO queue: stores vehicles waiting for a parking slot.
        private final ParkingQueue waitingQueue = new ParkingQueue();

        private final ArrayList<String> activityLog = new ArrayList<>();

        ParkingLot(int totalSlots) {
            this.totalSlots = totalSlots;
            addLog("Parking lot is ready with " + totalSlots + " slots.");
        }

        public synchronized ApiResult addVehicle(String registration, String vehicleType) {
            String number = cleanRegistration(registration);
            String type = cleanVehicleType(vehicleType);

            if (!isValidRegistration(number)) {
                return new ApiResult(false,
                        "Enter a valid registration number (5 to 15 letters, numbers, or hyphens).");
            }

            if (type == null) {
                return new ApiResult(false, "Select a valid vehicle type.");
            }

            if (vehicleToSlot.containsKey(number) || waitingQueue.containsRegistration(number)) {
                return new ApiResult(false, "This vehicle is already parked or waiting.");
            }

            Vehicle vehicle = new Vehicle(number, type);
            int availableSlot = findFirstAvailableSlot();

            if (availableSlot == -1) {
                waitingQueue.enqueue(vehicle);
                addLog(number + " joined the waiting queue.");
                return new ApiResult(true, number + " added to the waiting queue at position "
                        + waitingQueue.size() + ".");
            }

            parkVehicle(vehicle, availableSlot);
            addLog(number + " parked in Slot " + availableSlot + ".");
            return new ApiResult(true, number + " successfully parked in Slot " + availableSlot + ".");
        }

        public synchronized ApiResult exitVehicle(String registration) {
            String number = cleanRegistration(registration);
            ParkingRecord record = vehicleToSlot.get(number);

            if (record == null) {
                return new ApiResult(false, "This vehicle is not currently parked.");
            }

            int freedSlot = record.slotNumber;
            vehicleToSlot.remove(number);
            occupiedSlots.remove(freedSlot);
            addLog(number + " exited from Slot " + freedSlot + ".");

            if (!waitingQueue.isEmpty()) {
                Vehicle nextVehicle = waitingQueue.dequeue();
                parkVehicle(nextVehicle, freedSlot);
                addLog(nextVehicle.registrationNumber + " moved from the waiting queue to Slot "
                        + freedSlot + ".");
                return new ApiResult(true, number + " exited. " + nextVehicle.registrationNumber
                        + " was automatically assigned Slot " + freedSlot + ".");
            }

            return new ApiResult(true, number + " exited. Slot " + freedSlot + " is now available.");
        }

        public synchronized ApiResult searchVehicle(String registration) {
            String number = cleanRegistration(registration);
            if (number.isBlank()) {
                return new ApiResult(false, "Enter a vehicle registration number to search.");
            }

            ParkingRecord record = vehicleToSlot.get(number);
            if (record != null) {
                return new ApiResult(true, number + " is parked in Slot " + record.slotNumber
                        + " (" + record.vehicle.type + ").");
            }

            List<Vehicle> waiting = waitingQueue.toList();
            for (int i = 0; i < waiting.size(); i++) {
                if (waiting.get(i).registrationNumber.equals(number)) {
                    return new ApiResult(true, number + " is waiting at queue position " + (i + 1) + ".");
                }
            }

            return new ApiResult(false, "No parking or waiting record found for " + number + ".");
        }

        public synchronized void reset() {
            vehicleToSlot.clear();
            occupiedSlots.clear();
            while (!waitingQueue.isEmpty()) {
                waitingQueue.dequeue();
            }
            activityLog.clear();
            addLog("Simulation has been reset.");
        }

        public synchronized ApiResult loadDemoData() {
            reset();

            for (int i = 1; i <= 14; i++) {
                String registration = String.format("PB10AB%04d", 1000 + i);
                String type;
                if (i % 3 == 0) {
                    type = "Electric Car";
                } else if (i % 3 == 1) {
                    type = "Car";
                } else {
                    type = "Bike";
                }
                addVehicle(registration, type);
            }

            addLog("Demo loaded: 12 vehicles parked and 2 vehicles waiting.");
            return new ApiResult(true,
                    "Demo loaded: 12 vehicles are parked and 2 vehicles are waiting. Exit PB10AB1003 to test FIFO.");
        }

        private void parkVehicle(Vehicle vehicle, int slotNumber) {
            vehicleToSlot.put(vehicle.registrationNumber,
                    new ParkingRecord(vehicle, slotNumber));
            occupiedSlots.add(slotNumber);
        }

        private int findFirstAvailableSlot() {
            for (int slot = 1; slot <= totalSlots; slot++) {
                if (!occupiedSlots.contains(slot)) {
                    return slot;
                }
            }
            return -1;
        }

        private void addLog(String message) {
            activityLog.add(0, message);
            if (activityLog.size() > 12) {
                activityLog.remove(activityLog.size() - 1);
            }
        }

        private synchronized String getStateJson() {
            StringBuilder json = new StringBuilder();
            json.append("{");
            json.append("\"totalSlots\":").append(totalSlots).append(",");
            json.append("\"occupiedCount\":").append(occupiedSlots.size()).append(",");
            json.append("\"availableCount\":").append(totalSlots - occupiedSlots.size()).append(",");
            json.append("\"waitingCount\":").append(waitingQueue.size()).append(",");

            json.append("\"slots\":[");
            for (int slot = 1; slot <= totalSlots; slot++) {
                if (slot > 1) json.append(",");
                ParkingRecord record = findRecordForSlot(slot);
                json.append("{");
                json.append("\"number\":").append(slot).append(",");
                json.append("\"occupied\":").append(record != null).append(",");
                json.append("\"vehicle\":\"")
                        .append(record == null ? "" : jsonEscape(record.vehicle.registrationNumber)).append("\",");
                json.append("\"type\":\"")
                        .append(record == null ? "" : jsonEscape(record.vehicle.type)).append("\"");
                json.append("}");
            }
            json.append("],");

            json.append("\"waitingVehicles\":[");
            List<Vehicle> waiting = waitingQueue.toList();
            for (int i = 0; i < waiting.size(); i++) {
                if (i > 0) json.append(",");
                Vehicle vehicle = waiting.get(i);
                json.append("{");
                json.append("\"position\":").append(i + 1).append(",");
                json.append("\"registration\":\"").append(jsonEscape(vehicle.registrationNumber)).append("\",");
                json.append("\"type\":\"").append(jsonEscape(vehicle.type)).append("\"");
                json.append("}");
            }
            json.append("],");

            json.append("\"activityLog\":[");
            for (int i = 0; i < activityLog.size(); i++) {
                if (i > 0) json.append(",");
                json.append("\"").append(jsonEscape(activityLog.get(i))).append("\"");
            }
            json.append("]}");
            return json.toString();
        }

        private ParkingRecord findRecordForSlot(int slotNumber) {
            for (ParkingRecord record : vehicleToSlot.values()) {
                if (record.slotNumber == slotNumber) {
                    return record;
                }
            }
            return null;
        }

        private String cleanRegistration(String registration) {
            return registration == null ? "" : registration.trim().toUpperCase();
        }

        private boolean isValidRegistration(String registration) {
            return registration.matches("[A-Z0-9-]{5,15}");
        }

        private String cleanVehicleType(String type) {
            if (type == null) return null;
            String cleanType = type.trim();
            if (cleanType.equals("Car") || cleanType.equals("Bike")
                    || cleanType.equals("Electric Car")) {
                return cleanType;
            }
            return null;
        }
    }

    private static class ApiResult {
        private final boolean ok;
        private final String message;

        ApiResult(boolean ok, String message) {
            this.ok = ok;
            this.message = message;
        }

        String toJson() {
            return "{\"ok\":" + ok + ",\"message\":\"" + jsonEscape(message) + "\"}";
        }
    }

    private static String jsonEscape(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }

    // -----------------------------------------------------------------
    // HTML, CSS and JavaScript are embedded here so only one Java file
    // is needed to run the complete project.
    // -----------------------------------------------------------------

    private static final String HTML_PAGE = """
            <!DOCTYPE html>
            <html lang="en">
            <head>
              <meta charset="UTF-8">
              <meta name="viewport" content="width=device-width, initial-scale=1.0">
              <title>Smart Parking Lot Simulator</title>
              <style>
                * { box-sizing: border-box; }
                body { margin: 0; font-family: Arial, sans-serif; background: #f3f6fb; color: #172033; }
                .wrap { max-width: 1180px; margin: auto; padding: 28px 20px 42px; }
                header { display: flex; justify-content: space-between; align-items: center; gap: 16px; margin-bottom: 22px; }
                h1 { margin: 0 0 7px; font-size: 27px; }
                .sub { color: #68758b; margin: 0; font-size: 14px; }
                .tag { background: #e7efff; color: #2455b5; padding: 9px 13px; border-radius: 20px; font-size: 12px; font-weight: bold; }
                .cards { display: grid; grid-template-columns: repeat(4, 1fr); gap: 13px; margin-bottom: 18px; }
                .card, .panel { background: white; border: 1px solid #e5eaf2; border-radius: 13px; box-shadow: 0 4px 14px rgba(26,48,85,.04); }
                .card { padding: 17px; }
                .card .label { color: #718097; font-size: 12px; margin-bottom: 8px; }
                .card .value { font-size: 28px; font-weight: bold; }
                .layout { display: grid; grid-template-columns: 1.35fr .9fr; gap: 17px; align-items: start; }
                .panel { padding: 19px; margin-bottom: 17px; }
                .panel h2 { margin: 0 0 15px; font-size: 17px; }
                .parking-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 11px; }
                .slot { min-height: 105px; border: 1px solid #d9e3ef; border-radius: 10px; padding: 12px 9px; background: #f4fbf7; text-align: center; }
                .slot.occupied { background: #fff3ed; border-color: #ffd3c1; }
                .slot .number { font-size: 12px; font-weight: bold; color: #607087; }
                .slot .car { font-size: 22px; margin: 8px 0 5px; }
                .slot .reg { font-size: 11px; font-weight: bold; word-break: break-word; }
                .slot .state { color: #258454; font-size: 11px; margin-top: 4px; }
                .slot.occupied .state { color: #c15a32; }
                .legend { display: flex; gap: 17px; margin-top: 13px; color: #68758b; font-size: 12px; }
                .dot { display: inline-block; width: 9px; height: 9px; border-radius: 50%; margin-right: 5px; }
                .dot.free { background: #39a66b; } .dot.busy { background: #e77c4f; }
                label { display: block; font-size: 12px; color: #536178; font-weight: bold; margin: 11px 0 6px; }
                input, select { width: 100%; border: 1px solid #d9e1ec; border-radius: 8px; padding: 11px 12px; font-size: 13px; background: white; color: #172033; }
                button { border: 0; border-radius: 8px; padding: 11px 14px; font-size: 13px; font-weight: bold; cursor: pointer; }
                .primary { background: #245ac4; color: white; width: 100%; margin-top: 13px; }
                .secondary { background: #edf2fa; color: #30425e; }
                .danger { background: #fff0ec; color: #b94d2b; }
                .actions { display: flex; gap: 9px; margin-top: 12px; }
                .actions button { flex: 1; }
                .notice { display: none; padding: 11px 13px; border-radius: 8px; font-size: 13px; margin-bottom: 16px; }
                .notice.show { display: block; } .notice.good { background: #eaf8f0; color: #216a43; } .notice.bad { background: #fff0ed; color: #a64229; }
                .queue-item { display: flex; justify-content: space-between; padding: 10px 11px; border: 1px solid #e8edf4; border-radius: 8px; margin-bottom: 7px; font-size: 12px; }
                .queue-item span:last-child { color: #718097; }
                .empty { color: #8a96a8; text-align: center; padding: 17px 5px; font-size: 13px; }
                .log { padding: 0; margin: 0; list-style: none; }
                .log li { border-bottom: 1px solid #edf0f5; padding: 9px 0; font-size: 12px; color: #59677d; }
                .log li:last-child { border-bottom: 0; }
                .concepts { display: grid; grid-template-columns: repeat(3, 1fr); gap: 10px; }
                .concept { background: #f7f9fc; border: 1px solid #e9edf4; border-radius: 9px; padding: 12px; }
                .concept strong { display: block; font-size: 13px; margin-bottom: 5px; }
                .concept span { color: #69778d; font-size: 11px; line-height: 1.5; }
                footer { color: #8a96a8; font-size: 11px; text-align: center; margin-top: 6px; }
                @media (max-width: 820px) { .layout { grid-template-columns: 1fr; } }
                @media (max-width: 560px) { .cards { grid-template-columns: repeat(2, 1fr); } .parking-grid { grid-template-columns: repeat(2, 1fr); } header { align-items: flex-start; flex-direction: column; } .concepts { grid-template-columns: 1fr; } }
              </style>
            </head>
            <body>
              <main class="wrap">
                <header>
                  <div>
                    <h1>Smart Parking Lot Simulator</h1>
                    <p class="sub">A live simulation of vehicle entry, parking allocation and FIFO waiting management.</p>
                  </div>
                  <div class="tag">CSM228 · DATA STRUCTURES</div>
                </header>

                <div id="notice" class="notice"></div>

                <section class="cards">
                  <div class="card"><div class="label">Total Slots</div><div class="value" id="totalSlots">12</div></div>
                  <div class="card"><div class="label">Occupied Slots</div><div class="value" id="occupiedCount">0</div></div>
                  <div class="card"><div class="label">Available Slots</div><div class="value" id="availableCount">12</div></div>
                  <div class="card"><div class="label">Waiting Vehicles</div><div class="value" id="waitingCount">0</div></div>
                </section>

                <div class="layout">
                  <div>
                    <section class="panel">
                      <h2>Parking Area</h2>
                      <div class="parking-grid" id="parkingGrid"></div>
                      <div class="legend"><span><i class="dot free"></i>Available</span><span><i class="dot busy"></i>Occupied</span></div>
                    </section>

                    <section class="panel">
                      <h2>Waiting Queue · FIFO</h2>
                      <div id="waitingQueue"><div class="empty">No vehicles are waiting.</div></div>
                    </section>

                    <section class="panel">
                      <h2>Data Structures Used</h2>
                      <div class="concepts">
                        <div class="concept"><strong>HashMap</strong><span>Vehicle number is mapped to its parking record for quick search.</span></div>
                        <div class="concept"><strong>HashSet</strong><span>Stores occupied slot numbers and helps prevent duplicate allocation.</span></div>
                        <div class="concept"><strong>Queue</strong><span>Waiting vehicles follow First In, First Out (FIFO) order.</span></div>
                      </div>
                    </section>
                  </div>

                  <div>
                    <section class="panel">
                      <h2>Vehicle Entry</h2>
                      <form id="entryForm">
                        <label for="entryRegistration">Vehicle Registration Number</label>
                        <input id="entryRegistration" name="registration" placeholder="e.g. PB10AB1234" maxlength="15" required>
                        <label for="vehicleType">Vehicle Type</label>
                        <select id="vehicleType" name="type"><option>Car</option><option>Bike</option><option>Electric Car</option></select>
                        <button class="primary" type="submit">Add Vehicle</button>
                      </form>
                    </section>

                    <section class="panel">
                      <h2>Vehicle Exit</h2>
                      <form id="exitForm">
                        <label for="exitRegistration">Vehicle Registration Number</label>
                        <input id="exitRegistration" name="registration" placeholder="Enter parked vehicle number" required>
                        <button class="primary" type="submit">Exit Vehicle</button>
                      </form>
                    </section>

                    <section class="panel">
                      <h2>Search Vehicle</h2>
                      <form id="searchForm">
                        <label for="searchRegistration">Vehicle Registration Number</label>
                        <input id="searchRegistration" name="registration" placeholder="Enter vehicle number" required>
                        <button class="primary" type="submit">Search</button>
                      </form>
                    </section>

                    <section class="panel">
                      <h2>Simulation Controls</h2>
                      <p class="sub">Load sample vehicles to see the parking lot fill up and the waiting queue work.</p>
                      <div class="actions">
                        <button class="secondary" id="demoButton" type="button">Load Demo</button>
                        <button class="danger" id="resetButton" type="button">Reset</button>
                      </div>
                    </section>

                    <section class="panel">
                      <h2>Activity Log</h2>
                      <ul class="log" id="activityLog"><li>Waiting for activity...</li></ul>
                    </section>
                  </div>
                </div>
                <footer>Java handles all parking and data structure operations · HTML/CSS/JavaScript display the simulation</footer>
              </main>

              <script>
                const notice = document.getElementById('notice');

                function escapeHtml(value) {
                  return String(value).replaceAll('&', '&amp;').replaceAll('<', '&lt;')
                    .replaceAll('>', '&gt;').replaceAll('"', '&quot;').replaceAll("'", '&#039;');
                }

                function showNotice(message, good) {
                  notice.textContent = message;
                  notice.className = 'notice show ' + (good ? 'good' : 'bad');
                }

                async function sendRequest(url, data = {}) {
                  const response = await fetch(url, {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
                    body: new URLSearchParams(data)
                  });
                  return await response.json();
                }

                async function refreshDashboard() {
                  const response = await fetch('/api/state');
                  const data = await response.json();

                  document.getElementById('totalSlots').textContent = data.totalSlots;
                  document.getElementById('occupiedCount').textContent = data.occupiedCount;
                  document.getElementById('availableCount').textContent = data.availableCount;
                  document.getElementById('waitingCount').textContent = data.waitingCount;

                  const parkingGrid = document.getElementById('parkingGrid');
                  parkingGrid.innerHTML = data.slots.map(slot => `
                    <div class="slot ${slot.occupied ? 'occupied' : ''}">
                      <div class="number">SLOT ${slot.number}</div>
                      <div class="car">${slot.occupied ? (slot.type === 'Bike' ? '🏍️' : '🚗') : '＋'}</div>
                      <div class="reg">${slot.occupied ? escapeHtml(slot.vehicle) : 'EMPTY'}</div>
                      <div class="state">${slot.occupied ? escapeHtml(slot.type) : 'Available'}</div>
                    </div>
                  `).join('');

                  const queue = document.getElementById('waitingQueue');
                  if (data.waitingVehicles.length === 0) {
                    queue.innerHTML = '<div class="empty">No vehicles are waiting.</div>';
                  } else {
                    queue.innerHTML = data.waitingVehicles.map(vehicle => `
                      <div class="queue-item"><strong>#${vehicle.position} · ${escapeHtml(vehicle.registration)}</strong><span>${escapeHtml(vehicle.type)}</span></div>
                    `).join('');
                  }

                  const log = document.getElementById('activityLog');
                  log.innerHTML = data.activityLog.length
                    ? data.activityLog.map(item => `<li>${escapeHtml(item)}</li>`).join('')
                    : '<li>No activity yet.</li>';
                }

                document.getElementById('entryForm').addEventListener('submit', async event => {
                  event.preventDefault();
                  const form = new FormData(event.target);
                  try {
                    const result = await sendRequest('/api/entry', Object.fromEntries(form.entries()));
                    showNotice(result.message, result.ok);
                    if (result.ok) event.target.reset();
                    await refreshDashboard();
                  } catch (error) { showNotice('Could not connect to the Java application.', false); }
                });

                document.getElementById('exitForm').addEventListener('submit', async event => {
                  event.preventDefault();
                  const registration = document.getElementById('exitRegistration').value;
                  try {
                    const result = await sendRequest('/api/exit', { registration });
                    showNotice(result.message, result.ok);
                    if (result.ok) event.target.reset();
                    await refreshDashboard();
                  } catch (error) { showNotice('Could not connect to the Java application.', false); }
                });

                document.getElementById('searchForm').addEventListener('submit', async event => {
                  event.preventDefault();
                  const registration = document.getElementById('searchRegistration').value;
                  try {
                    const result = await sendRequest('/api/search', { registration });
                    showNotice(result.message, result.ok);
                  } catch (error) { showNotice('Could not connect to the Java application.', false); }
                });

                document.getElementById('demoButton').addEventListener('click', async () => {
                  try {
                    const result = await sendRequest('/api/demo');
                    showNotice(result.message, result.ok);
                    await refreshDashboard();
                  } catch (error) { showNotice('Could not load demo data.', false); }
                });

                document.getElementById('resetButton').addEventListener('click', async () => {
                  try {
                    const result = await sendRequest('/api/reset');
                    showNotice(result.message, result.ok);
                    await refreshDashboard();
                  } catch (error) { showNotice('Could not reset the simulation.', false); }
                });

                refreshDashboard().catch(() => showNotice('Waiting for the Java server to start...', false));
              </script>
            </body>
            </html>
            """;
}