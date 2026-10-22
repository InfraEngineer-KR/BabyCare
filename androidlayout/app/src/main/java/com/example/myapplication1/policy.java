package com.example.myapplication1;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class policy extends AppCompatActivity {
    private RecyclerView recyclerView;
    private policyAdapter adapter;
    private List<AuthModels.PolicyResponse> policyList = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_policy);

        // 툴바의 전체홈 버튼 (필요할 경우)
        TextView btnWeb = findViewById(R.id.btn_web);
        btnWeb.setOnClickListener(v -> {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.gov.kr")));
        });

        recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        fetchPolicies();
    }

    private void fetchPolicies() {
        String BASE_URL = BuildConfig.BASE_URL + "/";
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        ApiService apiService = retrofit.create(ApiService.class);
        apiService.getPolicies().enqueue(new Callback<List<AuthModels.PolicyResponse>>() {
            @Override
            public void onResponse(Call<List<AuthModels.PolicyResponse>> call, Response<List<AuthModels.PolicyResponse>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    policyList = response.body();
                    adapter = new policyAdapter(policyList);
                    recyclerView.setAdapter(adapter);
                }
            }

            @Override
            public void onFailure(Call<List<AuthModels.PolicyResponse>> call, Throwable t) {
                Log.e("Policy_Error", t.getMessage());
            }
        });
    }
}