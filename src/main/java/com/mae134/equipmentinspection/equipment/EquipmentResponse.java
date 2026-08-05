package com.mae134.equipmentinspection.equipment;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record EquipmentResponse(
    Long id,
    String equipmentCode,
    String name,
    String manufacturer,
    String model,
    String location,
    LocalDate installedDate,
    Integer inspectionCycleDays,
    String description,
    Boolean active,
    LocalDateTime createdAt,
    LocalDateTime updatedAt) {}
