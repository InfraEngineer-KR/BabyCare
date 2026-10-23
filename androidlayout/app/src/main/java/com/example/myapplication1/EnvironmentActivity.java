package com.example.myapplication1;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.Switch;
import android.widget.TextView;
import android.graphics.Color;
import android.content.res.ColorStateList;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class EnvironmentActivity extends AppCompatActivity {

    private static final String TAG = "EnvironmentActivity";

    private RecyclerView rvDevices;
    private TextView tvNoDevice;
    private TextView tvCurrentTemp;
    private TextView tvCurrentHumid;
    private TextView tvTempBadge;
    private TextView tvHumidBadge;
    private Switch switchAutoControl;

    private String accessToken = "";
    private String userId = "";
    private ApiService apiService;

    private double currentTemp = 0;
    private double currentHumid = 0;

    private DeviceAdapter deviceAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_environment);

        rvDevices        = findViewById(R.id.rvDevices);
        tvNoDevice       = findViewById(R.id.tvNoDevice);
        tvCurrentTemp    = findViewById(R.id.tvCurrentTemp);
        tvCurrentHumid   = findViewById(R.id.tvCurrentHumid);
        tvTempBadge      = findViewById(R.id.tvTempBadge);
        tvHumidBadge     = findViewById(R.id.tvHumidBadge);
        switchAutoControl = findViewById(R.id.switchAutoControl);

        // 자동제어 토글 색: 켜짐 파랑 / 꺼짐 회색
        switchAutoControl.setTrackTintList(new ColorStateList(
                new int[][]{ {android.R.attr.state_checked}, {} },
                new int[]{ Color.parseColor("#4A90E2"), Color.parseColor("#C4C9CC") }
        ));

        ImageButton btnBack = findViewById(R.id.btnBack);

        SharedPreferences pref = getSharedPreferences("UserPrefs", MODE_PRIVATE);
        accessToken = pref.getString("accessToken", "");
        userId = pref.getString("username", "");

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(BuildConfig.BASE_URL + "/")
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        apiService = retrofit.create(ApiService.class);

        rvDevices.setLayoutManager(new LinearLayoutManager(this));

        btnBack.setOnClickListener(v -> finish());

        // 자동제어 토글
        switchAutoControl.setOnCheckedChangeListener((btn, isChecked) -> {
            sendAutoControl(isChecked);
        });

        loadAutoControl();
        loadTemhu();
    }

    private void loadAutoControl() {
        apiService.getAutoControl("Bearer " + accessToken)
                .enqueue(new Callback<AuthModels.AutoControlResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<AuthModels.AutoControlResponse> call,
                                           @NonNull Response<AuthModels.AutoControlResponse> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            boolean isAuto = response.body().autoControl;
                            switchAutoControl.setOnCheckedChangeListener(null);
                            switchAutoControl.setChecked(isAuto);
                            switchAutoControl.setOnCheckedChangeListener((btn, checked) -> sendAutoControl(checked));
                            if (deviceAdapter != null) {
                                deviceAdapter.setAutoControl(isAuto);
                            }
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<AuthModels.AutoControlResponse> call, @NonNull Throwable t) {
                        Log.e(TAG, "자동제어 로드 실패: " + t.getMessage());
                    }
                });
    }

    private void sendAutoControl(boolean isAuto) {
        apiService.setAutoControl("Bearer " + accessToken,
                new AuthModels.AutoControlRequest(isAuto))
                .enqueue(new Callback<AuthModels.AutoControlResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<AuthModels.AutoControlResponse> call,
                                           @NonNull Response<AuthModels.AutoControlResponse> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            if (deviceAdapter != null) {
                                deviceAdapter.setAutoControl(isAuto);
                            }
                        } else {
                            Toast.makeText(EnvironmentActivity.this, "자동제어 설정 실패", Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<AuthModels.AutoControlResponse> call, @NonNull Throwable t) {
                        Log.e(TAG, "자동제어 설정 실패: " + t.getMessage());
                        Toast.makeText(EnvironmentActivity.this, "서버 연결 실패", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void loadTemhu() {
        apiService.getTemhuLatest(userId)
                .enqueue(new Callback<AuthModels.TemperHumilityResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<AuthModels.TemperHumilityResponse> call,
                                           @NonNull Response<AuthModels.TemperHumilityResponse> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            currentTemp = response.body().temperature;
                            currentHumid = response.body().humidity;
                            tvCurrentTemp.setText(String.format("%.1f°C", currentTemp));
                            tvCurrentHumid.setText(String.format("%.1f%%", currentHumid));
                            showTempBadge(currentTemp);
                            showHumidBadge(currentHumid);
                        }
                        loadDevices();
                    }

                    @Override
                    public void onFailure(@NonNull Call<AuthModels.TemperHumilityResponse> call, @NonNull Throwable t) {
                        Log.e(TAG, "온습도 로드 실패: " + t.getMessage());
                        loadDevices();
                    }
                });
    }
    // 영유아 권장 실내 온도 (18/28도는 서버 경고 기준과 동일)
    private void showTempBadge(double temp) {
        String text;
        if (temp < 18) text = "추움";
        else if (temp < 20) text = "서늘함";
        else if (temp <= 24) text = "쾌적함";
        else if (temp < 28) text = "따뜻함";
        else text = "더움";

        tvTempBadge.setText(text);
        tvTempBadge.setTextColor(text.equals("쾌적함")
                ? Color.parseColor("#4B626A")    // 원래 색
                : Color.parseColor("#D9822B"));  // 주의 (주황)
    }

    // 영유아 권장 실내 습도 40~60%
    private void showHumidBadge(double humid) {
        String text;
        if (humid < 40) text = "건조함";
        else if (humid <= 60) text = "적정";
        else text = "습함";

        tvHumidBadge.setText(text);
        tvHumidBadge.setTextColor(text.equals("적정")
                ? Color.parseColor("#795D43")    // 원래 색
                : Color.parseColor("#D9822B"));  // 주의 (주황)
    }
    private void loadDevices() {
        apiService.getDevices("Bearer " + accessToken)
                .enqueue(new Callback<AuthModels.DeviceResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<AuthModels.DeviceResponse> call,
                                           @NonNull Response<AuthModels.DeviceResponse> response) {

                        Log.d(TAG, "디바이스 목록 응답: " + response.code());

                        if (response.isSuccessful()
                                && response.body() != null
                                && response.body().ok
                                && response.body().devices != null
                                && !response.body().devices.isEmpty()) {

                            deviceAdapter = new DeviceAdapter(
                                    response.body().devices,
                                    apiService,
                                    accessToken
                            );
                            deviceAdapter.setAutoControl(switchAutoControl.isChecked());

                            rvDevices.setAdapter(deviceAdapter);
                            tvNoDevice.setVisibility(View.GONE);

                        } else {
                            tvNoDevice.setVisibility(View.VISIBLE);
                            Toast.makeText(EnvironmentActivity.this,
                                    "연동된 기기가 없습니다.",
                                    Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<AuthModels.DeviceResponse> call, @NonNull Throwable t) {
                        Log.e(TAG, "디바이스 목록 실패: " + t.getMessage());
                        Toast.makeText(EnvironmentActivity.this,
                                "서버 연결 실패",
                                Toast.LENGTH_SHORT).show();
                    }
                });
    }
}
