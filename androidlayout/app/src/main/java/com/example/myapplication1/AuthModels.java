package com.example.myapplication1;

import com.google.gson.annotations.SerializedName;

import java.io.Serializable;
import java.util.List;

public class AuthModels {

    // =========================================================
    // 1. 인증 및 계정 관련 모델
    // =========================================================

    public static class VerifyRequest {

        @SerializedName("email")
        String email;

        public VerifyRequest(String email) {
            this.email = email;
        }
    }

    public static class CodeCheckRequest {

        @SerializedName("email")
        String email;

        @SerializedName("code")
        String code;

        public CodeCheckRequest(String email, String code) {
            this.email = email;
            this.code = code;
        }
    }

    public static class SignupRequest {

        @SerializedName("email")
        String email;

        @SerializedName("username")
        String username;

        @SerializedName("password")
        String password;

        @SerializedName("consent")
        boolean consent;

        @SerializedName("babyBirth")
        String babyBirth;

        public SignupRequest(
                String email,
                String username,
                String password,
                boolean consent,
                String babyBirth
        ) {
            this.email = email;
            this.username = username;
            this.password = password;
            this.consent = consent;
            this.babyBirth = babyBirth;
        }
    }

    public static class LoginRequest {

        @SerializedName("username")
        String username;

        @SerializedName("password")
        String password;

        public LoginRequest(String username, String password) {
            this.username = username;
            this.password = password;
        }
    }

    public static class UserResponse implements Serializable {

        @SerializedName("ok")
        public boolean ok;

        @SerializedName("success")
        public boolean success;

        @SerializedName("message")
        public String message;

        @SerializedName("accessToken")
        public String accessToken;

        @SerializedName("refreshToken")
        public String refreshToken;

        @SerializedName("userId")
        public String userId;
    }

    public static class ResetPasswordRequest {

        @SerializedName("email")
        String email;

        @SerializedName("newPassword")
        String newPassword;

        public ResetPasswordRequest(String email, String newPassword) {
            this.email = email;
            this.newPassword = newPassword;
        }
    }

    public static class UpdateProfileRequest {

        @SerializedName("username")
        public String username;

        @SerializedName("name")
        public String name;

        @SerializedName("babyBirth")
        public String babyBirth;

        @SerializedName("currentPassword")
        public String currentPassword;

        @SerializedName("newPassword")
        public String newPassword;

        public UpdateProfileRequest(
                String username,
                String name,
                String babyBirth,
                String currentPassword,
                String newPassword
        ) {
            this.username = username;
            this.name = name;
            this.babyBirth = babyBirth;
            this.currentPassword = currentPassword;
            this.newPassword = newPassword;
        }
    }

    // =========================================================
    // 2. 정책 / 예방접종 모델
    // =========================================================

    public static class PolicyResponse implements Serializable {

        @SerializedName("서비스명")
        public String title;

        @SerializedName("서비스요약")
        public String summary;

        @SerializedName("소관부처명")
        public String department;

        @SerializedName("소관조직명")
        public String subDepartment;

        @SerializedName("서비스URL")
        public String url;
    }

    public static class VaccineResponse implements Serializable {

        @SerializedName("id")
        public String id;

        @SerializedName("name")
        public String name;

        @SerializedName("degree")
        public int degree;

        @SerializedName("dueDate")
        public String dueDate;

        @SerializedName("dDay")
        public int dDay;

        @SerializedName("targetMonthString")
        public String targetMonthString;

        @SerializedName("description")
        public String description;

        @SerializedName("status")
        public String status;
    }

    public static class VaccineUpdate {

        @SerializedName("dueDate")
        public String dueDate;

        public VaccineUpdate(String dueDate) {
            this.dueDate = dueDate;
        }
    }

    // =========================================================
    // 3. 온습도 / 수면 데이터 모델
    // =========================================================

    public static class TemperHumilityResponse implements Serializable {

        @SerializedName("userId")
        public String userId;

        @SerializedName("temperature")
        public double temperature;

        @SerializedName("humidity")
        public double humidity;

        @SerializedName("noise")
        public Double noise;

        @SerializedName("sleepScore")
        public Integer sleepScore;

        @SerializedName("cryDetected")
        public Boolean cryDetected;

        @SerializedName("cryProbability")
        public Double cryProbability;

        @SerializedName("timestamp")
        public String timestamp;
    }

    public static class TemperHistoryResponse implements Serializable {

        @SerializedName("time")
        public String time;

        @SerializedName("temperature")
        public double temperature;

        @SerializedName("humidity")
        public double humidity;

        @SerializedName("score")
        public Integer sleepScore;
    }

    public static class SleepResponse implements Serializable {

        @SerializedName("time")
        public String time;

