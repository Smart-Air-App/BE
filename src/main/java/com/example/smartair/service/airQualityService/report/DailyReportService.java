package com.example.smartair.service.airQualityService.report;

import com.example.smartair.entity.airData.report.DailySensorAirQualityReport;
import com.example.smartair.entity.airData.snapshot.HourlySensorAirQualitySnapshot;
import com.example.smartair.entity.sensor.Sensor;
import com.example.smartair.exception.CustomException;
import com.example.smartair.exception.ErrorCode;
import com.example.smartair.repository.airQualityRepository.airQualityReportRepository.DailySensorAirQualityReportRepository;
import com.example.smartair.repository.airQualityRepository.airQualitySnapshotRepository.HourlySensorAirQualitySnapshotRepository;
import com.example.smartair.repository.sensorRepository.SensorRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class DailyReportService {

    private final SensorRepository sensorRepository;
    private final DailySensorAirQualityReportRepository dailyReportRepository;
    private final HourlySensorAirQualitySnapshotRepository snapshotRepository;

    /**
     * 특정 장치의 특정 날짜에 대한 일별 보고서를 생성하거나 업데이트합니다.
     * 이미 보고서가 존재하면 업데이트하고, 없으면 새로 생성합니다.
     */
    @Transactional
    public DailySensorAirQualityReport createOrUpdateDailyReport(Long sensorId, LocalDate date) {
        Sensor sensor = sensorRepository.findById(sensorId)
                .orElseThrow(() -> new CustomException(ErrorCode.SENSOR_NOT_FOUND, "sensor id: " + sensorId));

        Optional<DailySensorAirQualityReport> existingReportOpt =
                dailyReportRepository.findBySensorAndReportDate(sensor, date);

        LocalDateTime startOfDay = date.atStartOfDay();
        LocalDateTime endOfDay = date.atTime(LocalTime.MAX); // 23:59:59.999999999

        List<HourlySensorAirQualitySnapshot> hourlySnapshots =
                snapshotRepository.findBySensorAndSnapshotHourBetweenOrderBySnapshotHourAsc(sensor, startOfDay, endOfDay);

        if (hourlySnapshots.isEmpty()) {
            throw new CustomException(ErrorCode.SNAPSHOT_NOT_FOUND, "해당 날짜에 대한 HourlySensorAirQualitySnapshot이 없습니다.");
        }

        DailySensorAirQualityReport report;
        if (existingReportOpt.isPresent()){
            //업데이트
            report = existingReportOpt.get();
            log.info("Sensor SerialNumber: {}의 {} 날짜에 대한 기존 일별 보고서를 업데이트합니다.",
                    sensor.getSerialNumber(), date);
        }
        else{
            //새로 생성
            log.info("Sensor SerialNumber: {}의 {} 날짜에 대한 새로운 일별 보고서를 생성합니다.",
                    sensor.getSerialNumber(), date);
            report = DailySensorAirQualityReport.builder()
                    .sensor(sensor)
                    .reportDate(date)
                    .build();
        }

        report.setValidDataPointCount(hourlySnapshots.size());
        calculateAndSetDailyStatistics(report, hourlySnapshots);
        report = dailyReportRepository.save(report);

        return report;
    }

    @Transactional
    public DailySensorAirQualityReport generateDailyReportManually(String serialNumber, LocalDate date) {
        log.info("센서 {}의 {} 날짜에 대한 일별 보고서를 수동으로 생성합니다.", serialNumber, date);

        Sensor sensor = sensorRepository.findBySerialNumber(serialNumber)
                .orElseThrow(() -> new CustomException(ErrorCode.SENSOR_NOT_FOUND, "sensor serialNumber: " + serialNumber));

        return createOrUpdateDailyReport(sensor.getId(), date);
    }

    @Transactional
    public void deleteDailyReportBySerialNumber(String serialNumber, LocalDate date) {
        log.info("센서 serialNumber {}의 {} 날짜에 대한 일별 보고서를 삭제합니다.", serialNumber, date);

        Sensor sensor = sensorRepository.findBySerialNumber(serialNumber)
                .orElseThrow(() -> new CustomException(ErrorCode.SENSOR_NOT_FOUND, "sensor serialNumber: " + serialNumber));

        DailySensorAirQualityReport report = dailyReportRepository.findBySensorAndReportDate(sensor, date)
                .orElseThrow(() -> new CustomException(ErrorCode.REPORT_NOT_FOUND, "report not found with SensorId: " + serialNumber + ", date: " + date));

        dailyReportRepository.delete(report);
        log.info("센서 {}의 {} 날짜에 대한 일별 보고서 삭제 완료", serialNumber, date);
    }

    /**
     * 특정 장치의 특정 날짜에 대한 일별 보고서를 조회합니다.
     */
    @Transactional(readOnly = true)
    public DailySensorAirQualityReport getDailyReport(String serialNumber, LocalDate date) {
        Sensor sensor = sensorRepository.findBySerialNumber(serialNumber)
                .orElseThrow(() -> new CustomException(ErrorCode.SENSOR_NOT_FOUND, "sensor id: " + serialNumber));
        return dailyReportRepository.findBySensorAndReportDate(sensor, date)
                .orElseThrow(() -> new CustomException(ErrorCode.REPORT_NOT_FOUND, "report not found with SensorId: " + serialNumber + ", date: " + date));
    }

    /**
     * 특정 장치의 특정 기간 동안의 일별 보고서 목록을 조회합니다.
     */
    @Transactional(readOnly = true)
    public List<DailySensorAirQualityReport> getDailyReportsForPeriod(String serialNumber, LocalDate startDate, LocalDate endDate) {
        if (startDate.isAfter(endDate)) {
            throw new CustomException(ErrorCode.INVALID_DATE_RANGE);
        }
        Sensor sensor = sensorRepository.findBySerialNumber(serialNumber)
                .orElseThrow(() -> new CustomException(ErrorCode.SENSOR_NOT_FOUND, "Sensor serialNumber: " + serialNumber));
        return dailyReportRepository.findBySensorAndReportDateBetweenOrderByReportDateAsc(sensor, startDate, endDate);
    }


    /**
     * 특정 ID의 일별 보고서를 삭제합니다.
     *
     */
    @Transactional
    public void deleteDailyReport(Long reportId) {
        DailySensorAirQualityReport report = dailyReportRepository.findById(reportId)
                .orElseThrow(() -> new CustomException(ErrorCode.REPORT_NOT_FOUND, "report id: " + reportId));

        dailyReportRepository.delete(report);
        log.info("일별 보고서 ID: {} 삭제 완료", reportId);
    }

    /**
     * 생성된 지 N일이 지난 오래된 일별 보고서를 삭제합니다. (예: 2주 = 14일)
     */
    @Transactional
    public int deleteOldDailyReports(int daysOld) {
        if (daysOld <= 0) {
            throw new CustomException(ErrorCode.INVALID_INPUT_DATA,  "daysOld는 1 이상이어야 합니다.");
        }
        LocalDate cutoffDate = LocalDate.now().minusDays(daysOld);
        log.info("{} 이전의 일별 보고서 삭제 시작 ({}일 이전 데이터)", cutoffDate, daysOld);
        List<DailySensorAirQualityReport> oldReports = dailyReportRepository.findByReportDateBefore(cutoffDate);
        if (oldReports.isEmpty()) {
            log.info("{} 이전의 삭제할 일별 보고서가 없습니다.", cutoffDate);
            return 0;
        }
        // 주의: 대량 삭제 시 성능 문제를 고려해야 할 수 있습니다. (예: 배치 처리)
        dailyReportRepository.deleteAllInBatch(oldReports); // deleteAll() 보다 효율적일 수 있음
        log.info("{} 이전 일별 보고서 {}개 삭제 완료", cutoffDate, oldReports.size());
        return oldReports.size();
    }

    /**
     * 특정 장치와 관련된 모든 일별 보고서를 삭제합니다.
     * Device 삭제 시 호출될 수 있습니다.
     */
    @Transactional
    public int deleteDailyReportsByDeviceId(String serialNumber) {
        if (sensorRepository.findBySerialNumber(serialNumber).isEmpty()) {
            throw new CustomException(ErrorCode.SENSOR_NOT_FOUND, "sensor serialNumber: " + serialNumber);
        }
        log.info("Sensor serialNumber: {} 관련 모든 일별 보고서 삭제 시작", serialNumber);
        List<DailySensorAirQualityReport> reportsToDelete = dailyReportRepository.findAllBySensorSerialNumber(serialNumber);
        if (reportsToDelete.isEmpty()) {
            log.info("Device ID: {} 관련 삭제할 일별 보고서가 없습니다.", serialNumber);
            return 0;
        }
        dailyReportRepository.deleteAllInBatch(reportsToDelete);
        log.info("Sensor serialNumber: {} 관련 일별 보고서 {}개 삭제 완료", serialNumber, reportsToDelete.size());
        return reportsToDelete.size();
    }


    private void calculateAndSetDailyStatistics(DailySensorAirQualityReport report, List<HourlySensorAirQualitySnapshot> snapshots) {
        // 온도 통계
        DoubleSummaryStatistics tempStats = snapshots.stream()
                .map(HourlySensorAirQualitySnapshot::getHourlyAvgTemperature)
                .filter(Objects::nonNull)
                .collect(Collectors.summarizingDouble(Double::doubleValue));
        report.setDailyAvgTemperature(tempStats.getCount() > 0 ? tempStats.getAverage() : null);
        report.setDailyMaxTemperature(tempStats.getCount() > 0 ? tempStats.getMax() : null);
        report.setDailyMinTemperature(tempStats.getCount() > 0 ? tempStats.getMin() : null);

        // 습도 통계
        DoubleSummaryStatistics humidityStats = snapshots.stream()
                .map(HourlySensorAirQualitySnapshot::getHourlyAvgHumidity)
                .filter(Objects::nonNull)
                .collect(Collectors.summarizingDouble(Double::doubleValue));
        report.setDailyAvgHumidity(humidityStats.getCount() > 0 ? humidityStats.getAverage() : null);
        report.setDailyMaxHumidity(humidityStats.getCount() > 0 ? humidityStats.getMax() : null);
        report.setDailyMinHumidity(humidityStats.getCount() > 0 ? humidityStats.getMin() : null);

        // TVOC 통계
        IntSummaryStatistics tvocStats = snapshots.stream()
                .map(HourlySensorAirQualitySnapshot::getHourlyAvgTvoc)
                .filter(Objects::nonNull)
                .collect(Collectors.summarizingInt(Integer::intValue));
        report.setDailyAvgTvoc(tvocStats.getCount() > 0 ?  tvocStats.getAverage() : null);
        report.setDailyMaxTvoc(tvocStats.getCount() > 0 ? tvocStats.getMax() : null);

        // eCO2 통계
        IntSummaryStatistics eco2Stats = snapshots.stream()
                .map(HourlySensorAirQualitySnapshot::getHourlyAvgEco2)
                .filter(Objects::nonNull)
                .collect(Collectors.summarizingInt(Integer::intValue));
        report.setDailyAvgEco2(eco2Stats.getCount() > 0 ? eco2Stats.getAverage() : null);
        report.setDailyMaxEco2(eco2Stats.getCount() > 0 ? eco2Stats.getMax() : null);
        
        // PM2.5 통계
        DoubleSummaryStatistics pm25Stats = snapshots.stream()
                .map(HourlySensorAirQualitySnapshot::getHourlyAvgPm25)
                .filter(Objects::nonNull)
                .collect(Collectors.summarizingDouble(Double::doubleValue));
        report.setDailyAvgPm25(pm25Stats.getCount() > 0 ? pm25Stats.getAverage() : null);
        report.setDailyMaxPm25(pm25Stats.getCount() > 0 ? pm25Stats.getMax() : null);

        // 점수 통계 (평균만)
        DoubleSummaryStatistics overallScoreStats = snapshots.stream().map(HourlySensorAirQualitySnapshot::getOverallScore).filter(Objects::nonNull).collect(Collectors.summarizingDouble(Double::doubleValue));
        report.setDailyOverallScore(overallScoreStats.getCount() > 0 ? overallScoreStats.getAverage() : 0.0);
        DoubleSummaryStatistics pm25ScoreStats = snapshots.stream().map(HourlySensorAirQualitySnapshot::getPm25Score).filter(Objects::nonNull).collect(Collectors.summarizingDouble(Double::doubleValue));
        report.setDailyPm25Score(pm25ScoreStats.getCount() > 0 ? pm25ScoreStats.getAverage() : 0.0);
        DoubleSummaryStatistics eco2ScoreStats = snapshots.stream().map(HourlySensorAirQualitySnapshot::getEco2Score).filter(Objects::nonNull).collect(Collectors.summarizingDouble(Double::doubleValue));
        report.setDailyEco2Score(eco2ScoreStats.getCount() > 0 ? eco2ScoreStats.getAverage() : 0.0);
        DoubleSummaryStatistics tvocScoreStats = snapshots.stream().map(HourlySensorAirQualitySnapshot::getTvocScore).filter(Objects::nonNull).collect(Collectors.summarizingDouble(Double::doubleValue));
        report.setDailyTvocScore(tvocScoreStats.getCount() > 0 ? tvocScoreStats.getAverage() : 0.0);
        
        // validDataPointCount는 createOrUpdateDailyReport 메서드에서 이미 설정됨
    }

    private void setDefaultDailyStatistics(DailySensorAirQualityReport report) {
        report.setDailyAvgTemperature(null);
        report.setDailyAvgHumidity(null);
        report.setDailyAvgTvoc(null);
        report.setDailyAvgEco2(null);
        report.setDailyAvgPm25(null);
        report.setDailyOverallScore(0.0);
        report.setDailyPm25Score(0.0);
        report.setDailyEco2Score(0.0);
        report.setDailyTvocScore(0.0);
        report.setDailyMaxTemperature(null);
        report.setDailyMinTemperature(null);
        report.setDailyMaxHumidity(null);
        report.setDailyMinHumidity(null);
        report.setDailyMaxTvoc(null);
        report.setDailyMaxEco2(null);
        report.setDailyMaxPm25(null);
        report.setValidDataPointCount(0);
    }
}