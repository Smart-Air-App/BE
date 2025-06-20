package com.example.smartair.controller.sensorContoller;

import com.example.smartair.dto.roomSensorDto.RoomSensorResponseDto;
import com.example.smartair.dto.sensorDto.SensorRequestDto;
import com.example.smartair.dto.sensorDto.SensorResponseDto;
import com.example.smartair.entity.login.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@Tag(name = "Sensor API", description = "사용자 센서 관리 API")
public interface SensorControllerDocs {

    @Operation(
            summary = "센서 등록",
            description = """
        ## 센서 등록

        사용자가 새로운 센서를 등록합니다.

        ---

        **요청 정보**
        - 인증 정보는 `@AuthenticationPrincipal` 을 통해 자동 주입됩니다.

        **요청 본문 (`RequestBody`)**
        - `deviceDto` (Object): 센서 등록에 필요한 정보
          - `serialNumber` (Long): 센서의 일련번호
          - `name` (String): 등록할 센서의 이름

        ---

        **응답**
        - `200 OK`: "success" 메시지 반환
        - `401 Unauthorized`: 인증 정보가 없을 경우 오류 메시지 반환
        """
    )
    ResponseEntity<?> setSensor(@AuthenticationPrincipal CustomUserDetails userDetails,
                                @RequestBody SensorRequestDto.setSensorDto deviceDto) throws Exception;

    @Operation(
            summary = "센서를 방에 등록",
            description = """
    ## 센서를 방에 등록

    사용자가 보유한 센서를 특정 방에 등록합니다.

    ---

    **요청 정보**
    - 인증 정보는 `@AuthenticationPrincipal` 을 통해 자동 주입됩니다.

    **요청 본문 (`RequestBody`)**
    - `sensorDto` (Object): 센서-방 매핑에 필요한 정보
      - `serialNumber` (Long): 센서의 일련번호
      - `roomId` (Long): 등록할 방의 ID

    ---

    **응답**
    - `200 OK`: 센서-방 매핑 정보 반환
    - `401 Unauthorized`: 인증 정보가 없을 경우
    - `404 Not Found`: 센서 또는 방을 찾을 수 없는 경우
    - `409 Conflict`: 이미 방에 등록된 센서인 경우
    """
    )
    ResponseEntity<RoomSensorResponseDto> addSensorToRoom(@AuthenticationPrincipal CustomUserDetails userDetails,
                                                          @RequestBody SensorRequestDto.addSensorToRoomDto sensorDto) throws Exception;


    @Operation(
            summary = "센서 삭제",
            description = """
        ## 센서 삭제

        사용자가 등록한 센서를 삭제합니다.
        관련된 SensorAirQualityData, PredictedAirQualityData, Fineparticlesdata, DailyReport, WeeklyReport, HourlySnapshot도 삭제됩니다.

        ---

        **요청 정보**
        - 인증 정보는 `@AuthenticationPrincipal` 을 통해 자동 주입됩니다.
        - 'serialNumber' (String): 삭제할 센서의 일련번호

        ---

        **응답**
        - `200 OK`: "success" 메시지 반환
        - `401 Unauthorized`: 인증 정보가 없을 경우 오류 메시지 반환
        """
    )
    ResponseEntity<?> deleteSensor(@AuthenticationPrincipal CustomUserDetails userDetails,
                                   @RequestParam String serialNumber) throws Exception;

    @Operation(
            summary = "센서 상태 조회",
            description = """
        ## 센서 상태 조회

        특정 센서의 상태를 조회합니다.

        ---

        **요청 정보**
        - 인증 정보는 `@AuthenticationPrincipal` 을 통해 자동 주입됩니다.

        **요청 본문 (`RequestBody`)**
        - `sensorSerialNumber` (String): 조회할 센서의 일련번호

        ---

        **응답**
        - `200 OK`: 센서의 상태 반환
        - `401 Unauthorized`: 인증 정보가 없을 경우 오류 메시지 반환
        """
    )
    ResponseEntity<?> getSensorStatus(@AuthenticationPrincipal CustomUserDetails userDetails,
                                      @RequestBody String deviceSerialNumber) throws Exception;

    @DeleteMapping("/sensor/room")
    @Operation(summary = "방에서 센서 등록 해제", description = "특정 방에서 센서의 등록을 해제합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "센서 등록 해제 성공"),
            @ApiResponse(responseCode = "401", description = "인증되지 않은 사용자"),
            @ApiResponse(responseCode = "404", description = "센서 또는 방을 찾을 수 없음"),
            @ApiResponse(responseCode = "403", description = "권한 없음")
    })
    ResponseEntity<?> unregisterSensorFromRoom(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam String serialNumber,
            @RequestParam Long roomId) throws Exception;

    @GetMapping("/sensor/{sensorId}")
    @Operation(summary = "센서 ID로 센서 정보 조회", description = "센서 ID를 기반으로 센서 정보를 조회합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "센서 정보 조회 성공"),
            @ApiResponse(responseCode = "401", description = "인증 실패"),
            @ApiResponse(responseCode = "403", description=  "해당 센서에 대한 권한 없음"),
            @ApiResponse(responseCode = "404", description = "해당 ID의 센서를 찾을 수 없음")
    })
    ResponseEntity<SensorResponseDto> getSensorById(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(value = "센서 ID", required = true) Long sensorId);

    @GetMapping("/user/sensors")
    @Operation(summary = "사용자 센서 목록 조회", description = "사용자가 등록한 모든 센서의 목록을 조회합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "센서 목록 조회 성공"),
            @ApiResponse(responseCode = "401", description = "인증 실패")
    })
    ResponseEntity<List<SensorResponseDto>> getUserSensors(@AuthenticationPrincipal CustomUserDetails userDetails);

    @GetMapping("/sensor/find/{serialNumber}")
    @Operation(summary = "일련번호로 센서 정보 조회", description = "센서의 일련번호를 기반으로 센서 정보를 조회합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "센서 정보 조회 성공"),
            @ApiResponse(responseCode = "401", description = "인증 실패"),
            @ApiResponse(responseCode = "404", description = "해당 일련번호의 센서를 찾을 수 없음")
    })
    ResponseEntity<SensorResponseDto> getSensorBySerialNumber(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Parameter(description = "센서 일련번호", required = true) String serialNumber);
}