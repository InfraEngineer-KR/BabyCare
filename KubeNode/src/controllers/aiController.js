const OpenAI = require("openai");
const AiReport = require("../models/ai");
const TemperHumility = require("../models/TemperHumility");
const { SoundAnalysisResult } = require("../models/Soundanalysis");

// =========================================================
// 리포트 데이터 설정
//  USE_SAMPLE_REPORT = false (기본)
//    → 최근 12시간 실제 데이터(온습도/소음/수면점수/울음)로 리포트 생성
//    → 실제 데이터가 하나도 없으면 자동으로 아래 SAMPLE_DATA(예시)로 생성
//  USE_SAMPLE_REPORT = true
//    → 실제 데이터와 상관없이 항상 SAMPLE_DATA(예시)로 생성 (시연 리허설용)
// =========================================================
const USE_SAMPLE_REPORT = false;

const REPORT_TYPE = "아침 수면 종합 리포트";
const NOISE_DB_OFFSET = 80; // 소음 보정값 (앱 GrapeActivity와 동일: dBFS + 80)
const REPORT_HOURS = 12;    // 리포트 분석 기간 (최근 12시간)

// 예시 데이터 (전날 데이터를 못 쌓았을 때 사용)
const SAMPLE_DATA = {
    avgTemp: 20.0,
    avgHumidity: 60.0,
    avgNoise: 50.0,
    avgScore: 66.0,
    cryingCount: 10,
    dataCount: 720
};

// 한국 시간 문자열 (YYYY-MM-DD HH:mm)
const toKst = (date) =>
    date.toLocaleString("sv-SE", { timeZone: "Asia/Seoul" }).slice(0, 16);

const average = (list) =>
    list.length
        ? parseFloat((list.reduce((a, b) => a + b, 0) / list.length).toFixed(1))
        : null;

// 최근 기간 실제 데이터 집계 (데이터 없으면 null)
const buildRealData = async (since, now) => {
    const query = { timestamp: { $gte: since, $lte: now } };
    if (process.env.IOT_USER_ID) query.userId = process.env.IOT_USER_ID;

    const rows = await TemperHumility.find(query).lean();
    if (rows.length === 0) return null;

    const values = (key) => rows.map(r => r[key]).filter(v => typeof v === "number");

    const avgNoiseDbfs = average(values("noise"));
    const cryingCount = await SoundAnalysisResult.countDocuments({
        cry_start_Time: { $gte: since, $lte: now }
    });

    return {
        avgTemp: average(values("temperature")),
        avgHumidity: average(values("humidity")),
        avgNoise: avgNoiseDbfs !== null
            ? parseFloat((avgNoiseDbfs + NOISE_DB_OFFSET).toFixed(1))
            : null,
        avgScore: average(values("sleepScore")),
        cryingCount,
        dataCount: rows.length
    };
};

const client = new OpenAI({
    apiKey: process.env.OPENAI_API_KEY
});

// [공통] GPT 호출 함수
const generateAiReport = async (data) => {
    const prompt = `
### [역할 정의]
너는 신생아 수면 환경 데이터를 분석하고, 보호자가 이해하기 쉬운 리포트를 작성하는 전문가다.

### [지시 사항]
1. 제공된 [입력 데이터]를 분석하여 [출력 양식]의 구조에 맞춰 리포트를 작성한다.
2. 단순 수치 나열이 아니라 데이터를 종합적으로 해석하여 '부모님의 마음'을 고려해 다정하게 설명한다.
3. 의료적 진단이 아닌 환경 개선을 위한 조언 중심으로 작성한다.
4. 문장은 자연스러운 한국어로 작성한다.

### [출력 양식]
[기본 정보]
리포트 유형: ${data.reportType}
분석 기간: ${data.periodStart} ~ ${data.periodEnd}

[전체 요약]
(어젯밤 수면의 질을 총평하는 문단)

[환경 상태 분석]
(온도, 습도, 소음 데이터를 기반으로 한 환경 평가)

[수면 상태 분석]
(수면 점수 및 분포를 기반으로 한 분석)

[이상 징후 및 권장 행동]
(특이사항 요약 및 오늘 밤 개선할 점 제안)

### [입력 데이터 (통계 요약)]
${JSON.stringify(data, null, 2)}
`;

    const response = await client.chat.completions.create({
        model: "gpt-4o-mini",
        messages: [{ role: "user", content: prompt }],
        temperature: 0.7
    });

    return response.choices[0].message.content;
};

