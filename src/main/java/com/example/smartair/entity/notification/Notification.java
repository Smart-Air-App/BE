package com.example.smartair.entity.notification;

import com.example.smartair.entity.user.User;
import com.example.smartair.entity.airData.airQualityData.SensorAirQualityData;
import com.example.smartair.util.BaseEntity;
import jakarta.persistence.*;

@Entity
public class Notification extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String message;
    private String title;
    private boolean readStatus;

    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne
    @JoinColumn(name = "sensor_air_quality_data_id")
    private SensorAirQualityData airQualityData;
}
