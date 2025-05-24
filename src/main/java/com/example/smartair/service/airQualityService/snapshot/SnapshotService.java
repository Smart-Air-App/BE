package com.example.smartair.service.airQualityService.snapshot;

import com.example.smartair.entity.airData.airQualityData.SensorAirQualityData;
import com.example.smartair.entity.airData.fineParticlesData.FineParticlesData;
import com.example.smartair.entity.airData.snapshot.HourlySensorAirQualitySnapshot;
import com.example.smartair.entity.airScore.airQualityScore.SensorAirQualityScore;
import com.example.smartair.entity.sensor.Sensor;
import com.example.smartair.exception.CustomException;
import com.example.smartair.exception.ErrorCode;
import com.example.smartair.repository.airQualityRepository.airQualityDataRepository.AirQualityDataRepository;
import com.example.smartair.repository.airQualityRepository.airQualitySnapshotRepository.HourlyDeviceAirQualitySnapshotRepository;
import com.example.smartair.repository.sensorRepository.SensorRepository;
import com.example.smartair.service.airQualityService.calculator.AirQualityCalculator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class SnapshotService {

    private final SensorRepository sensorRepository;
    private final AirQualityDataRepository airQualityDataRepository;
    private final HourlyDeviceAirQualitySnapshotRepository snapshotRepository;
    private final AirQualityCalculator airQualityCalculator;

    /**
     * 센서별로 특정 시간대에 대한 스냅샷을 생성합니다.
     */
    @Transactional
    public void createHourlySnapshot(LocalDateTime snapshotHourBase) {
        // 시간 단위로 절삭
        snapshotHourBase = snapshotHourBase.truncatedTo(ChronoUnit.HOURS);

        //시간별 데이터를 DB에서 조회
        List<SensorAirQualityData> airQualityDataList = airQualityDataRepository.findByCreatedAtBetween(
                snapshotHourBase,
                snapshotHourBase.plusHours(1));


        //센서별로 그룹화하여 스냅샷 생성
        Map<Long, List<SensorAirQualityData>> sensorDataMap = airQualityDataList.stream()
                .collect(Collectors.groupingBy(data -> data.getSensor().getId()));

        //각 센서별로 평균값 계산하여 스냅샷 저장
        sensorDataMap.forEach(this::createSensorSnapshot);
    }

    public void createSensorSnapshot(Long sensorId, List<SensorAirQualityData> hourlyRawDataList) {
        try {
            if (hourlyRawDataList.isEmpty()) {
                log.warn("Device ID: {} 의 {} 시간 스냅샷에 대한 데이터가 없어 스냅샷 생성을 건너뜁니다.",
                        sensorId, hourlyRawDataList.get(0).getCreatedAt());
                return;
            }

            Sensor sensor = sensorRepository.findById(sensorId)
                    .orElseThrow(() -> new CustomException(ErrorCode.SENSOR_NOT_FOUND));


            LocalDateTime snapshotHour = hourlyRawDataList.get(0).getCreatedAt().truncatedTo(ChronoUnit.HOURS);

            // 평균값 계산
            Double avgTemperature = calculateAverage(hourlyRawDataList, SensorAirQualityData::getTemperature);
            Double avgHumidity = calculateAverage(hourlyRawDataList, SensorAirQualityData::getHumidity);
            Integer avgPressure = calculateIntAverage(hourlyRawDataList, SensorAirQualityData::getPressure);
            Integer avgTvoc = calculateIntAverage(hourlyRawDataList, SensorAirQualityData::getTvoc);
            Integer avgEco2 = calculateIntAverage(hourlyRawDataList, SensorAirQualityData::getEco2);
            // PM10, PM25 평균 계산
            Double avgPm10 = calculateAvgPm10Standard(hourlyRawDataList);
            Double avgPm25 = calculateAvgPm25Standard(hourlyRawDataList);

            // 점수 계산을 위한 대표 데이터 생성
            SensorAirQualityData representativeData = createRepresentativeData(
                    sensor, avgTemperature, avgHumidity, avgPressure, avgTvoc, avgEco2, avgPm10, avgPm25);

            // 모든 점수를 한 번에 계산
            SensorAirQualityScore calculatedScores = airQualityCalculator.calculateScore(representativeData);

            // 스냅샷 생성
            HourlySensorAirQualitySnapshot snapshot = HourlySensorAirQualitySnapshot.builder()
                    .sensor(sensor)
                    .snapshotHour(snapshotHour)
                    .hourlyAvgTemperature(avgTemperature)
                    .hourlyAvgHumidity(avgHumidity)
                    .hourlyAvgPressure(avgPressure)
                    .hourlyAvgTvoc(avgTvoc)
                    .hourlyAvgEco2(avgEco2)
                    .hourlyAvgPm10(avgPm10)
                    .hourlyAvgPm25(avgPm25) // 여기까지 평균 데이터 설정
                    .overallScore(calculatedScores.getOverallScore()) // 여기부터 평균 점수 설정
                    .pm10Score(calculatedScores.getPm10Score())
                    .pm25Score(calculatedScores.getPm25Score())
                    .eco2Score(calculatedScores.getEco2Score())
                    .tvocScore(calculatedScores.getTvocScore())
                    .build();

            snapshotRepository.save(snapshot);
            log.info("센서 ID: {}의 {} 시간 스냅샷 생성/업데이트 완료", sensorId, snapshotHour);
        } catch (Exception e) {
            log.error("센서 ID: {}의 스냅샷 생성 중 오류 발생: {}", sensorId, e.getMessage(), e);
        }
    }

    /**
     * 특정 장치의 특정 시간에 대한 시간별 스냅샷을 조회합니다.
     */
    @Transactional(readOnly = true)
    public HourlySensorAirQualitySnapshot getHourlySnapshot(String serialNumber, LocalDateTime snapshotHour) {
        Sensor sensor = sensorRepository.findBySerialNumber(serialNumber)
                .orElseThrow(() -> new CustomException(ErrorCode.SENSOR_NOT_FOUND));

        LocalDateTime startHour = snapshotHour.truncatedTo(ChronoUnit.HOURS);
        LocalDateTime endHour = startHour.plusHours(1);

        return snapshotRepository.findBySensorAndHourRange(sensor, startHour, endHour)
                .orElseThrow(() -> new CustomException(ErrorCode.SNAPSHOT_NOT_FOUND));
    }

    /**
     * 기존 시간별 스냅샷을 업데이트합니다.
     */
    @Transactional
    public HourlySensorAirQualitySnapshot updateHourlySnapshot(HourlySensorAirQualitySnapshot snapshot) {
        // 기존 스냅샷의 시간 범위에 해당하는 DeviceAirQualityData 리스트 조회
        List<SensorAirQualityData> dataList = airQualityDataRepository
                .findBySensorAndCreatedAtBetweenOrderByCreatedAtAsc(snapshot.getSensor(),
                        snapshot.getSnapshotHour(), snapshot.getSnapshotHour().plusHours(1));

        if (dataList.isEmpty()) {
            log.warn("Device ID: {} 의 {} 시간 스냅샷에 대한 데이터가 없어 업데이트하지 않습니다.",
                    snapshot.getSensor().getId(), snapshot.getSnapshotHour());
            throw new CustomException(ErrorCode.SENSOR_AIR_DATA_NOT_FOUND);
        }

        // 평균값 재계산
        Double avgTemperature = calculateAverage(dataList, SensorAirQualityData::getTemperature);
        Double avgHumidity = calculateAverage(dataList, SensorAirQualityData::getHumidity);
        Integer avgPressure = calculateIntAverage(dataList, SensorAirQualityData::getPressure);
        Integer avgTvoc = calculateIntAverage(dataList, SensorAirQualityData::getTvoc);
        Integer avgEco2 = calculateIntAverage(dataList, SensorAirQualityData::getEco2);
        Double avgPm10 = calculateAvgPm10Standard(dataList);
        Double avgPm25 = calculateAvgPm25Standard(dataList);

        // 점수 계산을 위한 대표 데이터 생성
        SensorAirQualityData representativeData = createRepresentativeData(
                snapshot.getSensor(), avgTemperature, avgHumidity, avgPressure, avgTvoc, avgEco2, avgPm10, avgPm25);

        // 모든 점수를 한 번에 계산
        SensorAirQualityScore calculatedScores = airQualityCalculator.calculateScore(representativeData);

        // 스냅샷 객체 업데이트
        snapshot.setHourlyAvgTemperature(avgTemperature);
        snapshot.setHourlyAvgHumidity(avgHumidity);
        snapshot.setHourlyAvgPressure(avgPressure);
        snapshot.setHourlyAvgTvoc(avgTvoc);
        snapshot.setHourlyAvgEco2(avgEco2);
        snapshot.setHourlyAvgPm10(avgPm10);
        snapshot.setHourlyAvgPm25(avgPm25);
        snapshot.setOverallScore(calculatedScores.getOverallScore());
        snapshot.setPm10Score(calculatedScores.getPm10Score());
        snapshot.setPm25Score(calculatedScores.getPm25Score());
        snapshot.setEco2Score(calculatedScores.getEco2Score());
        snapshot.setTvocScore(calculatedScores.getTvocScore());

        // 업데이트된 스냅샷 저장 및 반환
        log.info("Device ID: {} 의 {} 시간 스냅샷 업데이트 완료",
                snapshot.getSensor().getId(), snapshot.getSnapshotHour());
        return snapshotRepository.save(snapshot);
    }

    /**
     * 특정 센서의 가장 최신의 대기질 데이터를 조회합니다.
     * @param serialNumber
     * @return
     */
    public SensorAirQualityData getLatestAirQualityData(String serialNumber) {
        Sensor sensor = sensorRepository.findBySerialNumber(serialNumber).orElseThrow(()-> new CustomException(ErrorCode.SENSOR_NOT_FOUND, "Sensor serialNumber: " + serialNumber));

        return airQualityDataRepository.findTopBySensor_SerialNumberOrderByCreatedAtDesc(serialNumber)
                .orElseThrow(() -> new CustomException(ErrorCode.SENSOR_AIR_DATA_NOT_FOUND, "Sensor serialNumber {}" + serialNumber));
    }

    /**
     * DeviceAirQualityData 리스트에서 Double 필드의 평균을 계산합니다.
     */
    private <T> Double calculateAverage(List<SensorAirQualityData> dataList,
                                        java.util.function.Function<SensorAirQualityData, Double> fieldExtractor) {

        return dataList.stream()
                .map(fieldExtractor)
                .filter(value -> value != null)
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0.0);
    }

    /**
     * DeviceAirQualityData 리스트에서 Integer 필드의 평균을 계산합니다.
     */
    private Integer calculateIntAverage(List<SensorAirQualityData> dataList,
                                        java.util.function.Function<SensorAirQualityData, Integer> fieldExtractor) {
        return (int) dataList.stream()
                .map(fieldExtractor)
                .filter(value -> value != null)
                .mapToInt(Integer::intValue)
                .average()
                .orElse(0.0);
    }

    /**
     * 점수 계산을 위한 대표 DeviceAirQualityData 객체를 생성합니다.
     */
    private SensorAirQualityData createRepresentativeData(Sensor sensor, Double avgTemperature,
                                                          Double avgHumidity, Integer avgPressure, Integer avgTvoc, Integer avgEco2, Double avgPm10, Double avgPm25) {

        // 미세먼지 데이터 객체 생성
        FineParticlesData fineParticlesData = new FineParticlesData();
        fineParticlesData.setPm10_standard(avgPm10);
        fineParticlesData.setPm25_standard(avgPm25);

        // 대표 데이터 객체 생성
        return SensorAirQualityData.builder()
                .sensor(sensor)
                .temperature(avgTemperature)
                .humidity(avgHumidity)
                .pressure(avgPressure)
                .tvoc(avgTvoc)
                .eco2(avgEco2)
                .fineParticlesData(fineParticlesData)
                .build();
    }

    /**
     * DeviceAirQualityData 리스트에서 PM10 평균을 계산합니다.
     */
    private Double calculateAvgPm10Standard(List<SensorAirQualityData> dataList) {
        return dataList.stream()
                .map(data -> data.getFineParticlesData())
                .filter(fpd -> fpd != null) // null 체크 필수
                .mapToDouble(FineParticlesData::getPm10_standard)
                .average()
                .orElse(0.0);
    }

    /**
     * DeviceAirQualityData 리스트에서 PM25_standard 평균을 계산합니다.
     */
    private Double calculateAvgPm25Standard(List<SensorAirQualityData> dataList) {
        return dataList.stream()
                .map(SensorAirQualityData::getFineParticlesData)
                .filter(fpd -> fpd != null) // null 체크 필수
                .mapToDouble(FineParticlesData::getPm25_standard)
                .average()
                .orElse(0.0);
    }
}