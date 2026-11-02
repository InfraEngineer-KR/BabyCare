/**
 * BodyTempCalibration.js
 * 아기 정상 체온 보정값 (사용자별 1개)
 */
const mongoose = require('mongoose');

const bodyTempCalibrationSchema = new mongoose.Schema({
    userId: {
        type: String,
        required: true,
        unique: true
    },
    baseTemp: {
        type: Number,
        required: true
    },
    method: {
        type: String,       // 'now' = 지금 상태로 보정, 'days' = 기간으로 보정
        default: null
    },
    sampleCount: {
        type: Number,
        default: null
    },
    updatedAt: {
        type: Date,
        default: Date.now
    }
});

module.exports = mongoose.model('BodyTempCalibration', bodyTempCalibrationSchema);