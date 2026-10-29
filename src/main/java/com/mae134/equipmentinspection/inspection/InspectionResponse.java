package com.mae134.equipmentinspection.inspection;

import java.time.LocalDateTime;

public record InspectionResponse(
    Long id,
    Long equipmentId,
    Long userId,
    LocalDateTime inspectionAt,
    String comment,
    LocalDateTime createdAt,
    LocalDateTime updatedAt) {}
