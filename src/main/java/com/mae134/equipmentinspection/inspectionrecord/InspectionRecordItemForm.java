package com.mae134.equipmentinspection.inspectionrecord;

import java.math.BigDecimal;

public class InspectionRecordItemForm {

  private Long inspectionItemId;

  private BigDecimal numericValue;

  private Boolean booleanValue;

  private boolean notApplicable;

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
