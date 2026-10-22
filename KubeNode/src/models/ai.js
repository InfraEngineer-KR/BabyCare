const mongoose = require('mongoose');

const aiReportSchema = new mongoose.Schema({
    reportType: String,       // 보고서 유형
    periodStart: String,      // 분석 시작 시간
    periodEnd: String,        // 분석 종료 시간
    avgTemp: Number,          // 평균 온도
    avgHumidity: Number,      // 평균 습도
    avgNoise: Number,         // 평균 소음
    avgScore: Number,         // 평균 수면 점수
    cryingCount: Number,      // 울음 횟수
    reportText: String,       // GPT가 생성한 보고서 내용
    isSample: { type: Boolean, default: false }, // 예시 데이터로 만든 리포트 여부
}, {
    timestamps: true          // createdAt, updatedAt 자동 생성
});

module.exports = mongoose.model('AiReport', aiReportSchema);