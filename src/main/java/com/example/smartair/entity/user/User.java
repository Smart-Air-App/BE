package com.example.smartair.entity.user;

import com.example.smartair.entity.device.Device;
import com.example.smartair.entity.hvacSetting.HvacSetting;
import com.example.smartair.entity.notification.Notification;
import com.example.smartair.entity.room.Room;
import com.example.smartair.util.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;


@Entity
@Getter
@Setter
public class User extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    private String username;
    private String email;
    private String password;

    private String role;

    @OneToMany
    private List<Room> rooms = new ArrayList<>();

    @OneToMany
    private List<Device> devices = new ArrayList<>();

    @OneToMany
    private List<Notification> notifications = new ArrayList<>();

    @OneToMany
    private List<HvacSetting> hvacSettings = new ArrayList<>();
}

