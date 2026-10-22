package com.example.myapplication1;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.Manifest;
import android.content.pm.PackageManager;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class Menuactivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_menuactivity);

        // 홈 디자인 변경으로 미리보기 영상은 사용하지 않음 (영상은 카메라 화면에서)
        ImageView ivProfile = findViewById(R.id.ivHeaderIcon);

        LinearLayout btnEnvironment =
                findViewById(R.id.btn_environment);

        LinearLayout btnCamera =
                findViewById(R.id.btn_camera);

        LinearLayout btnSchedule =
                findViewById(R.id.btn_schedule);

        LinearLayout btnGraph =
                findViewById(R.id.btn_graph);

        LinearLayout btnReport =
                findViewById(R.id.btn_report);

        LinearLayout btnPolicy =
                findViewById(R.id.btn_policy);

        ivProfile.setOnClickListener(v ->
                startActivity(
                        new Intent(
                                Menuactivity.this,
                                mypage.class
                        )
                )
        );

        btnEnvironment.setOnClickListener(v ->
                startActivity(
                        new Intent(
                                Menuactivity.this,
                                EnvironmentActivity.class
                        )
                )
        );

        btnCamera.setOnClickListener(v ->
                startActivity(
                        new Intent(
                                Menuactivity.this,
                                camera.class
                        )
                )
        );

        btnSchedule.setOnClickListener(v ->
                startActivity(
                        new Intent(
                                Menuactivity.this,
                                Schedule.class
                        )
                )
        );

        btnGraph.setOnClickListener(v ->
                startActivity(
                        new Intent(
                                Menuactivity.this,
                                GrapeActivity.class
                        )
                )
        );

        btnReport.setOnClickListener(v ->
                startActivity(
                        new Intent(
                                Menuactivity.this,
                                GptReportActivity.class
                        )
                )
        );

        btnPolicy.setOnClickListener(v ->
                startActivity(
                        new Intent(
                                Menuactivity.this,
                                policy.class
                        )
                )
        );

        // 저장된 로그인 userId 가져오기
        SharedPreferences prefs =
                getSharedPreferences(
                        "UserPrefs",
                        MODE_PRIVATE
                );

        String userId =
                prefs.getString("username", "");

        // 알림 권한 요청 (Android 13+, 울음/온도 알림 표시에 필요)
        checkNotificationPermission();

        // 울음 감지 서비스 시작
        Intent serviceIntent =
                new Intent(
                        this,
                        CryAlertService.class
                );

        serviceIntent.putExtra("userId", userId);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            startForegroundService(serviceIntent);

        } else {

            startService(serviceIntent);
        }
    }
    private void checkNotificationPermission() {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                        this,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS},
                        101
                );
            }
        }
    }
}