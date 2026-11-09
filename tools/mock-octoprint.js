// Minimal OctoPrint REST mock for on-device verification of Readout.
// Not part of the app; serves the exact payload shapes the parser expects.
const http = require("http");

const now = () => Math.floor(Date.now() / 1000);
const startedAt = Date.now();
const finishAfter = Number(process.env.MOCK_FINISH_AFTER_MS || 0);
const finished = () => finishAfter > 0 && Date.now() - startedAt > finishAfter;

const printer = {
  temperature: {
    tool0: { actual: 209.6, target: 210.0, offset: 0 },
    bed: { actual: 59.4, target: 60.0, offset: 0 },
  },
  sd: { ready: true },
  connection: { current: { state: "Operational", port: "/dev/ttyACM0" } },
  state: {
    text: "Printing",
    flags: {
      operational: true, paused: false, printing: true, cancelling: false,
      pausing: false, sdReady: true, error: false, ready: false, finishing: false,
      resuming: false, closedOrError: false, starting: false,
    },
  },
};

const job = {
  job: {
    file: {
      name: "benchy_hull_0.2mm_PLA.gcode",
      origin: "local",
      path: "benchy_hull_0.2mm_PLA.gcode",
      size: 4821337,
      date: now() - 5400,
    },
    estimatedPrintTime: 4980,
    filament: { tool0: { length: 4210.5, volume: 10.1 } },
  },
  progress: {
    completion: 41.7,
    filepos: 2010000,
    printTime: 1720,
    printTimeLeft: 2410,
    printTimeLeftOrigin: "average",
  },
  state: "Printing",
};

const files = {
  files: [
    { name: "benchy_hull_0.2mm_PLA.gcode", path: "benchy_hull_0.2mm_PLA.gcode", origin: "local", size: 4821337, date: now() - 5400, type: "machinecode" },
    { name: "calibration_cube.gcode", path: "calibration_cube.gcode", origin: "local", size: 812345, date: now() - 86400 * 3, type: "machinecode" },
    { name: "cable_clip_x4.gcode", path: "cable_clip_x4.gcode", origin: "local", size: 120334, date: now() - 86400 * 9, type: "machinecode" },
    { name: "archive", path: "archive", origin: "local", type: "folder" },
  ],
  free: 12_000_000_000,
  total: 32_000_000_000,
};

const timelapses = [
  { name: "benchy_timelapse.mp4", size: 27917, date: now() - 3600 },
  { name: "calibration_cube_timelapse.mp4", size: 27917, date: now() - 86400 * 2 },
];

let psuOn = false;
let appKeyRequestedAt = 0;
let wledOn = false;
const relays = [
  { id: "0", name: "Light", active: false },
  { id: "1", name: "Fan", active: true },
];

