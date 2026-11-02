/**
 * videoRoutes.js
 * 비디오 스트림 및 분석 관련 라우트
 */

const express = require('express');
const router = express.Router();
const videoController = require('../controllers/videoController');

/**
 * POST /api/video/start
 * 비디오 분석 시작
 */
router.post('/start', (req, res) => {
  videoController.startAnalysis(req, res);
});

/**
 * POST /api/video/stop
 * 비디오 분석 중지
 */
router.post('/stop', (req, res) => {
  videoController.stopAnalysis(req, res);
});

/**
 * GET /api/video/current-frame
 * 현재 프레임 조회 (JPEG 이미지)
 */
router.get('/current-frame', (req, res) => {
  videoController.getCurrentFrame(req, res);
});

/**
 * GET /api/video/analysis-results
 * 비디오 분석 결과 조회
 */
router.get('/analysis-results', (req, res) => {
  videoController.getAnalysisResults(req, res);
});

/**
 * GET /api/video/status
 * 스트림 및 분석 상태 조회
 */
router.get('/status', (req, res) => {
  videoController.getStreamStatus(req, res);
});

/**
 * GET /api/video/calibration
 * 현재 정상 체온 조회
 */
router.get('/calibration', (req, res) => {
  videoController.getCalibration(req, res);
});

/**
 * GET /api/video/calibration/days
 * '기간으로 보정' 목록 (최근 날짜별 측정 개수 / 평균)
 */
router.get('/calibration/days', (req, res) => {
  videoController.getCalibrationDays(req, res);
});

/**
 * POST /api/video/calibrate
 * 정상 체온 보정 ({ mode: 'now' } 또는 { mode: 'days', days: [...] })
 */
router.post('/calibrate', (req, res) => {
  videoController.calibrate(req, res);
});

module.exports = router;