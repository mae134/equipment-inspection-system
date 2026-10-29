package com.mae134.equipmentinspection.inspectionresult;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mae134.equipmentinspection.equipment.Equipment;
import com.mae134.equipmentinspection.inspection.Inspection;
import com.mae134.equipmentinspection.inspection.InspectionRepository;
import com.mae134.equipmentinspection.inspectionitem.EquipmentInspectionItem;
import com.mae134.equipmentinspection.inspectionitem.EquipmentInspectionItemRepository;
import com.mae134.equipmentinspection.inspectionitem.InspectionItemType;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InspectionResultServiceTest {

  @Mock private InspectionResultRepository inspectionResultRepository;

  @Mock private InspectionRepository inspectionRepository;

  @Mock private EquipmentInspectionItemRepository inspectionItemRepository;

  private InspectionResultService inspectionResultService;

  @BeforeEach
  void setUp() {
    inspectionResultService =
        new InspectionResultService(
            inspectionResultRepository, inspectionRepository, inspectionItemRepository);
  }

  @Test
  void saveShouldReturnOkForNumericValueWithinRange() {
    Equipment equipment = mock(Equipment.class);
    when(equipment.getId()).thenReturn(1L);

    Inspection inspection = mock(Inspection.class);
    when(inspection.getEquipment()).thenReturn(equipment);

    EquipmentInspectionItem inspectionItem = mock(EquipmentInspectionItem.class);
    when(inspectionItem.getId()).thenReturn(1L);
    when(inspectionItem.getEquipment()).thenReturn(equipment);
    when(inspectionItem.getType()).thenReturn(InspectionItemType.NUMERIC);
    when(inspectionItem.getMinValue()).thenReturn(new BigDecimal("0"));
    when(inspectionItem.getMaxValue()).thenReturn(new BigDecimal("80"));

    InspectionResultRequest request =
        new InspectionResultRequest(1L, 1L, new BigDecimal("50"), null, false, "正常値");

    InspectionResult savedResult = new InspectionResult();
    savedResult.setId(1L);
    savedResult.setInspection(inspection);
    savedResult.setInspectionItem(inspectionItem);
    savedResult.setNumericValue(request.numericValue());
    savedResult.setBooleanValue(request.booleanValue());
    savedResult.setResult(InspectionResultStatus.OK);
    savedResult.setComment(request.comment());

    when(inspectionRepository.findById(1L)).thenReturn(Optional.of(inspection));

    when(inspectionItemRepository.findById(1L)).thenReturn(Optional.of(inspectionItem));

    when(inspectionResultRepository.existsByInspectionIdAndInspectionItemId(1L, 1L))
        .thenReturn(false);

    when(inspectionResultRepository.save(any(InspectionResult.class))).thenReturn(savedResult);

    InspectionResultResponse response = inspectionResultService.save(request);

    assertEquals(InspectionResultStatus.OK, response.result());
    assertEquals(new BigDecimal("50"), response.numericValue());

    verify(inspectionRepository).findById(1L);
    verify(inspectionItemRepository).findById(1L);
    verify(inspectionResultRepository).existsByInspectionIdAndInspectionItemId(1L, 1L);
    verify(inspectionResultRepository).save(any(InspectionResult.class));
  }

  @Test
  void saveShouldReturnNgForNumericValueAboveMax() {
    Equipment equipment = mock(Equipment.class);
    when(equipment.getId()).thenReturn(1L);

    Inspection inspection = mock(Inspection.class);
    when(inspection.getEquipment()).thenReturn(equipment);

    EquipmentInspectionItem inspectionItem = mock(EquipmentInspectionItem.class);
    when(inspectionItem.getId()).thenReturn(1L);
    when(inspectionItem.getEquipment()).thenReturn(equipment);
    when(inspectionItem.getType()).thenReturn(InspectionItemType.NUMERIC);
    when(inspectionItem.getMinValue()).thenReturn(new BigDecimal("0"));
    when(inspectionItem.getMaxValue()).thenReturn(new BigDecimal("80"));

    InspectionResultRequest request =
        new InspectionResultRequest(1L, 1L, new BigDecimal("85"), null, false, "上限超過");

    InspectionResult savedResult = new InspectionResult();
    savedResult.setId(1L);
    savedResult.setInspection(inspection);
    savedResult.setInspectionItem(inspectionItem);
    savedResult.setNumericValue(request.numericValue());
    savedResult.setResult(InspectionResultStatus.NG);
    savedResult.setComment(request.comment());

    when(inspectionRepository.findById(1L)).thenReturn(Optional.of(inspection));

    when(inspectionItemRepository.findById(1L)).thenReturn(Optional.of(inspectionItem));

    when(inspectionResultRepository.existsByInspectionIdAndInspectionItemId(1L, 1L))
        .thenReturn(false);

    when(inspectionResultRepository.save(any(InspectionResult.class))).thenReturn(savedResult);

    InspectionResultResponse response = inspectionResultService.save(request);

    assertEquals(InspectionResultStatus.NG, response.result());
    assertEquals(new BigDecimal("85"), response.numericValue());

    verify(inspectionResultRepository).save(any(InspectionResult.class));
  }

  @Test
  void saveShouldReturnOkForBooleanValueMatchingNormalValue() {
    Equipment equipment = mock(Equipment.class);
    when(equipment.getId()).thenReturn(1L);

    Inspection inspection = mock(Inspection.class);
    when(inspection.getEquipment()).thenReturn(equipment);

    EquipmentInspectionItem inspectionItem = mock(EquipmentInspectionItem.class);
    when(inspectionItem.getId()).thenReturn(2L);
    when(inspectionItem.getEquipment()).thenReturn(equipment);
    when(inspectionItem.getType()).thenReturn(InspectionItemType.BOOLEAN);
    when(inspectionItem.getNormalBooleanValue()).thenReturn(false);

    InspectionResultRequest request =
        new InspectionResultRequest(1L, 2L, null, false, false, "油漏れなし");

    InspectionResult savedResult = new InspectionResult();
    savedResult.setId(1L);
    savedResult.setInspection(inspection);
    savedResult.setInspectionItem(inspectionItem);
    savedResult.setBooleanValue(request.booleanValue());
    savedResult.setResult(InspectionResultStatus.OK);
    savedResult.setComment(request.comment());

    when(inspectionRepository.findById(1L)).thenReturn(Optional.of(inspection));

    when(inspectionItemRepository.findById(2L)).thenReturn(Optional.of(inspectionItem));

    when(inspectionResultRepository.existsByInspectionIdAndInspectionItemId(1L, 2L))
        .thenReturn(false);

    when(inspectionResultRepository.save(any(InspectionResult.class))).thenReturn(savedResult);

    InspectionResultResponse response = inspectionResultService.save(request);

    assertEquals(InspectionResultStatus.OK, response.result());
    assertEquals(false, response.booleanValue());

    verify(inspectionResultRepository).save(any(InspectionResult.class));
  }

  @Test
  void saveShouldReturnNgForBooleanValueDifferentFromNormalValue() {
    Equipment equipment = mock(Equipment.class);
    when(equipment.getId()).thenReturn(1L);

    Inspection inspection = mock(Inspection.class);
    when(inspection.getEquipment()).thenReturn(equipment);

    EquipmentInspectionItem inspectionItem = mock(EquipmentInspectionItem.class);
    when(inspectionItem.getId()).thenReturn(2L);
    when(inspectionItem.getEquipment()).thenReturn(equipment);
    when(inspectionItem.getType()).thenReturn(InspectionItemType.BOOLEAN);
    when(inspectionItem.getNormalBooleanValue()).thenReturn(false);

    InspectionResultRequest request =
        new InspectionResultRequest(1L, 2L, null, true, false, "油漏れあり");

    InspectionResult savedResult = new InspectionResult();
    savedResult.setId(1L);
    savedResult.setInspection(inspection);
    savedResult.setInspectionItem(inspectionItem);
    savedResult.setBooleanValue(request.booleanValue());
    savedResult.setResult(InspectionResultStatus.NG);
    savedResult.setComment(request.comment());

    when(inspectionRepository.findById(1L)).thenReturn(Optional.of(inspection));

    when(inspectionItemRepository.findById(2L)).thenReturn(Optional.of(inspectionItem));

    when(inspectionResultRepository.existsByInspectionIdAndInspectionItemId(1L, 2L))
        .thenReturn(false);

    when(inspectionResultRepository.save(any(InspectionResult.class))).thenReturn(savedResult);

    InspectionResultResponse response = inspectionResultService.save(request);

    assertEquals(InspectionResultStatus.NG, response.result());
    assertEquals(true, response.booleanValue());

    verify(inspectionResultRepository).save(any(InspectionResult.class));
  }

  @Test
  void saveShouldReturnNotApplicableWhenMarkedNotApplicable() {
    Equipment equipment = mock(Equipment.class);
    when(equipment.getId()).thenReturn(1L);

    Inspection inspection = mock(Inspection.class);
    when(inspection.getEquipment()).thenReturn(equipment);

    EquipmentInspectionItem inspectionItem = mock(EquipmentInspectionItem.class);
    when(inspectionItem.getId()).thenReturn(1L);
    when(inspectionItem.getEquipment()).thenReturn(equipment);

    InspectionResultRequest request =
        new InspectionResultRequest(1L, 1L, null, null, true, "設備停止中のため測定不可");

    InspectionResult savedResult = new InspectionResult();
    savedResult.setId(1L);
    savedResult.setInspection(inspection);
    savedResult.setInspectionItem(inspectionItem);
    savedResult.setNumericValue(null);
    savedResult.setBooleanValue(null);
    savedResult.setResult(InspectionResultStatus.NOT_APPLICABLE);
    savedResult.setComment(request.comment());

    when(inspectionRepository.findById(1L)).thenReturn(Optional.of(inspection));

    when(inspectionItemRepository.findById(1L)).thenReturn(Optional.of(inspectionItem));

    when(inspectionResultRepository.existsByInspectionIdAndInspectionItemId(1L, 1L))
        .thenReturn(false);

    when(inspectionResultRepository.save(any(InspectionResult.class))).thenReturn(savedResult);

    InspectionResultResponse response = inspectionResultService.save(request);

    assertEquals(InspectionResultStatus.NOT_APPLICABLE, response.result());
    assertEquals(null, response.numericValue());
    assertEquals(null, response.booleanValue());

    verify(inspectionResultRepository).save(any(InspectionResult.class));
  }

  @Test
  void saveShouldThrowExceptionWhenNotApplicableHasValue() {
    Equipment equipment = mock(Equipment.class);
    when(equipment.getId()).thenReturn(1L);

    Inspection inspection = mock(Inspection.class);
    when(inspection.getEquipment()).thenReturn(equipment);

    EquipmentInspectionItem inspectionItem = mock(EquipmentInspectionItem.class);
    when(inspectionItem.getEquipment()).thenReturn(equipment);

    InspectionResultRequest request =
        new InspectionResultRequest(1L, 1L, new BigDecimal("50"), null, true, "対象外なのに値あり");

    when(inspectionRepository.findById(1L)).thenReturn(Optional.of(inspection));

    when(inspectionItemRepository.findById(1L)).thenReturn(Optional.of(inspectionItem));

    when(inspectionResultRepository.existsByInspectionIdAndInspectionItemId(1L, 1L))
        .thenReturn(false);

    assertThrows(IllegalArgumentException.class, () -> inspectionResultService.save(request));

    verify(inspectionResultRepository, never()).save(any(InspectionResult.class));
  }

  @Test
  void updateShouldThrowExceptionWhenInspectionAndItemCombinationAlreadyExists() {
    Equipment equipment = mock(Equipment.class);
    when(equipment.getId()).thenReturn(1L);

    Inspection inspection = mock(Inspection.class);
    when(inspection.getEquipment()).thenReturn(equipment);

    EquipmentInspectionItem inspectionItem = mock(EquipmentInspectionItem.class);
    when(inspectionItem.getEquipment()).thenReturn(equipment);

    InspectionResult existingResult = new InspectionResult();
    existingResult.setId(5L);

    InspectionResultRequest request =
        new InspectionResultRequest(1L, 2L, null, false, false, "更新時の重複確認");

    when(inspectionResultRepository.findById(5L)).thenReturn(Optional.of(existingResult));

    when(inspectionRepository.findById(1L)).thenReturn(Optional.of(inspection));

    when(inspectionItemRepository.findById(2L)).thenReturn(Optional.of(inspectionItem));

    when(inspectionResultRepository.existsByInspectionIdAndInspectionItemIdAndIdNot(1L, 2L, 5L))
        .thenReturn(true);

    assertThrows(IllegalArgumentException.class, () -> inspectionResultService.update(5L, request));

    verify(inspectionResultRepository, never()).save(any(InspectionResult.class));
  }
}
