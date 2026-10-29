package com.mae134.equipmentinspection.inspectionrecord;

import java.time.LocalDateTime;

public record InspectionHistoryResponse(
    Long inspectionId, LocalDateTime inspectionAt, String inspectorName, String comment) {}
