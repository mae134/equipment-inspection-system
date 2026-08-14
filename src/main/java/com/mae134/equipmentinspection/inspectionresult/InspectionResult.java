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
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "inspection_result")
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
}
