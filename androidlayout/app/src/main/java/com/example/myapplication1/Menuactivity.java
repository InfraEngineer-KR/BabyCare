package com.example.myapplication1;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.Manifest;
import android.content.pm.PackageManager;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.Date;
import java.util.TimeZone;
import java.util.List;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.annotation.NonNull;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class Menuactivity extends AppCompatActivity {

    private String userId = "";
    private ApiService apiService;

    private TextView tvToday, tvGreeting;
    private TextView tvTemperature, tvHumidity;
    private TextView tvSleepScoreBadge, tvSleepTime, tvSleepStatus;
    private TextView tvVaccineSubtitle;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_menuactivity);

        apiService = RetrofitClient.getApiService();

        tvToday = findViewById(R.id.tvToday);
        tvGreeting = findViewById(R.id.tvGreeting);

        tvTemperature = findViewById(R.id.tvTemperature);
        tvHumidity = findViewById(R.id.tvHumidity);
        tvSleepScoreBadge = findViewById(R.id.tvSleepScoreBadge);
        tvSleepTime = findViewById(R.id.tvSleepTime);
        tvSleepStatus = findViewById(R.id.tvSleepStatus);
        tvVaccineSubtitle = findViewById(R.id.tvVaccineSubtitle);

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
        // 요약 카드도 누르면 해당 화면으로
        findViewById(R.id.cardEnvironmentSummary).setOnClickListener(v ->
                startActivity(new Intent(Menuactivity.this, EnvironmentActivity.class)));

        findViewById(R.id.cardSleepSummary).setOnClickListener(v ->
                startActivity(new Intent(Menuactivity.this, GrapeActivity.class)));

        findViewById(R.id.cardVaccineSchedule).setOnClickListener(v ->
                startActivity(new Intent(Menuactivity.this, Schedule.class)));
        // 저장된 로그인 userId 가져오기
        SharedPreferences prefs =
                getSharedPreferences(
                        "UserPrefs",
                        MODE_PRIVATE
                );

        userId =
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
    // 다른 화면 갔다 돌아올 때마다 최신 값으로 갱신
    @Override
    protected void onResume() {
        super.onResume();
        showDateAndGreeting();
        loadEnvironment();  
        loadNextVaccine();        // 추가
    }

    private void showDateAndGreeting() {
        Calendar now = Calendar.getInstance();
        tvToday.setText(new SimpleDateFormat("M월 d일 EEEE", Locale.KOREAN).format(now.getTime()));

        int hour = now.get(Calendar.HOUR_OF_DAY);
        String greeting;
        if (hour >= 5 && hour < 12) greeting = "좋은 아침이에요, 엄마 아빠!";
        else if (hour >= 12 && hour < 18) greeting = "좋은 오후예요, 엄마 아빠!";
        else if (hour >= 18 && hour < 22) greeting = "편안한 저녁 보내세요, 엄마 아빠!";
        else greeting = "아기가 푹 자는 밤이에요, 엄마 아빠!";
        tvGreeting.setText(greeting);
    }
    // 최신 온습도 (환경 화면과 같은 API)
    private void loadEnvironment() {
        if (userId.isEmpty()) return;

        apiService.getTemhuLatest(userId).enqueue(new Callback<AuthModels.TemperHumilityResponse>() {
            @Override
            public void onResponse(@NonNull Call<AuthModels.TemperHumilityResponse> call,
                                   @NonNull Response<AuthModels.TemperHumilityResponse> response) {
                AuthModels.TemperHumilityResponse data = response.body();
                if (!response.isSuccessful() || data == null) {
                    tvTemperature.setText("--°C");
                    tvHumidity.setText("💧 습도 --%");
                    tvSleepScoreBadge.setText("--");
                    tvSleepTime.setText("데이터 없음");
                    tvSleepStatus.setText("센서 연결을 확인하세요");
                    return;
                }

                tvTemperature.setText(String.format(Locale.KOREA, "%.1f°C", data.temperature));
                tvHumidity.setText(String.format(Locale.KOREA, "💧 습도 %.0f%%", data.humidity));

                // 수면 환경 점수 + 등급
                if (data.sleepScore != null) {
                    tvSleepScoreBadge.setText(data.sleepScore + "점");
                    tvSleepTime.setText(scoreGrade(data.sleepScore));
                } else {
                    tvSleepScoreBadge.setText("--");
                    tvSleepTime.setText("측정 중");
                }

                // 마지막 업데이트 시각 (울음 감지된 기록이면 표시)
                String time = toKstTime(data.timestamp);
                String timeText = time != null ? time : "--:--";
                if (Boolean.TRUE.equals(data.cryDetected)) {
                    tvSleepStatus.setText("울음 감지 · " + timeText);
                } else {
                    tvSleepStatus.setText("업데이트 " + timeText);
                }
            }

            @Override
            public void onFailure(@NonNull Call<AuthModels.TemperHumilityResponse> call, @NonNull Throwable t) {
                tvTemperature.setText("--°C");
                tvHumidity.setText("💧 서버 연결 실패");
            }
        });
    }
    // 가장 가까운 예정 접종 (서버가 날짜순 정렬해서 줌)
    private void loadNextVaccine() {
        if (userId.isEmpty()) return;

        apiService.getVaccineSchedule(userId).enqueue(new Callback<List<AuthModels.VaccineResponse>>() {
            @Override
            public void onResponse(@NonNull Call<List<AuthModels.VaccineResponse>> call,
                                   @NonNull Response<List<AuthModels.VaccineResponse>> response) {
                List<AuthModels.VaccineResponse> list = response.body();
                if (!response.isSuccessful() || list == null) {
                    tvVaccineSubtitle.setText("일정을 불러오지 못했어요");
                    return;
                }

                for (AuthModels.VaccineResponse v : list) {
                    if (v.dDay < 0) continue;   // 이미 지난 접종은 건너뜀

                    String when = v.dDay == 0 ? "오늘" : "D-" + v.dDay;
                    tvVaccineSubtitle.setText(when + " · " + v.name + " " + v.degree + "차");
                    return;
                }
                tvVaccineSubtitle.setText("예정된 접종이 없어요");
            }

            @Override
            public void onFailure(@NonNull Call<List<AuthModels.VaccineResponse>> call, @NonNull Throwable t) {
                tvVaccineSubtitle.setText("서버 연결 실패");
            }
        });
    }
    // 수면 환경 점수(0~100) → 등급
    // 울음 감지 시 항상 55점 이하 → "주의", 온도 경고(18/28도) 수준이면 "보통" 이하
    private String scoreGrade(int score) {
        if (score >= 85) return "아주 좋음";
        if (score >= 75) return "좋음";
        if (score >= 60) return "보통";
        return "주의";
    }

    // 서버 시각(UTC ISO) → 한국 시간 "HH:mm"
    private String toKstTime(String iso) {
        if (iso == null) return null;
        String[] patterns = {"yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", "yyyy-MM-dd'T'HH:mm:ss'Z'"};
        for (String p : patterns) {
            try {
                SimpleDateFormat in = new SimpleDateFormat(p, Locale.US);
                in.setTimeZone(TimeZone.getTimeZone("UTC"));
                Date d = in.parse(iso);
                SimpleDateFormat out = new SimpleDateFormat("HH:mm", Locale.KOREA);
                out.setTimeZone(TimeZone.getTimeZone("Asia/Seoul"));
                return out.format(d);
            } catch (Exception ignored) {
            }
        }
        return null;
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