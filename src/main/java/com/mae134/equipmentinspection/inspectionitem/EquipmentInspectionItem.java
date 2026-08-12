package com.mae134.equipmentinspection.inspectionitem;

import com.mae134.equipmentinspection.equipment.Equipment;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "equipment_inspection_item")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EquipmentInspectionItem {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "equipment_id", nullable = false)
  private Equipment equipment;

  @Column(nullable = false, length = 100)
  private String name;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private InspectionItemType type;

  @Column(length = 20)
  private String unit;

  @Column(precision = 12, scale = 4)
  private BigDecimal minValue;

  @Column(precision = 12, scale = 4)
  private BigDecimal maxValue;

  @Column private Boolean normalBooleanValue;

  @Column(length = 500)
  private String description;

  @Column(nullable = false)
  private Integer displayOrder;

  @Column(nullable = false)
  private Boolean active = true;

  @Column(nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @Column(nullable = false)
  private LocalDateTime updatedAt;

  @PrePersist
  protected void onCreate() {
    LocalDateTime now = LocalDateTime.now();
    createdAt = now;
    updatedAt = now;
  }

  @PreUpdate
  protected void onUpdate() {
    updatedAt = LocalDateTime.now();
  }
}
