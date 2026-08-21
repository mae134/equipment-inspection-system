package com.mae134.equipmentinspection.inspectionrecord;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public class InspectionRecordItemForm {

  @NotNull(message = "点検項目IDは必須です")
  private Long inspectionItemId;

  private BigDecimal numericValue;

  private Boolean booleanValue;

  private boolean notApplicable;

  @Size(max = 500, message = "備考は500文字以内で入力してください")
  private String comment;

  public Long getInspectionItemId() {
    return inspectionItemId;
  }

  public void setInspectionItemId(Long inspectionItemId) {
    this.inspectionItemId = inspectionItemId;
  }

  public BigDecimal getNumericValue() {
    return numericValue;
  }

  public void setNumericValue(BigDecimal numericValue) {
    this.numericValue = numericValue;
  }

  public Boolean getBooleanValue() {
    return booleanValue;
  }

  public void setBooleanValue(Boolean booleanValue) {
    this.booleanValue = booleanValue;
  }

  public boolean isNotApplicable() {
    return notApplicable;
  }

  public void setNotApplicable(boolean notApplicable) {
    this.notApplicable = notApplicable;
  }

  public String getComment() {
    return comment;
  }

  public void setComment(String comment) {
    this.comment = comment;
  }
}
