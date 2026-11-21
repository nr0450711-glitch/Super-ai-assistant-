require("dotenv").config();

const crypto = require("crypto");
const express = require("express");
const cors = require("cors");
const helmet = require("helmet");
const rateLimit = require("express-rate-limit");
const admin = require("firebase-admin");

const app = express();
app.set("trust proxy", 1);
app.use(helmet());
app.use(express.json({ limit: "64kb" }));

const allowedOrigins = (process.env.ALLOWED_ORIGINS || "")
  .split(",")
  .map((s) => s.trim())
  .filter(Boolean);

app.use(
  cors({
    origin(origin, callback) {
      // Allow native mobile / curl requests with no Origin header, or explicit allowlist
      if (!origin || allowedOrigins.includes(origin)) {
        return callback(null, true);
      }
      return callback(new Error("Origin not allowed by CORS"));
    },
    methods: ["GET", "POST"],
    allowedHeaders: ["Content-Type", "Authorization", "X-Jarvis-Token"],
  })
);

const limiter = rateLimit({
  windowMs: 60 * 1000,
  max: 30,
  standardHeaders: true,
  legacyHeaders: false,
  message: { error: "Rate limit exceeded. Please retry in a moment." },
});
app.use("/api/", limiter);

let firebaseReady = false;
if (process.env.GOOGLE_APPLICATION_CREDENTIALS || process.env.FIREBASE_CONFIG) {
  try {
    admin.initializeApp({
      credential: admin.credential.applicationDefault(),
    });
    firebaseReady = true;
  } catch (err) {
    console.warn("Firebase Admin initialization skipped:", err.message);
  }
}

function constantTimeEquals(a, b) {
  if (typeof a !== "string" || typeof b !== "string") return false;
  const bufA = Buffer.from(a, "utf8");
  const bufB = Buffer.from(b, "utf8");
  if (bufA.length !== bufB.length) return false;
  return crypto.timingSafeEqual(bufA, bufB);
}

async function authenticateRequest(req, res, next) {
  const sharedToken = (process.env.JARVIS_SHARED_TOKEN || "").trim();
  const authHeader = req.headers.authorization || "";
  const bearerToken = authHeader.startsWith("Bearer ") ? authHeader.slice(7).trim() : "";
  const headerToken = typeof req.headers["x-jarvis-token"] === "string" ? req.headers["x-jarvis-token"].trim() : "";

  if (!firebaseReady && !sharedToken) {
    return res.status(503).json({
      error: "Server authentication is not configured. Set JARVIS_SHARED_TOKEN or GOOGLE_APPLICATION_CREDENTIALS.",
    });
  }

  if (sharedToken && (constantTimeEquals(bearerToken, sharedToken) || constantTimeEquals(headerToken, sharedToken))) {
    req.jarvisUser = { uid: "shared-token-client", authMode: "shared_token" };
    return next();
  }

  if (firebaseReady && bearerToken) {
    try {
      const decoded = await admin.auth().verifyIdToken(bearerToken);
      req.jarvisUser = { uid: decoded.uid, email: decoded.email || null, authMode: "firebase" };
      return next();
    } catch (err) {
      return res.status(401).json({ error: "Invalid or expired Firebase Auth ID token." });
    }
  }

  return res.status(401).json({ error: "Missing or invalid authorization credentials." });
}

app.get("/health", (_req, res) => {
  res.json({
    ok: true,
    service: "jarvis-vip-backend",
    openAiConfigured: Boolean(process.env.OPENAI_API_KEY),
    firebaseAuthEnabled: firebaseReady,
    sharedTokenEnabled: Boolean(process.env.JARVIS_SHARED_TOKEN),
  });
});

app.post("/api/jarvis/chat", authenticateRequest, async (req, res) => {
  const openAiKey = (process.env.OPENAI_API_KEY || "").trim();
  if (!openAiKey) {
    return res.status(503).json({ error: "OPENAI_API_KEY is not configured on the backend." });
  }

  const { message, history = [], language = "auto", deviceContext = {} } = req.body || {};
  if (typeof message !== "string" || !message.trim() || message.length > 4000) {
    return res.status(400).json({ error: "Invalid 'message'. Must be a non-empty string up to 4000 chars." });
  }

  const sanitizedLanguage = ["en-IN", "hi-IN", "auto"].includes(language) ? language : "auto";
  const safeHistory = Array.isArray(history)
    ? history
        .slice(-10)
        .filter(
          (item) =>
            item &&
            (item.role === "user" || item.role === "assistant") &&
            typeof item.content === "string" &&
            item.content.length <= 2000
        )
        .map((item) => ({ role: item.role, content: item.content }))
    : [];

  const systemPrompt = [
    "You are JARVIS VIP, a refined, capable, bilingual (Hindi & English) female-voiced personal AI assistant on Android.",
    "Respond concisely, warmly, and naturally. Match the user's language (Hindi/Hinglish/English) or preferred locale: " +
      sanitizedLanguage +
      ".",
    "Never invent device actions you did not perform; if the user wants a call, SMS, WhatsApp message, alarm, timer, calendar event, app launch, or Facebook post, provide a short helpful reply and include a suggested action description.",
    `Device telemetry snapshot: battery=${Number(deviceContext.batteryPct) || 0}%, charging=${Boolean(
      deviceContext.isCharging
    )}, ramFreeMb=${Number(deviceContext.ramFreeMb) || 0}.`,
  ].join("\n");

  const model = (process.env.OPENAI_MODEL || "gpt-4o-mini").trim();

  try {
    const upstream = await fetch("https://api.openai.com/v1/chat/completions", {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${openAiKey}`,
      },
      body: JSON.stringify({
        model,
        temperature: 0.6,
        max_tokens: 500,
        messages: [{ role: "system", content: systemPrompt }, ...safeHistory, { role: "user", content: message.trim() }],
      }),
    });

    const data = await upstream.json();
    if (!upstream.ok) {
      return res.status(upstream.status).json({
        error: data?.error?.message || "OpenAI upstream request failed.",
      });
    }

    const reply = data?.choices?.[0]?.message?.content?.trim() || "I am ready to assist.";
    return res.json({
      reply,
      model,
      language: sanitizedLanguage,
      uid: req.jarvisUser.uid,
    });
  } catch (err) {
    console.error("OpenAI proxy error:", err.message);
    return res.status(502).json({ error: "Failed to reach OpenAI upstream service." });
  }
});

const port = Number(process.env.PORT) || 8080;
app.listen(port, () => {
  console.log(`Jarvis VIP secure backend listening on port ${port}`);
});
