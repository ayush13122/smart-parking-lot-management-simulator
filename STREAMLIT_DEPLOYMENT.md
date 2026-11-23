# Streamlit Community Cloud Deployment

## Project Architecture

- `src/SmartParkingLotSimulator.java` contains the Java DSA implementation and local HTTP API.
- `app.py` provides the Streamlit dashboard and communicates with the Java process on the server.
- `packages.txt` installs Java 17 on Streamlit Community Cloud.
- `requirements.txt` installs Streamlit and Requests.
- `.streamlit/config.toml` applies the dark dashboard theme.

The Java backend is the source of truth for vehicle allocation, HashMap lookup, HashSet occupancy tracking, and the custom ArrayList-based FIFO queue with a front index. Python is used only for the hosted dashboard and API calls.

## Deploy

1. Push the project files to this GitHub repository.
2. Open https://share.streamlit.io and sign in with GitHub.
3. Select **Create app**.
4. Repository: `ayush13122/smart-parking-lot-management-simulator`
5. Branch: `main`
6. Main file path: `app.py`
7. Click **Deploy**.

Streamlit Cloud reads Python dependencies from `requirements.txt` and system packages from `packages.txt`.

## Run Locally

Requirements: Python 3.9+ and JDK 17+.

```bash
pip install -r requirements.txt
streamlit run app.py
```

The Streamlit process compiles `src/SmartParkingLotSimulator.java`, starts the Java server internally on port 8765, and calls it over `127.0.0.1`. You do not need to open the old Java HTML page separately.

## Important Notes

- The first app start may take a little longer while Java is compiled and launched.
- The Java application stores simulation state in memory; restarting the app resets it.
- Users connected to the same hosted app instance share the demo state.
- Use **Reset** or **Load Demo** to return to a known state.
- The local Java browser interface remains available when running the Java main class directly.
