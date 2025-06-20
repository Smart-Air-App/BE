package com.example.smartair.service.airQualityService;

import com.example.smartair.dto.airQualityScoreDto.AverageScoreDto;
import com.example.smartair.entity.airData.airQualityData.SensorAirQualityData;
import com.example.smartair.entity.airScore.airQualityScore.SensorAirQualityScore;
import com.example.smartair.entity.airScore.airQualityScore.RoomAirQualityScore;
import com.example.smartair.entity.sensor.Sensor;
import com.example.smartair.entity.room.Room;
import com.example.smartair.entity.roomSensor.RoomSensor;
import com.example.smartair.exception.CustomException;
import com.example.smartair.exception.ErrorCode;
import com.example.smartair.repository.airQualityRepository.airQualityScoreRepository.SensorAirQualityScoreRepository;
import com.example.smartair.repository.airQualityRepository.airQualityScoreRepository.RoomAirQualityScoreRepository;
import com.example.smartair.repository.roomSensorRepository.RoomSensorRepository;
import com.example.smartair.service.airQualityService.calculator.AirQualityCalculator;
import com.example.smartair.util.BaseEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class AirQualityScoreService {

    private final AirQualityCalculator airQualityCalculator;
    private final SensorAirQualityScoreRepository sensorAirQualityScoreRepository;
    private final RoomAirQualityScoreRepository roomAirQualityScoreRepository;
    private final RoomSensorRepository roomSensorRepository;

    /**
     * 주어진 AirQualityData를 기반으로 공기질 점수를 계산합니다.
     *
     * @param airQualityData
     */
    @Transactional
    public void calculateAndSaveDeviceScore(SensorAirQualityData airQualityData) { //개별 데이터 실시간 점수 계산
        if (airQualityData == null) {
            throw new CustomException(ErrorCode.INVALID_INPUT_DATA, "AirQualityData is null");
        }
        Sensor sensor = airQualityData.getSensor();
        if (sensor == null) {
            throw new CustomException(ErrorCode.SENSOR_NOT_FOUND, "Sensor not found for AirQualityData");
        }

        // 개별 DeviceAirQualityScore 계산 및 저장
        SensorAirQualityScore calculatedDeviceScore = airQualityCalculator.calculateScore(airQualityData);
        calculatedDeviceScore.setSensorAirQualityData(airQualityData);
        sensorAirQualityScoreRepository.save(calculatedDeviceScore);
        log.info("SensorAirQualityScore 저장 완료: ID {}", calculatedDeviceScore.getId());

        // Room 정보 조회 (없을 수 있음)
        Optional<Room> roomOptional = roomSensorRepository.findBySensor(sensor)
                .map(RoomSensor::getRoom);

        // 방 정보가 존재하는 경우에만 평균 점수 업데이트
        roomOptional.ifPresent(room -> {
            try{
                updateRoomAverageScore(room);
            }catch (Exception e){
            log.error("Room ID {}의 평균 점수 업데이트 중 오류 발생", room.getId(), e);
        }
        });
}

    @Transactional
    public void updateRoomAverageScore(Room room) { //방 평균 점수 업데이트
        log.info("Updating average score for Room ID: {}", room.getId());
        List<SensorAirQualityScore> airQualityScoreList = new ArrayList<>();
        List<Sensor> sensorList = roomSensorRepository.findAllSensorByRoom(room);

        if (sensorList.isEmpty()) {
            log.warn("Room ID: {} 에 속한 Sensor가 없습니다. 평균 점수 계산을 건너뛰었습니다.", room.getId());
            return; // 처리할 디바이스 없으면 종료
        }

        for (Sensor sensor : sensorList) {
            try {
                //디바이스의 최신 한 건의 공기질 점수 데이터 조회
                sensorAirQualityScoreRepository.findTopBySensorAirQualityData_SensorOrderByCreatedAtDesc(sensor)
                        .ifPresent(airQualityScoreList::add);
                // .orElseThrow() 대신 ifPresent 사용으로 점수 없는 디바이스는 그냥 넘어감
            } catch (Exception e) { // 데이터 조회 중 예외 처리 (예: DB 연결 문제)
                log.error("Sensor ID {}의 최신 공기질 점수 조회 중 오류 발생", sensor.getId(), e);
            }
        }

        // 방에 존재하는 모든 디바이스 공기질 데이터의 평균 데이터 계산
        AverageScoreDto averageDeviceScoreDto = calculateAverageDeviceScore(airQualityScoreList);

        // 기존 가장 최신 한 건의 RoomAirQualityScore 조회
        Optional<RoomAirQualityScore> existingRoomScoreOptional = roomAirQualityScoreRepository.findFirstByRoomOrderByCreatedAtDesc(room);
        RoomAirQualityScore roomScoreToSave; // 저장할 객체 선언

        if (existingRoomScoreOptional.isPresent()) {
            // 기존 기록 업데이트
            roomScoreToSave = existingRoomScoreOptional.get();
            log.info("Room ID {}의 기존 평균 점수 레코드(ID: {})를 업데이트합니다.", room.getId(), roomScoreToSave.getId());
        } else {
            // 신규 기록 생성
            roomScoreToSave = new RoomAirQualityScore();
            roomScoreToSave.setRoom(room);
            log.info("Room ID {}의 평균 점수 레코드를 신규 생성합니다.", room.getId());
        }

        // 평균 점수 DTO 값으로 업데이트/설정
        roomScoreToSave.setOverallScore(averageDeviceScoreDto.getOverallScore());
        roomScoreToSave.setPm10Score(averageDeviceScoreDto.getPm10Score());
        roomScoreToSave.setPm25Score(averageDeviceScoreDto.getPm25Score());
        roomScoreToSave.setEco2Score(averageDeviceScoreDto.getEco2Score());
        roomScoreToSave.setTvocScore(averageDeviceScoreDto.getTvocScore());
        roomAirQualityScoreRepository.save(roomScoreToSave);
        log.info("Room ID {}의 평균 점수 저장 완료 (Score ID: {})", room.getId(), roomScoreToSave.getId());
    }

//        // 공간 평균 점수 업데이트 트리거
//        Place place = room.getPlace();
//        if (place != null) {
//            try {
//                updatePlaceAverageScore(place); // 공간 점수 업데이트 메서드 호출
//            } catch (Exception e) {
//                log.error("Place ID {}의 평균 점수 업데이트 중 오류 발생 (Triggered from Room ID {})", place.getId(), room.getId(), e);
//            }
//        } else {
//            log.warn("Room ID {}에 연결된 Place 정보가 없어 Place 점수 업데이트를 건너뛰었습니다.", room.getId());
//        }
//    }

//    // 공간 평균 점수 업데이트 메서드
//    private void updatePlaceAverageScore(Place place) {
//        log.info("Placeholder: updatePlaceAverageScore called for Place ID: {}", place.getId());
//
//        List<RoomAirQualityScore> roomAirQualityScoreList = new ArrayList<>();
//
//        // 1. place에 속한 room 목록 조회
//         List<Room> roomList = roomRepository.findAllByPlace(place);
//         if (roomList.isEmpty()) {
//             log.warn("Place ID: {}에 속한 Room이 없습니다.", place.getId());
//             return;
//         }
//
//        // 2. 각 room의 최신 RoomAirQualityScore 조회
//        for (Room room : roomList) {
//            roomAirQualityScoreRepository.findFirstByRoomOrderByCreatedAtDesc(room)
//                    .ifPresent(roomAirQualityScoreList::add);
//        }
//
//        // 3. 점수들 평균 계산
//        AverageScoreDto averageRoomScoreDto = calculateAverageRoomScore(roomAirQualityScoreList);
//
//        // 4. 기존 PlaceAirQualityScore 조회 및 업데이트/생성
//        Optional<PlaceAirQualityScore> existingPlaceScoreOptional = placeAirQualityScoreRepository.findFirstByPlaceOrderByCreatedAtDesc(place);
//        PlaceAirQualityScore placeScoreToSave;
//
//        if (existingPlaceScoreOptional.isPresent()) {
//            placeScoreToSave = existingPlaceScoreOptional.get();
//            log.info("Place ID {}의 기존 평균 점수 레코드(ID: {})를 업데이트합니다.", place.getId(), placeScoreToSave.getId());
//        }
//        else{
//            placeScoreToSave = new PlaceAirQualityScore();
//            placeScoreToSave.setPlace(place);
//            log.info("Place ID {}의 평균 점수 레코드를 신규 생성합니다.", place.getId());
//        }
//
//        // 5. 저장
//        placeScoreToSave.setOverallScore(averageRoomScoreDto.getOverallScore());
//        placeScoreToSave.setPm10Score(averageRoomScoreDto.getPm10Score());
//        placeScoreToSave.setPm25Score(averageRoomScoreDto.getPm25Score());
//        placeScoreToSave.setEco2Score(averageRoomScoreDto.getEco2Score());
//        placeScoreToSave.setTvocScore(averageRoomScoreDto.getTvocScore());
//        placeAirQualityScoreRepository.save(placeScoreToSave);
//        log.info("Place ID {}의 평균 점수 저장 완료 (Score ID: {})", place.getId(), placeScoreToSave.getId());
//    }

    public AverageScoreDto calculateAverageRoomScore(List<RoomAirQualityScore> airQualityScoreList) { //방 평균 점수 계산
        log.info("===calculateAverageRoomScore 실행 시작 ({}개 데이터)===", airQualityScoreList.size());

        // 빈 리스트 체크 추가
        if (airQualityScoreList.isEmpty()) {
            log.warn("평균 점수 계산 대상 리스트가 비어있습니다. 0점을 반환합니다.");
            return AverageScoreDto.builder()
                    .overallScore(0.0).pm10Score(0.0).pm25Score(0.0)
                    .eco2Score(0.0).tvocScore(0.0).build();
        }

        double sumOverallScore = 0; // 변수 이름 명확화 (sum)
        double sumPm10Score = 0;
        double sumPm25Score = 0;
        double sumEco2Score = 0;
        double sumTvocScore = 0;

        for (RoomAirQualityScore roomAirQualityScore : airQualityScoreList) {
            sumOverallScore += roomAirQualityScore.getOverallScore();
            sumPm10Score += roomAirQualityScore.getPm10Score();
            sumPm25Score += roomAirQualityScore.getPm25Score();
            sumEco2Score += roomAirQualityScore.getEco2Score();
            sumTvocScore += roomAirQualityScore.getTvocScore();
        }

        int count = airQualityScoreList.size(); // 개수 미리 계산

        AverageScoreDto averageScoreDto = AverageScoreDto.builder()
                .overallScore(sumOverallScore / count) // 평균 계산
                .pm10Score(sumPm10Score / count)
                .pm25Score(sumPm25Score / count)
                .eco2Score(sumEco2Score / count)
                .tvocScore(sumTvocScore / count)
                .build(); // DTO에 deviceId, timestamp 등은 포함되지 않음

        log.info("===calculateAverageRoomScore 실행 완료===");
        return averageScoreDto;
    }

    public AverageScoreDto calculateAverageDeviceScore(List<SensorAirQualityScore> airQualityScoreList) {// 센서 평균 점수 계산
        log.info("===calculateAverageDeviceScore 실행 시작 ({}개 데이터)===", airQualityScoreList.size());

        // 빈 리스트 체크 추가
        if (airQualityScoreList.isEmpty()) {
            log.warn("평균 점수 계산 대상 리스트가 비어있습니다. 0점을 반환합니다.");
            return AverageScoreDto.builder()
                    .overallScore(0.0).pm10Score(0.0).pm25Score(0.0)
                    .eco2Score(0.0).tvocScore(0.0).build();
        }

        double sumOverallScore = 0; // 변수 이름 명확화 (sum)
        double sumPm10Score = 0;
        double sumPm25Score = 0;
        double sumEco2Score = 0;
        double sumTvocScore = 0;

        for (SensorAirQualityScore sensorAirQualityScore : airQualityScoreList) {
            sumOverallScore += sensorAirQualityScore.getOverallScore();
            sumPm10Score += sensorAirQualityScore.getPm10Score();
            sumPm25Score += sensorAirQualityScore.getPm25Score();
            sumEco2Score += sensorAirQualityScore.getEco2Score();
            sumTvocScore += sensorAirQualityScore.getTvocScore();
        }

        int count = airQualityScoreList.size(); // 개수 미리 계산

        AverageScoreDto averageScoreDto = AverageScoreDto.builder()
                .overallScore(sumOverallScore / count) // 평균 계산
                .pm10Score(sumPm10Score / count)
                .pm25Score(sumPm25Score / count)
                .eco2Score(sumEco2Score / count)
                .tvocScore(sumTvocScore / count)
                .build(); // DTO에 deviceId, timestamp 등은 포함되지 않음

        log.info("===calculateAverageDeviceScore 실행 완료===");
        return averageScoreDto;
    }

   @Transactional
    public void calculateHourlyRoomScore(Room room){ //시간별 방 점수 계산
       log.info("===Scheduling : Calculating hourly average score for Room ID: {}===", room.getId());
       LocalDateTime oneHourAgo = LocalDateTime.now().minusHours(1);

       List<Sensor> sensors = roomSensorRepository.findAllSensorByRoom(room);
       List<SensorAirQualityScore> hourlyScores = new ArrayList<>();
       for (Sensor sensor : sensors) {
           try {
               List<SensorAirQualityScore> hourlyData = sensorAirQualityScoreRepository
                       .findScoresBySensorSerialNumberAndTimeRange(sensor.getSerialNumber(), oneHourAgo, LocalDateTime.now());
               hourlyScores.addAll(hourlyData);
           } catch (Exception e) {
               log.error("Scheduling : Failed to process data for Sensor ID {}: {}", sensor.getId(), e.getMessage());
           }
       }
       if (!hourlyScores.isEmpty()){
           AverageScoreDto averageScoreDto = calculateAverageDeviceScore(hourlyScores);
           RoomAirQualityScore roomAirQualityScore = RoomAirQualityScore.builder()
                   .room(room)
                   .overallScore(averageScoreDto.getOverallScore())
                   .pm10Score(averageScoreDto.getPm10Score())
                   .pm25Score(averageScoreDto.getPm25Score())
                   .eco2Score(averageScoreDto.getEco2Score())
                   .tvocScore(averageScoreDto.getTvocScore())
                   .build();
           roomAirQualityScoreRepository.save(roomAirQualityScore);
           log.info("Scheduling : Hourly average score saved for Room ID: {}, Score ID: {}", room.getId(), roomAirQualityScore.getId());
       }
       log.info("Scheduling : Completed hourly room score calculation for Room ID: {}", room.getId());
   }
}

