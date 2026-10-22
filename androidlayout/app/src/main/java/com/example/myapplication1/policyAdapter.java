package com.example.myapplication1;

import android.content.Intent;
import android.net.Uri; // Uri 추가
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button; // Button 추가
import android.widget.TextView;
import android.widget.Toast; // Toast 추가
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class policyAdapter extends RecyclerView.Adapter<policyAdapter.ViewHolder> {

    private List<AuthModels.PolicyResponse> policyList;

    public policyAdapter(List<AuthModels.PolicyResponse> list) {
        this.policyList = list;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.activity_policy_adapter, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        AuthModels.PolicyResponse policy = policyList.get(position);
        holder.tvTitle.setText(policy.title);
        holder.tvSummary.setText(policy.summary);
        holder.tvDept.setText(policy.department);

        // 1. 항목 전체 클릭 시: 상세 페이지 이동 (기존 기능)
        holder.itemView.setOnClickListener(v -> {
            Intent intent = new Intent(v.getContext(), policyDetail.class);
            intent.putExtra("policy_data", policy);
            v.getContext().startActivity(intent);
        });

        // 2. 💡 버튼 클릭 시: DB에서 온 링크로 이동 (형이 원하던 기능)
        holder.btnGoWeb.setOnClickListener(v -> {
            // DB 모델에 있는 url 필드명을 쓰세요 (예: policy.link 또는 policy.url)
            String webUrl = policy.url;

            if (webUrl != null && !webUrl.isEmpty()) {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(webUrl));
                v.getContext().startActivity(intent);
            } else {
                Toast.makeText(v.getContext(), "등록된 웹사이트가 없습니다.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public int getItemCount() {
        return policyList.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvTitle, tvSummary, tvDept;
        Button btnGoWeb; // 버튼 변수 추가

        public ViewHolder(View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tvPolicyTitle);
            tvSummary = itemView.findViewById(R.id.tvPolicySummary);
            tvDept = itemView.findViewById(R.id.tvDept);
            btnGoWeb = itemView.findViewById(R.id.btn_go_web); // ID 연결
        }
    }
}