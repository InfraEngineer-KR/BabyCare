const TemperHumility = require('../models/TemperHumility');
const { calcScore } = require('./sleepController');

const TEMP_HIGH = 28;
const TEMP_LOW = 18;
const RAPID_TEMP_CHANGE = 3;

exports.setUserId = function(userId) {
    this.userId = userId;
};

exports.setApp = function(app) {
    this.app = app;
};

/**
 * 위험 상태 검사 + Socket 알림
 */
// previous: 이번 데이터를 저장하기 "전"의 최신 기록 (급변 비교용)
const checkDangerCondition = async (userId, temperature, io, previous) => {

    try {

        const latest = previous;

        if (temperature >= TEMP_HIGH) {
            console.log(`[고온 감지] ${temperature}도`);
            if (io) {
                io.to(userId).emit('temperatureAlert', {
                    type: 'HIGH_TEMPERATURE',
                    userId,
                    temperature,
                    message: `실내 온도가 너무 높습니다 (${temperature}°C)`
                });
            }
        }

        if (temperature <= TEMP_LOW) {
            console.log(`[저온 감지] ${temperature}도`);
            if (io) {
                io.to(userId).emit('temperatureAlert', {
                    type: 'LOW_TEMPERATURE',
                    userId,
                    temperature,
                    message: `실내 온도가 너무 낮습니다 (${temperature}°C)`
                });
            }
        }

        if (latest) {
            const diff = temperature - latest.temperature;

            if (diff >= RAPID_TEMP_CHANGE) {
                console.log(`[온도 급상승] ${latest.temperature} → ${temperature}`);
                if (io) {
                    io.to(userId).emit('temperatureAlert', {
                        type: 'RAPID_TEMP_RISE',
                        userId,
                        before: latest.temperature,
                        current: temperature,
                        diff,
                        message: `온도가 급격히 상승했습니다 (${diff.toFixed(1)}°C)`
                    });
                }
            }

            if (diff <= -RAPID_TEMP_CHANGE) {
                console.log(`[온도 급하락] ${latest.temperature} → ${temperature}`);
                if (io) {
                    io.to(userId).emit('temperatureAlert', {
                        type: 'RAPID_TEMP_DROP',
                        userId,
                        before: latest.temperature,
                        current: temperature,
                        diff,
                        message: `온도가 급격히 하락했습니다 (${Math.abs(diff).toFixed(1)}°C)`
                    });
                }
            }
        }

    } catch (error) {
        console.error('[위험 상태 검사 실패]', error.message);
    }
};

/**
 * Android → 최신 데이터 조회
 */
exports.getLatestData = async (req, res) => {

    const { userId } = req.query;

    try {

        const query = userId ? { userId } : {};

        const latestData = await TemperHumility.findOne(query)
            .sort({ timestamp: -1 });

        if (!latestData) {
            return res.status(404).json({
                message: "저장된 데이터가 없습니다."
            });
        }

        res.status(200).json(latestData);

    } catch (error) {
        res.status(500).json({
            message: "데이터 조회 중 오류 발생",
            error: error.message
        });
    }
};

/**
 * Android → 수면점수 변화 그래프용 데이터
 */
exports.getSleepScoreHistory = async (req, res) => {

    const { userId } = req.query;

    if (!userId) {
        return res.status(400).json({ message: "userId 필요" });
    }

    try {
        const since = new Date(Date.now() - 6 * 60 * 60 * 1000);
        const history = await TemperHumility.find({
            userId,
            sleepScore: { $ne: null },
            timestamp: { $gte: since }
        })
        .sort({ timestamp: 1 })
        .select("sleepScore timestamp");

        const formatted = history.map(item => ({
            score: item.sleepScore,
            time: item.timestamp
        }));

        res.status(200).json(formatted);

    } catch (error) {
        res.status(500).json({
            message: "수면점수 조회 실패",
            error: error.message
        });
    }
};

exports.getHistoryData = exports.getSleepScoreHistory;

/**
 * MQTT 온습도 수신 → DB 저장 + 즉시 수면점수 계산
 */
