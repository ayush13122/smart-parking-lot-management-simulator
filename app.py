import html
import os
import shutil
import subprocess
import tempfile
import time
from pathlib import Path

import requests
import streamlit as st


ROOT = Path(__file__).resolve().parent
JAVA_SOURCE = ROOT / "src" / "SmartParkingLotSimulator.java"
CLASS_DIR = ROOT / ".runtime_classes"
JAVA_LOG = Path(tempfile.gettempdir()) / "smart_parking_java_backend.log"

st.set_page_config(
    page_title="Smart Parking Lot Simulator | CSM228",
    page_icon="🅿️",
    layout="wide",
    initial_sidebar_state="expanded",
)


@st.cache_resource(show_spinner="Starting the Java parking simulator...")
def start_java_backend():
    """Compile and launch the original Java DSA backend once per app instance."""
    if not JAVA_SOURCE.exists():
        raise FileNotFoundError(f"Java source file not found: {JAVA_SOURCE}")

    javac = shutil.which("javac")
    java = shutil.which("java")
    if not javac or not java:
        raise RuntimeError(
            "Java JDK was not found. Make sure packages.txt installs openjdk-17-jdk-headless."
        )

    CLASS_DIR.mkdir(exist_ok=True)
    class_file = CLASS_DIR / "SmartParkingLotSimulator.class"
    if not class_file.exists() or class_file.stat().st_mtime < JAVA_SOURCE.stat().st_mtime:
        compile_result = subprocess.run(
            [javac, "-d", str(CLASS_DIR), str(JAVA_SOURCE)],
            cwd=str(ROOT),
            capture_output=True,
            text=True,
            timeout=90,
        )
        if compile_result.returncode != 0:
            raise RuntimeError(
                "Java compilation failed:\n"
                + (compile_result.stderr or compile_result.stdout or "No compiler output")
            )

    log_handle = open(JAVA_LOG, "a", encoding="utf-8", buffering=1)
    process = subprocess.Popen(
        [java, "-Djava.awt.headless=true", "-cp", str(CLASS_DIR), "SmartParkingLotSimulator"],
        cwd=str(ROOT),
        stdout=log_handle,
        stderr=subprocess.STDOUT,
        stdin=subprocess.DEVNULL,
        start_new_session=True,
    )

    # The Java source starts at port 8080 and tries subsequent ports if needed.
    for _ in range(40):
        if process.poll() is not None:
            log_handle.flush()
            log_text = JAVA_LOG.read_text(encoding="utf-8", errors="replace") if JAVA_LOG.exists() else ""
            raise RuntimeError("Java backend stopped during startup.\n" + log_text[-5000:])

        for port in range(8765, 8766):
            base_url = f"http://127.0.0.1:{port}"
            try:
                response = requests.get(base_url + "/api/state", timeout=0.5)
                if response.ok and isinstance(response.json(), dict) and "totalSlots" in response.json():
                    return {"process": process, "base_url": base_url, "log_handle": log_handle}
            except (requests.RequestException, ValueError):
                pass
        time.sleep(0.5)

    process.terminate()
    log_handle.flush()
    log_text = JAVA_LOG.read_text(encoding="utf-8", errors="replace") if JAVA_LOG.exists() else ""
    raise RuntimeError("Could not connect to the Java backend after startup.\n" + log_text[-5000:])


def api_get(base_url, path):
    response = requests.get(base_url + path, timeout=5)
    response.raise_for_status()
    return response.json()


def api_post(base_url, path, data=None):
    response = requests.post(base_url + path, data=data or {}, timeout=8)
    response.raise_for_status()
    return response.json()


def safe(value):
    return html.escape(str(value if value is not None else ""))


def show_flash():
    message = st.session_state.pop("flash_message", None)
    kind = st.session_state.pop("flash_kind", "info")
    if message:
        if kind == "success":
            st.success(message)
        elif kind == "error":
            st.error(message)
        elif kind == "warning":
            st.warning(message)
        else:
            st.info(message)


