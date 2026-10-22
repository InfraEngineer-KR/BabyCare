const dgram = require('dgram')
const { spawn } = require('child_process')
const path = require('path')
const fs = require('fs')
const mqtt = require('mqtt')

const videoController = require('./controllers/videoController')
const soundAnalysisController = require('./controllers/soundAnalysisController')
const temhuController = require('./controllers/TemhuController')
const stController = require('./controllers/smartThingsController')

let wss

// =========================================================
// 설정
// =========================================================
const UDP_PORT = parseInt(process.env.UDP_PORT) || 8888

const HLS_DIR =
    process.env.HLS_DIR ||
    path.resolve(__dirname, '../public/stream')

const CAMERA_TIMEOUT_MS =
    parseInt(process.env.CAMERA_TIMEOUT_MS) || 5000

const RESTART_DELAY_MS = 3000            // ffmpeg 종료 시 재시작 대기

const FRAME_WIDTH = 1280
const FRAME_HEIGHT = 720
const FRAME_SIZE = FRAME_WIDTH * FRAME_HEIGHT * 3

let ffmpegProcess = null                 // 분석용 (raw RGB + raw audio)
let hlsProcess = null                    // HLS 스트리밍용
let frameBuffer = Buffer.alloc(0)

// =========================================================
// HLS 폴더 준비
// =========================================================
function clearHlsDir() {
    if (!fs.existsSync(HLS_DIR)) return
    fs.readdirSync(HLS_DIR).forEach(file => {
        try {
            fs.unlinkSync(path.join(HLS_DIR, file))
        } catch (_) {}
    })
}

if (!fs.existsSync(HLS_DIR)) {
    fs.mkdirSync(HLS_DIR, { recursive: true })
} else {
    // 서버 재시작 시 기존 파일 삭제
    clearHlsDir()
}

// =========================================================
// FFMPEG 공통 감시
// - stderr 읽어서 파이프 안 막히게
// - 에러/종료 시 서버는 살려두고 RESTART_DELAY_MS 후 재시작
// =========================================================
function watchProcess(proc, name, restart) {
    let restarting = false

    const scheduleRestart = (reason) => {
        if (restarting) return
        restarting = true
        console.error(`[${name}] ${reason} → ${RESTART_DELAY_MS / 1000}초 후 재시작`)
        setTimeout(restart, RESTART_DELAY_MS)
    }

    proc.stderr.on('data', () => {})
    proc.stdin.on('error', (err) => console.error(`[${name}] 입력 에러:`, err.message))
    proc.on('error', (err) => scheduleRestart(`실행 에러: ${err.message}`))
    proc.on('exit', (code, signal) => scheduleRestart(`종료 code: ${code} signal: ${signal}`))
}

// =========================================================
// FFMPEG: 분석용 (raw RGB + raw audio)
// =========================================================
function startAnalysisFfmpeg() {
    // 이전 프로세스에서 남은 조각은 버림 (새 스트림과 섞이면 데이터가 어긋남)
    frameBuffer = Buffer.alloc(0)
    soundAnalysisController.audioRemainder = Buffer.alloc(0)

    const proc = spawn('ffmpeg', [
        '-loglevel', 'error',
        '-fflags', '+discardcorrupt',
        '-f', 'mpegts',
        '-i', 'pipe:0',

        // 영상 → pipe:1
        '-map', '0:v',
        '-vf', `scale=${FRAME_WIDTH}:${FRAME_HEIGHT}`,
        '-f', 'rawvideo',
        '-pix_fmt', 'rgb24',
        'pipe:1',

        // 오디오 → pipe:3 (오디오 없으면 그냥 건너뜀: 0:a?)
        '-map', '0:a?',
        '-f', 'f32le',
        '-ar', '48000',
        '-ac', '1',
        'pipe:3'
    ], {
        stdio: ['pipe', 'pipe', 'pipe', 'pipe']
    })

    // 비디오 프레임 처리
    proc.stdout.on('data', (chunk) => {
        frameBuffer = Buffer.concat([frameBuffer, chunk])

        while (frameBuffer.length >= FRAME_SIZE) {
            const frame = frameBuffer.slice(0, FRAME_SIZE)
            frameBuffer = frameBuffer.slice(FRAME_SIZE)
            videoController.onFrame(frame)
        }
    })

    // 오디오 처리
    proc.stdio[3].on('data', (audioChunk) => {
        soundAnalysisController.onAudio(audioChunk)
    })
    proc.stdio[3].on('error', (err) => {
        console.error('[ff-analysis] 오디오 출력 에러:', err.message)
    })

    watchProcess(proc, 'ff-analysis', startAnalysisFfmpeg)
    ffmpegProcess = proc
    console.log('🎬 ffmpeg(분석) 시작')
}

