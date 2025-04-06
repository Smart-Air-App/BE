package com.example.smartair.entity.airScore;

import com.example.smartair.entity.room.Room;
import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.List;

@Entity
public class TotalAirQualityScore {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    private double score;

    @OneToMany
    private List<Room> rooms = new ArrayList<>();
}
