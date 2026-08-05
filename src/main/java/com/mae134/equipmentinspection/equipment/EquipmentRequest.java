package com.mae134.equipmentinspection.equipment;

import java.time.LocalDate;

public record EquipmentRequest(
    String equipmentCode,
    String name,
    String manufacturer,
    String model,
    String location,
    LocalDate installedDate,
    Integer inspectionCycleDays,
    String description,
    Boolean active) {}
