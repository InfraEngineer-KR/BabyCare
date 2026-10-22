package com.example.myapplication1;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class DeviceAdapter extends RecyclerView.Adapter<DeviceAdapter.DeviceViewHolder> {

    private static final String TAG = "DeviceAdapter";

    private final List<AuthModels.Device> devices;
    private final ApiService apiService;
    private final String accessToken;

    private final Integer[] fanSpeeds;  // 실제 SmartThings fanSpeed 값 (1=auto, 2=1단계, 3=2단계, 4=3단계)
    private final String[] switchStates;
    private final double[] coolingSetpoints;

    private boolean isAutoControl = false;

    public DeviceAdapter(List<AuthModels.Device> devices, ApiService apiService, String accessToken) {
        this.devices = devices;
        this.apiService = apiService;
        this.accessToken = accessToken;
        this.fanSpeeds = new Integer[devices.size()];
        this.switchStates = new String[devices.size()];
        this.coolingSetpoints = new double[devices.size()];
    }

    public void setAutoControl(boolean isAuto) {
        this.isAutoControl = isAuto;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public DeviceViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_device, parent, false);
        return new DeviceViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull DeviceViewHolder holder, int position) {
        AuthModels.Device device = devices.get(position);
        List<String> caps = device.capabilities;

        holder.tvDeviceName.setText(
                device.label != null && !device.label.isEmpty() ? device.label : device.name
        );

        boolean hasSwitch = caps != null && caps.contains("switch");
        boolean hasFanSpeed = caps != null && caps.contains("fanSpeed");
        boolean hasCooling = caps != null && caps.contains("thermostatCoolingSetpoint");

        // 초기 visibility
        holder.btnOnOff.setVisibility(hasSwitch ? View.VISIBLE : View.GONE);
        holder.btnAuto.setVisibility(hasFanSpeed ? View.VISIBLE : View.GONE);
        holder.tvFanSpeed.setVisibility(View.GONE);
        holder.btnFanUp.setVisibility(View.GONE);
        holder.btnFanDown.setVisibility(View.GONE);
        holder.btnTempUp.setVisibility(hasCooling ? View.VISIBLE : View.GONE);
        holder.btnTempDown.setVisibility(hasCooling ? View.VISIBLE : View.GONE);
        holder.tvTempValue.setVisibility(hasCooling ? View.VISIBLE : View.GONE);

        // 자동제어 상태에 따라 버튼 활성화/비활성화
        setButtonsEnabled(holder, !isAutoControl);

        // 상태 로드
        loadDeviceStatus(holder, device.deviceId, position);

        //온오프 버튼
        if (hasSwitch) {
            holder.btnOnOff.setOnClickListener(v -> {
                String cmd = "on".equals(switchStates[position]) ? "off" : "on";
                controlDevice(holder, device.deviceId, position, "switch", cmd, 0);
                if ("on".equals(cmd)) {
                    controlDevice(holder, device.deviceId, position, "fanSpeed", "set", 2);
                }
            });
        }

        // AUTO 버튼 (fanSpeed 1로 설정)
        if (hasFanSpeed) {
            holder.btnAuto.setOnClickListener(v ->
                    controlDevice(holder, device.deviceId, position, "fanSpeed", "set", 1));

            // 팬 단계 up - AUTO면 무조건 1단계(fanSpeed=2)로, 아니면 올리기
            holder.btnFanUp.setOnClickListener(v -> {
                int current = fanSpeeds[position] != null ? fanSpeeds[position] : 2;
                if (current == 1) {
                    // AUTO 상태에서 화살표 누르면 1단계로
                    controlDevice(holder, device.deviceId, position, "fanSpeed", "set", 2);
                    return;
                }
                if (current >= 4) {
                    Toast.makeText(holder.itemView.getContext(), "최고 단계입니다", Toast.LENGTH_SHORT).show();
                    return;
                }
                controlDevice(holder, device.deviceId, position, "fanSpeed", "up", current);
            });

            // 팬 단계 down - AUTO면 무조건 1단계(fanSpeed=2)로, 아니면 내리기
            holder.btnFanDown.setOnClickListener(v -> {
                int current = fanSpeeds[position] != null ? fanSpeeds[position] : 2;
                if (current == 1) {
                    // AUTO 상태에서 화살표 누르면 1단계로
                    controlDevice(holder, device.deviceId, position, "fanSpeed", "set", 2);
                    return;
                }
                if (current <= 2) {
                    Toast.makeText(holder.itemView.getContext(), "최저 단계입니다", Toast.LENGTH_SHORT).show();
                    return;
                }
                controlDevice(holder, device.deviceId, position, "fanSpeed", "down", current);
            });
        }

        // 에어컨 온도 버튼
        if (hasCooling) {
            holder.btnTempUp.setOnClickListener(v ->
                    controlDevice(holder, device.deviceId, position,
                            "thermostatCoolingSetpoint", "up", coolingSetpoints[position]));
            holder.btnTempDown.setOnClickListener(v ->
                    controlDevice(holder, device.deviceId, position,
                            "thermostatCoolingSetpoint", "down", coolingSetpoints[position]));
        }
    }

    private void loadDeviceStatus(DeviceViewHolder holder, String deviceId, int position) {
        apiService.getDeviceStatus("Bearer " + accessToken, deviceId)
                .enqueue(new Callback<AuthModels.DeviceStatusResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<AuthModels.DeviceStatusResponse> call,
                                           @NonNull Response<AuthModels.DeviceStatusResponse> response) {
                        if (response.isSuccessful() && response.body() != null && response.body().ok) {
                            AuthModels.DeviceStatusResponse body = response.body();
                            switchStates[position] = body.switchValue;
                            fanSpeeds[position] = body.fanSpeed;
                            if (body.coolingSetpoint != null) coolingSetpoints[position] = body.coolingSetpoint;
                            updateUI(holder, position, body);
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<AuthModels.DeviceStatusResponse> call, @NonNull Throwable t) {
                        Log.e(TAG, "상태 조회 실패: " + t.getMessage());
                    }
                });
    }

    private void setButtonsEnabled(DeviceViewHolder holder, boolean enabled) {
        holder.itemView.post(() -> {
            float alpha = enabled ? 1.0f : 0.4f;
            holder.btnOnOff.setEnabled(enabled);
            holder.btnOnOff.setAlpha(alpha);
            holder.btnAuto.setEnabled(enabled);
            holder.btnAuto.setAlpha(alpha);
            holder.btnFanUp.setEnabled(enabled);
            holder.btnFanUp.setAlpha(alpha);
            holder.btnFanDown.setEnabled(enabled);
            holder.btnFanDown.setAlpha(alpha);
            holder.btnTempUp.setEnabled(enabled);
            holder.btnTempUp.setAlpha(alpha);
            holder.btnTempDown.setEnabled(enabled);
            holder.btnTempDown.setAlpha(alpha);
        });
    }

    private void controlDevice(DeviceViewHolder holder, String deviceId, int position,
                               String capability, String command, double value) {
        AuthModels.ControlRequest request = new AuthModels.ControlRequest(deviceId, capability, command, value);

        apiService.controlDevice("Bearer " + accessToken, request)
                .enqueue(new Callback<AuthModels.ControlResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<AuthModels.ControlResponse> call,
                                           @NonNull Response<AuthModels.ControlResponse> response) {
                        if (response.isSuccessful() && response.body() != null && response.body().ok) {
                            AuthModels.ControlResponse body = response.body();
                            switchStates[position] = body.switchValue;
                            fanSpeeds[position] = body.fanSpeed;
                            if (body.coolingSetpoint != null) coolingSetpoints[position] = body.coolingSetpoint;

                            if ("switch".equals(capability)) {
                                loadDeviceStatus(holder, deviceId, position);
                                return;
                            }

                            AuthModels.DeviceStatusResponse status = new AuthModels.DeviceStatusResponse();
                            status.switchValue = body.switchValue;
                            status.fanSpeed = body.fanSpeed;
                            status.temperature = body.temperature;
                            status.humidity = body.humidity;
                            status.coolingSetpoint = body.coolingSetpoint;
                            updateUI(holder, position, status);
                        } else {
                            Toast.makeText(holder.itemView.getContext(), "제어 실패", Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<AuthModels.ControlResponse> call, @NonNull Throwable t) {
                        Log.e(TAG, "제어 실패: " + t.getMessage());
                        Toast.makeText(holder.itemView.getContext(), "서버 연결 실패", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void updateUI(DeviceViewHolder holder, int position, AuthModels.DeviceStatusResponse status) {
        holder.itemView.post(() -> {
            if (status.switchValue != null) {
                boolean isOn = "on".equals(status.switchValue);
                holder.btnOnOff.setText(isOn ? "ON" : "OFF");

                if (isOn && fanSpeeds[position] != null) {
                    int speed = fanSpeeds[position];
                    if (speed == 1) {
                        // AUTO 상태 - 단계: AUTO 표시, 화살표는 보임
                        holder.tvFanSpeed.setVisibility(View.VISIBLE);
                        holder.btnFanUp.setVisibility(View.VISIBLE);
                        holder.btnFanDown.setVisibility(View.VISIBLE);
                        holder.tvFanSpeed.setText("단계: AUTO");
                        holder.btnAuto.setBackgroundTintList(
                                android.content.res.ColorStateList.valueOf(0xFF2D3250));
                    } else {
                        // 1~3단계 표시 (fanSpeed 2=1단계, 3=2단계, 4=3단계)
                        holder.tvFanSpeed.setVisibility(View.VISIBLE);
                        holder.btnFanUp.setVisibility(View.VISIBLE);
                        holder.btnFanDown.setVisibility(View.VISIBLE);
                        holder.tvFanSpeed.setText("단계: " + (speed - 1));
                        holder.btnAuto.setBackgroundTintList(
                                android.content.res.ColorStateList.valueOf(0xFF9DA4BC));
                    }
                } else {
                    holder.tvFanSpeed.setVisibility(View.GONE);
                    holder.btnFanUp.setVisibility(View.GONE);
                    holder.btnFanDown.setVisibility(View.GONE);
                }
            }
            if (status.fanSpeed != null) {
                fanSpeeds[position] = status.fanSpeed;
            }
            if (status.coolingSetpoint != null) {
                coolingSetpoints[position] = status.coolingSetpoint;
                holder.tvTempValue.setText(String.format("설정: %.0f°C", status.coolingSetpoint));
            }
        });
    }

    @Override
    public int getItemCount() {
        return devices.size();
    }

    static class DeviceViewHolder extends RecyclerView.ViewHolder {
        TextView tvDeviceName, tvTempValue, tvFanSpeed;
        Button btnOnOff, btnAuto;
        ImageButton btnTempUp, btnTempDown, btnFanUp, btnFanDown;

        DeviceViewHolder(@NonNull View itemView) {
            super(itemView);
            tvDeviceName = itemView.findViewById(R.id.tvDeviceName);
            tvTempValue  = itemView.findViewById(R.id.tvTempValue);
            tvFanSpeed   = itemView.findViewById(R.id.tvFanSpeed);
            btnOnOff     = itemView.findViewById(R.id.btnOnOff);
            btnAuto      = itemView.findViewById(R.id.btnAuto);
            btnTempUp    = itemView.findViewById(R.id.btnTempUp);
            btnTempDown  = itemView.findViewById(R.id.btnTempDown);
            btnFanUp     = itemView.findViewById(R.id.btnFanUp);
            btnFanDown   = itemView.findViewById(R.id.btnFanDown);
        }
    }
}