def flash_and_rerun(message, kind="info"):
    st.session_state["flash_message"] = message
    st.session_state["flash_kind"] = kind
    st.rerun()


# Dark navy dashboard styling, matching the local Java interface.
st.markdown(
    """
    <style>
      .stApp { background: #0f172a; color: #f8fafc; }
      [data-testid="stHeader"] { background: rgba(15,23,42,0.92); }
      [data-testid="stSidebar"] { background: #172338; }
      .block-container { max-width: 1450px; padding-top: 1.4rem; padding-bottom: 2.5rem; }
      .hero { background: linear-gradient(125deg,#1e293b,#111c31); border: 1px solid #334155;
              border-radius: 16px; padding: 22px 25px; margin-bottom: 18px; }
      .hero h1 { margin: 0; color: #dbeafe; font-size: 1.85rem; font-weight: 800; }
      .hero p { color: #9fb0c8; margin: 7px 0 0 0; }
      .tag { display: inline-block; padding: 4px 9px; border-radius: 20px; margin: 11px 6px 0 0;
             font-size: 0.73rem; font-weight: 700; border: 1px solid #475569; color: #dbeafe; }
      .slot-card { background: #202e45; border: 1px solid #40516e; border-radius: 12px;
                   padding: 12px; min-height: 126px; margin-bottom: 10px; }
      .slot-card.occupied { border: 1px solid #d48a00; }
      .slot-card.free { border: 1px solid #16845e; }
      .slot-head { display:flex; justify-content:space-between; gap:8px; align-items:center; margin-bottom:8px; }
      .slot-num { color:#a9bad2; font-family:monospace; font-weight:800; font-size:.78rem; }
      .slot-status { font-size:.62rem; font-weight:800; border-radius:20px; padding:3px 7px; }
      .slot-status.busy { background:#5b3d14; color:#ffd37b; }
      .slot-status.open { background:#123f35; color:#79e3ba; }
      .slot-reg { color:#f8fafc; font-weight:800; font-family:monospace; font-size:.82rem; margin-bottom:5px; }
      .slot-meta { color:#9eafc7; font-size:.69rem; line-height:1.55; }
      .section-title { color:#e2e8f0; font-weight:800; font-size:1.05rem; margin: 0 0 12px 0; }
      div[data-testid="stMetric"] { background:#202e45; border:1px solid #40516e; padding:13px 15px;
                                     border-radius:12px; }
      div[data-testid="stMetricLabel"] { color:#9eafc7; }
      div[data-testid="stMetricValue"] { color:#f8fafc; }
      .stButton button { border-radius:8px; font-weight:700; }
      .small-muted { color:#9eafc7; font-size:.82rem; }
      [data-testid="stForm"] { background:#202e45; border:1px solid #40516e; border-radius:12px; padding:15px; }
      .stTextInput label, .stSelectbox label { color:#b7c6da !important; }
    </style>
    """,
    unsafe_allow_html=True,
)

try:
    backend = start_java_backend()
    BASE_URL = backend["base_url"]
    state = api_get(BASE_URL, "/api/state")
except Exception as exc:
    st.error("The Java backend could not be started.")
    st.code(str(exc))
    st.info("Check that src/SmartParkingLotSimulator.java is in the repository and packages.txt installs the JDK.")
    st.stop()

show_flash()

st.markdown(
    """
    <div class="hero">
      <h1>🅿️ Smart Parking Lot Management Simulator</h1>
      <p>CSM228 – Data Structures | Individual University Project & Viva Demonstration</p>
      <span class="tag">HashMap&lt;String, ParkingRecord&gt;</span>
      <span class="tag">HashSet&lt;Integer&gt;</span>
      <span class="tag">Custom Queue (FIFO)</span>
    </div>
    """,
    unsafe_allow_html=True,
)

