import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.*;

// ================= MAIN JAVA APPLICATION =================

public class AgriSense {

    static final String DB = "jdbc:sqlite:agrisense.db";

    // ================= OOP: ABSTRACTION =================

    static abstract class Person {
        protected String name;
        protected String phone;

        Person(String name, String phone) {
            this.name = name;
            this.phone = phone;
        }

        abstract String getRole();
    }

    // ================= OOP: INHERITANCE =================

    static class Farmer extends Person {

        Farmer(String name, String phone) {
            super(name, phone);
        }

        @Override
        String getRole() {
            return "Farmer";
        }
    }

    // ================= OOP: ENCAPSULATION =================

    static class Crop {
        private String cropName;
        private String status;

        Crop(String cropName, String status) {
            this.cropName = cropName;
            this.status = status;
        }

        public String getCropName() {
            return cropName;
        }

        public String getStatus() {
            return status;
        }
    }

    // ================= DATABASE CONNECTION =================

    static Connection connect() throws SQLException {
        return DriverManager.getConnection(DB);
    }

    static void createDatabase() {
        String[] tables = {
            "CREATE TABLE IF NOT EXISTS farmers (" +
            "id INTEGER PRIMARY KEY AUTOINCREMENT," +
            "name TEXT NOT NULL, phone TEXT, village TEXT," +
            "land_area REAL)",

            "CREATE TABLE IF NOT EXISTS farms (" +
            "id INTEGER PRIMARY KEY AUTOINCREMENT," +
            "farmer_id INTEGER, location TEXT, soil_type TEXT," +
            "area REAL, crop_name TEXT)",

            "CREATE TABLE IF NOT EXISTS crops (" +
            "id INTEGER PRIMARY KEY AUTOINCREMENT," +
            "name TEXT, variety TEXT, sowing_date TEXT," +
            "harvest_date TEXT, status TEXT)",

            "CREATE TABLE IF NOT EXISTS tasks (" +
            "id INTEGER PRIMARY KEY AUTOINCREMENT," +
            "title TEXT, assigned_to TEXT, due_date TEXT," +
            "status TEXT DEFAULT 'Pending')",

            "CREATE TABLE IF NOT EXISTS irrigation (" +
            "id INTEGER PRIMARY KEY AUTOINCREMENT," +
            "field_name TEXT, method TEXT, irrigation_date TEXT," +
            "water_litres REAL, status TEXT)",

            "CREATE TABLE IF NOT EXISTS equipment (" +
            "id INTEGER PRIMARY KEY AUTOINCREMENT," +
            "name TEXT, category TEXT, condition TEXT," +
            "last_service TEXT)",

            "CREATE TABLE IF NOT EXISTS labor (" +
            "id INTEGER PRIMARY KEY AUTOINCREMENT," +
            "name TEXT, role TEXT, phone TEXT, daily_wage REAL)",

            "CREATE TABLE IF NOT EXISTS harvests (" +
            "id INTEGER PRIMARY KEY AUTOINCREMENT," +
            "crop_name TEXT, harvest_date TEXT," +
            "quantity_tons REAL, selling_price REAL, quality TEXT)",

            "CREATE TABLE IF NOT EXISTS sensor_readings (" +
            "id INTEGER PRIMARY KEY AUTOINCREMENT," +
            "field_name TEXT, ph REAL, temperature REAL," +
            "water_level REAL, recorded_at TEXT DEFAULT CURRENT_TIMESTAMP)"
        };

        try (Connection con = connect();
             Statement st = con.createStatement()) {
            for (String sql : tables) {
                st.execute(sql);
            }
            System.out.println("Database created successfully!");
        } catch (SQLException e) {
            System.err.println("Could not create the database. Check that the SQLite JDBC JAR is on the classpath.");
            e.printStackTrace();
        }
    }

    // ================= DATABASE OPERATIONS =================

    static final Map<String, List<String>> MODULES = new LinkedHashMap<>();

    static {
        MODULES.put("farmers",
            Arrays.asList("name", "phone", "village", "land_area"));
        MODULES.put("farms",
            Arrays.asList("farmer_id", "location", "soil_type", "area", "crop_name"));
        MODULES.put("crops",
            Arrays.asList("name", "variety", "sowing_date", "harvest_date", "status"));
        MODULES.put("tasks",
            Arrays.asList("title", "assigned_to", "due_date"));
        MODULES.put("irrigation",
            Arrays.asList("field_name", "method", "irrigation_date", "water_litres", "status"));
        MODULES.put("equipment",
            Arrays.asList("name", "category", "condition", "last_service"));
        MODULES.put("labor",
            Arrays.asList("name", "role", "phone", "daily_wage"));
        MODULES.put("harvests",
            Arrays.asList("crop_name", "harvest_date", "quantity_tons", "selling_price", "quality"));
        MODULES.put("sensor_readings",
            Arrays.asList("field_name", "ph", "temperature", "water_level"));
    }

