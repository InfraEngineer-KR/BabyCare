/**
 * soundAnalysisController.js
 * 울음 분석을 Flask 서버에 요청합니다
 */

const axios = require('axios');
const temhuController = require('./TemhuController');

// dB 저장용
const { SoundAnalysisResult } = require('../models/Soundanalysis');

class SoundAnalysisController {

  constructor() {

    this.flaskServerUrl =
      process.env.FLASK_SERVER_URL || 'http://127.0.0.1:5000';

    this.analysisResults = [];
    this.analysisHistory = [];

    this.audioBuffer = [];

    this.audioFlag = 0;
    this.silentCount = 0;
    this.notCryingCount = 0;

    this.isAnalyzing = false;
    this.isRunning = false;

    this.intervalId = null;

    // 10분 noise 저장용 타이머
    this.noiseIntervalId = null;

    this.app = null;

    // receiver.init()에서 주입받음
    this.userId = null;

    this.dbSamples = [];

    // 울음 상태
    this.currentCryEvent = null;
    // 4바이트(float32) 단위로 안 맞아서 남은 바이트 (다음 chunk 앞에 붙임)
    this.audioRemainder = Buffer.alloc(0);
  }

  // server.js에서 app 주입
  setApp(app) {
    this.app = app;
  }

  // receiver.init()에서 userId 주입
  setUserId(userId) {

    this.userId = userId;

    console.log(
      `[SoundAnalysisController] userId 설정됨: ${userId}`
    );
  }

  onAudio(audioChunk) {

    // 이전 chunk에서 남은 바이트(4바이트 미만)를 앞에 붙임
    const data = this.audioRemainder.length > 0
      ? Buffer.concat([this.audioRemainder, audioChunk])
      : audioChunk;

    // 4바이트 단위로 쓸 수 있는 길이만 사용, 남은 조각은 다음 chunk로
    const usable = data.length - (data.length % 4);
    this.audioRemainder = Buffer.from(data.subarray(usable));

    if (usable === 0) return;

    // 이 chunk의 샘플만 정확히 읽음 (메모리 전체를 읽지 않음)
    const samples = new Float32Array(usable / 4);
    for (let i = 0; i < samples.length; i++) {
      samples[i] = data.readFloatLE(i * 4);
    }

    for (let i = 0; i < samples.length; i++) {
      this.audioBuffer.push(samples[i]);
    }

    if (this.audioBuffer.length > 96000) {
      this.audioBuffer.splice(0, this.audioBuffer.length - 96000);
    }

    const db = this.calculateDb(samples);
    this.dbSamples.push(db);
    if (this.dbSamples.length > 600) {
      this.dbSamples.shift();
    }
  }

  calculateDb(samples) {

    const rms = Math.sqrt(
      samples.reduce((s, x) => s + x * x, 0)
      / samples.length
    );

    if (rms < 1e-10) return -100;

    return 20 * Math.log10(rms);
  }

  async startAudioAnalysis(req, res) {

    try {

      if (this.isRunning) {

        return res.status(400).json({
          message: '이미 실행 중'
        });
      }

      this.startLoop();
      //this.startNoiseInterval();

      res.json({
        success: true,
        message: '음성 분석 시작'
      });

    } catch (error) {

      res.status(500).json({
        success: false,
        error: error.message
      });
    }
  }

  async stopAudioAnalysis(req, res) {

    try {

      this.isRunning = false;

      if (this.intervalId !== null) {
        clearTimeout(this.intervalId);
        this.intervalId = null;
      }

      if (this.noiseIntervalId !== null) {
        clearInterval(this.noiseIntervalId);
        this.noiseIntervalId = null;
      }

      this.audioFlag = 0;
      this.silentCount = 0;
      this.notCryingCount = 0;
      this.dbSamples = [];

      res.json({
        success: true,
        message: '음성 분석 중지'
      });

    } catch (error) {

      res.status(500).json({
        success: false,
        error: error.message
      });
    }
  }

  // // =========================================================
  // // 10분마다 평균 dB 계산 후 temperhumilities에 저장
  // // =========================================================
  // startNoiseInterval() {

  //   this.noiseIntervalId = setInterval(async () => {

  //     if (this.dbSamples.length === 0) return;

  //     const avgDb =
  //       this.dbSamples.reduce((a, b) => a + b, 0) /
  //       this.dbSamples.length;

  //     this.dbSamples = [];

  //     console.log(
  //       `[SoundAnalysisController] 10분 평균 dB: ${avgDb.toFixed(2)}`
  //     );

  //     if (this.userId) {
  //       await temhuController.saveNoiseData(this.userId, avgDb);
  //     } else {
  //       console.warn('[SoundAnalysisController] userId 없어서 noise 저장 불가');
  //     }

