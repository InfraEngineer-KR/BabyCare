package com.example.myapplication1;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

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
    private TextView tvReportScore;
    private TextView tvReportDate, tvReportScoreBody;
    private TextView tvAvgTemp, tvAvgHumidity, tvAvgNoise;
    private TextView tvReportSummary, tvEnvAnalysis, tvCareGuide;
    private AuthModels.AiReportData currentReport;
    // GPT 리포트 섹션 이름 (서버 프롬프트의 [출력 양식]과 같아야 함)
    private static final String[] SECTION_NAMES = {
            "전체 요약", "환경 상태 분석", "수면 상태 분석", "이상 징후 및 권장 행동"
    };
    private ImageView btnBack;

    private ApiService apiService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_gpt_report);

        tvReportTitle = findViewById(R.id.tvReportTitle);
        tvReportContent = findViewById(R.id.tvReportContent);
        tvReportScore = findViewById(R.id.tvReportScore);
        tvReportDate = findViewById(R.id.tvReportDate);
        tvReportScoreBody = findViewById(R.id.tvReportScoreBody);        
        tvAvgTemp = findViewById(R.id.tvAvgTemp);
        tvAvgHumidity = findViewById(R.id.tvAvgHumidity);
        tvAvgNoise = findViewById(R.id.tvAvgNoise);
        tvReportSummary = findViewById(R.id.tvReportSummary);
        tvEnvAnalysis = findViewById(R.id.tvEnvAnalysis);
        tvCareGuide = findViewById(R.id.tvCareGuide);
        btnBack = findViewById(R.id.btnBack);

        btnBack.setOnClickListener(v -> finish());
        findViewById(R.id.btnShareReport).setOnClickListener(v -> shareReport());
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
        currentReport = data;
        if (data == null) return;

        // 제목: "10월 22일 아침 수면 리포트" (분석 끝 날짜 기준)
        tvReportTitle.setText(toReportTitle(data.periodEnd));
        tvReportDate.setText(toPeriodText(data.periodStart, data.periodEnd));   // 추가

        // 평균 수면 환경 점수
        tvReportScore.setText(String.valueOf(Math.round(data.avgScore)));

        // 평균 온도 / 습도 / 소음 (소음은 서버에서 dB로 변환해서 줌)
        tvAvgTemp.setText(String.format(Locale.KOREA, "%.1f°C", data.avgTemp));
        tvAvgHumidity.setText(String.format(Locale.KOREA, "%.0f%%", data.avgHumidity));
        tvAvgNoise.setText(String.format(Locale.KOREA, "%.0fdB", data.avgNoise));

        String text = data.reportText;

        if (text == null || text.isEmpty()) {
            tvReportContent.setText("보고서 내용을 불러올 수 없습니다.");
        } else {
            String summary = getSection(text, "전체 요약");
            String env = getSection(text, "환경 상태 분석");
            String sleep = getSection(text, "수면 상태 분석");
            String care = getSection(text, "이상 징후 및 권장 행동");

            if (summary == null && env == null && sleep == null && care == null) {
                // 섹션을 하나도 못 찾으면 글 전체를 요약 칸에
                tvReportScoreBody.setText(clean(text));
                tvReportContent.setText("-");
                tvEnvAnalysis.setText("-");
                tvCareGuide.setText("-");
            } else {
                tvReportScoreBody.setText(summary != null ? summary : "-");
                tvEnvAnalysis.setText(env != null ? env : "-");
                tvReportContent.setText(sleep != null ? sleep : "-");
                tvCareGuide.setText(care != null ? care : "-");
            }
        }

        Log.d(TAG, "UI 업데이트 완료");
        Log.d(TAG, "온도: " + data.avgTemp);
        Log.d(TAG, "습도: " + data.avgHumidity);
        Log.d(TAG, "소음: " + data.avgNoise);
        Log.d(TAG, "수면 점수: " + data.avgScore);
        Log.d(TAG, "울음 횟수: " + data.cryingCount);
    }

    // "2026-10-22 08:00" → "10월 22일 아침 수면 리포트"
    private String toReportTitle(String periodEnd) {
        if (periodEnd != null) {
            try {
                Date d = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).parse(periodEnd);
                return new SimpleDateFormat("M월 d일", Locale.KOREA).format(d) + " 아침 수면 리포트";
            } catch (Exception ignored) {
            }
        }
        return "아침 수면 리포트";
    }
    
    // "2026-10-22 20:00", "2026-10-23 08:00" → "10월 22일 20:00 ~ 10월 23일 08:00"
    private String toPeriodText(String start, String end) {
        try {
            SimpleDateFormat in = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US);
            SimpleDateFormat out = new SimpleDateFormat("M월 d일 HH:mm", Locale.KOREA);
            return out.format(in.parse(start)) + " ~ " + out.format(in.parse(end));
        } catch (Exception e) {
            return "";
        }
    }
    // GPT 글에서 섹션 하나 잘라내기: 섹션 이름 다음 줄부터 다음 섹션 이름 전까지
    private String getSection(String text, String name) {
        int nameAt = text.indexOf(name);
        if (nameAt < 0) return null;

        int start = text.indexOf('\n', nameAt);   // 섹션 제목 줄 다음부터
        if (start < 0) return null;

        // 가장 가까운 다음 섹션 이름 위치
        int end = text.length();
        for (String other : SECTION_NAMES) {
            int at = text.indexOf(other, start);
            if (at >= 0 && at < end) end = at;
        }
        // 다음 섹션 제목 줄의 시작으로 당김 ("[", "**", "###" 등이 앞에 붙어 있으므로)
        int lineStart = text.lastIndexOf('\n', end);
        if (lineStart >= start) end = lineStart;

        String section = clean(text.substring(start, end));
        return section.isEmpty() ? null : section;
    }

    // 마크다운 기호 정리 (굵게 **, 제목 #)
    private String clean(String s) {
        return s.replace("**", "")
                .replaceAll("(?m)^#+\\s*", "")
                .trim();
    }
    // 리포트 공유 (카카오톡, 문자 등 휴대폰 공유 창)
    private void shareReport() {
        if (currentReport == null) {
            Toast.makeText(this, "리포트를 불러온 뒤 공유할 수 있어요", Toast.LENGTH_SHORT).show();
            return;
        }

        AuthModels.AiReportData d = currentReport;
        StringBuilder sb = new StringBuilder();
        sb.append("[첫 걸음] ").append(toReportTitle(d.periodEnd)).append("\n\n");
        sb.append("수면 환경 점수: ").append(Math.round(d.avgScore)).append(" / 100\n");
        sb.append(String.format(Locale.KOREA, "평균 온도 %.1f°C · 습도 %.0f%% · 소음 %.0fdB\n",
                d.avgTemp, d.avgHumidity, d.avgNoise));

        if (d.reportText != null) {
            String summary = getSection(d.reportText, "전체 요약");
            String care = getSection(d.reportText, "이상 징후 및 권장 행동");
            if (summary != null) sb.append("\n■ 전체 요약\n").append(summary).append("\n");
            if (care != null) sb.append("\n■ 오늘 밤 케어 가이드\n").append(care).append("\n");
        }

        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_SUBJECT, toReportTitle(d.periodEnd));
        intent.putExtra(Intent.EXTRA_TEXT, sb.toString());
        startActivity(Intent.createChooser(intent, "리포트 공유하기"));
    }
}