        @SerializedName("temp")
        public float temp;

        @SerializedName("humidity")
        public float humidity;

        @SerializedName("noise")
        public float noise;

        @SerializedName("actualScore")
        public float score;

        @SerializedName("status")
        public String status;

        @SerializedName("isEmergency")
        public boolean isEmergency;
    }

    // =========================================================
    // 4. SmartThings 모델
    // =========================================================

    public static class STTokenRequest {

        @SerializedName("email")
        public String email;

        @SerializedName("token")
        public String token;

        public STTokenRequest(String email, String token) {
            this.email = email;
            this.token = token;
        }
    }

    public static class DeviceResponse implements Serializable {

        @SerializedName("ok")
        public boolean ok;

        @SerializedName("message")
        public String message;

        @SerializedName("devices")
        public List<Device> devices;
    }

    public static class Device implements Serializable {

        @SerializedName("deviceId")
        public String deviceId;

        @SerializedName("name")
        public String name;

        @SerializedName("label")
        public String label;

        @SerializedName("category")
        public String category;

        @SerializedName("capabilities")
        public List<String> capabilities;
    }

    public static class DeviceStatusResponse implements Serializable {

        @SerializedName("ok")
        public boolean ok;

        @SerializedName("deviceId")
        public String deviceId;

        @SerializedName("switch")
        public String switchValue;

        @SerializedName("fanSpeed")
        public Integer fanSpeed;

        @SerializedName("temperature")
        public Double temperature;

        @SerializedName("humidity")
        public Double humidity;

        @SerializedName("coolingSetpoint")
        public Double coolingSetpoint;
    }
    //자동환경제어 ON/OFF
    public static class AutoControlRequest implements Serializable {

        @SerializedName("autoControl")
        public boolean autoControl;

        public AutoControlRequest(boolean autoControl) {
            this.autoControl = autoControl;
        }
    }

    public static class AutoControlResponse implements Serializable {

        @SerializedName("ok")
        public boolean ok;

        @SerializedName("autoControl")
        public boolean autoControl;
    }
    public static class ControlRequest implements Serializable {

        @SerializedName("deviceId")
        public String deviceId;

        @SerializedName("capability")
        public String capability;

        @SerializedName("command")
        public String command;

        @SerializedName("value")
        public double value;

        public ControlRequest(
                String deviceId,
                String capability,
                String command,
                double value
        ) {
            this.deviceId = deviceId;
            this.capability = capability;
            this.command = command;
            this.value = value;
        }
    }

    public static class ControlResponse implements Serializable {

        @SerializedName("ok")
        public boolean ok;

        @SerializedName("message")
        public String message;

        @SerializedName("switch")
        public String switchValue;

        @SerializedName("fanSpeed")
        public Integer fanSpeed;

        @SerializedName("temperature")
        public Double temperature;

        @SerializedName("humidity")
        public Double humidity;

        @SerializedName("coolingSetpoint")
        public Double coolingSetpoint;
    }

    // =========================================================
    // 5. AI 분석 모델
    // =========================================================

    public static class AnalysisResponse implements Serializable {

        @SerializedName("timestamp")
        public long timestamp;

        @SerializedName("result")
        public AnalysisData result;

        public static class AnalysisData implements Serializable {

            @SerializedName("data")
            public InnerData data;
        }

        public static class InnerData implements Serializable {

            @SerializedName("result")
            public DetectionResult detectionResult;
        }

        public static class DetectionResult implements Serializable {

            @SerializedName("infant_detected")
            public boolean infantDetected;

            @SerializedName("confidence")
            public float confidence;
        }
    }

    // =========================================================
    // 6. AI 리포트 모델
    // =========================================================

    public static class AiReportResponse implements Serializable {

        @SerializedName("success")
        public boolean success;

        @SerializedName("data")
        public AiReportData data;
    }

    public static class AiReportData implements Serializable {

        @SerializedName("_id")
        public String id;

        @SerializedName("reportType")
        public String reportType;

        @SerializedName("periodStart")
        public String periodStart;

        @SerializedName("periodEnd")
        public String periodEnd;

        @SerializedName("avgTemp")
        public double avgTemp;

        @SerializedName("avgHumidity")
        public double avgHumidity;

        @SerializedName("avgNoise")
        public double avgNoise;

        @SerializedName("avgScore")
        public double avgScore;

        @SerializedName("cryingCount")
        public int cryingCount;

        @SerializedName("reportText")
        public String reportText;

        @SerializedName("createdAt")
        public String createdAt;
    }
    public static class SleepScoreHistoryResponse implements Serializable {

        @SerializedName("userId")
        public String userId;

        @SerializedName("score")
        public float score;

        @SerializedName("time")
        public String time;
    }
}