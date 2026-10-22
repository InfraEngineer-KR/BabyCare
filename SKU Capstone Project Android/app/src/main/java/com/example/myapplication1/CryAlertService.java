package com.example.myapplication1;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import org.json.JSONObject;

import java.net.URISyntaxException;

import io.socket.client.IO;
import io.socket.client.Socket;

import com.example.myapplication1.BuildConfig;

public class CryAlertService extends Service {

    private static final String TAG = "CryAlertService";
    private static final String CHANNEL_ID = "CryAlertChannel";

    // 환경변수 기반 서버 주소
    private static final String SERVER_URL = BuildConfig.BASE_URL;

    private Socket socket;
    private String userId;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        // MenuActivity에서 userId 받아오기
        if (intent != null && intent.getStringExtra("userId") != null) {
            userId = intent.getStringExtra("userId");
        }

        // 시스템이 서비스를 재시작하면 intent가 null → 저장된 아이디 사용
        if (userId == null || userId.isEmpty()) {
            userId = getSharedPreferences("UserPrefs", MODE_PRIVATE)
                    .getString("username", "");
        }

        // 포그라운드 서비스 시작
        startForeground(1, buildForegroundNotification());

        // 소켓 연결
        connectSocket();

        return START_STICKY;
    }

    /**
     * Socket 연결
     */
    private void connectSocket() {
        // 이미 만든 소켓이 있으면 새로 만들지 않음 (알림 중복 방지)
        if (socket != null) {
            if (socket.connected()) {
                socket.emit("register", userId);   // 아이디만 다시 등록
            } else {
                socket.connect();                  // 끊겨 있으면 다시 연결
            }
            return;
        }

        try {

            IO.Options options = new IO.Options();
            options.reconnection = true;
            options.reconnectionAttempts = Integer.MAX_VALUE;
            options.reconnectionDelay = 1000;

            socket = IO.socket(SERVER_URL, options);

            /**
             * 소켓 연결 성공
             */
            socket.on(Socket.EVENT_CONNECT, args -> {

                Log.d(TAG, "소켓 연결 성공");

                // userId 방 입장
                socket.emit("register", userId);
            });

            /**
             * 울음 감지 알림
             */
            socket.on("cryAlert", args -> {

                Log.d(TAG, "울음 감지 알림 수신!");

                try {

                    JSONObject data = (JSONObject) args[0];

                    double probability =
                            data.optDouble("cryProbability", 0.0);

                    Log.d(TAG, "울음 확률: " + probability);

                } catch (Exception e) {

                    Log.e(TAG, "울음 데이터 파싱 오류", e);
                }

                // 진동
                vibrate();

                // 알림
                showCryNotification();
            });

            /**
             * 온도 경고 알림
             */
            socket.on("temperatureAlert", args -> {

                Log.d(TAG, "온도 경고 수신!");

                try {

                    JSONObject data = (JSONObject) args[0];

                    String type =
                            data.optString("type");

                    String message =
                            data.optString("message");

                    Log.d(TAG, "알림 타입: " + type);
                    Log.d(TAG, "메시지: " + message);

                    // 진동
                    vibrate();

                    // 알림 표시
                    showTemperatureNotification(message);

                } catch (Exception e) {

                    Log.e(TAG, "temperatureAlert 파싱 오류", e);
                }
            });

            /**
             * 연결 종료
             */
            socket.on(Socket.EVENT_DISCONNECT, args -> {

                Log.d(TAG, "소켓 연결 끊김");
            });

            socket.connect();

        } catch (URISyntaxException e) {

            Log.e(TAG, "소켓 URL 오류", e);
        }
    }

    /**
     * 진동 실행
     */
    private void vibrate() {

        Vibrator vibrator =
                (Vibrator) getSystemService(VIBRATOR_SERVICE);

        if (vibrator == null) return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            long[] pattern = {0, 500, 300, 500};

            vibrator.vibrate(
                    VibrationEffect.createWaveform(
                            pattern,
                            -1
                    )
            );

        } else {

            vibrator.vibrate(
                    new long[]{0, 500, 300, 500},
                    -1
            );
        }
    }

    /**
     * 울음 알림
     */
    private void showCryNotification() {

        NotificationManager manager =
                (NotificationManager) getSystemService(
                        NOTIFICATION_SERVICE
                );

        if (manager == null) return;

        NotificationCompat.Builder builder =
                new NotificationCompat.Builder(
                        this,
                        CHANNEL_ID
                )
                        .setSmallIcon(R.mipmap.ic_launcher)
                        .setContentTitle("👶 울음 감지!")
                        .setContentText(
                                "아이가 울고 있습니다. 확인해주세요."
                        )
                        .setPriority(
                                NotificationCompat.PRIORITY_HIGH
                        )
                        .setAutoCancel(true);

        manager.notify(2, builder.build());
    }

    /**
     * 온도 경고 알림
     */
    private void showTemperatureNotification(String message) {

        NotificationManager manager =
                (NotificationManager) getSystemService(
                        NOTIFICATION_SERVICE
                );

        if (manager == null) return;

        NotificationCompat.Builder builder =
                new NotificationCompat.Builder(
                        this,
                        CHANNEL_ID
                )
                        .setSmallIcon(R.mipmap.ic_launcher)
                        .setContentTitle("🌡️ 온도 경고")
                        .setContentText(message)
                        .setPriority(
                                NotificationCompat.PRIORITY_HIGH
                        )
                        .setAutoCancel(true);

        manager.notify(3, builder.build());
    }

    /**
     * 포그라운드 알림
     */
    private Notification buildForegroundNotification() {

        NotificationManager manager =
                (NotificationManager) getSystemService(
                        NOTIFICATION_SERVICE
                );

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            NotificationChannel channel =
                    new NotificationChannel(
                            CHANNEL_ID,
                            "울음 감지 서비스",
                            NotificationManager.IMPORTANCE_HIGH
                    );

            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }

        return new NotificationCompat.Builder(
                this,
                CHANNEL_ID
        )
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("모니터링 서비스 실행 중")
                .setContentText(
                        "울음 및 온도 상태를 감지하고 있습니다."
                )
                .setPriority(
                        NotificationCompat.PRIORITY_LOW
                )
                .build();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {

        super.onDestroy();

        if (socket != null) {

            socket.disconnect();
            socket.off();
            socket = null;
        }
    }
}