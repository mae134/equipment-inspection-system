package com.mae134.equipmentinspection.inspectionresult;

import com.mae134.equipmentinspection.inspection.Inspection;
import com.mae134.equipmentinspection.inspectionitem.EquipmentInspectionItem;
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
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(
    name = "inspection_result",
    uniqueConstraints = {@UniqueConstraint(columnNames = {"inspection_id", "inspection_item_id"})})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InspectionResult {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "inspection_id", nullable = false)
  private Inspection inspection;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "inspection_item_id", nullable = false)
  private EquipmentInspectionItem inspectionItem;

  @Column(precision = 12, scale = 4)
  private BigDecimal numericValue;

  @Column private Boolean booleanValue;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private InspectionResultStatus result;

  @Column(length = 500)
  private String comment;

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
