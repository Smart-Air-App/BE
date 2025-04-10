package com.example.smartair.entity.device;

import com.example.smartair.entity.hvacSetting.HvacSetting;
import com.example.smartair.entity.roomDevice.RoomDevice;
import com.example.smartair.entity.user.User;
import com.example.smartair.entity.airData.AirQualityData;
import com.example.smartair.entity.airData.FineParticlesData;
import com.example.smartair.util.BaseEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Device extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private String serialNumber;
    private boolean runningStatus;

    @ManyToOne //기기와 유저 : 다대일 관계
    @JoinColumn(name = "user_id")
    private User user;


}