exports.onData = async function(data) {
    try {
        const soundAnalysisController = require('./soundAnalysisController');
        const { temperature, humidity } = data;
        const userId = this.userId;
        const io = this.app ? this.app.get('io') : null;

        if (!userId || temperature === undefined || humidity === undefined) {
            console.error('[onData] 필수 데이터 누락');
            return;
        }

        // 최신 도큐먼트에서 noise, cry 가져와서 sleepScore 계산
        const latest = await TemperHumility.findOne({ userId })
            .sort({ timestamp: -1 });

        const noise = soundAnalysisController.dbSamples.length > 0
            ? soundAnalysisController.dbSamples.reduce((a, b) => a + b, 0) / soundAnalysisController.dbSamples.length
            : null;

        const isCrying = false;
        const sleepScore = calcScore(temperature, humidity, noise, isCrying);

        const sensorData = new TemperHumility({
            userId,
            temperature,
            humidity,
            noise,
            cryDetected: isCrying,
            cryProbability: latest?.cryProbability ?? null,
            sleepScore,
            timestamp: new Date()
        });

        await sensorData.save();

        console.log(
            `[MQTT 저장] userId=${userId}, temp=${temperature}, hum=${humidity}, sleepScore=${sleepScore}`
        );

                await checkDangerCondition(userId, temperature, io, latest);

    } catch (error) {
        console.error('[onData 실패]', error.message);
    }
};

/**
 * 울음 감지 → DB 업데이트 + sleepScore 재계산 + Android 알림
 */
const CRY_ALERT_COOLDOWN_MS = 30 * 1000;   // 울음 알림 최소 간격 (30초)
let lastCryAlertAt = 0;

exports.saveCryEvent = async function(userId, cryProbability, io) {

    // 1. DB 기록 (온습도 기록이 아직 없으면 건너뜀)
    try {
        const latest = await TemperHumility.findOne({ userId })
            .sort({ timestamp: -1 });

        if (latest) {
            const sleepScore = calcScore(latest.temperature, latest.humidity, latest.noise, true);

            await TemperHumility.findByIdAndUpdate(latest._id, {
                $set: {
                    cryDetected: true,
                    cryProbability,
                    sleepScore
                }
            });

            console.log(`[울음 감지] userId=${userId}, 확률=${cryProbability}, sleepScore=${sleepScore}`);
        } else {
            console.warn(`[울음 감지] userId=${userId} 온습도 기록이 없어 DB 기록은 건너뜀`);
        }

    } catch (error) {
        console.error('[saveCryEvent 실패]', error.message);
    }

    // 2. 알림 (DB 결과와 상관없이 보냄, 쿨다운 적용)
    const now = Date.now();
    if (!io || now - lastCryAlertAt < CRY_ALERT_COOLDOWN_MS) return;
    lastCryAlertAt = now;

    const percent = cryProbability != null
        ? `${(cryProbability * 100).toFixed(1)}%`
        : '알 수 없음';

    io.to(userId).emit('cryAlert', {
        type: 'CRY_DETECTED',
        userId,
        cryProbability,
        message: `아기 울음이 감지되었습니다 (확률: ${percent})`
    });
};

// /**
//  * 10분 평균 noise → DB 업데이트 + sleepScore 재계산
//  */
// exports.saveNoiseData = async function(userId, avgDb) {

//     try {

//         const latest = await TemperHumility.findOne({ userId })
//             .sort({ timestamp: -1 });

//         const temperature = latest?.temperature ?? null;
//         const humidity = latest?.humidity ?? null;
//         const isCrying = latest?.cryDetected ?? false;
//         const sleepScore = calcScore(temperature, humidity, avgDb, isCrying);

//         await TemperHumility.findByIdAndUpdate(latest._id, {
//             $set: {
//                 noise: avgDb,
//                 sleepScore
//             }
//         });

//         console.log(`[Noise 저장] userId=${userId}, avgDb=${avgDb.toFixed(2)}, sleepScore=${sleepScore}`);

//     } catch (error) {
//         console.error('[saveNoiseData 실패]', error.message);
//     }
// };