require('dotenv').config();

const express = require('express');
const http = require('http');
const WebSocket = require('ws');
const { Server } = require('socket.io');
const cron = require('node-cron');
const axios = require('axios');
const path = require('path');

const { connectDB } = require('./src/db');
const receiver = require('./src/receiver');

const soundAnalysisController = require('./src/controllers/soundAnalysisController');
const videoController = require('./src/controllers/videoController');
const temhuController = require('./src/controllers/TemhuController');
const sleepController = require('./src/controllers/sleepController');
const aiController = require('./src/controllers/aiController');

// =========================================================
// IoT 기기 주인 (센서/카메라 데이터를 이 계정으로 저장)
// =========================================================
const IOT_USER_ID = process.env.IOT_USER_ID || null;

if (IOT_USER_ID) {
  soundAnalysisController.setUserId(IOT_USER_ID);
  temhuController.setUserId(IOT_USER_ID);
  videoController.setUserId(IOT_USER_ID);
} else {
  console.warn('⚠️ IOT_USER_ID 미설정: 앱이 접속하기 전까지 센서 데이터가 저장되지 않습니다.');
}

const app = express();
const server = http.createServer(app);

// =========================================================
// WebSocket (IoT device)
// =========================================================
const wss = new WebSocket.Server({ server });

app.set('wss', wss);

// =========================================================
// Socket.IO (Android realtime)
// =========================================================
const io = new Server(server, {
  cors: {
    origin: process.env.CORS_ORIGIN || '*'
  }
});

app.set('io', io);

io.on('connection', (socket) => {

  console.log('📱 안드로이드 연결됨:', socket.id);

  socket.on('register', (userId) => {

    socket.join(userId);

    console.log(`✅ [${userId}] 소켓 등록됨`);

    // 기기 주인이 .env로 고정돼 있으면 앱 접속으로 바꾸지 않음
    if (!IOT_USER_ID) {
      soundAnalysisController.setUserId(userId);
      temhuController.setUserId(userId);
      videoController.setUserId(userId);
    }
  });

  socket.on('disconnect', () => {
    console.log('📱 안드로이드 연결 끊김:', socket.id);
  });
});

// =========================================================
// Middleware
// =========================================================
const PORT = process.env.PORT || 3001;
const HOST = process.env.HOST || '0.0.0.0';
const REQUEST_LIMIT = process.env.REQUEST_LIMIT || '50mb';
const BASE_URL = process.env.INTERNAL_API_URL || `http://127.0.0.1:${PORT}`;

app.use(express.json({ limit: REQUEST_LIMIT }));
app.use(express.urlencoded({ limit: REQUEST_LIMIT, extended: true }));

app.use((req, res, next) => {
  console.log("==============================================");
  console.log("📥 요청:", req.method, req.originalUrl);
  next();
});

// =========================================================
// ROUTES
// =========================================================

// HEALTH CHECK
app.get('/health', (req, res) => {
  res.status(200).json({ status: 'healthy' });
});

// AUTH
const authRouter = require('./src/routes/authRoutes');
app.use('/auth', authRouter);

// DATA
const policyRouter = require('./src/routes/policyRoutes');
const vaccineRouter = require('./src/routes/vaccineRoutes');
app.use('/api/policies', policyRouter);
app.use('/api/vaccine', vaccineRouter);

// SENSOR
const temhuRoutes = require('./src/routes/temhuRoutes');
const sleepRoutes = require('./src/routes/sleepRoutes');
app.use('/api/temhu', temhuRoutes);
app.use('/api/sleep', sleepRoutes);

// SMARTTHINGS
const smartThingsRouter = require('./src/routes/smartThingsRoutes');
app.use('/api/smartthings', smartThingsRouter);

// AI
const aiRouter = require('./src/routes/airouter');
app.use('/api/ai', aiRouter);

// OTHER
const videoRoutes = require('./src/routes/videoRoutes');
const soundAnalysisRoutes = require('./src/routes/Soundanalysisroutes');

app.use('/api/video', videoRoutes);
app.use('/api/sound-analysis', soundAnalysisRoutes);

// =========================================================
// STREAM
// =========================================================
const HLS_DIR =
  process.env.HLS_DIR ||
  path.resolve(__dirname, 'public/stream');

app.use(
  '/stream',
  (req, res, next) => {
    if (req.path.endsWith('.m3u8')) {
      res.setHeader('Content-Type', 'application/vnd.apple.mpegurl');
      res.setHeader('Cache-Control', 'no-cache, no-store');
    } else if (req.path.endsWith('.ts')) {
      res.setHeader('Content-Type', 'video/mp2t');
    }
    next();
  },
  express.static(HLS_DIR)
);

// =========================================================
// CRON JOBS
// =========================================================
const CRON_DAILY_REPORT =
  process.env.CRON_DAILY_REPORT || '0 8 * * *';

cron.schedule(CRON_DAILY_REPORT, () => {
  console.log('🌅 [AI] 일일 리포트 생성');
  sleepController.generateDailyComprehensiveReport();
}, { timezone: 'Asia/Seoul' });

const { startAutoControlScheduler } = require('./src/controllers/smartThingsController');


// =========================================================
// SERVER START
// =========================================================
server.listen(PORT, HOST, async () => {

  console.log('==============================================');

  try {

    await connectDB();
    console.log(`✅ MongoDB 연결 성공`);

    soundAnalysisController.setApp(app);
    temhuController.setApp(app);
    videoController.setApp(app);

    receiver.init(wss);
    console.log(`✅ WebSocket(Receiver) 초기화 성공`);

    axios.post(`${BASE_URL}/api/video/start`).catch(() => {});
    axios.post(`${BASE_URL}/api/sound-analysis/start`).catch(() => {});
   // 오늘 리포트가 없을 때만 생성 (8시에 서버가 꺼져 있었던 경우 보충)
    await aiController.generateDailyReport();

    console.log(`🚀 서버 가동 중: http://${HOST}:${PORT}`);
    console.log(`🔗 내부 BASE_URL: ${BASE_URL}`);

  } catch (err) {
    console.error('❌ 서버 초기화 중 오류 발생:', err.message);
    process.exit(1);
  }

  console.log('==============================================');
});