package com.mae134.equipmentinspection.inspectionrecord;

import com.mae134.equipmentinspection.inspectionitem.InspectionItemType;
import com.mae134.equipmentinspection.inspectionresult.InspectionResultStatus;
import java.math.BigDecimal;

public record InspectionHistoryResultResponse(
    String itemName,
    InspectionItemType type,
    String unit,
    BigDecimal numericValue,
    Boolean booleanValue,
    InspectionResultStatus result,
    String comment,
    Integer displayOrder) {}