// =========================================================
// FFMPEG: HLS 스트리밍
// =========================================================
function startHlsFfmpeg() {
    // 이전 세그먼트/목록이 남아 있으면 플레이어가 꼬이므로 정리
    clearHlsDir()

    const proc = spawn('ffmpeg', [
        '-loglevel', 'error',
        '-f', 'mpegts',
        '-i', 'pipe:0',
        '-c:v', 'copy',
        '-c:a', 'copy',
        '-f', 'hls',
        '-hls_time', '2',
        '-hls_list_size', '5',
        // omit_endlist: ffmpeg가 죽어도 '방송 끝' 표시 안 씀 (플레이어가 재생 종료로 판단하지 않게)
        '-hls_flags', 'delete_segments+append_list+omit_endlist',
        // 재시작해도 세그먼트 번호가 계속 증가하도록 (플레이어가 이미 본 번호로 착각하지 않게)
        '-hls_start_number_source', 'datetime',
        '-hls_allow_cache', '0',
        '-hls_segment_filename', path.join(HLS_DIR, 'streamingfile%d.ts'),
        path.join(HLS_DIR, 'streamingfile.m3u8')
    ], {
        stdio: ['pipe', 'ignore', 'pipe']
    })

    watchProcess(proc, 'ff-hls', startHlsFfmpeg)
    hlsProcess = proc
    console.log('📺 ffmpeg(HLS) 시작')
}

// =========================================================
// UDP 서버
// =========================================================
const udpServer = dgram.createSocket('udp4')

let lastMessageTime = Date.now()
let firstPacketLogged = false

udpServer.on('message', (msg) => {
    if (!firstPacketLogged) {
        console.log(`📥 UDP 첫 패킷 수신: ${msg.length} bytes`)
        firstPacketLogged = true
    }

    lastMessageTime = Date.now()

    // 프로세스가 살아 있을 때만 전달 (재시작 중이면 건너뜀)
    if (ffmpegProcess && ffmpegProcess.stdin.writable) ffmpegProcess.stdin.write(msg)
    if (hlsProcess && hlsProcess.stdin.writable) hlsProcess.stdin.write(msg)
})

udpServer.on('error', (err) => {
    console.error('[UDP] 에러:', err)
    udpServer.close()
})

// 카메라 끊김 감지: 타임아웃 지나면 HLS 파일 삭제
// 객체인식 음성분석도 초기화
setInterval(() => {
    if (Date.now() - lastMessageTime > CAMERA_TIMEOUT_MS) {
        clearHlsDir()
        soundAnalysisController.analysisResults = []
        soundAnalysisController.analysisHistory = []
        soundAnalysisController.audioBuffer = []
        soundAnalysisController.audioFlag = 0
        soundAnalysisController.silentCount = 0
        soundAnalysisController.notCryingCount = 0
        soundAnalysisController.isAnalyzing = false
        soundAnalysisController.dbSamples = []
        soundAnalysisController.currentCryEvent = null
        videoController.currentFrame = null
        videoController.frameBuffer = []
        videoController.isAnalyzing = false
        videoController.latestThermal = null
        videoController.analysisResults = []
    }
}, CAMERA_TIMEOUT_MS)

// =========================================================
// MQTT
// =========================================================
const MQTT_HOST = process.env.MQTT_HOST || 'localhost'
const mqttClient = mqtt.connect(`mqtt://${MQTT_HOST}:1883`)

mqttClient.on('connect', () => {
    mqttClient.subscribe('baby/thermal')
    mqttClient.subscribe('baby/environment')
    console.log(`📡 MQTT 연결됨 (${MQTT_HOST}:1883)`)
})

mqttClient.on('error', (err) => {
    console.error('[MQTT] 에러:', err.message)
})

mqttClient.on('message', (topic, message) => {
    let data
    try {
        data = JSON.parse(message.toString())
    } catch (err) {
        console.error('[MQTT] JSON 파싱 실패:', err.message)
        return
    }

    // ─────────────────────────────────────────────────────
    // 열화상
    // ─────────────────────────────────────────────────────
    if (topic === 'baby/thermal') {
        if (!Array.isArray(data.frame) || data.frame.length === 0) {
            console.error('[MQTT] 열화상 데이터 형식 오류 → 건너뜀')
            return
        }

        const avg = (
            data.frame.reduce((a, b) => a + b, 0) / data.frame.length
        ).toFixed(2)

        console.log(
            `[MQTT] 열화상 평균온도: ${avg}`
        )

        videoController.onThermal(data)
    }

    // ─────────────────────────────────────────────────────
    // 온습도
    // ─────────────────────────────────────────────────────
    else if (topic === 'baby/environment') {
        if (typeof data.temperature !== 'number' || typeof data.humidity !== 'number') {
            console.error('[MQTT] 온습도 데이터 형식 오류 → 건너뜀')
            return
        }

        console.log(
             `[MQTT] 온습도 수신: ${data.temperature}°C ${data.humidity}%`
        )

        temhuController.onData(data)
        stController.onData(data.humidity)
    }
})

// =========================================================
// INIT
// =========================================================
function init(wssInstance) {
    wss = wssInstance

    startAnalysisFfmpeg()
    startHlsFfmpeg()

    udpServer.bind(UDP_PORT, () => {
        console.log(`📡 UDP 수신 대기 중 (${UDP_PORT})`)
    })
}

module.exports = { init }