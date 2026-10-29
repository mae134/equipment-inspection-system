package com.mae134.equipmentinspection.inspectionrecord;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mae134.equipmentinspection.equipment.Equipment;
import com.mae134.equipmentinspection.inspection.Inspection;
import com.mae134.equipmentinspection.inspection.InspectionRepository;
import com.mae134.equipmentinspection.inspection.InspectionService;
import com.mae134.equipmentinspection.inspectionitem.EquipmentInspectionItem;
import com.mae134.equipmentinspection.inspectionitem.InspectionItemType;
import com.mae134.equipmentinspection.inspectionresult.InspectionResult;
import com.mae134.equipmentinspection.inspectionresult.InspectionResultRepository;
import com.mae134.equipmentinspection.inspectionresult.InspectionResultService;
import com.mae134.equipmentinspection.inspectionresult.InspectionResultStatus;
import com.mae134.equipmentinspection.user.User;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InspectionRecordServiceTest {

  @Mock private InspectionService inspectionService;

  @Mock private InspectionResultService inspectionResultService;

  @Mock private InspectionRepository inspectionRepository;

  @Mock private InspectionResultRepository inspectionResultRepository;

  private InspectionRecordService inspectionRecordService;

  @BeforeEach
  void setUp() {
    inspectionRecordService =
        new InspectionRecordService(
            inspectionService,
            inspectionResultService,
            inspectionRepository,
            inspectionResultRepository);
  }

  @Test
  void findHistoryByEquipmentIdShouldReturnInspectionHistory() {
    User user = mock(User.class);
    when(user.getName()).thenReturn("テスト点検者");

    Inspection inspection = mock(Inspection.class);
    when(inspection.getId()).thenReturn(1L);
    when(inspection.getUser()).thenReturn(user);
    when(inspection.getInspectionAt()).thenReturn(LocalDateTime.of(2026, 8, 31, 20, 55));
    when(inspection.getComment()).thenReturn("異常なし");

    when(inspectionRepository.findByEquipmentIdOrderByInspectionAtDesc(1L))
        .thenReturn(List.of(inspection));

    List<InspectionHistoryResponse> history = inspectionRecordService.findHistoryByEquipmentId(1L);

    assertEquals(1, history.size());
    assertEquals(1L, history.get(0).inspectionId());
    assertEquals("テスト点検者", history.get(0).inspectorName());
    assertEquals("異常なし", history.get(0).comment());

    verify(inspectionRepository).findByEquipmentIdOrderByInspectionAtDesc(1L);
  }

  @Test
  void findHistoryDetailShouldReturnInspectionDetail() {
    Equipment equipment = mock(Equipment.class);
    when(equipment.getId()).thenReturn(1L);
    when(equipment.getEquipmentCode()).thenReturn("EQ-001");
    when(equipment.getName()).thenReturn("モーター設備A");

    User user = mock(User.class);
    when(user.getName()).thenReturn("テスト点検者");

    LocalDateTime inspectionAt = LocalDateTime.of(2026, 8, 31, 20, 55);
    LocalDateTime createdAt = LocalDateTime.of(2026, 8, 31, 20, 56);

    Inspection inspection = mock(Inspection.class);
    when(inspection.getId()).thenReturn(1L);
    when(inspection.getEquipment()).thenReturn(equipment);
    when(inspection.getUser()).thenReturn(user);
    when(inspection.getInspectionAt()).thenReturn(inspectionAt);
    when(inspection.getComment()).thenReturn("異常なし");
    when(inspection.getCreatedAt()).thenReturn(createdAt);

    EquipmentInspectionItem inspectionItem = mock(EquipmentInspectionItem.class);
    when(inspectionItem.getName()).thenReturn("モーター温度");
    when(inspectionItem.getType()).thenReturn(InspectionItemType.NUMERIC);
    when(inspectionItem.getUnit()).thenReturn("℃");
    when(inspectionItem.getDisplayOrder()).thenReturn(1);

    InspectionResult inspectionResult = mock(InspectionResult.class);
    when(inspectionResult.getInspectionItem()).thenReturn(inspectionItem);
    when(inspectionResult.getNumericValue()).thenReturn(new BigDecimal("12.0000"));
    when(inspectionResult.getBooleanValue()).thenReturn(null);
    when(inspectionResult.getResult()).thenReturn(InspectionResultStatus.OK);
    when(inspectionResult.getComment()).thenReturn("正常");

    when(inspectionRepository.findById(1L)).thenReturn(Optional.of(inspection));
    when(inspectionResultRepository.findByInspectionId(1L)).thenReturn(List.of(inspectionResult));

    InspectionHistoryDetailResponse detail = inspectionRecordService.findHistoryDetail(1L);

    assertEquals(1L, detail.inspectionId());
    assertEquals("EQ-001", detail.equipmentCode());
    assertEquals("モーター設備A", detail.equipmentName());
    assertEquals("テスト点検者", detail.inspectorName());
    assertEquals("異常なし", detail.comment());
    assertEquals(1, detail.results().size());

    InspectionHistoryResultResponse result = detail.results().get(0);

    assertEquals("モーター温度", result.itemName());
    assertEquals(InspectionItemType.NUMERIC, result.type());
    assertEquals("℃", result.unit());
    assertEquals(new BigDecimal("12.0000"), result.numericValue());
    assertEquals(InspectionResultStatus.OK, result.result());
    assertEquals("正常", result.comment());

    verify(inspectionRepository).findById(1L);
    verify(inspectionResultRepository).findByInspectionId(1L);
  }
}
