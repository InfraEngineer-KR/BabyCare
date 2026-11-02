package com.example.myapplication1;

import android.app.DatePickerDialog;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class mypage extends AppCompatActivity {

    private ApiService apiService;
    private EditText etEditName, etBabyBirth, etCurrentPw, etEditPw;
    private Button btnSaveAll;
    private ImageButton btnAddSmartThings;
    private LinearLayout itemEmptyDevice, layoutDeviceList;
    private TextView tvBodyTempStatus;

    private String loginUserEmail;
    private String jwtToken;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_mypage);

        loadUserInfo();
        initViews();
        setupListeners();
        loadRegisteredDevices();
        loadCalibration();
    }

    private void loadUserInfo() {
        SharedPreferences sharedPref = getSharedPreferences("UserPrefs", MODE_PRIVATE);
        String token = sharedPref.getString("accessToken", "");
        loginUserEmail = sharedPref.getString("userEmail", "");
        this.jwtToken = "Bearer " + token;

        Log.d("JWT_CHECK", "accessToken = " + token);
        Log.d("JWT_CHECK", "jwtToken = " + jwtToken);
        Log.d("JWT_CHECK", "userEmail = " + loginUserEmail);
    }

    private void initViews() {
        apiService = RetrofitClient.getApiService();
        etEditName = findViewById(R.id.et_edit_name);
        etBabyBirth = findViewById(R.id.et_edit_baby_birth);
        etCurrentPw = findViewById(R.id.et_current_pw);
        etEditPw = findViewById(R.id.et_edit_pw);
        btnSaveAll = findViewById(R.id.btn_save_all);
        btnAddSmartThings = findViewById(R.id.btn_add_smartthings);
        itemEmptyDevice = findViewById(R.id.item_empty_device);
        layoutDeviceList = findViewById(R.id.layout_device_list);
        tvBodyTempStatus = findViewById(R.id.tv_body_temp_status);

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
    }

    private void setupListeners() {
        etBabyBirth.setOnClickListener(v -> {
            Calendar c = Calendar.getInstance();
            new DatePickerDialog(this, (view, y, m, d) -> {
                etBabyBirth.setText(String.format("%d-%02d-%02d", y, m + 1, d));
            }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show();
        });

        btnAddSmartThings.setOnClickListener(v -> showSmartThingsRegistrationDialog());
        btnSaveAll.setOnClickListener(v -> updateProfileProcess());

        findViewById(R.id.btn_calibrate_now).setOnClickListener(v -> confirmNowCalibration());
        findViewById(R.id.btn_calibrate_days).setOnClickListener(v -> loadCalibrationDays());
    }

    private void updateProfileProcess() {
        String newName = etEditName.getText().toString();
        String babyBirth = etBabyBirth.getText().toString();
        String currentPw = etCurrentPw.getText().toString();
        String newPw = etEditPw.getText().toString();

        if (!newPw.isEmpty() && newPw.length() < 13) {
            Toast.makeText(this, "비밀번호는 13자리 이상이어야 합니다!", Toast.LENGTH_SHORT).show();
            return;
        }

        // 🔥 핵심: username = 아이디 / name = 표시이름
        AuthModels.UpdateProfileRequest request = new AuthModels.UpdateProfileRequest(
                loginUserEmail,  // ← 여기에 "test2" 같은 아이디 들어있음
                newName,         // ← roh (표시 이름)
                babyBirth,
                currentPw,
                newPw
        );

        apiService.updateProfile(request).enqueue(new Callback<AuthModels.UserResponse>() {
            @Override
            public void onResponse(Call<AuthModels.UserResponse> call, Response<AuthModels.UserResponse> response) {
                if (response.isSuccessful()) {
                    Toast.makeText(mypage.this, "수정 성공!", Toast.LENGTH_SHORT).show();
                    finish();
                } else {
                    Toast.makeText(mypage.this, "수정 실패: 정보를 확인하세요.", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<AuthModels.UserResponse> call, Throwable t) {
                Toast.makeText(mypage.this, "서버 연결 오류", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void loadRegisteredDevices() {
        apiService.getDevices(jwtToken).enqueue(new Callback<AuthModels.DeviceResponse>() {
            @Override
            public void onResponse(Call<AuthModels.DeviceResponse> call, Response<AuthModels.DeviceResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    displayDevices(response.body().devices);
                }
            }
            @Override public void onFailure(Call<AuthModels.DeviceResponse> call, Throwable t) {}
        });
    }

    private void showSmartThingsRegistrationDialog() {
        final EditText etToken = new EditText(this);
        etToken.setHint("PAT 입력");
        new AlertDialog.Builder(this).setTitle("SmartThings 연동").setView(etToken)
                .setPositiveButton("연동", (dialog, which) -> sendTokenToServer(etToken.getText().toString().trim())).show();
    }

    private void sendTokenToServer(String token) {
        if (token == null || token.trim().isEmpty()) {
            Toast.makeText(this, "토큰을 입력하세요.", Toast.LENGTH_SHORT).show();
            return;
        }

        Log.d("ST_DEBUG", "loginUserEmail = " + loginUserEmail);
        Log.d("ST_DEBUG", "jwtToken = " + jwtToken);
        Log.d("ST_DEBUG", "token = " + token.trim());

        AuthModels.STTokenRequest request =
                new AuthModels.STTokenRequest(loginUserEmail, token.trim());

        apiService.registerSTToken(jwtToken, request)
                .enqueue(new Callback<AuthModels.DeviceResponse>() {
                    @Override
                    public void onResponse(Call<AuthModels.DeviceResponse> call,
                                           Response<AuthModels.DeviceResponse> response) {

                        Log.d("ST_DEBUG", "response code = " + response.code());

                        if (response.isSuccessful() && response.body() != null) {
                            displayDevices(response.body().devices);
                            Toast.makeText(mypage.this, "연동 성공!", Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(
                                    mypage.this,
                                    "연동 실패: " + response.code(),
                                    Toast.LENGTH_LONG
                            ).show();

                            try {
                                if (response.errorBody() != null) {
                                    Log.e("ST_ERROR", "errorBody = " + response.errorBody().string());
                                }
                            } catch (Exception e) {
                                Log.e("ST_ERROR", "errorBody 읽기 실패", e);
                            }
                        }
                    }

                    @Override
                    public void onFailure(Call<AuthModels.DeviceResponse> call, Throwable t) {
                        Toast.makeText(mypage.this, "서버 연결 오류", Toast.LENGTH_SHORT).show();
                        Log.e("ST_ERROR", "onFailure = " + t.getMessage(), t);
                    }
                });
    }

    private void displayDevices(List<AuthModels.Device> devices) {
        if (devices == null || devices.isEmpty()) {
            itemEmptyDevice.setVisibility(View.VISIBLE);
            layoutDeviceList.removeAllViews();
            return;
        }
        itemEmptyDevice.setVisibility(View.GONE);
        layoutDeviceList.removeAllViews();
        for (AuthModels.Device device : devices) {
            TextView tv = new TextView(this);
            tv.setText("✔ " + (device.label != null ? device.label : device.name));
            tv.setPadding(45, 40, 45, 40);
            tv.setBackgroundResource(R.drawable.bg_edittext_rounded);
            layoutDeviceList.addView(tv);
        }
    }

    // =========================================================
    // 아기 정상 체온 보정
    // =========================================================

    private void loadCalibration() {
        apiService.getCalibration().enqueue(new Callback<Map<String, Object>>() {
            @Override
            public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    showCalibration(response.body());
                } else {
                    tvBodyTempStatus.setText("체온 보정 정보를 불러오지 못했습니다.");
                }
            }

            @Override
            public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                tvBodyTempStatus.setText("서버에 연결할 수 없습니다.");
            }
        });
    }

    private void showCalibration(Map<String, Object> body) {
        Object base = body.get("baseTemp");
        Object delta = body.get("delta");

        if (!(base instanceof Number)) {
            tvBodyTempStatus.setText("체온 보정 정보를 불러오지 못했습니다.");
            return;
        }

        String text = String.format(Locale.KOREA, "정상 체온 %.1f°C", ((Number) base).doubleValue());
        text += Boolean.TRUE.equals(body.get("calibrated")) ? " (보정됨)" : " (기본값)";
        if (delta instanceof Number) {
            text += String.format(Locale.KOREA, "\n±%.1f°C 이상 벗어나면 알림", ((Number) delta).doubleValue());
        }
        tvBodyTempStatus.setText(text);
    }

    // 지금 상태로 보정 (최근 5분)
    private void confirmNowCalibration() {
        new AlertDialog.Builder(this)
                .setTitle("지금 상태로 보정")
                .setMessage("최근 5분 동안 측정된 체온으로 정상 체온을 다시 맞춥니다.\n아이가 정상 체온일 때만 진행해 주세요.")
                .setPositiveButton("보정", (dialog, which) -> {
                    Map<String, Object> body = new HashMap<>();
                    body.put("mode", "now");
                    sendCalibration(body);
                })
                .setNegativeButton("취소", null)
                .show();
    }

    // 기간으로 보정: 날짜 목록 불러오기
    private void loadCalibrationDays() {
        apiService.getCalibrationDays().enqueue(new Callback<Map<String, Object>>() {
            @Override
            public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                Object days = response.body() != null ? response.body().get("days") : null;
                if (!response.isSuccessful() || !(days instanceof List) || ((List<?>) days).isEmpty()) {
                    Toast.makeText(mypage.this, "최근 측정 기록이 없습니다.", Toast.LENGTH_SHORT).show();
                    return;
                }
                showCalibrationDaysDialog((List<?>) days);
            }

            @Override
            public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                Toast.makeText(mypage.this, "서버 연결 오류", Toast.LENGTH_SHORT).show();
            }
        });
    }

    // 기간으로 보정: 아팠던 날은 체크 해제
    private void showCalibrationDaysDialog(List<?> days) {
        String[] dates = new String[days.size()];
        String[] labels = new String[days.size()];
        boolean[] checked = new boolean[days.size()];

        for (int i = 0; i < days.size(); i++) {
            Map<?, ?> day = (Map<?, ?>) days.get(i);
            String date = String.valueOf(day.get("date"));
            int count = ((Number) day.get("count")).intValue();
            double avg = ((Number) day.get("avg")).doubleValue();

            dates[i] = date;
            labels[i] = String.format(Locale.KOREA, "%s   측정 %,d개   평균 %.1f°C",
                    date.substring(5).replace('-', '/'), count, avg);
            checked[i] = true;
        }

        new AlertDialog.Builder(this)
                .setTitle("아팠던 날은 체크를 해제하세요")
                .setMultiChoiceItems(labels, checked, (dialog, which, isChecked) -> checked[which] = isChecked)
                .setPositiveButton("선택한 날짜로 보정", (dialog, which) -> {
                    List<String> selected = new ArrayList<>();
                    for (int i = 0; i < dates.length; i++) {
                        if (checked[i]) selected.add(dates[i]);
                    }
                    if (selected.isEmpty()) {
                        Toast.makeText(this, "날짜를 하나 이상 선택해 주세요.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    Map<String, Object> body = new HashMap<>();
                    body.put("mode", "days");
                    body.put("days", selected);
                    sendCalibration(body);
                })
                .setNegativeButton("취소", null)
                .show();
    }

    private void sendCalibration(Map<String, Object> body) {
        apiService.calibrate(body).enqueue(new Callback<Map<String, Object>>() {
            @Override
            public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    showCalibration(response.body());
                    Toast.makeText(mypage.this, "체온 보정 완료", Toast.LENGTH_SHORT).show();
                    return;
                }
                String message = "보정 실패";
                try {
                    if (response.errorBody() != null) {
                        message = new JSONObject(response.errorBody().string()).optString("message", message);
                    }
                } catch (Exception e) {
                    Log.e("CALIBRATE", "errorBody 읽기 실패", e);
                }
                Toast.makeText(mypage.this, message, Toast.LENGTH_LONG).show();
            }

            @Override
            public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                Toast.makeText(mypage.this, "서버 연결 오류", Toast.LENGTH_SHORT).show();
            }
        });
    }
}