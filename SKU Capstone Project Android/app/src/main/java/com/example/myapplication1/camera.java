package com.example.myapplication1;

import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

import org.videolan.libvlc.LibVLC;
import org.videolan.libvlc.Media;
import org.videolan.libvlc.MediaPlayer;
import org.videolan.libvlc.util.VLCVideoLayout;

import java.util.ArrayList;

public class camera extends AppCompatActivity {

    private static final long RETRY_DELAY_MS = 2000;        // 끊긴 뒤 재연결 대기
    private static final long STALL_TIMEOUT_MS = 8000;      // 이 시간 동안 재생이 안 나가면 재연결
    private static final long WATCHDOG_INTERVAL_MS = 3000;  // 멈춤 감시 주기

    private LibVLC libVLC;
    private MediaPlayer mediaPlayer;
    private TextView statusText;
    private String streamUrl;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean isActive = false;   // 화면이 보이는 중인지 (onPause의 stop은 재연결 안 함)
    private long lastProgressAt = 0;    // 마지막으로 재생이 진행된 시각

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);

        statusText = findViewById(R.id.statusText);
        VLCVideoLayout playerView = findViewById(R.id.playerView);

        ArrayList<String> options = new ArrayList<>();
        options.add("--network-caching=150");
        options.add("--live-caching=150");
        options.add("--clock-jitter=0");
        options.add("--clock-synchro=0");
        options.add("--video-filter=transform");
        options.add("--transform-type=90");    // 90도 회전
        options.add("--avcodec-fast");         // SW 디코딩 속도 향상
        options.add("--avcodec-skiploopfilter=2"); // 화질 감소 cpu 부하 감소
        options.add("--drop-late-frames"); // 지연 누적 방지
        options.add("--skip-frames"); // 버벅 거리면 건너 뛰기

        libVLC = new LibVLC(this, options);
        mediaPlayer = new MediaPlayer(libVLC);
        mediaPlayer.attachViews(playerView, null, false, false);

        streamUrl = BuildConfig.BASE_URL + "/stream/streamingfile.m3u8";

        // 재생 상태 감시: 진행되면 시각 기록, 에러/끝나면 재연결
        mediaPlayer.setEventListener(event -> {
            switch (event.type) {
                case MediaPlayer.Event.Playing:
                case MediaPlayer.Event.TimeChanged:
                    lastProgressAt = SystemClock.elapsedRealtime();
                    statusText.setText("Live Streaming");
                    break;
                case MediaPlayer.Event.EncounteredError:
                case MediaPlayer.Event.EndReached:
                    scheduleReconnect();
                    break;
            }
        });
    }

    // 스트림 새로 연결 (처음 재생/재연결 공통)
    private void startStream() {
        if (mediaPlayer == null) return;
        statusText.setText("연결 중...");
        lastProgressAt = SystemClock.elapsedRealtime();

        Media media = new Media(libVLC, Uri.parse(streamUrl));
        // true면 회전이 안됨
        media.setHWDecoderEnabled(false, false);
        mediaPlayer.setMedia(media);
        media.release();
        mediaPlayer.play();
    }

    private void scheduleReconnect() {
        if (!isActive) return;
        statusText.setText("재연결 중...");
        handler.removeCallbacks(reconnectTask);
        handler.postDelayed(reconnectTask, RETRY_DELAY_MS);
    }

    private final Runnable reconnectTask = () -> {
        if (isActive) startStream();
    };

    // 에러 없이 멈춰 있는 경우(무한 버퍼링 등)도 잡기 위한 감시
    private final Runnable watchdog = new Runnable() {
        @Override
        public void run() {
            if (!isActive) return;
            if (SystemClock.elapsedRealtime() - lastProgressAt > STALL_TIMEOUT_MS) {
                scheduleReconnect();
                lastProgressAt = SystemClock.elapsedRealtime(); // 연속 재연결 방지
            }
            handler.postDelayed(this, WATCHDOG_INTERVAL_MS);
        }
    };

    @Override
    protected void onResume() {
        super.onResume();
        isActive = true;
        startStream();
        handler.postDelayed(watchdog, WATCHDOG_INTERVAL_MS);
    }

    @Override
    protected void onPause() {
        super.onPause();
        isActive = false;
        handler.removeCallbacksAndMessages(null);
        if (mediaPlayer != null) {
            mediaPlayer.stop();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);

        if (mediaPlayer != null) {
            mediaPlayer.setEventListener(null);
            mediaPlayer.stop();
            mediaPlayer.detachViews();
            mediaPlayer.release();
            mediaPlayer = null;
        }

        if (libVLC != null) {
            libVLC.release();
            libVLC = null;
        }
    }
}