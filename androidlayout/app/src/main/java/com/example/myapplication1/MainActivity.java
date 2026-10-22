package com.example.myapplication1;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity_Login";

    EditText etId, etPassword;
    ApiService apiService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        etId = findViewById(R.id.et_id);
        etPassword = findViewById(R.id.et_password);

        Button btnLogin = findViewById(R.id.btn_login);
        View btnSignup = findViewById(R.id.btn_signup);
        TextView tvFindAccount = findViewById(R.id.tv_find_account);

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(BuildConfig.BASE_URL + "/")
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        apiService = retrofit.create(ApiService.class);

        btnLogin.setOnClickListener(v -> performLogin());

        btnSignup.setOnClickListener(v -> {
            Intent intent = new Intent(getApplicationContext(), TermsActivity.class);
            startActivity(intent);
        });

        tvFindAccount.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, ResetPasswordActivity.class);
            startActivity(intent);
        });
    }

    private void performLogin() {

        String username = etId.getText().toString().trim();
        String password = etPassword.getText().toString().trim();

        if (username.isEmpty() || password.isEmpty()) {

            Toast.makeText(
                    MainActivity.this,
                    "아이디와 비밀번호를 입력해주세요.",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        AuthModels.LoginRequest loginRequest =
                new AuthModels.LoginRequest(username, password);

        apiService.login(loginRequest)
                .enqueue(new Callback<AuthModels.UserResponse>() {

                    @Override
                    public void onResponse(
                            Call<AuthModels.UserResponse> call,
                            Response<AuthModels.UserResponse> response
                    ) {

                        if (
                                response.isSuccessful()
                                        && response.body() != null
                                        && response.body().ok
                        ) {

                            AuthModels.UserResponse body =
                                    response.body();

                            saveUserSession(
                                    username,
                                    body.accessToken,
                                    body.refreshToken
                            );

                            Toast.makeText(
                                    MainActivity.this,
                                    "로그인 성공!",
                                    Toast.LENGTH_SHORT
                            ).show();

                            Intent intent =
                                    new Intent(
                                            MainActivity.this,
                                            Menuactivity.class
                                    );

                            startActivity(intent);
                            finish();

                        } else {

                            Toast.makeText(
                                    MainActivity.this,
                                    "로그인 정보가 올바르지 않습니다.",
                                    Toast.LENGTH_SHORT
                            ).show();
                        }
                    }

                    @Override
                    public void onFailure(
                            Call<AuthModels.UserResponse> call,
                            Throwable t
                    ) {

                        Log.e(
                                TAG,
                                "Login Network Error: "
                                        + t.getMessage()
                        );

                        Toast.makeText(
                                MainActivity.this,
                                "서버 연결 실패",
                                Toast.LENGTH_SHORT
                        ).show();

                        // 테스트용: 서버가 꺼져 있어도 메뉴로 진입 가능하게 처리
                        Intent intent =
                                new Intent(
                                        MainActivity.this,
                                        Menuactivity.class
                                );

                        startActivity(intent);
                        finish();
                    }
                });
    }

    private void saveUserSession(
            String username,
            String accessToken,
            String refreshToken
    ) {

        SharedPreferences pref =
                getSharedPreferences(
                        "UserPrefs",
                        MODE_PRIVATE
                );

        SharedPreferences.Editor editor =
                pref.edit();

        editor.putString("username", username);
        editor.putString("userEmail", username);

        editor.putString("accessToken", accessToken);
        editor.putString("refreshToken", refreshToken);

        editor.apply();

        Log.d(TAG, "세션 저장 완료");
        Log.d(TAG, "username = " + username);
        Log.d(TAG, "accessToken = " + accessToken);
        Log.d(TAG, "refreshToken = " + refreshToken);
    }
}