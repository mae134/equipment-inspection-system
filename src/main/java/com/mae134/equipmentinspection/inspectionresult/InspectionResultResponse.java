package com.mae134.equipmentinspection.inspectionresult;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record InspectionResultResponse(
    Long id,
    Long inspectionId,
    Long inspectionItemId,
    BigDecimal numericValue,
    Boolean booleanValue,
    InspectionResultStatus result,
    String comment,
    LocalDateTime createdAt,
    LocalDateTime updatedAt) {}
