package com.mae134.equipmentinspection.inspectionrecord;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mae134.equipmentinspection.equipment.EquipmentResponse;
import com.mae134.equipmentinspection.equipment.EquipmentService;
import com.mae134.equipmentinspection.inspectionitem.EquipmentInspectionItemService;
import com.mae134.equipmentinspection.user.UserRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ui.ConcurrentModel;
import org.springframework.ui.Model;

@ExtendWith(MockitoExtension.class)
class InspectionRecordControllerTest {

  @Mock private EquipmentService equipmentService;

  @Mock private EquipmentInspectionItemService inspectionItemService;

  @Mock private InspectionRecordService inspectionRecordService;

  @Mock private UserRepository userRepository;

  private InspectionRecordController inspectionRecordController;

  @BeforeEach
  void setUp() {
    inspectionRecordController =
        new InspectionRecordController(
            equipmentService, inspectionItemService, inspectionRecordService, userRepository);
  }

  @Test
  void showInspectionHistoryShouldDisplayInspectionHistory() {
    EquipmentResponse equipment = mock(EquipmentResponse.class);

    List<InspectionHistoryResponse> history =
        List.of(
            new InspectionHistoryResponse(
                1L, LocalDateTime.of(2026, 8, 31, 20, 55), "テスト点検者", "異常なし"));

    when(equipmentService.findById(1L)).thenReturn(Optional.of(equipment));
    when(inspectionRecordService.findHistoryByEquipmentId(1L)).thenReturn(history);

    Model model = new ConcurrentModel();

    String viewName = inspectionRecordController.showInspectionHistory(1L, model);

    assertEquals("inspection-history", viewName);
    assertEquals(equipment, model.getAttribute("equipment"));
    assertEquals(history, model.getAttribute("inspectionHistory"));

    verify(equipmentService).findById(1L);
    verify(inspectionRecordService).findHistoryByEquipmentId(1L);
  }

  @Test
  void showInspectionHistoryDetailShouldDisplayInspectionDetail() {
    InspectionHistoryDetailResponse detail =
        new InspectionHistoryDetailResponse(
            1L,
            1L,
            "EQ-001",
            "モーター設備A",
            LocalDateTime.of(2026, 8, 31, 20, 55),
            "テスト点検者",
            "異常なし",
            LocalDateTime.of(2026, 8, 31, 20, 56),
            List.of());

    when(inspectionRecordService.findHistoryDetail(1L)).thenReturn(detail);

    Model model = new ConcurrentModel();

    String viewName = inspectionRecordController.showInspectionHistoryDetail(1L, model);

    assertEquals("inspection-history-detail", viewName);
    assertEquals(detail, model.getAttribute("detail"));

    verify(inspectionRecordService).findHistoryDetail(1L);
  }
}
