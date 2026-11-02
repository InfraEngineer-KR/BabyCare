/**
 * videoController.js
 * 기능: 
 * 1. IoT 카메라 영상 수신 (onFrame)
 * 2. udp 관련 제거 >> 스트리밍 방식 변경
 * 3. 30초마다 Flask 서버로 10프레임 묶어서 분석 요청 (이전 요청 완료 후 타이머 시작)
 * 3_5. test > 열화상를 30초마다 수신, 분석 요청
 * 4. 분석 결과 Android 클라이언트(WebSocket) 브로드캐스트
 * 5. 아기 체온이 정상 체온 대비 크게 오르거나 내리면 경고
 * 6. 정상 체온 보정 (지금 상태 / 기간 선택, DB 저장)
 */

const axios = require('axios');
const sharp = require('sharp'); // 이미지 리사이징 및 압축용
const { VideoAnalysis } = require('../models/videoanalysis'); // DB 저장용
const BodyTempCalibration = require('../models/BodyTempCalibration');

const { publishAlert } = require('./TemhuController');

// 아기 체온 경고: 정상 체온 ±BODY_TEMP_DELTA 를 벗어나면 알림 (.env에서 바꿀 수 있음)
const BODY_TEMP_BASE = parseFloat(process.env.BODY_TEMP_BASE) || 38.0;   // 보정 전 기본 정상 체온
const BODY_TEMP_DELTA = parseFloat(process.env.BODY_TEMP_DELTA) || 1.5;
const BODY_ALERT_COOLDOWN_MS = (parseFloat(process.env.BODY_ALERT_COOLDOWN_MIN) || 1) * 60 * 1000;
const BODY_TEMP_CONSECUTIVE = parseInt(process.env.BODY_TEMP_CONSECUTIVE) || 1;   // 몇 번 연속 벗어나야 경고할지

// 정상 체온 보정 설정 (확인 후 여기서 직접 바꿔서 사용)
const CALIBRATION_WINDOW_MS = 5 * 60 * 1000;   // '지금 상태로 보정'에 쓸 기간 (5분)
const CALIBRATION_DAYS = 7;                    // '기간으로 보정' 목록에 보여줄 최근 일수
const CALIBRATION_MIN_SAMPLES = 10;            // 최소 몇 개 측정돼야 보정할지
const CALIBRATION_TRIM_RATIO = 0.1;            // 날짜별로 위아래에서 잘라낼 비율

class VideoController {
    constructor() {
        this.currentFrame = null;
        this.frameTimestamp = null;
        this.isAnalyzing = false;      // ✅ 요청 중복 실행 방지 플래그
        this.androidClients = new Set(); // WebSocket 클라이언트 관리
        this.flaskServerUrl = process.env.FLASK_SERVER_URL || 'http://127.0.0.1:5000';
        this.analysisResults = [];
        this.frameBuffer = []; // 분석용 10장 버퍼
        this.latestThermal = null;
        this.isRunning = false;

        this.baseTemp = BODY_TEMP_BASE;                 // 현재 정상 체온 (보정되면 DB 값으로 바뀜)
        this.bodyAlertCount = { RISE: 0, DROP: 0 };     // 연속 이상 횟수
        this.lastBodyAlertAt = { RISE: 0, DROP: 0 };    // 마지막 경고 시각
    }

  // ✅ server.js에서 app 주입 (io 접근용)
  setApp(app) {
    this.app = app;
  }

  // ✅ receiver.init()에서 userId 주입
  setUserId(userId) {
    this.userId = userId;
    console.log(`[videoController] userId 설정됨: ${userId}`);
    this.loadBaseTemp();
  }

    /**
     * receiver.js에서 8888포트로 들어온 영상을 프레임 단위로 받음
     * @param {Buffer} videoFrame - 1280 * 720 * 3 크기의 raw RGB 데이터
     */
    onFrame(videoFrame) {
        this.currentFrame = videoFrame;
        this.frameTimestamp = Date.now();

        // 1. 분석용 버퍼링 (최근 10장 유지)
        this.frameBuffer.push(videoFrame);
        if (this.frameBuffer.length > 1) {
            this.frameBuffer.shift();
        }

    }

