package com.mae134.equipmentinspection.inspectionrecord;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class InspectionRecordForm {

  @NotNull private Long userId;

  @NotNull private LocalDateTime inspectionAt;

  @Size(max = 500)
  private String comment;

  @Valid private List<InspectionRecordItemForm> items = new ArrayList<>();

  public Long getUserId() {
    return userId;
  }

  public void setUserId(Long userId) {
    this.userId = userId;
  }

  public LocalDateTime getInspectionAt() {
    return inspectionAt;
  }

  public void setInspectionAt(LocalDateTime inspectionAt) {
    this.inspectionAt = inspectionAt;
  }

  public String getComment() {
    return comment;
  }

  public void setComment(String comment) {
    this.comment = comment;
  }

  public List<InspectionRecordItemForm> getItems() {
    return items;
  }

  public void setItems(List<InspectionRecordItemForm> items) {
    this.items = items;
  }
}
