package com.example.smartair.repository.airQualityRepository.airQualitySnapshotRepository;

import com.example.smartair.entity.airData.snapshot.HourlySensorAirQualitySnapshot;
import com.example.smartair.entity.sensor.Sensor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface HourlyDeviceAirQualitySnapshotRepository extends JpaRepository<HourlySensorAirQualitySnapshot, Long> {
    Optional<HourlySensorAirQualitySnapshot> findBySensorAndSnapshotHour(Sensor sensor, LocalDateTime snapshotHour);

    @Query("SELECT DISTINCT h.sensor FROM HourlySensorAirQualitySnapshot h " +
            "WHERE h.snapshotHour BETWEEN :startOfDay AND :endOfDay")
    Set<Sensor> findDistinctSensorsBySnapshotHourBetween(
            @Param("startOfDay") LocalDateTime startOfDay,
            @Param("endOfDay") LocalDateTime endOfDay
    );

    List<HourlySensorAirQualitySnapshot> findBySensorAndSnapshotHourBetweenOrderBySnapshotHourAsc(Sensor sensor, LocalDateTime startOfDay, LocalDateTime endOfDay);

    @Query("SELECT h FROM HourlySensorAirQualitySnapshot h WHERE h.sensor = :sensor AND h.snapshotHour >= :startHour AND h.snapshotHour < :endHour")
    Optional<HourlySensorAirQualitySnapshot> findBySensorAndHourRange(
            @Param("sensor") Sensor sensor,
            @Param("startHour") LocalDateTime startHour,
            @Param("endHour") LocalDateTime endHour);
}
