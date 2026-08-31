package com.mae134.equipmentinspection.inspectionrecord;

import java.time.LocalDateTime;
import java.util.List;

public record InspectionHistoryDetailResponse(
    Long inspectionId,
    Long equipmentId,
    String equipmentCode,
    String equipmentName,
    LocalDateTime inspectionAt,
    String inspectorName,
    String comment,
    LocalDateTime createdAt,
    List<InspectionHistoryResultResponse> results) {}
