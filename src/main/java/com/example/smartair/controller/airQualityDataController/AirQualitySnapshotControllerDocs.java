package com.example.smartair.controller.airQualityDataController;

import com.example.smartair.dto.airQualityDataDto.AirQualityDataResponse;
import com.example.smartair.dto.airQualityDataDto.HourlySensorAirQualitySnapshotResponse;
import com.example.smartair.entity.airData.snapshot.HourlySensorAirQualitySnapshot;
import com.example.smartair.entity.login.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;

import java.time.LocalDateTime;

@Tag(name = "AirQuality Snapshot API", description = "시간별 공기질 스냅샷 조회 API")
public interface AirQualitySnapshotControllerDocs {

    @Operation(summary = "특정 시간의 공기질 스냅샷 조회",
            description = "특정 센서의 지정된 시간(YYYY-MM-DDTHH:00:00 형식)에 해당하는 시간별 공기질 스냅샷 정보를 조회합니다. " +
                          "조회된 스냅샷에는 각종 공기질 데이터의 시간별 평균값과 계산된 점수들이 포함됩니다.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "스냅샷 조회 성공",
                            content = @Content(schema = @Schema(implementation = HourlySensorAirQualitySnapshot.class))),
                    @ApiResponse(responseCode = "404", description = "센서 또는 해당 시간의 스냅샷을 찾을 수 없음",
                            content = @Content(schema = @Schema(hidden = true)))
            })
    ResponseEntity<HourlySensorAirQualitySnapshotResponse> getHourlySnapshot(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails,
            @Parameter(description = "센서 일련번호", required = true, example = "1") @PathVariable String serialNumber,
            @Parameter(description = "조회할 스냅샷 시간 (YYYY-MM-DDTHH:00:00 형식). 분, 초, 나노초는 무시되고 정시로 처리됩니다.",
                       required = true, example = "2023-10-28T14:00:00")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime snapshotHour);



    @Operation(summary = "특정 센서의 가장 최신의 대기질 데이터 조회",
            description = "특정 센서의 가장 최신의 대기질 데이터를 조회합니다. ",
            responses = {
                    @ApiResponse(responseCode = "200", description = "대기질 데이터 조회 성공",
                            content = @Content(schema = @Schema(implementation = AirQualityDataResponse.class))),
                    @ApiResponse(responseCode = "404", description = "센서를 찾을 수 없음",
                            content = @Content(schema = @Schema(hidden = true)))
            })
    ResponseEntity<AirQualityDataResponse> getLatestSensorAirQualityData(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails,
            @Parameter(description = "센서 일련번호", required = true, example = "1") @PathVariable String serialNumber);
} 