# Demo controls
control1, control2, control3, _ = st.columns([1.35, 1.55, 0.85, 2.2])
with control1:
    if st.button("🌟 Load Demo (14 Vehicles)", use_container_width=True):
        try:
            result = api_post(BASE_URL, "/api/demo")
            flash_and_rerun(result.get("message", "Demo data loaded."), "success")
        except requests.RequestException as exc:
            flash_and_rerun(f"Could not load demo data: {exc}", "error")
with control2:
    if st.button("⚡ Demo FIFO Exit (PB10AB1003)", use_container_width=True):
        try:
            result = api_post(BASE_URL, "/api/exit", {"registration": "PB10AB1003"})
            flash_and_rerun(result.get("message", "FIFO exit demonstration completed."), "success" if result.get("ok") else "warning")
        except requests.RequestException as exc:
            flash_and_rerun(f"Could not run FIFO demo: {exc}", "error")
with control3:
    if st.button("🗑️ Reset", use_container_width=True):
        try:
            result = api_post(BASE_URL, "/api/reset")
            flash_and_rerun(result.get("message", "Simulation reset."), "success")
        except requests.RequestException as exc:
            flash_and_rerun(f"Could not reset simulation: {exc}", "error")

# Refresh state after the action controls have been evaluated.
try:
    state = api_get(BASE_URL, "/api/state")
except requests.RequestException as exc:
    st.error(f"Could not refresh simulator state: {exc}")
    st.stop()

m1, m2, m3, m4 = st.columns(4)
m1.metric("🅿️ TOTAL CAPACITY", state.get("totalSlots", 12))
m2.metric("🚘 OCCUPIED SLOTS", f"{state.get('occupiedCount', 0)} / {state.get('totalSlots', 12)}")
m3.metric("✅ AVAILABLE SLOTS", state.get("availableCount", 0))
m4.metric("⌛ WAITING IN QUEUE (FIFO)", state.get("waitingCount", 0))

left, right = st.columns([1.35, 1], gap="large")

with left:
    st.markdown('<div class="section-title">📍 Parking Lot Slots (1 to 12)</div>', unsafe_allow_html=True)
    st.caption("Capacity: 12 fixed slots · Green = Available · Amber = Occupied")
    slots = state.get("slots", [])
    slot_columns = st.columns(3)
    for index, slot in enumerate(slots):
        occupied = slot.get("occupied", False)
        vehicle = slot.get("vehicle", "")
        slot_number = slot.get("number", index + 1)
        reg = safe(vehicle)
        vehicle_type = safe(slot.get("type", ""))
        owner = "Not collected"
        entry_time = "Current session"
        if occupied:
            icon = "⚡" if vehicle_type == "Electric Car" else ("🏍️" if vehicle_type == "Bike" else "🚘")
            card = f"""
              <div class="slot-card occupied">
                <div class="slot-head"><span class="slot-num">SLOT {slot_number:02d}</span><span class="slot-status busy">OCCUPIED</span></div>
                <div class="slot-reg">{icon} {reg}</div>
                <div class="slot-meta">Type: {vehicle_type}<br>Owner: {owner}<br>Entry: {entry_time}</div>
              </div>
            """
        else:
            card = f"""
              <div class="slot-card free">
                <div class="slot-head"><span class="slot-num">SLOT {slot_number:02d}</span><span class="slot-status open">AVAILABLE</span></div>
                <div class="slot-reg">🅿️ Empty</div>
                <div class="slot-meta">Ready for next vehicle</div>
              </div>
            """
        with slot_columns[index % 3]:
            st.markdown(card, unsafe_allow_html=True)

