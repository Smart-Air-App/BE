package com.example.smartair.service.airQualityService;

import com.example.smartair.controller.predictedAirQualityController.PredictedAirQualityControllerDocs;
import com.example.smartair.dto.predictedAirQualityDto.PredictedAirQualityDto;
import com.example.smartair.dto.roomSensorDto.SensorRoomMappingDto;
import com.example.smartair.entity.airData.predictedAirQualityData.PredictedAirQualityData;
import com.example.smartair.entity.roomSensor.RoomSensor;
import com.example.smartair.entity.sensor.Sensor;
import com.example.smartair.exception.CustomException;
import com.example.smartair.exception.ErrorCode;
import com.example.smartair.repository.airQualityRepository.predictedAirQualityRepository.PredictedAirQualityRepository;
import com.example.smartair.repository.roomSensorRepository.RoomSensorRepository;
import com.example.smartair.repository.sensorRepository.SensorRepository;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@AllArgsConstructor
@Slf4j
public class PredictedAirQualityService  {

    private final PredictedAirQualityRepository predictedAirQualityRepository;
    private final RoomSensorRepository roomSensorRepository;
    private static final DateTimeFormatter PREDICTED_TIMESTAMP_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    public List<SensorRoomMappingDto> getSensorMappingWithRoom() {
        List<RoomSensor> roomSensors = roomSensorRepository.findAll();

        return roomSensors.stream()
                .map(rs -> new SensorRoomMappingDto(
                        rs.getSensor().getSerialNumber(),
                        rs.getSensor().getRoomRegisterDate()
                ))
                .toList();

    }

    public void setPredictedAirQuality(List<PredictedAirQualityDto> predictedAirQualityDtoList) {

        for (PredictedAirQualityDto dto : predictedAirQualityDtoList) {
            String sensorSerialNumber = dto.getSensorSerialNumber();
            LocalDateTime timestamp = dto.getTimestamp().minusHours(9);
            float pm10 = dto.getPm10();
            float co2 = dto.getCo2();
            float tvoc = dto.getTvoc();

            // 센서 시리얼 번호로 방 ID 조회
            Long roomId = roomSensorRepository.findBySensor_SerialNumber(sensorSerialNumber)
                    .orElseThrow(() -> new CustomException(
                            ErrorCode.ROOM_SENSOR_MAPPING_NOT_FOUND,
                            String.format("센서 일련번호 %s와 매핑된 방 정보를 찾을 수 없습니다.", sensorSerialNumber)
                    ))
                    .getRoom()
                    .getId();

            PredictedAirQualityData predictedAirQualityData;
            Optional<PredictedAirQualityData> existingData = predictedAirQualityRepository.findBySensorSerialNumberAndTimestamp(sensorSerialNumber, timestamp);

            // 예측된 공기질 데이터가 이미 존재하는 경우 업데이트
            if(existingData.isPresent()) {
                predictedAirQualityData = existingData.get();
                predictedAirQualityData.setPm10(pm10);
                predictedAirQualityData.setCo2(co2);
                predictedAirQualityData.setTvoc(tvoc);
            }else { // 예측된 공기질 데이터가 존재하지 않는 경우 새로 생성
                predictedAirQualityData = PredictedAirQualityData.builder()
                        .sensorSerialNumber(sensorSerialNumber)
                        .roomId(roomId)
                        .timestamp(timestamp)
                        .pm10(pm10)
                        .co2(co2)
                        .tvoc(tvoc)
                        .build();
            }
            log.info("예측된 공기질 데이터 저장: Sensor={}, Timestamp={}, PM10={}, CO2={}, TVOC={}",
                    sensorSerialNumber, timestamp, pm10, co2, tvoc);
            // 예측된 공기질 데이터 저장
            predictedAirQualityRepository.save(predictedAirQualityData);
        }
    }

    // 예측된 공기질 데이터를 조회하는 메소드
    public List<PredictedAirQualityData> getPredictedAirQuality(String sensorSerialNumber) {
        return predictedAirQualityRepository.findBySensorSerialNumberOrderByTimestamp(sensorSerialNumber);
    }

    @Scheduled(cron = "0 0 0 * * *")
    public void deleteAllData() {
        log.info("매일 자정 데이터 삭제 작업 시작");
        try {
            predictedAirQualityRepository.deleteAllInBatch();
            log.info("모든 데이터 삭제 완료");
        } catch (Exception e) {
            log.error("데이터 삭제 중 오류 발생: {}", e.getMessage(), e);
        }
    }
}