    onThermal(thermalData) {
        // 열화상을 받고
        this.latestThermal = thermalData;
        
        // start 요청이 있을 때만 분석 트리거
        if (this.isRunning) {
            this.requestFlaskAnalysis();
        }
    }


    /**
     * 분석 시작 명령 (라우터에서 호출)
     */
    async startAnalysis(req, res) {
        try {
            this.isRunning = true;
            res.json({ success: true, message: 'Flask 비디오 분석을 시작합니다.' });
        } catch (error) {
            res.status(500).json({ success: false, error: error.message });
        }
    }

    /**
     * 분석 중지 명령
     */
    async stopAnalysis(req, res) {
        try {
            this.isRunning = false;
            res.json({ success: true, message: '분석을 중지했습니다.' });
        } catch (error) {
            res.status(500).json({ success: false, error: error.message });
        }
    }


    /**
     * Flask 서버로 10장의 프레임을 묶어서 분석 요청
     */
    async requestFlaskAnalysis() {
        // ✅ 혹시 모를 중복 실행 방지 (2중 안전장치)
        if (this.isAnalyzing) {
            console.warn('[VideoController] 이전 분석 요청이 아직 진행 중입니다. 건너뜀.');
            return;
        }

        try {
            if (this.frameBuffer.length < 1) {
                console.warn('[VideoController] 분석에 필요한 프레임 부족 (현재: ' + this.frameBuffer.length + ')');
                return;
            }

            this.isAnalyzing = true; // ✅ 요청 시작 플래그

            // sharp를 이용해 프레임들을 JPEG(base64) 형태로 변환
            const frames = await Promise.all(
                this.frameBuffer.map(f =>
                    sharp(f, { raw: { width: 1280, height: 720, channels: 3 } })
                        .jpeg()
                        .toBuffer()
                        .then(buf => buf.toString('base64'))
                )
            );

            // Flask로 POST 요청
            const response = await axios.post(
                `${this.flaskServerUrl}/api/video/analyze`,
                { frames, timestamp: this.frameTimestamp , thermal: this.latestThermal }, //32*24
                { timeout: 30000, headers: { 'Content-Type': 'application/json' } }
            );

            const analysisResult = {
                timestamp: this.frameTimestamp,
                result: response.data,
                receivedAt: Date.now()
            };

            this.analysisResults.push(analysisResult);
            if (this.analysisResults.length > 100) this.analysisResults.shift();

            // 아기 감지 시 로그 출력
            if (response.data.data?.result?.infant_detected === true) {
                console.log("🍼 [알림] 아기 감지 완료");
                console.log("🌡️ [체온]", response.data.data?.result?.thermal);
            }

            const result = response.data.data?.result;

            // 체온 이상 검사 (아기가 잡혀서 체온이 나온 경우만)
            const temp = Number(result?.thermal);
            if (result?.thermal != null && Number.isFinite(temp)) {
                this.checkBodyTemp(temp);
            }

            // 분석 결과 DB 저장
            // 타임 스탬프는 flask에 넘기는 시점에서 찍어도 되는데 
            // 어차피 1분 안짝이라 크게 의미 없을것 같아서 일단 현재 시간
            await VideoAnalysis.create({
                timestamp: new Date(),
                infantDetected: result?.infant_detected ?? false,
                thermal: result?.thermal ?? null,
                confidence: result?.confidence ?? null,
                bbox: result?.bbox ? {
                    x1: result.bbox[0],
                    y1: result.bbox[1],
                    x2: result.bbox[2],
                    y2: result.bbox[3]
                } : null
            });

            // 웹소켓으로 보내나요? 아니면 io로 통일하나요?
            // Android 클라이언트에게 WebSocket으로 결과 전송
            this.broadcastAnalysisResult(analysisResult);
        } catch (error) {
            console.error('[VideoController] Flask 분석 요청 실패:', error.message);
        } finally {
            this.isAnalyzing = false; // ✅ 성공/실패 관계없이 플래그 해제
        }
    }

    /**
     * 체온을 정상 체온과 비교 (BODY_TEMP_CONSECUTIVE번 연속 벗어나면 경고)
     */
    checkBodyTemp(temp) {
        const diff = temp - this.baseTemp;

        if (diff >= BODY_TEMP_DELTA) this.raiseBodyAlert('RISE', temp, diff);
        else this.bodyAlertCount.RISE = 0;

        if (diff <= -BODY_TEMP_DELTA) this.raiseBodyAlert('DROP', temp, diff);
        else this.bodyAlertCount.DROP = 0;
    }

