package com.example.smartair.entity.airData.snapshot;

import com.example.smartair.entity.airData.report.DailySensorAirQualityReport;
import com.example.smartair.entity.sensor.Sensor;
import com.example.smartair.util.BaseEntity;
import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Builder
@Table(
        name = "hourly_sensor_air_quality_snapshot",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_sensor_snapshot_hour",
                        columnNames = {"sensor_id", "snapshot_hour"}
                )
        }
)
public class HourlySensorAirQualitySnapshot extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sensor_id", nullable = false)
    private Sensor sensor;

    @JsonFormat(pattern="yyyy-MM-dd'T'HH:mm:ss")
    @Column(name = "snapshot_hour", nullable = false) // 컬럼명을 명시적으로 지정
    private LocalDateTime snapshotHour; // 해당 시간대의 시작 시각 (예: 2023-10-27 13:00:00)

    // 해당 시간대의 대표 공기질 데이터 값들
    private Double hourlyAvgTemperature;
    private Double hourlyAvgHumidity;
    private Integer hourlyAvgPressure;
    private Integer hourlyAvgTvoc;
    private Integer hourlyAvgEco2;

    //미세먼지 데이터 대표값들
    private Double hourlyAvgPm10;
    private Double hourlyAvgPm25;

    // 해당 시간대의 대표 공기질 데이터에 대한 점수
    private Double overallScore;
    private Double pm10Score;
    private Double pm25Score;
    private Double eco2Score;
    private Double tvocScore;
}