package com.example.smartair.entity.airScore.airQualityScore;

import com.example.smartair.entity.airData.airQualityData.SensorAirQualityData;
import com.example.smartair.util.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Setter
@Getter
@NoArgsConstructor
public class SensorAirQualityScore extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    @JoinColumn(name = "sensor_air_quality_data_id")
    private SensorAirQualityData sensorAirQualityData;

    private double overallScore;

    private double pm10Score;
    private double pm25Score;
    private double eco2Score;
    private double tvocScore;
}