    /**
     * 체온 경고 (종류별 쿨다운)
     */
    raiseBodyAlert(kind, current, diff) {
        this.bodyAlertCount[kind]++;
        if (this.bodyAlertCount[kind] < BODY_TEMP_CONSECUTIVE) return;
        if (Date.now() - this.lastBodyAlertAt[kind] < BODY_ALERT_COOLDOWN_MS) return;

        this.lastBodyAlertAt[kind] = Date.now();
        this.bodyAlertCount[kind] = 0;

        const type = kind === 'RISE' ? 'BODY_TEMP_RISE' : 'BODY_TEMP_DROP';
        const message = kind === 'RISE'
            ? '아기 체온이 평소보다 높게 측정되고 있습니다'
            : '아기 체온이 평소보다 낮게 측정되고 있습니다';

        console.log(`[체온 경고] ${message} (측정 ${current.toFixed(1)}°C, 정상 ${this.baseTemp}°C, 차이 ${diff.toFixed(1)})`);

        publishAlert(type, message);   // 라즈베리파이 LED

        const io = this.app?.get('io');
        if (io && this.userId) {
            io.to(this.userId).emit('temperatureAlert', {
                type,
                userId: this.userId,
                baseTemp: this.baseTemp,
                message
            });
        }
    }

    // --- 정상 체온 보정 ---

    /**
     * DB에 저장된 정상 체온 불러오기 (없으면 기본값)
     */
    async loadBaseTemp() {
        try {
            const saved = await BodyTempCalibration.findOne({ userId: this.userId }).lean();
            this.baseTemp = saved ? saved.baseTemp : BODY_TEMP_BASE;
            console.log(`[체온 보정] 정상 체온 ${this.baseTemp}°C (${saved ? '보정값' : '기본값'})`);
        } catch (error) {
            console.error('[체온 보정] 불러오기 실패:', error.message);
        }
    }

    /**
     * 한국 시간 기준 날짜 문자열 (YYYY-MM-DD)
     */
    toDayString(date) {
        return new Date(date).toLocaleDateString('sv-SE', { timeZone: 'Asia/Seoul' });
    }

    /**
     * 날짜별로 위아래를 잘라 평균 → 날짜별 평균을 다시 평균
     */
    computeBaseTemp(rows) {
        const byDay = {};
        rows.forEach(r => {
            const day = this.toDayString(r.timestamp);
            (byDay[day] = byDay[day] || []).push(r.thermal);
        });

        const dayMeans = Object.values(byDay).map(values => {
            values.sort((a, b) => a - b);
            const cut = Math.floor(values.length * CALIBRATION_TRIM_RATIO);
            const trimmed = values.slice(cut, values.length - cut);
            return trimmed.reduce((a, b) => a + b, 0) / trimmed.length;
        });

        return parseFloat((dayMeans.reduce((a, b) => a + b, 0) / dayMeans.length).toFixed(2));
    }

    /**
     * 현재 정상 체온 조회
     */
    async getCalibration(req, res) {
        try {
            const saved = await BodyTempCalibration.findOne({ userId: this.userId }).lean();
            res.json({
                success: true,
                baseTemp: this.baseTemp,
                delta: BODY_TEMP_DELTA,
                calibrated: !!saved,
                method: saved?.method ?? null,
                updatedAt: saved?.updatedAt ?? null
            });
        } catch (error) {
            res.status(500).json({ success: false, message: error.message });
        }
    }

    /**
     * '기간으로 보정' 목록: 최근 CALIBRATION_DAYS일 날짜별 측정 개수 / 평균
     */
    async getCalibrationDays(req, res) {
        try {
            const since = new Date(Date.now() - CALIBRATION_DAYS * 24 * 60 * 60 * 1000);
            const days = await VideoAnalysis.aggregate([
                { $match: { timestamp: { $gte: since }, thermal: { $ne: null } } },
                {
                    $group: {
                        _id: { $dateToString: { format: '%Y-%m-%d', date: '$timestamp', timezone: 'Asia/Seoul' } },
                        count: { $sum: 1 },
                        avg: { $avg: '$thermal' }
                    }
                },
                { $sort: { _id: -1 } }
            ]);

            res.json({
                success: true,
                days: days.map(d => ({
                    date: d._id,
                    count: d.count,
                    avg: parseFloat(d.avg.toFixed(2))
                }))
            });
        } catch (error) {
            res.status(500).json({ success: false, message: error.message });
        }
    }

