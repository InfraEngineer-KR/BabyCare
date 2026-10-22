package com.example.myapplication1;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.widget.CalendarView;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class Schedule extends AppCompatActivity {

    private static final String CHANNEL_ID = "vaccine_channel";

    private final List<AuthModels.VaccineResponse> vaccineList = new ArrayList<>();

    private ApiService apiService;
    private VaccineAdapter adapter;

    private TextView tvNextVaccineDate;
    private CardView cardUpcomingSummary;
    private CalendarView calendarView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_schedule);

        initView();

        RecyclerView recyclerView = findViewById(R.id.rv_vaccine_list);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        adapter = new VaccineAdapter(vaccineList);
        recyclerView.setAdapter(adapter);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        createNotificationChannel();
        checkNotificationPermission();

        String BASE_URL =
                "http://"
                        + BuildConfig.SERVER_IP
                        + ":"
                        + BuildConfig.SERVER_PORT
                        + "/";

        Log.d("SERVER_URL", BASE_URL);

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        apiService = retrofit.create(ApiService.class);

        loadData();

        calendarView.setOnDateChangeListener((view, year, month, dayOfMonth) -> {
            String date =
                    year + "/" +
                            (month + 1) + "/" +
                            dayOfMonth;
            Log.d("Schedule", "선택된 날짜: " + date);
        });
    }

    private void initView() {
        tvNextVaccineDate = findViewById(R.id.tvNextVaccineDate);
        cardUpcomingSummary = findViewById(R.id.cardUpcomingSummary);
        calendarView = findViewById(R.id.calendarView);
    }

    private void loadData() {

        SharedPreferences sharedPref =
                getSharedPreferences("UserPrefs", MODE_PRIVATE);

        String realUserId =
                sharedPref.getString("username", "");

        Log.d("USER_CHECK", "저장된 username = " + realUserId);

        if (realUserId.isEmpty()) {
            Log.e("USER_CHECK", "SharedPreferences 비어있음 → 테스트 유저 사용");
            realUserId = "test1";
            Toast.makeText(this, "테스트 계정으로 실행됨", Toast.LENGTH_SHORT).show();
        }

        apiService.getVaccineSchedule(realUserId)
                .enqueue(new Callback<List<AuthModels.VaccineResponse>>() {

                    @Override
                    public void onResponse(
                            @NonNull Call<List<AuthModels.VaccineResponse>> call,
                            @NonNull Response<List<AuthModels.VaccineResponse>> response
                    ) {
                        Log.d("API", "응답 코드 = " + response.code());

                        if (response.isSuccessful() && response.body() != null) {
                            Log.d("API", "데이터 개수 = " + response.body().size());

                            vaccineList.clear();
                            vaccineList.addAll(response.body());

                            updateUI();

                        } else {
                            Log.e("API", "응답 실패 or body null");
                            Toast.makeText(Schedule.this, "데이터 없음", Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onFailure(
                            @NonNull Call<List<AuthModels.VaccineResponse>> call,
                            @NonNull Throwable t
                    ) {
                        Log.e("API_ERROR", t.getMessage() != null ? t.getMessage() : "Unknown Error");
                        Toast.makeText(Schedule.this, "서버 연결 실패", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void updateUI() {

        // ✅ 접종 완료(dDay < 0) 제외, 90일 이내(dDay <= 90)만 필터링
        List<AuthModels.VaccineResponse> filtered = new ArrayList<>();
        for (AuthModels.VaccineResponse v : vaccineList) {
            if (v.dDay >= 0 && v.dDay <= 90) {
                filtered.add(v);
            }
        }

        vaccineList.clear();
        vaccineList.addAll(filtered);
        adapter.notifyDataSetChanged();

        if (!vaccineList.isEmpty()) {

            AuthModels.VaccineResponse next = vaccineList.get(0);

            String statusText;

            if (next.dDay == 0) {
                statusText =
                        "오늘 접종 예정\n"
                                + "[" + next.name + "]\n"
                                + next.dueDate;
            } else {
                statusText =
                        String.format(
                                "D-%d [%s]\n%s 예정",
                                next.dDay,
                                next.name,
                                next.dueDate
                        );
            }

            tvNextVaccineDate.setText(statusText);

            // [비활성화] 접종일 수정 기능
            // - 앱: 수정 창(showEditDialog) + 서버 요청(updateVaccineOnServer)은 구현됨
            // - 서버: PUT /api/vaccine/update/:id API와 변경 날짜 저장 구조가 없어 항상 "수정 실패"
            // → 서버 API를 만든 뒤 아래 줄 주석을 풀면 활성화됨
            //서버에 사용자별 변경 날짜를 저장하는 컬렉션과 PUT /api/vaccine/update/:id API를 추가합니다.
            //서버의 getVaccineSchedule에서 저장된 날짜로 덮어쓰도록 수정합니다.
            // 앱 196번째 줄 주석을 풉니다.
            // cardUpcomingSummary.setOnClickListener(v -> showEditDialog(next));

            sendNotification(
                    "접종 알림",
                    "다가오는 접종 일정이 " + vaccineList.size() + "건 있습니다."
            );

        } else {
            tvNextVaccineDate.setText("현재 예정된 접종이 없습니다.");
            cardUpcomingSummary.setOnClickListener(null);
        }
    }

    private void showEditDialog(AuthModels.VaccineResponse item) {

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("접종 일정 수정");

        final EditText input = new EditText(this);
        input.setText(item.dueDate);
        builder.setView(input);

        builder.setPositiveButton(
                "수정",
                (dialog, id) -> updateVaccineOnServer(item.id, input.getText().toString())
        );
        builder.setNegativeButton("취소", null);
        builder.show();
    }

    private void updateVaccineOnServer(String vaccineId, String newDate) {

        apiService.updateVaccine(vaccineId, new AuthModels.VaccineUpdate(newDate))
                .enqueue(new Callback<Void>() {

                    @Override
                    public void onResponse(
                            @NonNull Call<Void> call,
                            @NonNull Response<Void> response
                    ) {
                        if (response.isSuccessful()) {
                            Toast.makeText(Schedule.this, "수정 완료", Toast.LENGTH_SHORT).show();
                            loadData();
                        } else {
                            Toast.makeText(Schedule.this, "수정 실패", Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onFailure(
                            @NonNull Call<Void> call,
                            @NonNull Throwable t
                    ) {
                        Toast.makeText(Schedule.this, "서버 연결 실패", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void createNotificationChannel() {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel =
                    new NotificationChannel(
                            CHANNEL_ID,
                            "예방접종 알림",
                            NotificationManager.IMPORTANCE_DEFAULT
                    );

            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
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

    @SuppressLint("MissingPermission")
    private void sendNotification(String title, String content) {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
        ) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        NotificationCompat.Builder builder =
                new NotificationCompat.Builder(this, CHANNEL_ID)
                        .setSmallIcon(android.R.drawable.ic_dialog_info)
                        .setContentTitle(title)
                        .setContentText(content)
                        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                        .setAutoCancel(true);

        NotificationManagerCompat.from(this).notify(1, builder.build());
    }
}