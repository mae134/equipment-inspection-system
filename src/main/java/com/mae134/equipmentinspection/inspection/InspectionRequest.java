package com.mae134.equipmentinspection.inspection;

import java.time.LocalDateTime;

public record InspectionRequest(
    Long equipmentId, Long userId, LocalDateTime inspectionAt, String comment) {}
