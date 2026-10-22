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
import java.util.Calendar;
import java.util.List;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class mypage extends AppCompatActivity {

    private ApiService apiService;
    private EditText etEditName, etBabyBirth, etCurrentPw, etEditPw;
    private Button btnSaveAll;
    private ImageButton btnAddSmartThings;
    private LinearLayout itemEmptyDevice, layoutDeviceList;

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
}