with right:
    st.markdown('<div class="section-title">⚙️ Parking Operations</div>', unsafe_allow_html=True)
    st.caption("Real-time Java DSA actions")

    with st.form("entry_form", clear_on_submit=True):
        st.markdown("**➕ Park New Vehicle (Entry)**")
        reg_number = st.text_input("Vehicle Registration Number", placeholder="E.g. PB10AB1015")
        vehicle_type = st.selectbox("Vehicle Type", ["Car", "Bike", "Electric Car"])
        entry_submitted = st.form_submit_button("📥 Park Vehicle / Enqueue", use_container_width=True)
    if entry_submitted:
        try:
            result = api_post(BASE_URL, "/api/entry", {"registration": reg_number, "type": vehicle_type})
            flash_and_rerun(result.get("message", "Entry request processed."), "success" if result.get("ok") else "warning")
        except requests.RequestException as exc:
            flash_and_rerun(f"Entry request failed: {exc}", "error")

    with st.form("exit_form", clear_on_submit=True):
        st.markdown("**➖ Exit Vehicle**")
        exit_reg = st.text_input("Vehicle Registration Number", placeholder="E.g. PB10AB1003")
        exit_submitted = st.form_submit_button("📤 Process Exit & Allocate Freed Slot (FIFO)", use_container_width=True)
    if exit_submitted:
        try:
            result = api_post(BASE_URL, "/api/exit", {"registration": exit_reg})
            flash_and_rerun(result.get("message", "Exit request processed."), "success" if result.get("ok") else "warning")
        except requests.RequestException as exc:
            flash_and_rerun(f"Exit request failed: {exc}", "error")

    with st.form("search_form", clear_on_submit=False):
        st.markdown("**🔎 Search Vehicle**")
        search_reg = st.text_input("Search by Registration Number", placeholder="E.g. PB10AB1001 or PB10AB1013")
        search_submitted = st.form_submit_button("🔍 Search Vehicle", use_container_width=True)
    if search_submitted:
        try:
            result = api_post(BASE_URL, "/api/search", {"registration": search_reg})
            st.session_state["search_result"] = result
        except requests.RequestException as exc:
            st.error(f"Search request failed: {exc}")

    search_result = st.session_state.get("search_result")
    if search_result:
        if search_result.get("ok"):
            st.success(search_result.get("message", "Vehicle found."))
            st.caption("Lookup complexity: " + str(search_result.get("complexity", "")))
        else:
            st.warning(search_result.get("message", "Vehicle not found."))

bottom_left, bottom_right = st.columns([1, 1], gap="large")
with bottom_left:
    st.markdown('<div class="section-title">⌛ Waiting Queue (FIFO)</div>', unsafe_allow_html=True)
    queue = state.get("waitingVehicles", [])
    if queue:
        for vehicle in queue:
            st.markdown(
                f"**#{vehicle.get('position', '')} · {safe(vehicle.get('registration', ''))}**  "
                f"<span class='small-muted'>| {safe(vehicle.get('type', ''))}</span>",
                unsafe_allow_html=True,
            )
    else:
        st.caption("No vehicles waiting. The queue is empty.")

with bottom_right:
    st.markdown('<div class="section-title">🧾 Activity Log</div>', unsafe_allow_html=True)
    logs = state.get("activityLog", [])
    if logs:
        for item in logs[:12]:
            st.caption(item)
    else:
        st.caption("No activity recorded yet.")

with st.expander("📚 Data Structures & Time Complexity", expanded=False):
    st.markdown(
        """
        - **HashMap (`HashMap<String, ParkingRecord>`)**: maps a vehicle registration number to its parking record. Average lookup, insertion, and deletion are **O(1)**.
        - **HashSet (`HashSet<Integer>`)**: tracks occupied slot numbers and avoids allocating the same slot twice. Average add, remove, and contains are **O(1)**.
        - **Custom Queue (ArrayList + front index)**: new waiting vehicles join at the rear; the first waiting vehicle leaves from the front. Enqueue and dequeue are **O(1)**.
        - **Finding an available slot** scans the 12 fixed slots, so it is **O(n)** in the number of slots.
        - **Searching the waiting queue** traverses the linked nodes, so it is **O(w)** in the number of waiting vehicles.
        """
    )
    st.caption("The Python file is only the Streamlit presentation layer. All vehicle allocation, lookup, occupied-slot tracking, and custom FIFO queue operations are performed by the Java backend.")

st.divider()
st.caption("CSM228 · Data Structures · Java DSA backend + Streamlit dashboard")