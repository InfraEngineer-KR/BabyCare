package com.example.myapplication1;

import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Path;
import retrofit2.http.Query;

public interface ApiService {

    // =========================================================
    // 1. 인증 및 회원 관련
    // =========================================================

    @POST("auth/request-verify")
    Call<AuthModels.UserResponse> requestVerify(
            @Body AuthModels.VerifyRequest body
    );

    @POST("auth/request-reset-verify")
    Call<AuthModels.UserResponse> requestResetVerify(
            @Body AuthModels.VerifyRequest body
    );

    @POST("auth/verify-code")
    Call<AuthModels.UserResponse> verifyCode(
            @Body AuthModels.CodeCheckRequest body
    );

    @POST("auth/signup")
    Call<AuthModels.UserResponse> signup(
            @Body AuthModels.SignupRequest body
    );

    @POST("auth/login")
    Call<AuthModels.UserResponse> login(
            @Body AuthModels.LoginRequest body
    );

    @POST("auth/refresh")
    Call<AuthModels.UserResponse> refresh(
            @Body Map<String, String> refreshToken
    );

    @POST("auth/reset-password")
    Call<AuthModels.UserResponse> resetPassword(
            @Body AuthModels.ResetPasswordRequest body
    );

    @PUT("auth/update-profile")
    Call<AuthModels.UserResponse> updateProfile(
            @Body AuthModels.UpdateProfileRequest body
    );


    // =========================================================
    // 2. 정책 / 예방접종 관련
    // =========================================================

    @GET("api/policies")
    Call<List<AuthModels.PolicyResponse>> getPolicies();

    @GET("api/vaccine/schedule/{userId}")
    Call<List<AuthModels.VaccineResponse>> getVaccineSchedule(
            @Path("userId") String userId
    );

    @PUT("api/vaccine/update/{vaccineId}")
    Call<Void> updateVaccine(
            @Path("vaccineId") String vaccineId,
            @Body AuthModels.VaccineUpdate request
    );


    // =========================================================
    // 3. 온습도 / 수면 데이터
    // =========================================================

    /**
     * 최신 온습도 데이터 조회
     * 예:
     * /api/temhu/latest?userId=lkms1472
     */
    @GET("api/temhu/latest")
    Call<AuthModels.TemperHumilityResponse> getTemhuLatest(
            @Query("userId") String userId
    );

    /**
     * 12시간 온습도 이력 조회
     * 예:
     * /api/temhu/history?userId=lkms1472
     */
    @GET("api/temhu/history")
    Call<List<AuthModels.TemperHistoryResponse>> getTemhuHistory(
            @Query("userId") String userId
    );

    /**
     * 수면점수 변화 그래프 데이터
     * 예:
     * /api/temhu/sleep-score-history?userId=lkms1472
     */
    @GET("api/temhu/sleep-score-history")
    Call<List<AuthModels.SleepScoreHistoryResponse>> getSleepScoreHistory(
            @Query("userId") String userId
    );

    /**
     * 수면 데이터 조회
     */
    @GET("api/sleep/data")
    Call<List<AuthModels.SleepResponse>> getSleepData();


    // =========================================================
    // 4. SmartThings (IoT)
    // =========================================================

    @POST("api/smartthings/register")
    Call<AuthModels.DeviceResponse> registerSTToken(
            @Header("Authorization") String jwtToken,
            @Body AuthModels.STTokenRequest body
    );

    @GET("api/smartthings/devices")
    Call<AuthModels.DeviceResponse> getDevices(
            @Header("Authorization") String jwtToken
    );

    @GET("api/smartthings/status/{deviceId}")
    Call<AuthModels.DeviceStatusResponse> getDeviceStatus(
            @Header("Authorization") String jwtToken,
            @Path("deviceId") String deviceId
    );

    @POST("api/smartthings/control")
    Call<AuthModels.ControlResponse> controlDevice(
            @Header("Authorization") String jwtToken,
            @Body AuthModels.ControlRequest body
    );
    // 자동제어 기능
    @POST("api/smartthings/auto-control")
    Call<AuthModels.AutoControlResponse> setAutoControl(
            @Header("Authorization") String jwtToken,
            @Body AuthModels.AutoControlRequest body
    );

    @GET("api/smartthings/auto-control")
    Call<AuthModels.AutoControlResponse> getAutoControl(
            @Header("Authorization") String jwtToken
    );

    // =========================================================
    // 5. AI 보고서
    // =========================================================

    /**
     * 최신 AI 리포트 조회
     */
    @GET("api/ai/report/latest")
    Call<AuthModels.AiReportResponse> getLatestReport();

    /**
     * AI 리포트 수동 생성
     */
    @POST("api/ai/generate")
    Call<AuthModels.AiReportResponse> generateReport();
}