  //   }, 10 * 60 * 1000);
  // }

  startLoop() {

    this.isRunning = true;

    const loop = async () => {

      if (!this.isRunning) return;

      if (this.audioBuffer.length > 0) {

        const samples = new Float32Array(this.audioBuffer);

        const db = this.calculateDb(samples);

        if (db > -40) {

          this.audioFlag = 1;
          this.silentCount = 0;

        } else {

          this.silentCount++;
        }

        if (this.audioFlag === 1 && !this.isAnalyzing) {
          await this.requestAudioFlaskAnalysis();
        }

        if (
          this.silentCount >= 10 &&
          this.notCryingCount >= 10
        ) {

          if (this.currentCryEvent) {

            await SoundAnalysisResult.findByIdAndUpdate(
              this.currentCryEvent._id,
              { cry_end_Time: new Date() }
            );

            this.currentCryEvent = null;
          }

          console.log(
            '[SoundAnalysisController] 조용해짐. audioFlag 0으로 변경'
          );

          this.audioFlag = 0;
          this.silentCount = 0;
          this.notCryingCount = 0;
        }
      }

      if (this.isRunning) {
        this.intervalId = setTimeout(loop, 1000);
      }
    };

    this.intervalId = setTimeout(loop, 0);
  }

  async requestAudioFlaskAnalysis() {

    try {

      this.isAnalyzing = true;

      const buffer = Buffer.from(
        new Float32Array(this.audioBuffer).buffer
      );

      const response = await axios.post(
        `${this.flaskServerUrl}/api/audio/analyze`,
        buffer,
        {
          timeout: 60000,
          headers: {
            'Content-Type': 'application/octet-stream'
          }
        }
      );

      const analysisResult = {
        timestamp: Date.now(),
        result: response.data,
        receivedAt: Date.now(),
        status: 'success'
      };

      this.analysisResults.push(analysisResult);
      this.analysisHistory.push(analysisResult);

      if (this.analysisResults.length > 50) {
        this.analysisResults.shift();
      }

      if (!response.data.data.cry_detected) {
        this.notCryingCount++;
      } else {
        this.notCryingCount = 0;
      }

      if (response.data.data.cry_detected === true) {

        console.log('[SoundAnalysisController] 울음 감지됨!');

        if (!this.userId) {
          console.warn(
            '[SoundAnalysisController] userId가 설정되지 않아 저장 불가'
          );
          return;
        }

        if (!this.currentCryEvent) {
          this.currentCryEvent =
            await SoundAnalysisResult.create({
              cry_start_Time: new Date()
            });
        }

        const io = this.app
          ? this.app.get('io')
          : null;

        const cryProbability =
          response.data.data.cry_ratio ?? null;

        await temhuController.saveCryEvent(
          this.userId,
          cryProbability,
          io
        );
      }

    } catch (error) {

      const errorResult = {
        timestamp: Date.now(),
        error: error.message,
        status: 'failed'
      };

      this.analysisHistory.push(errorResult);

    } finally {

      this.isAnalyzing = false;
    }
  }

  getAnalysisResults(req, res) {

    try {

      res.json({
        success: true,
        count: this.analysisResults.length,
        results: this.analysisResults
      });

    } catch (error) {

      console.error('[SoundAnalysisController] 결과 조회 실패:', error.message);

      res.status(500).json({
        success: false,
        message: '결과 조회 실패',
        error: error.message
      });
    }
  }

  getAnalysisHistory(req, res) {

    try {

      const limit =
        req.query.limit
          ? parseInt(req.query.limit)
          : 20;

      const history = this.analysisHistory.slice(-limit);

      res.json({
        success: true,
        count: history.length,
        total: this.analysisHistory.length,
        history: history
      });

    } catch (error) {

      console.error('[SoundAnalysisController] 히스토리 조회 실패:', error.message);

      res.status(500).json({
        success: false,
        message: '히스토리 조회 실패',
        error: error.message
      });
    }
  }

  getResultsByDate(req, res) {

    try {

      const { date } = req.params;
      const targetDate = new Date(date).getTime();
      const dayInMs = 24 * 60 * 60 * 1000;

      const filtered = this.analysisHistory.filter(item => {
        if (!item.timestamp) return false;
        return (
          item.timestamp >= targetDate &&
          item.timestamp < targetDate + dayInMs
        );
      });

      res.json({
        success: true,
        date: date,
        count: filtered.length,
        results: filtered
      });

    } catch (error) {

      console.error('[SoundAnalysisController] 날짜별 조회 실패:', error.message);

      res.status(500).json({
        success: false,
        message: '날짜별 조회 실패',
        error: error.message
      });
    }
  }
}

module.exports = new SoundAnalysisController();