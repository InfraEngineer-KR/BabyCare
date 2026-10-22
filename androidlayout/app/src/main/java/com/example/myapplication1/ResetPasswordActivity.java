package com.example.myapplication1;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatButton;

import java.io.IOException;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ResetPasswordActivity extends AppCompatActivity {

    private static final String TAG = "ResetPassword_Debug";

    private ApiService apiService;

    // XML ID 변경에 맞춘 변수 선언
    private EditText etResetEmail, etVerifyCode, etNewPw, etNewPwCheck;
    private AppCompatButton btnVerifyRequest, btnVerifyCheck, btnResetFinish;
    private ImageView btnBack;

    private boolean isEmailVerified = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reset_password); // 파일명이 activity_password_reset.xml인 경우

        apiService = RetrofitClient.getApiService();

        // 1. 뷰 초기화 (새로운 ID 반영)
        btnBack = findViewById(R.id.btn_back);
        etResetEmail = findViewById(R.id.et_reset_email);
        btnVerifyRequest = findViewById(R.id.btn_verify_request);

        etVerifyCode = findViewById(R.id.et_verify_code);
        btnVerifyCheck = findViewById(R.id.btn_verify_check);

        etNewPw = findViewById(R.id.et_new_pw);
        etNewPwCheck = findViewById(R.id.et_new_pw_check);
        btnResetFinish = findViewById(R.id.btn_reset_finish);

        // 상단 뒤로가기 버튼 이벤트
        btnBack.setOnClickListener(v -> finish());

        // 2. 비밀번호 재설정용 이메일 인증 요청
        btnVerifyRequest.setOnClickListener(v -> {
            String email = etResetEmail.getText().toString().trim();
            if (email.isEmpty()) {
                Toast.makeText(this, "이메일을 입력해주세요.", Toast.LENGTH_SHORT).show();
                return;
            }

            AuthModels.VerifyRequest request = new AuthModels.VerifyRequest(email);

            apiService.requestResetVerify(request).enqueue(new Callback<AuthModels.UserResponse>() {
                @Override
                public void onResponse(Call<AuthModels.UserResponse> call, Response<AuthModels.UserResponse> response) {
                    if (response.isSuccessful() && response.body() != null && response.body().ok) {
                        Toast.makeText(ResetPasswordActivity.this,
                                "인증번호가 이메일로 발송되었습니다.", Toast.LENGTH_SHORT).show();
                        // 팁: 필요하다면 여기서 etVerifyCode와 btnVerifyCheck를 VISIBLE로 변경하세요.
                    } else {
                        handleErrorResponse(response);
                        Toast.makeText(ResetPasswordActivity.this,
                                "가입되지 않은 이메일이거나 요청 실패", Toast.LENGTH_SHORT).show();
                    }
                }

                @Override
                public void onFailure(Call<AuthModels.UserResponse> call, Throwable t) {
                    Log.e(TAG, "인증요청 실패: " + t.getMessage());
                    Toast.makeText(ResetPasswordActivity.this, "네트워크 오류", Toast.LENGTH_SHORT).show();
                }
            });
        });

        // 3. 인증번호 확인
        btnVerifyCheck.setOnClickListener(v -> {
            String email = etResetEmail.getText().toString().trim();
            String code = etVerifyCode.getText().toString().trim();

            if (email.isEmpty() || code.isEmpty()) {
                Toast.makeText(this, "이메일과 인증번호를 입력해주세요.", Toast.LENGTH_SHORT).show();
                return;
            }

            AuthModels.CodeCheckRequest request = new AuthModels.CodeCheckRequest(email, code);

            apiService.verifyCode(request).enqueue(new Callback<AuthModels.UserResponse>() {
                @Override
                public void onResponse(Call<AuthModels.UserResponse> call, Response<AuthModels.UserResponse> response) {
                    if (response.isSuccessful() && response.body() != null && response.body().ok) {
                        isEmailVerified = true;
                        Toast.makeText(ResetPasswordActivity.this, "이메일 인증 성공!", Toast.LENGTH_SHORT).show();

                        // 인증 완료 후 이메일 수정 불가 처리
                        etResetEmail.setEnabled(false);
                        btnVerifyRequest.setEnabled(false);
                    } else {
                        handleErrorResponse(response);
                        Toast.makeText(ResetPasswordActivity.this, "인증번호가 틀리거나 만료되었습니다.", Toast.LENGTH_SHORT).show();
                    }
                }

                @Override
                public void onFailure(Call<AuthModels.UserResponse> call, Throwable t) {
                    Log.e(TAG, "인증확인 실패: " + t.getMessage());
                }
            });
        });

        // 4. 비밀번호 재설정 완료
        btnResetFinish.setOnClickListener(v -> {
            String email = etResetEmail.getText().toString().trim();
            String newPw = etNewPw.getText().toString().trim();
            String newPwCheck = etNewPwCheck.getText().toString().trim();

            if (!isEmailVerified) {
                Toast.makeText(this, "먼저 이메일 인증을 완료해주세요.", Toast.LENGTH_SHORT).show();
                return;
            }

            if (newPw.isEmpty() || newPwCheck.isEmpty()) {
                Toast.makeText(this, "새 비밀번호를 입력해주세요.", Toast.LENGTH_SHORT).show();
                return;
            }

            if (!newPw.equals(newPwCheck)) {
                Toast.makeText(this, "비밀번호가 서로 다릅니다.", Toast.LENGTH_SHORT).show();
                return;
            }

            AuthModels.ResetPasswordRequest request = new AuthModels.ResetPasswordRequest(email, newPw);

            apiService.resetPassword(request).enqueue(new Callback<AuthModels.UserResponse>() {
                @Override
                public void onResponse(Call<AuthModels.UserResponse> call, Response<AuthModels.UserResponse> response) {
                    if (response.isSuccessful() && response.body() != null && response.body().ok) {
                        Toast.makeText(ResetPasswordActivity.this, "비밀번호가 성공적으로 변경되었습니다.", Toast.LENGTH_SHORT).show();
                        finish();
                    } else {
                        handleErrorResponse(response);
                        Toast.makeText(ResetPasswordActivity.this, "비밀번호 변경 실패", Toast.LENGTH_SHORT).show();
                    }
                }

                @Override
                public void onFailure(Call<AuthModels.UserResponse> call, Throwable t) {
                    Log.e(TAG, "비밀번호 변경 실패: " + t.getMessage());
                    Toast.makeText(ResetPasswordActivity.this, "네트워크 오류", Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    private void handleErrorResponse(Response<?> response) {
        Log.e(TAG, "Status Code: " + response.code());
        try {
            if (response.errorBody() != null) {
                Log.e(TAG, "Error Body: " + response.errorBody().string());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}