const server = http.createServer((req, res) => {
  const url = req.url.split("?")[0];
  const send = (body, code = 200) => {
    res.writeHead(code, { "Content-Type": "application/json" });
    res.end(JSON.stringify(body));
  };
  console.log(req.method, req.url);

  const isAppKeys = url.startsWith("/plugin/appkeys");
  if (!isAppKeys && req.headers["x-api-key"] !== "verify-only") {
    // Accept any key except empty so the flow behaves like a permissive instance.
    if (!req.headers["x-api-key"]) return send({ error: "Unauthorized" }, 401);
  }

  if (url === "/api/version") return send({ server: "1.10.2", api: "0.1", text: "OctoPrint 1.10.2" });
  if (url === "/api/printer") {
    if (finished()) {
      return send({ temperature: printer.temperature, sd: { ready: true },
        state: { text: "Operational", flags: { operational: true, printing: false, ready: true } } });
    }
    return send(printer);
  }
  if (url === "/api/job") {
    if (finished()) {
      return send({ job: job.job, progress: { completion: 100, printTime: 4980, printTimeLeft: 0, printTimeLeftOrigin: "average" }, state: "Operational" });
    }
    return send(job);
  }
  if (url === "/api/files") return send(files);
  if (url === "/api/connection") return send({ current: { state: "Operational" } });
  if (url.startsWith("/webcam")) {
    const png = require("path").join(__dirname, "camera-test.png");
    if (require("fs").existsSync(png)) {
      res.writeHead(200, { "Content-Type": "image/png" });
      return res.end(require("fs").readFileSync(png));
    }
    res.writeHead(404, { "Content-Type": "text/plain" });
    return res.end("no camera in mock");
  }
  if (url === "/api/plugin/wled") {
    if (req.method === "GET") {
      return send({ lights_on: wledOn, status: "started" });
    }
    let body = "";
    req.on("data", (chunk) => { body += chunk.toString("utf8"); });
    req.on("end", () => {
      const command = (() => { try { return JSON.parse(body).command; } catch { return ""; } })();
      if (command === "lights_on") wledOn = true;
      if (command === "lights_off") wledOn = false;
      console.log("wled", command, "->", wledOn);
      send({ lights_on: wledOn });
    });
    return;
  }
  if (url === "/api/plugin/octorelay") {
    if (req.method === "GET") {
      const payload = {};
      relays.forEach((relay) => { payload[relay.id] = relay; });
      return send(payload);
    }
    let body = "";
    req.on("data", (chunk) => { body += chunk.toString("utf8"); });
    req.on("end", () => {
      const command = (() => { try { return JSON.parse(body); } catch { return {}; } })();
      const relay = relays.find((item) => item.id === String(command.id));
      if (relay && command.command === "update") relay.active = Boolean(command.active);
      console.log("relay", JSON.stringify(command), "->", relay ? relay.active : "unknown");
      send({});
    });
    return;
  }
  if (url === "/plugin/appkeys/probe") {
    res.writeHead(204);
    return res.end();
  }
  if (req.method === "POST" && url === "/plugin/appkeys/request") {
    let body = "";
    req.on("data", (chunk) => { body += chunk.toString("utf8"); });
    req.on("end", () => {
      appKeyRequestedAt = Date.now();
      console.log("appkeys requested", body);
      res.writeHead(201, { "Content-Type": "application/json" });
      res.end(JSON.stringify({ user_token: "tok-demo" }));
    });
    return;
  }
  if (req.method === "POST" && url.startsWith("/plugin/appkeys/request/")) {
    const granted = appKeyRequestedAt > 0 && Date.now() - appKeyRequestedAt > 4000;
    if (!granted) {
      res.writeHead(403, { "Content-Type": "application/json" });
      return res.end(JSON.stringify({ error: "pending" }));
    }
    console.log("appkeys granted");
    res.writeHead(200, { "Content-Type": "application/json" });
    return res.end(JSON.stringify({ api_key: "app-key-demo" }));
  }
  if (url === "/api/plugin/psucontrol") {
    if (req.method === "GET") return send({ isPSUOn: psuOn });
    let body = "";
    req.on("data", (chunk) => { body += chunk.toString("utf8"); });
    req.on("end", () => {
      const command = (() => { try { return JSON.parse(body).command; } catch { return ""; } })();
      if (command === "turnPSUOn") psuOn = true;
      if (command === "turnPSUOff") psuOn = false;
      if (command === "togglePSU") psuOn = !psuOn;
      console.log("psu", command, "->", psuOn);
      send({ isPSUOn: psuOn });
    });
    return;
  }
  if (url === "/api/timelapse") return send({ files: timelapses });
  if (url.startsWith("/download/timelapse/")) {
    const path = require("path").join(__dirname, "timelapse-test.mp4");
    if (!require("fs").existsSync(path)) {
      res.writeHead(404, { "Content-Type": "text/plain" });
      return res.end("no video");
    }
    const buffer = require("fs").readFileSync(path);
    const range = req.headers.range;
    if (range) {
      const match = /bytes=(\d+)-(\d*)/.exec(range);
      const start = Number(match[1]);
      const end = match[2] ? Number(match[2]) : buffer.length - 1;
      const chunk = buffer.subarray(start, end + 1);
      res.writeHead(206, {
        "Content-Type": "video/mp4",
        "Accept-Ranges": "bytes",
        "Content-Range": `bytes ${start}-${end}/${buffer.length}`,
        "Content-Length": chunk.length,
      });
      return res.end(chunk);
    }
    res.writeHead(200, {
      "Content-Type": "video/mp4",
      "Accept-Ranges": "bytes",
      "Content-Length": buffer.length,
    });
    return res.end(buffer);
  }

  // Commands: accept and acknowledge. DELETE removes the entry from the in-memory list.
  if (req.method === "DELETE") {
    const prefix = "/api/files/local/";
    if (url.startsWith(prefix)) {
      const path = decodeURIComponent(url.slice(prefix.length));
      files.files = files.files.filter((f) => f.path !== path);
    }
    const timelapsePrefix = "/api/timelapse/";
    if (url.startsWith(timelapsePrefix)) {
      const name = decodeURIComponent(url.slice(timelapsePrefix.length));
      const index = timelapses.findIndex((item) => item.name === name);
      if (index >= 0) timelapses.splice(index, 1);
      console.log("deleted timelapse", name);
    }
    return send({});
  }
  if (req.method === "POST" && url === "/api/files/local") {
    let body = "";
    req.on("data", (chunk) => { body += chunk.toString("latin1"); });
    req.on("end", () => {
      const match = body.match(/filename="([^"]+)"/);
      if (match) {
        const name = match[1];
        files.files.unshift({ name, path: name, origin: "local", size: body.length, date: now(), type: "machinecode" });
        console.log("uploaded", name, body.length, "bytes");
      }
      send({ done: true, files: { local: { name: match ? match[1] : "upload.gcode" } } });
    });
    return;
  }
  if (req.method === "POST") return send({});
  return send({ error: "not found" }, 404);
});

const port = Number(process.env.PORT || 8008);
server.listen(port, "127.0.0.1", () => console.log("mock OctoPrint on http://127.0.0.1:" + port));
