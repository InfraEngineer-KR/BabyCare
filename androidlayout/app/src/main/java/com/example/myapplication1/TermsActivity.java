package com.example.myapplication1;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatButton;

public class TermsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_terms); // 약관 xml 연결

        CheckBox cbAgree = findViewById(R.id.cb_agree);
        AppCompatButton btnNext = findViewById(R.id.btn_next);

        // 1. 체크박스 체크 여부에 따라 버튼 활성화/비활성화
        cbAgree.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    btnNext.setEnabled(true); // 버튼 클릭 가능
                    btnNext.setAlpha(1.0f);   // 버튼 색 진하게
                } else {
                    btnNext.setEnabled(false); // 버튼 클릭 불가
                    btnNext.setAlpha(0.5f);    // 버튼 색 연하게
                }
            }
        });

        // 2. 다음 버튼 누르면 SignActivity(회원정보 입력창)로 이동
        btnNext.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(getApplicationContext(), SignActivity.class);
                startActivity(intent);
                finish(); // 약관 화면은 닫기
            }
        });
    }
}