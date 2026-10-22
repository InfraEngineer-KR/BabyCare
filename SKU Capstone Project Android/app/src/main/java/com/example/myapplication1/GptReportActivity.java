package com.example.myapplication1;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class GptReportActivity extends AppCompatActivity {

    private static final String TAG = "GptReportActivity";

    private TextView tvReportTitle;
    private TextView tvReportContent;
    private ImageView btnBack;

    private ApiService apiService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_gpt_report);

        tvReportTitle = findViewById(R.id.tvReportTitle);
        tvReportContent = findViewById(R.id.tvReportContent);
        btnBack = findViewById(R.id.btnBack);

        btnBack.setOnClickListener(v -> finish());

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(BuildConfig.BASE_URL + "/")
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        apiService = retrofit.create(ApiService.class);
        loadLatestReport();
    }

    private void loadLatestReport() {
        tvReportContent.setText("AI 보고서를 불러오는 중...");

        apiService.getLatestReport().enqueue(new Callback<AuthModels.AiReportResponse>() {
            @Override
            public void onResponse(Call<AuthModels.AiReportResponse> call,
                                   Response<AuthModels.AiReportResponse> response) {
                Log.d(TAG, "응답 코드: " + response.code());

                if (response.isSuccessful()
                        && response.body() != null
                        && response.body().success) {

                    updateUI(response.body().data);

                } else {
                    generateReport();
                }
            }

            @Override
            public void onFailure(Call<AuthModels.AiReportResponse> call, Throwable t) {
                Log.e(TAG, "서버 연결 실패: " + t.getMessage());

                Toast.makeText(
                        GptReportActivity.this,
                        "서버 연결 실패",
                        Toast.LENGTH_SHORT
                ).show();

                tvReportContent.setText(
                        "서버에 연결할 수 없습니다.\n잠시 후 다시 시도해주세요."
                );
            }
        });
    }

    private void generateReport() {

        tvReportContent.setText(
                "AI 보고서를 생성하는 중...\n잠시만 기다려주세요 🤖"
        );

        apiService.generateReport().enqueue(new Callback<AuthModels.AiReportResponse>() {

            @Override
            public void onResponse(Call<AuthModels.AiReportResponse> call,
                                   Response<AuthModels.AiReportResponse> response) {

                Log.d(TAG, "생성 응답 코드: " + response.code());

                if (response.isSuccessful()
                        && response.body() != null
                        && response.body().success) {

                    updateUI(response.body().data);

                } else {

                    tvReportTitle.setText("분석 결과");

                    tvReportContent.setText(
                            "아직 분석할 데이터가 없습니다.\n데이터가 쌓이면 자동 생성됩니다 😊"
                    );
                }
            }

            @Override
            public void onFailure(Call<AuthModels.AiReportResponse> call, Throwable t) {

                Log.e(TAG, "보고서 생성 실패: " + t.getMessage());

                Toast.makeText(
                        GptReportActivity.this,
                        "보고서 생성 실패",
                        Toast.LENGTH_SHORT
                ).show();

                tvReportContent.setText(
                        "보고서 생성 중 오류가 발생했습니다."
                );
            }
        });
    }

    private void updateUI(AuthModels.AiReportData data) {

        if (data == null) return;

        String title = String.format(
                "📋 %s\n📅 %s ~ %s",
                data.reportType != null ? data.reportType : "수면 리포트",
                data.periodStart != null ? data.periodStart : "--",
                data.periodEnd != null ? data.periodEnd : "--"
        );

        tvReportTitle.setText(title);

        if (data.reportText != null && !data.reportText.isEmpty()) {

            tvReportContent.setText(data.reportText);

        } else {

            tvReportContent.setText("보고서 내용을 불러올 수 없습니다.");
        }

        Log.d(TAG, "UI 업데이트 완료");
        Log.d(TAG, "온도: " + data.avgTemp);
        Log.d(TAG, "습도: " + data.avgHumidity);
        Log.d(TAG, "소음: " + data.avgNoise);
        Log.d(TAG, "수면 점수: " + data.avgScore);
        Log.d(TAG, "울음 횟수: " + data.cryingCount);
    }
}