    static List<Map<String, Object>> getRecords(String table) throws SQLException {
        if (!MODULES.containsKey(table)) {
            throw new IllegalArgumentException("Invalid module");
        }

        List<Map<String, Object>> records = new ArrayList<>();

        try (Connection con = connect();
             Statement st = con.createStatement();
             ResultSet rs = st.executeQuery(
                 "SELECT * FROM " + table + " ORDER BY id DESC")) {

            ResultSetMetaData md = rs.getMetaData();

            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                for (int i = 1; i <= md.getColumnCount(); i++) {
                    row.put(md.getColumnName(i), rs.getObject(i));
                }
                records.add(row);
            }
        }

        return records;
    }

    static void addRecord(String table, Map<String, String> data) throws SQLException {
        if (!MODULES.containsKey(table)) {
            throw new IllegalArgumentException("Invalid module");
        }

        List<String> fields = MODULES.get(table);
        List<String> requiredFields = Arrays.asList(
            "name", "title", "location", "field_name", "crop_name", "assigned_to"
        );

        for (String field : fields) {
            String value = data.getOrDefault(field, "").trim();
            if (requiredFields.contains(field) && value.isEmpty()) {
                throw new IllegalArgumentException(field + " is required");
            }
        }

        String columns = String.join(",", fields);
        String placeholders = String.join(",",
            Collections.nCopies(fields.size(), "?"));
        String sql = "INSERT INTO " + table + " (" + columns +
            ") VALUES (" + placeholders + ")";

        List<String> numericFields = Arrays.asList(
            "farmer_id", "area", "land_area", "water_litres",
            "daily_wage", "quantity_tons", "selling_price",
            "ph", "temperature", "water_level"
        );

        try (Connection con = connect();
             PreparedStatement ps = con.prepareStatement(sql)) {

            for (int i = 0; i < fields.size(); i++) {
                String field = fields.get(i);
                String value = data.getOrDefault(field, "").trim();

                if (value.isEmpty()) {
                    ps.setNull(i + 1, numericFields.contains(field)
                        ? Types.REAL : Types.VARCHAR);
                } else if (numericFields.contains(field)) {
                    try {
                        ps.setDouble(i + 1, Double.parseDouble(value));
                    } catch (NumberFormatException e) {
                        throw new IllegalArgumentException(
                            "Please enter a valid number for " + field
                        );
                    }
                } else {
                    ps.setString(i + 1, value);
                }
            }

            ps.executeUpdate();
        }
    }

    static void updateTask(int id, String action) throws SQLException {
        String sql;

        if ("complete".equals(action)) {
            sql = "UPDATE tasks SET status='Completed' WHERE id=?";
        } else if ("delete".equals(action)) {
            sql = "DELETE FROM tasks WHERE id=?";
        } else {
            throw new IllegalArgumentException("Invalid action");
        }

        try (Connection con = connect();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, id);
            int affected = ps.executeUpdate();
            if (affected == 0) {
                throw new IllegalArgumentException("Task not found");
            }
        }
    }

    // ================= DASHBOARD STATISTICS =================

    static Map<String, Object> getDashboard() throws SQLException {
        Map<String, Object> data = new LinkedHashMap<>();

        try (Connection con = connect();
             Statement st = con.createStatement()) {

            String[] tables = {"farmers", "farms", "crops", "tasks", "harvests"};

            for (String table : tables) {
                try (ResultSet rs = st.executeQuery(
                    "SELECT COUNT(*) FROM " + table)) {
                    rs.next();
                    data.put(table, rs.getInt(1));
                }
            }

            try (ResultSet rs = st.executeQuery(
                "SELECT COALESCE(SUM(quantity_tons),0) FROM harvests")) {
                rs.next();
                data.put("yield", rs.getDouble(1));
            }

            try (ResultSet rs = st.executeQuery(
                "SELECT COALESCE(AVG(ph),0), " +
                "COALESCE(AVG(temperature),0), " +
                "COALESCE(AVG(water_level),0) FROM sensor_readings")) {
                rs.next();
                data.put("ph", rs.getDouble(1));
                data.put("temperature", rs.getDouble(2));
                data.put("water", rs.getDouble(3));
            }
        }

        return data;
    }

    // ================= JSON CONVERSION =================

    static String json(Object obj) {
        if (obj == null) return "null";

        if (obj instanceof Number || obj instanceof Boolean) {
            return obj.toString();
        }

        if (obj instanceof Map<?, ?> map) {
            StringJoiner result = new StringJoiner(",", "{", "}");
            map.forEach((key, value) ->
                result.add(json(key.toString()) + ":" + json(value)));
            return result.toString();
        }

        if (obj instanceof Iterable<?> list) {
            StringJoiner result = new StringJoiner(",", "[", "]");
            for (Object item : list) {
                result.add(json(item));
            }
            return result.toString();
        }

        StringBuilder escaped = new StringBuilder("\"");
        String value = obj.toString();

        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            switch (ch) {
                case '"' -> escaped.append("\\\"");
                case '\\' -> escaped.append("\\\\");
                case '\b' -> escaped.append("\\b");
                case '\f' -> escaped.append("\\f");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                default -> {
                    if (ch < 0x20) {
                        escaped.append(String.format("\\u%04x", (int) ch));
                    } else {
                        escaped.append(ch);
                    }
                }
            }
        }

        return escaped.append('"').toString();
    }

    // ================= HTTP REQUEST HANDLING =================

    static Map<String, String> readForm(HttpExchange ex) throws IOException {
        String body = new String(
            ex.getRequestBody().readAllBytes(),
            StandardCharsets.UTF_8
        );

        Map<String, String> data = new HashMap<>();

        if (body.isEmpty()) return data;

        for (String pair : body.split("&")) {
            String[] parts = pair.split("=", 2);
            String key = URLDecoder.decode(parts[0], StandardCharsets.UTF_8);
            String value = parts.length == 2
                ? URLDecoder.decode(parts[1], StandardCharsets.UTF_8)
                : "";
            data.put(key, value);
        }

        return data;
    }

    static void send(HttpExchange ex, int status,
                     String response, String type) throws IOException {
        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);

        ex.getResponseHeaders().set(
            "Content-Type", type + "; charset=UTF-8"
        );
        ex.getResponseHeaders().set("Access-Control-Allow-Origin", "*");

        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = ex.getResponseBody()) {
            out.write(bytes);
        }
    }

    static void handleAPI(HttpExchange ex) throws IOException {
        try {
            String path = ex.getRequestURI().getPath();
            String apiPath = path.replaceFirst("^/api/?", "");
            String[] parts = apiPath.split("/");
            String module = parts[0];
            String method = ex.getRequestMethod();

            if ("dashboard".equals(module) && "GET".equals(method)) {
                send(ex, 200, json(getDashboard()), "application/json");
                return;
            }

            if ("tasks".equals(module) && parts.length == 2
                    && "POST".equals(method)) {
                Map<String, String> data = readForm(ex);
                updateTask(Integer.parseInt(parts[1]), data.get("action"));
                send(ex, 200, "{\"success\":true}", "application/json");
                return;
            }

            if ("GET".equals(method)) {
                send(ex, 200, json(getRecords(module)), "application/json");
            } else if ("POST".equals(method)) {
                addRecord(module, readForm(ex));
                send(ex, 201, "{\"success\":true}", "application/json");
            } else {
                send(ex, 405, "{\"error\":\"Method not allowed\"}",
                    "application/json");
            }

        } catch (IllegalArgumentException e) {
            send(ex, 400, json(Map.of("error", e.getMessage())),
                "application/json");
        } catch (SQLException e) {
            e.printStackTrace();
            send(ex, 500, "{\"error\":\"Database error\"}",
                "application/json");
        } catch (Exception e) {
            e.printStackTrace();
            send(ex, 500, "{\"error\":\"Server error\"}",
                "application/json");
        }
    }

    // ================= FRONTEND HTML =================

    static final String HTML = """
    <!DOCTYPE html>
    <html>
    <head>
        <title>AgriSense - Digital Farming Assistant</title>
        <meta name="viewport" content="width=device-width, initial-scale=1">
        <script src="https://cdn.jsdelivr.net/npm/chart.js"></script>
        <style>
        *{box-sizing:border-box}
        body{margin:0;font-family:Arial;background:#f1f5f2;color:#26372b}
        .sidebar{width:235px;background:#fff;height:100vh;position:fixed;padding:20px;overflow-y:auto}
        .brand{font-size:25px;color:#198b45;font-weight:bold;margin-bottom:30px}
        .sidebar a{display:block;padding:13px;color:#59665c;text-decoration:none;border-radius:7px;margin:5px 0}
        .sidebar a:hover,.sidebar a.active{background:#e5f5e9;color:#16833e}
        .main{margin-left:235px;padding:25px}
        header{background:white;padding:18px 25px;border-radius:10px;margin-bottom:22px;display:flex;justify-content:space-between}
        h1{font-size:25px}
        h2{font-size:19px}
        .cards{display:grid;grid-template-columns:repeat(4,1fr);gap:15px;margin-bottom:20px}
        .card,.panel{background:white;padding:20px;border-radius:12px;margin-bottom:18px;box-shadow:0 2px 8px #00000008}
        .card h2{font-size:28px;color:#168c46}
        .card p,.muted{color:#819087}
        .grid{display:grid;grid-template-columns:1fr 1fr;gap:15px}
        form{display:flex;flex-wrap:wrap;gap:10px;margin:15px 0}
        input,select{padding:11px;border:1px solid #dce5df;border-radius:6px;flex:1;min-width:130px}
        button{background:#168d46;color:white;padding:11px 16px;border:0;border-radius:6px;cursor:pointer}
        button:hover{background:#107538}
        table{width:100%;border-collapse:collapse;min-width:500px}
        th,td{text-align:left;padding:12px;border-bottom:1px solid #e9eee9}
        th{background:#f2f8f3}
        .tablebox{overflow-x:auto}
        .weather{background:#e8f6eb;padding:20px;border-radius:10px}
        .status{color:#168d46;font-weight:bold}
        .section{scroll-margin-top:20px}
        footer{text-align:center;color:#87958b;padding:20px}
        @media(max-width:900px){.sidebar{width:180px}.main{margin-left:180px}.cards{grid-template-columns:repeat(2,1fr)}}
        @media(max-width:600px){.sidebar{position:relative;width:100%;height:auto}.sidebar a{display:inline-block;padding:8px}.main{margin:0;padding:12px}.cards,.grid{grid-template-columns:1fr}header{font-size:12px}}
        </style>
    </head>
    <body>
    <div class="sidebar">
        <div class="brand">🌱 AgriSense</div>
        <a href="#dashboard">▦ Dashboard</a>
        <a href="#farmers">♙ Farmers</a>
        <a href="#farms">⌂ Farms</a>
        <a href="#crops">🌾 Crop Management</a>
        <a href="#sensor_readings">🌱 Soil & Water</a>
        <a href="#weather">☀ Weather</a>
        <a href="#irrigation">💧 Irrigation</a>
        <a href="#equipment">⚙ Equipment</a>
        <a href="#tasks">☷ Task Management</a>
        <a href="#labor">♟ Labor Management</a>
        <a href="#harvests">🌾 Harvest & Sales</a>
        <a href="#reports">📊 Reports</a>
    </div>

    <div class="main">
    <header>
        <b>AgriSense Digital Farming Assistant</b>
        <span>👤 Farm Administrator</span>
    </header>

    <section id="dashboard" class="section">
        <h1>Good Morning, Farmer! 🌱</h1>
        <p>Manage your farm operations in one place.</p>
        <div class="cards">
            <div class="card"><p>Total Farmers</p><h2 id="farmersCount">0</h2></div>
            <div class="card"><p>Total Farms</p><h2 id="farmsCount">0</h2></div>
            <div class="card"><p>Crop Records</p><h2 id="cropsCount">0</h2></div>
            <div class="card"><p>Harvest Yield</p><h2 id="yieldCount">0</h2><small>Tons</small></div>
        </div>

        <div class="grid">
            <div class="panel">
                <h2>Harvest Analytics</h2>
                <canvas id="chart"></canvas>
            </div>
            <div class="panel">
                <h2>Soil & Water Metrics</h2>
                <div class="weather">
                    <h3>Average Soil pH: <span id="ph">0</span></h3>
                    <h3>Temperature: <span id="temp">0</span> °C</h3>
                    <h3>Water Level: <span id="water">0</span> L</h3>
                </div>
            </div>
        </div>
    </section>

    <div id="modules"></div>

    <section id="weather" class="panel section">
        <h2>☀ Weather Information</h2>
        <p>Sample weather information. Connect a weather API for live forecasts.</p>
        <div class="cards">
            <div class="card">Temperature<h2>24°C</h2></div>
            <div class="card">Humidity<h2>60%</h2></div>
            <div class="card">Wind Speed<h2>12 km/h</h2></div>
            <div class="card">Rain Chance<h2>10%</h2></div>
        </div>
    </section>

    <section id="reports" class="panel section">
        <h2>📊 Reports & Analytics</h2>
        <p>Export your farming records as CSV files.</p>
        <button onclick="exportData()">Export Reports</button>
    </section>

    <footer>AgriSense © 2026 | Digital Farming Assistant</footer>
    </div>

    <script>
    const config = {
      farmers: {
        title:"Farmer Management",
        fields:[["name","Farmer Name","text"],["phone","Phone","text"],["village","Village","text"],["land_area","Land Area (acres)","number"]],
        columns:["id","name","phone","village","land_area"]
      },
      farms: {
        title:"Farm Management",
        fields:[["farmer_id","Farmer ID","number"],["location","Location","text"],["soil_type","Soil Type","text"],["area","Area (acres)","number"],["crop_name","Crop Name","text"]],
        columns:["id","farmer_id","location","soil_type","area","crop_name"]
      },
      crops: {
        title:"Crop Management",
        fields:[["name","Crop Name","text"],["variety","Variety","text"],["sowing_date","Sowing Date","date"],["harvest_date","Harvest Date","date"],["status","Status","text"]],
        columns:["id","name","variety","sowing_date","harvest_date","status"]
      },
      sensor_readings: {
        title:"Soil & Water Monitoring",
        fields:[["field_name","Field Name","text"],["ph","Soil pH","number"],["temperature","Temperature","number"],["water_level","Water Level (L)","number"]],
        columns:["id","field_name","ph","temperature","water_level","recorded_at"]
      },
      irrigation: {
        title:"Irrigation Management",
        fields:[["field_name","Field Name","text"],["method","Method","text"],["irrigation_date","Date","date"],["water_litres","Water Litres","number"],["status","Status","text"]],
        columns:["id","field_name","method","irrigation_date","water_litres","status"]
      },
      equipment: {
        title:"Equipment Management",
        fields:[["name","Equipment Name","text"],["category","Category","text"],["condition","Condition","text"],["last_service","Last Service","date"]],
        columns:["id","name","category","condition","last_service"]
      },
      tasks: {
        title:"Task Management",
        fields:[["title","Task Name","text"],["assigned_to","Assigned To","text"],["due_date","Due Date","date"]],
        columns:["id","title","assigned_to","due_date","status"]
      },
      labor: {
        title:"Labor Management",
        fields:[["name","Worker Name","text"],["role","Role","text"],["phone","Phone","text"],["daily_wage","Daily Wage","number"]],
        columns:["id","name","role","phone","daily_wage"]
      },
      harvests: {
        title:"Harvest & Sales",
        fields:[["crop_name","Crop Name","text"],["harvest_date","Harvest Date","date"],["quantity_tons","Quantity (tons)","number"],["selling_price","Selling Price","number"],["quality","Quality","text"]],
        columns:["id","crop_name","harvest_date","quantity_tons","selling_price","quality"]
      }
    };

    let chart;

    function makeModules() {
      const root = document.getElementById("modules");

      for (const [module, c] of Object.entries(config)) {
        const section = document.createElement("section");
        section.id = module;
        section.className = "panel section";

        const fields = c.fields.map(f =>
          `<input name="${f[0]}" type="${f[2]}" placeholder="${f[1]}" required>`
        ).join("");

        let headers = c.columns.map(x =>
          `<th>${x.replaceAll("_"," ")}</th>`
        ).join("");

        if (module === "tasks") headers += "<th>Actions</th>";

        section.innerHTML = `
          <h2>${c.title}</h2>
          <form id="form-${module}">
            ${fields}
            <button type="submit">Add Record</button>
          </form>
          <div class="tablebox">
            <table>
              <thead><tr>${headers}</tr></thead>
              <tbody id="table-${module}"></tbody>
            </table>
          </div>
        `;

        root.appendChild(section);

        document.getElementById("form-" + module)
          .addEventListener("submit", e => saveRecord(e, module));
      }

      Object.keys(config).forEach(loadRecords);
    }

    async function api(url, options = {}) {
      const response = await fetch(url, options);
      const result = await response.json();

      if (!response.ok) {
        throw new Error(result.error || "Request failed");
      }

      return result;
    }

    function escapeHTML(value) {
      return String(value ?? "").replace(/[&<>"']/g, c => ({
        "&":"&amp;", "<":"&lt;", ">":"&gt;",
        '"':"&quot;", "'":"&#39;"
      })[c]);
    }

    async function saveRecord(event, module) {
      event.preventDefault();
      const form = event.target;

      try {
        await api("/api/" + module, {
          method:"POST",
          headers:{"Content-Type":"application/x-www-form-urlencoded"},
          body:new URLSearchParams(new FormData(form))
        });

        form.reset();
        await loadRecords(module);
        await loadDashboard();
        alert("Record added successfully!");
      } catch (error) {
        alert(error.message);
      }
    }

    async function loadRecords(module) {
      try {
        const records = await api("/api/" + module);
        const columns = config[module].columns;
        const tbody = document.getElementById("table-" + module);

        tbody.innerHTML = records.map(row => {
          let cells = columns.map(key =>
            `<td>${escapeHTML(row[key])}</td>`
          ).join("");

          if (module === "tasks") {
            cells += `<td>
              ${row.status !== "Completed"
                ? `<button onclick="taskAction(${Number(row.id)},'complete')">Complete</button>`
                : ""}
              <button onclick="taskAction(${Number(row.id)},'delete')">Delete</button>
            </td>`;
          }

          return `<tr>${cells}</tr>`;
        }).join("");
      } catch (error) {
        console.error("Could not load " + module + ":", error);
      }
    }

    async function taskAction(id, action) {
      try {
        await api("/api/tasks/" + id, {
          method:"POST",
          headers:{"Content-Type":"application/x-www-form-urlencoded"},
          body:new URLSearchParams({action})
        });

        await loadRecords("tasks");
        await loadDashboard();
      } catch (error) {
        alert(error.message);
      }
    }

    async function loadDashboard() {
      try {
        const data = await api("/api/dashboard");

        document.getElementById("farmersCount").textContent = data.farmers;
        document.getElementById("farmsCount").textContent = data.farms;
        document.getElementById("cropsCount").textContent = data.crops;
        document.getElementById("yieldCount").textContent = data.yield;
        document.getElementById("ph").textContent = Number(data.ph).toFixed(1);
        document.getElementById("temp").textContent = Number(data.temperature).toFixed(1);
        document.getElementById("water").textContent = Number(data.water).toFixed(1);

        const harvests = await api("/api/harvests");

        if (chart) chart.destroy();

        chart = new Chart(document.getElementById("chart"), {
          type:"bar",
          data:{
            labels:harvests.map(x => x.crop_name),
            datasets:[{
              label:"Harvest Quantity (tons)",
              data:harvests.map(x => x.quantity_tons),
              backgroundColor:"#28a45d"
            }]
          },
          options:{responsive:true}
        });
      } catch (error) {
        console.error("Could not load dashboard:", error);
      }
    }

    async function exportData() {
      try {
        for (const module of Object.keys(config)) {
          const records = await api("/api/" + module);
          if (!records.length) continue;

          const columns = config[module].columns;
          const csv = [
            columns.join(","),
            ...records.map(row =>
              columns.map(key =>
                '"' + String(row[key] ?? "").replaceAll('"', '""') + '"'
              ).join(",")
            )
          ].join("\\n");

          const blobUrl = URL.createObjectURL(
            new Blob([csv], {type:"text/csv;charset=utf-8;"})
          );

          const link = document.createElement("a");
          link.href = blobUrl;
          link.download = module + ".csv";
          document.body.appendChild(link);
          link.click();
          link.remove();
          URL.revokeObjectURL(blobUrl);
        }

        alert("Reports exported successfully!");
      } catch (error) {
        alert("Export failed: " + error.message);
      }
    }

    makeModules();
    loadDashboard();
    </script>
    </body>
    </html>
    """;

    // ================= START SERVER =================

    public static void main(String[] args) throws Exception {
        createDatabase();

        HttpServer server = HttpServer.create(
            new InetSocketAddress(8080), 0
        );

        server.createContext("/api/", AgriSense::handleAPI);

        server.createContext("/", exchange -> {
            byte[] response = HTML.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set(
                "Content-Type", "text/html; charset=UTF-8"
            );
            exchange.sendResponseHeaders(200, response.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(response);
            }
        });

        server.setExecutor(
            java.util.concurrent.Executors.newCachedThreadPool()
        );

        server.start();

        System.out.println("----------------------------------");
        System.out.println("AgriSense Website Started!");
        System.out.println("Open: http://localhost:8080");
        System.out.println("----------------------------------");
    }
}
