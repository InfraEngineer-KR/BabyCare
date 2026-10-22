package com.example.myapplication1;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class policyDetail extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_policy_detail);

        // 1. 데이터 받기 (implements Serializable 확인 필수)
        AuthModels.PolicyResponse data = (AuthModels.PolicyResponse) getIntent().getSerializableExtra("policy_data");

        // 2. 뷰 연결 (XML의 ID와 일치해야 함)
        TextView title = findViewById(R.id.detailTitle);
        TextView dept = findViewById(R.id.detailDept);
        TextView summary = findViewById(R.id.detailSummary);
        Button btnWeb = findViewById(R.id.btnGoWeb);

        if (data != null) {
            title.setText(data.title);
            dept.setText(data.department);
            summary.setText(data.summary);

            // 3. 웹사이트 이동 버튼
            btnWeb.setOnClickListener(v -> {
                if (data.url != null && !data.url.isEmpty()) {
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(data.url));
                    startActivity(intent);
                } else {
                    Toast.makeText(this, "연결 가능한 웹사이트 주소가 없습니다.", Toast.LENGTH_SHORT).show();
                }
            });
        }
    }
} // 💡 클래스 닫는 괄호 확인