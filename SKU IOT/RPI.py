# 라즈베리파이 > node 송출 코드

# 3파트
# 1. 카메라 캡쳐 -> UDP 8888포트로 Node에 스트림 송출, ffmpeg 죽으면 자동 재시작

# 2. 30초마다 열화상 데이터 768개 읽어서 MQTT로 Node에 전송
#    1883포트  baby/thermal로 publish

# 3. DHT22에서 60초마다 온습도 읽어 MQTT로 Node에 전송
#    1883포트 baby/environment로 publish

# 에러처리
# 센서 연결 끊겨도 다른 스레드는 계속 동작
# 열화상 이상값 감지 시 스킵
# DHT22 런타임 에러 시 재시도
# ffmpeg 죽으면 마이크 재탐지 후 재시작

#!/usr/bin/env python3
# -*- coding: utf-8 -*-

import subprocess
import signal
import sys
import time
import json
import threading

import paho.mqtt.client as mqtt
import adafruit_dht
import board
import busio
import numpy as np
import adafruit_mlx90640


SERVER_IP = "10.182.209.33"

STREAM_PORT = 8888
MQTT_PORT = 1883

MQTT_TOPIC_THERMAL = "baby/thermal"
MQTT_TOPIC_ENV = "baby/environment"


def kill_ffmpeg():
    subprocess.run("killall -9 ffmpeg 2>/dev/null", shell=True)


def handle_sigint(sig, frame):
    print("\nShutting down...")
    kill_ffmpeg()
    sys.exit(0)



def get_mic_num():
    result = subprocess.run(
        "arecord -l | grep '^card' | head -n 1 | awk '{print $2}' | tr -d ':'",
        shell=True,
        capture_output=True,
        text=True
    )
    mic_num = result.stdout.strip()
    return mic_num if mic_num else None


def build_ffmpeg_cmd(mic_num):
    return (
        f"ffmpeg -thread_queue_size 1024 -f v4l2 -input_format mjpeg "
        f"-video_size 1280x720 -framerate 10 -i /dev/video0 "
        f"-thread_queue_size 1024 -f alsa -i hw:{mic_num},0 "
        f"-c:v libx264 -preset ultrafast -tune zerolatency "
        f"-b:v 1000k -maxrate 1000k -bufsize 750k -g 10 "
        f"-c:a aac -b:a 256k "
        f"-f mpegts -mpegts_flags resend_headers "
        f"udp://{SERVER_IP}:{STREAM_PORT}?pkt_size=1316"
    )


def ffmpeg_loop():
    # 마이크가 잡힐 때까지 재시도 (부팅 직후 USB 인식이 늦을 수 있음)
    mic_num = get_mic_num()
    while not mic_num:
        print("Mic not found. Retry after 2 seconds...")
        time.sleep(2)
        mic_num = get_mic_num()

    print(f"Mic found: hw:{mic_num},0")
    print(f"Streaming to {SERVER_IP}:{STREAM_PORT}")

    proc = subprocess.Popen(
        build_ffmpeg_cmd(mic_num),
        shell=True,
        stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL
    )

    while True:
        time.sleep(2)

        if proc.poll() is not None:
            print("ffmpeg stopped. Restarting...")

            mic_num = get_mic_num()
            if not mic_num:
                print("Mic not found. Retry after 2 seconds...")
                time.sleep(2)
                continue

            proc = subprocess.Popen(
                build_ffmpeg_cmd(mic_num),
                shell=True,
                stdout=subprocess.DEVNULL,
                stderr=subprocess.DEVNULL
            )

            print("ffmpeg restarted.")


def init_thermal():
    i2c = busio.I2C(board.SCL, board.SDA, frequency=400000)
    mlx = adafruit_mlx90640.MLX90640(i2c)
    mlx.refresh_rate = adafruit_mlx90640.RefreshRate.REFRESH_0_5_HZ
    return mlx


def init_DHT22():
    return adafruit_dht.DHT22(board.D4, use_pulseio=False)


def publish_json(mqtt_client, topic, data):
    try:
        if not mqtt_client.is_connected():
            print(f"MQTT not connected. Skip publish: {topic}")
            return False

        payload = json.dumps(data)
        result = mqtt_client.publish(topic, payload)

        if result.rc != mqtt.MQTT_ERR_SUCCESS:
            print(f"MQTT publish failed: topic={topic}, rc={result.rc}")
            return False

        return True

    except Exception as e:
        print(f"MQTT publish exception: {e}")
        return False


def thermal_loop(mlx, mqtt_client):
    frame = [0.0] * 768

    while True:
        try:
            mlx.getFrame(frame)

            arr = np.array(frame)

            if np.any(np.isnan(arr)) or np.any(np.isinf(arr)):
                print("Invalid thermal values detected. Skip.")
                time.sleep(2)
                continue

            data = {
                "timestamp": time.time(),
                "frame": [round(x, 2) for x in arr.tolist()]
            }

            ok = publish_json(mqtt_client, MQTT_TOPIC_THERMAL, data)

            if ok:
                print("Thermal frame sent.")

        except Exception as e:
            print(f"Thermal send failed: {e}")

        time.sleep(30)


def on_connect(client, userdata, flags, reason_code, properties):
    print(f"MQTT connected: {reason_code}")


def on_disconnect(client, userdata, disconnect_flags, reason_code, properties):
    print(f"MQTT disconnected: {reason_code}")


def init_mqtt():
    client = mqtt.Client(mqtt.CallbackAPIVersion.VERSION2)

    client.on_connect = on_connect
    client.on_disconnect = on_disconnect

    print(f"Connecting MQTT broker: {SERVER_IP}:{MQTT_PORT}")

    client.connect_async(SERVER_IP, MQTT_PORT, keepalive=60)
    client.loop_start()

    return client


def main():
    signal.signal(signal.SIGINT, handle_sigint)

    kill_ffmpeg()

    print("Monitoring system booting...")

    mqtt_client = init_mqtt()

    t_ffmpeg = threading.Thread(target=ffmpeg_loop, daemon=True)
    t_ffmpeg.start()

    try:
        mlx = init_thermal()
        t_thermal = threading.Thread(
            target=thermal_loop,
            args=(mlx, mqtt_client),
            daemon=True
        )
        t_thermal.start()
        print("MLX90640 thermal thread started.")
    except Exception as e:
        print(f"MLX90640 init failed: {e}")

    try:
        dht_device = init_DHT22()
        print("DHT22 initialized.")
    except Exception as e:
        dht_device = None
        print(f"DHT22 init failed: {e}")

    while True:
        try:
            if dht_device is None:
                print("DHT22 not available.")
                time.sleep(60)
                continue

            temperature = dht_device.temperature
            humidity = dht_device.humidity

            if humidity is not None and temperature is not None:
                data = {
                    "timestamp": time.time(),
                    "temperature": temperature,
                    "humidity": humidity
                }

                ok = publish_json(mqtt_client, MQTT_TOPIC_ENV, data)

                if ok:
                    print(f"Env sent: {data}")
            else:
                print("DHT22 read failed.")

        except RuntimeError as e:
            print(f"DHT22 runtime error: {e}")
            try:
                dht_device.exit()
            except:
                pass
            time.sleep(2)
            try:
                dht_device = adafruit_dht.DHT22(board.D4, use_pulseio=False)
            except Exception as reinit_e:
                print(f"DHT22 재초기화 실패: {reinit_e}")

        except Exception as e:
            print(f"DHT22 error: {e}")

        time.sleep(60)


if __name__ == "__main__":
    sys.exit(main())