// [아침 8시 자동 + 서버 시작 시] 오늘 리포트가 없을 때만 생성
exports.generateDailyReport = async () => {
    try {
        const now = new Date();
        const since = new Date(now.getTime() - REPORT_HOURS * 60 * 60 * 1000);

        // 오늘(한국 날짜) 0시 이후 리포트 조회
        const todayKst = now.toLocaleDateString("sv-SE", { timeZone: "Asia/Seoul" });
        const todayStart = new Date(`${todayKst}T00:00:00+09:00`);
        const todayReports = { reportType: REPORT_TYPE, createdAt: { $gte: todayStart } };

        // 1. 오늘 실제 데이터 리포트가 이미 있으면 그대로 사용 (GPT 재호출 안 함)
        const existingReal = await AiReport.findOne({ ...todayReports, isSample: { $ne: true } })
            .sort({ createdAt: -1 });
        if (existingReal) {
            console.log("📄 오늘 리포트 이미 존재 → 생성 건너뜀");
            return existingReal;
        }

        // 2. 실제 데이터 집계 (USE_SAMPLE_REPORT=true면 건너뜀)
        const realData = USE_SAMPLE_REPORT ? null : await buildRealData(since, now);

        if (!realData) {
            // 오늘 예시 리포트가 이미 있으면 재사용 (재시작마다 GPT 호출 방지)
            const existingSample = await AiReport.findOne({ ...todayReports, isSample: true })
                .sort({ createdAt: -1 });
            if (existingSample) {
                console.log("📄 오늘 예시 리포트 이미 존재 → 생성 건너뜀");
                return existingSample;
            }
            console.log("📝 실제 데이터 없음(또는 USE_SAMPLE_REPORT=true) → 예시 데이터로 생성");
        }

        const data = {
            reportType: REPORT_TYPE,
            periodStart: toKst(since),
            periodEnd: toKst(now),
            ...(realData || SAMPLE_DATA)
        };

        const reportText = await generateAiReport(data);

        const report = new AiReport({
            ...data,
            reportText,
            isSample: !realData
        });
        await report.save();

        console.log(`🌅 AI 보고서 생성 완료 (${realData ? "실제 데이터" : "예시 데이터"})`);
        return report;

    } catch (err) {
        console.error("generateDailyReport 에러:", err);
    }
};

// [안드로이드 요청] 최신 보고서 조회
exports.getLatestReport = async (req, res) => {
    try {
        const report = await AiReport.findOne().sort({ createdAt: -1 });

        if (!report) {
            return res.status(404).json({
                success: false,
                message: "아직 생성된 보고서가 없습니다."
            });
        }

        res.status(200).json({
            success: true,
            data: report
        });

    } catch (err) {
        console.error("getLatestReport 에러:", err);
        res.status(500).json({ success: false, message: err.message });
    }
};

// [안드로이드 요청] 수동으로 보고서 생성 요청
exports.createReport = async (req, res) => {
    try {
        const report = await exports.generateDailyReport();

        if (!report) {
            return res.status(404).json({
                success: false,
                message: "보고서 생성할 데이터가 없습니다."
            });
        }

        res.status(200).json({
            success: true,
            data: report
        });

    } catch (err) {
        console.error("createReport 에러:", err);
        res.status(500).json({ success: false, message: err.message });
    }
};