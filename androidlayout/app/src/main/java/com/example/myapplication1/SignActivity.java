package com.example.myapplication1;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatButton;

import java.io.IOException;
import java.util.Calendar;
import java.util.Locale;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class SignActivity extends AppCompatActivity {

    private static final String TAG = "SignActivity_Debug";
    private ApiService apiService;
    private String userEmail;

    // ✅ 선택된 생년월일 저장용 (YYYY-MM-DD)
    private String selectedBabyBirth = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sign);

        apiService = RetrofitClient.getApiService();

        // 뷰 바인딩
        ImageView btnBack = findViewById(R.id.btn_back);
        EditText etName = findViewById(R.id.et_sign_name);
        EditText etEmail = findViewById(R.id.et_sign_email);
        EditText etVerifyCode = findViewById(R.id.et_verify_code);
        EditText etUsername = findViewById(R.id.et_sign_id);
        EditText etPassword = findViewById(R.id.et_sign_pw);
        EditText etPasswordCheck = findViewById(R.id.et_sign_pw_check);
        EditText etBabyBirth = findViewById(R.id.et_baby_birth);

        AppCompatButton btnVerifyRequest = findViewById(R.id.btn_verify_request);
        AppCompatButton btnVerifyCheck = findViewById(R.id.btn_verify_check);
        AppCompatButton btnSignupFinish = findViewById(R.id.btn_signup_finish);

        btnBack.setOnClickListener(v -> finish());

        // 생년월일 EditText 클릭 시 달력 띄우기
        etBabyBirth.setOnClickListener(v -> showDatePicker(etBabyBirth));

        // 2. [인증요청]
        btnVerifyRequest.setOnClickListener(v -> {
            userEmail = etEmail.getText().toString().trim();
            if (userEmail.isEmpty()) {
                Toast.makeText(this, "이메일을 입력해주세요.", Toast.LENGTH_SHORT).show();
                return;
            }

            AuthModels.VerifyRequest request = new AuthModels.VerifyRequest(userEmail);
            apiService.requestVerify(request).enqueue(new Callback<AuthModels.UserResponse>() {
                @Override
                public void onResponse(Call<AuthModels.UserResponse> call, Response<AuthModels.UserResponse> response) {
                    if (response.isSuccessful() && response.body() != null && response.body().ok) {
                        Toast.makeText(SignActivity.this, "인증번호가 발송되었습니다.", Toast.LENGTH_SHORT).show();
                        etVerifyCode.setVisibility(View.VISIBLE);
                        btnVerifyCheck.setVisibility(View.VISIBLE);
                    } else {
                        handleErrorResponse(response);
                        Toast.makeText(SignActivity.this, "인증 요청 실패", Toast.LENGTH_SHORT).show();
                    }
                }
                @Override
                public void onFailure(Call<AuthModels.UserResponse> call, Throwable t) {
                    Toast.makeText(SignActivity.this, "네트워크 연결 확인이 필요합니다.", Toast.LENGTH_SHORT).show();
                }
            });
        });

        // 3. [인증확인]
        btnVerifyCheck.setOnClickListener(v -> {
            String code = etVerifyCode.getText().toString().trim();
            if (code.isEmpty()) {
                Toast.makeText(this, "인증번호를 입력해주세요.", Toast.LENGTH_SHORT).show();
                return;
            }

            AuthModels.CodeCheckRequest request = new AuthModels.CodeCheckRequest(userEmail, code);
            apiService.verifyCode(request).enqueue(new Callback<AuthModels.UserResponse>() {
                @Override
                public void onResponse(Call<AuthModels.UserResponse> call, Response<AuthModels.UserResponse> response) {
                    if (response.isSuccessful() && response.body() != null && response.body().ok) {
                        Toast.makeText(SignActivity.this, "이메일 인증 성공!", Toast.LENGTH_SHORT).show();
                        etEmail.setEnabled(false);
                        btnVerifyRequest.setEnabled(false);
                    } else {
                        Toast.makeText(SignActivity.this, "인증번호가 틀리거나 만료되었습니다.", Toast.LENGTH_SHORT).show();
                    }
                }
                @Override
                public void onFailure(Call<AuthModels.UserResponse> call, Throwable t) {
                    Log.e(TAG, "인증확인 네트워크 실패");
                }
            });
        });

        // 4. [회원가입 완료]
        btnSignupFinish.setOnClickListener(v -> {
            String username = etUsername.getText().toString().trim();
            String password = etPassword.getText().toString().trim();
            String passwordCheck = etPasswordCheck.getText().toString().trim();

            if (userEmail == null || userEmail.isEmpty()) {
                Toast.makeText(this, "이메일 인증을 먼저 진행해주세요.", Toast.LENGTH_SHORT).show();
                return;
            }

            if (username.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "아이디와 비밀번호를 입력해주세요.", Toast.LENGTH_SHORT).show();
                return;
            }

            // 🔒 [추가] 비밀번호 13자리 제한 로직
            if (password.length() < 13) {
                Toast.makeText(this, "비밀번호는 최소 13자리 이상이어야 합니다!", Toast.LENGTH_SHORT).show();
                return;
            }

            if (!password.equals(passwordCheck)) {
                Toast.makeText(this, "비밀번호가 일치하지 않습니다.", Toast.LENGTH_SHORT).show();
                return;
            }

            if (selectedBabyBirth == null || selectedBabyBirth.isEmpty()) {
                Toast.makeText(this, "아기 생년월일을 선택해주세요.", Toast.LENGTH_SHORT).show();
                return;
            }

            AuthModels.SignupRequest request =
                    new AuthModels.SignupRequest(userEmail, username, password, true, selectedBabyBirth);

            apiService.signup(request).enqueue(new Callback<AuthModels.UserResponse>() {
                @Override
                public void onResponse(Call<AuthModels.UserResponse> call, Response<AuthModels.UserResponse> response) {
                    if (response.isSuccessful() && response.body() != null && response.body().ok) {
                        Toast.makeText(SignActivity.this, "회원가입 완료!", Toast.LENGTH_SHORT).show();
                        finish();
                    } else {
                        handleErrorResponse(response);
                        Toast.makeText(SignActivity.this, "가입 실패 (중복 혹은 인증 미완료)", Toast.LENGTH_SHORT).show();
                    }
                }
                @Override
                public void onFailure(Call<AuthModels.UserResponse> call, Throwable t) {
                    Log.e(TAG, "회원가입 네트워크 실패: " + t.getMessage());
                }
            });
        });
    }

    private void showDatePicker(EditText targetEditText) {
        Calendar cal = Calendar.getInstance();
        int year = cal.get(Calendar.YEAR);
        int month = cal.get(Calendar.MONTH);
        int day = cal.get(Calendar.DAY_OF_MONTH);

        DatePickerDialog dialog = new DatePickerDialog(
                this,
                (DatePicker view, int y, int m, int d) -> {
                    selectedBabyBirth = String.format(Locale.KOREA, "%04d-%02d-%02d", y, (m + 1), d);
                    targetEditText.setText(selectedBabyBirth);
                },
                year, month, day
        );
        dialog.show();
    }

    private void handleErrorResponse(Response<?> response) {
        Log.e(TAG, "에러 코드: " + response.code());
    }
}