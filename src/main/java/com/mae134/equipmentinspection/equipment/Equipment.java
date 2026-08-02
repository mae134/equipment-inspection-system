package com.mae134.equipmentinspection.equipment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "equipment")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Equipment {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "equipment_code", nullable = false, unique = true, length = 20)
  private String equipmentCode;

  @Column(nullable = false, length = 100)
  private String name;

  @Column(length = 100)
  private String manufacturer;

  @Column(length = 100)
  private String model;

  @Column(length = 100)
  private String location;

  @Column(name = "installed_date")
  private LocalDate installedDate;

  @Column(name = "inspection_cycle_days")
  private Integer inspectionCycleDays;

  @Column(length = 500)
  private String description;

  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;
}
