package com.example.smartair.dto.predictedAirQualityDto;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Data
public class PredictedAirQualityDto {
    private String sensorSerialNumber;
    private LocalDateTime timestamp;
    private float pm10;
    private float co2;
    private float tvoc;
}
