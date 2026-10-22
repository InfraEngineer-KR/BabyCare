package com.example.myapplication1;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class VaccineAdapter extends RecyclerView.Adapter<VaccineAdapter.ViewHolder> {

    private List<AuthModels.VaccineResponse> vaccineList;

    public VaccineAdapter(List<AuthModels.VaccineResponse> vaccineList) {
        this.vaccineList = vaccineList;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {

        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_vaccine, parent, false);

        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {

        AuthModels.VaccineResponse item = vaccineList.get(position);

        // =========================
        // 백신 이름
        // =========================
        holder.tvVaccineName.setText(
                item.name + " (" + item.degree + "차)"
        );

        // =========================
        // 날짜 상태 처리
        // =========================
        String dateText;

        if (item.dDay < 0) {

            dateText =
                    item.dueDate + " (접종 완료)";

            holder.tvVaccineDate.setTextColor(
                    Color.GRAY
            );

        } else if (item.dDay == 0) {

            dateText =
                    item.dueDate + " (오늘 접종)";

            holder.tvVaccineDate.setTextColor(
                    Color.parseColor("#FF8800")
            );

        } else {

            dateText =
                    item.dueDate + " (D-" + item.dDay + ")";

            holder.tvVaccineDate.setTextColor(
                    Color.parseColor("#5E6BB2")
            );
        }

        holder.tvVaccineDate.setText(dateText);
    }

    @Override
    public int getItemCount() {
        return vaccineList.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {

        public TextView tvVaccineName;
        public TextView tvVaccineDate;

        public ViewHolder(View itemView) {
            super(itemView);

            tvVaccineName =
                    itemView.findViewById(R.id.tvVaccineName);

            tvVaccineDate =
                    itemView.findViewById(R.id.tvVaccineDate);
        }
    }
}