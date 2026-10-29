package com.mae134.equipmentinspection.inspectionresult;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record InspectionResultRequest(
    @NotNull Long inspectionId,
    @NotNull Long inspectionItemId,
    BigDecimal numericValue,
    Boolean booleanValue,
    @NotNull Boolean notApplicable,
    @Size(max = 500) String comment) {}