    /**
     * 정상 체온 보정
     * - body { mode: 'now' }: 최근 5분 측정값으로 보정
     * - body { mode: 'days', days: ['2026-11-03', ...] }: 선택한 날짜들의 측정값으로 보정
     */
    async calibrate(req, res) {
        try {
            const mode = req.body?.mode === 'days' ? 'days' : 'now';
            let rows;

            if (mode === 'now') {
                const since = new Date(Date.now() - CALIBRATION_WINDOW_MS);
                rows = await VideoAnalysis.find({ timestamp: { $gte: since }, thermal: { $ne: null } })
                    .select('timestamp thermal')
                    .lean();
            } else {
                const selected = Array.isArray(req.body?.days) ? req.body.days : [];
                if (selected.length === 0) {
                    return res.status(400).json({ success: false, message: '보정에 사용할 날짜를 선택해 주세요.' });
                }

                const since = new Date(Date.now() - CALIBRATION_DAYS * 24 * 60 * 60 * 1000);
                const all = await VideoAnalysis.find({ timestamp: { $gte: since }, thermal: { $ne: null } })
                    .select('timestamp thermal')
                    .lean();
                rows = all.filter(r => selected.includes(this.toDayString(r.timestamp)));
            }

            if (rows.length < CALIBRATION_MIN_SAMPLES) {
                const message = mode === 'now'
                    ? `측정값이 부족합니다 (${rows.length}개). 아기가 화면에 잡힌 상태에서 잠시 후 다시 시도해 주세요.`
                    : `선택한 날짜의 측정값이 부족합니다 (${rows.length}개).`;
                return res.status(400).json({ success: false, message });
            }

            const baseTemp = this.computeBaseTemp(rows);

            await BodyTempCalibration.findOneAndUpdate(
                { userId: this.userId },
                { baseTemp, method: mode, sampleCount: rows.length, updatedAt: new Date() },
                { upsert: true }
            );

            this.baseTemp = baseTemp;
            this.bodyAlertCount = { RISE: 0, DROP: 0 };

            console.log(`[체온 보정] 정상 체온 ${baseTemp}°C로 설정 (${mode === 'now' ? '지금 상태' : '기간'}, 측정 ${rows.length}개)`);
            res.json({ success: true, baseTemp, delta: BODY_TEMP_DELTA, calibrated: true, method: mode, sampleCount: rows.length });
        } catch (error) {
            console.error('[체온 보정] 실패:', error.message);
            res.status(500).json({ success: false, message: '보정 중 오류가 발생했습니다.' });
        }
    }

    /**
     * 분석 결과를 WebSocket 연결된 안드로이드 기기들에 전송
     */
    broadcastAnalysisResult(result) {
        const payload = JSON.stringify({
            type: 'analysisResult',
            data: result
        });
        this.androidClients.forEach((ws) => {
            if (ws.readyState === 1) { // OPEN 상태
                try {
                    ws.send(payload);
                } catch (error) {
                    this.androidClients.delete(ws);
                }
            }
        });
    }

    // --- 안드로이드 클라이언트 관리 및 기타 메서드 ---

    addAndroidClient(ws) {
        this.androidClients.add(ws);
        console.log(`[VideoController] Android 연결 추가 (총 ${this.androidClients.size}개)`);
    }

    removeAndroidClient(ws) {
        this.androidClients.delete(ws);
        console.log(`[VideoController] Android 연결 제거 (총 ${this.androidClients.size}개)`);
    }

    getCurrentFrame(req, res) {
        if (!this.currentFrame) return res.status(404).json({ message: '프레임 없음' });
        sharp(this.currentFrame, { raw: { width: 1280, height: 720, channels: 3 } })
            .jpeg().toBuffer().then(buf => {
                res.set('Content-Type', 'image/jpeg');
                res.send(buf);
            });
    }

    getAnalysisResults(req, res) {
        res.json({ success: true, count: this.analysisResults.length, results: this.analysisResults });
    }
}

module.exports = new VideoController();