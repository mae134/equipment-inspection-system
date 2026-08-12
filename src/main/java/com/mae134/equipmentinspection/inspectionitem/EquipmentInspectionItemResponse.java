package com.mae134.equipmentinspection.inspectionitem;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record EquipmentInspectionItemResponse(
    Long id,
    Long equipmentId,
    String name,
    InspectionItemType type,
    String unit,
    BigDecimal minValue,
    BigDecimal maxValue,
    Boolean normalBooleanValue,
    String description,
    Integer displayOrder,
    Boolean active,
    LocalDateTime createdAt,
    LocalDateTime updatedAt) {}
