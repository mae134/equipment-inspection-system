package com.mae134.equipmentinspection.inspectionitem;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mae134.equipmentinspection.equipment.Equipment;
import com.mae134.equipmentinspection.equipment.EquipmentRepository;
import com.mae134.equipmentinspection.exception.ResourceNotFoundException;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EquipmentInspectionItemServiceTest {

  @Mock private EquipmentInspectionItemRepository inspectionItemRepository;

  @Mock private EquipmentRepository equipmentRepository;

  private EquipmentInspectionItemService inspectionItemService;

  @BeforeEach
  void setUp() {
    inspectionItemService =
        new EquipmentInspectionItemService(inspectionItemRepository, equipmentRepository);
  }

  @Test
  void saveShouldReturnCreatedNumericInspectionItem() {
    Equipment equipment = mock(Equipment.class);
    when(equipment.getId()).thenReturn(1L);

    EquipmentInspectionItemRequest request =
        new EquipmentInspectionItemRequest(
            1L,
            "モーター温度",
            InspectionItemType.NUMERIC,
            "℃",
            new BigDecimal("0"),
            new BigDecimal("80"),
            null,
            "モーター表面温度",
            1,
            true);

    EquipmentInspectionItem savedItem = new EquipmentInspectionItem();
    savedItem.setId(1L);
    savedItem.setEquipment(equipment);
    savedItem.setName(request.name());
    savedItem.setType(request.type());
    savedItem.setUnit(request.unit());
    savedItem.setMinValue(request.minValue());
    savedItem.setMaxValue(request.maxValue());
    savedItem.setNormalBooleanValue(request.normalBooleanValue());
    savedItem.setDescription(request.description());
    savedItem.setDisplayOrder(request.displayOrder());
    savedItem.setActive(request.active());

    when(equipmentRepository.findById(1L)).thenReturn(Optional.of(equipment));

    when(inspectionItemRepository.save(any(EquipmentInspectionItem.class))).thenReturn(savedItem);

    EquipmentInspectionItemResponse response = inspectionItemService.save(request);

    assertEquals(1L, response.id());
    assertEquals(1L, response.equipmentId());
    assertEquals("モーター温度", response.name());
    assertEquals(InspectionItemType.NUMERIC, response.type());
    assertEquals("℃", response.unit());
    assertEquals(new BigDecimal("0"), response.minValue());
    assertEquals(new BigDecimal("80"), response.maxValue());

    verify(equipmentRepository).findById(1L);
    verify(inspectionItemRepository).save(any(EquipmentInspectionItem.class));
  }

  @Test
  void saveShouldThrowExceptionWhenEquipmentDoesNotExist() {
    EquipmentInspectionItemRequest request =
        new EquipmentInspectionItemRequest(
            999L,
            "モーター温度",
            InspectionItemType.NUMERIC,
            "℃",
            new BigDecimal("0"),
            new BigDecimal("80"),
            null,
            "モーター表面温度",
            1,
            true);

    when(equipmentRepository.findById(999L)).thenReturn(Optional.empty());

    assertThrows(ResourceNotFoundException.class, () -> inspectionItemService.save(request));

    verify(equipmentRepository).findById(999L);
    verify(inspectionItemRepository, never()).save(any(EquipmentInspectionItem.class));
  }

  @Test
  void saveShouldThrowExceptionWhenMinValueIsGreaterThanMaxValue() {
    Equipment equipment = mock(Equipment.class);

    EquipmentInspectionItemRequest request =
        new EquipmentInspectionItemRequest(
            1L,
            "異常な温度設定",
            InspectionItemType.NUMERIC,
            "℃",
            new BigDecimal("100"),
            new BigDecimal("50"),
            null,
            "バリデーション確認用",
            3,
            true);

    when(equipmentRepository.findById(1L)).thenReturn(Optional.of(equipment));

    assertThrows(IllegalArgumentException.class, () -> inspectionItemService.save(request));

    verify(equipmentRepository).findById(1L);
    verify(inspectionItemRepository, never()).save(any(EquipmentInspectionItem.class));
  }

  @Test
  void saveShouldThrowExceptionWhenNumericHasNormalBooleanValue() {
    Equipment equipment = mock(Equipment.class);

    EquipmentInspectionItemRequest request =
        new EquipmentInspectionItemRequest(
            1L,
            "モーター温度",
            InspectionItemType.NUMERIC,
            "℃",
            new BigDecimal("0"),
            new BigDecimal("80"),
            true,
            "バリデーション確認用",
            1,
            true);

    when(equipmentRepository.findById(1L)).thenReturn(Optional.of(equipment));

    assertThrows(IllegalArgumentException.class, () -> inspectionItemService.save(request));

    verify(inspectionItemRepository, never()).save(any(EquipmentInspectionItem.class));
  }

  @Test
  void saveShouldThrowExceptionWhenBooleanHasNoNormalBooleanValue() {
    Equipment equipment = mock(Equipment.class);

    EquipmentInspectionItemRequest request =
        new EquipmentInspectionItemRequest(
            1L, "油漏れ", InspectionItemType.BOOLEAN, null, null, null, null, "油漏れの有無を確認する", 2, true);

    when(equipmentRepository.findById(1L)).thenReturn(Optional.of(equipment));

    assertThrows(IllegalArgumentException.class, () -> inspectionItemService.save(request));

    verify(inspectionItemRepository, never()).save(any(EquipmentInspectionItem.class));
  }

  @Test
  void saveShouldThrowExceptionWhenBooleanHasNumericSettings() {
    Equipment equipment = mock(Equipment.class);

    EquipmentInspectionItemRequest request =
        new EquipmentInspectionItemRequest(
            1L,
            "油漏れ",
            InspectionItemType.BOOLEAN,
            null,
            new BigDecimal("0"),
            null,
            false,
            "油漏れの有無を確認する",
            2,
            true);

    when(equipmentRepository.findById(1L)).thenReturn(Optional.of(equipment));

    assertThrows(IllegalArgumentException.class, () -> inspectionItemService.save(request));

    verify(inspectionItemRepository, never()).save(any(EquipmentInspectionItem.class));
  }

  @Test
  void saveShouldReturnCreatedBooleanInspectionItem() {
    Equipment equipment = mock(Equipment.class);
    when(equipment.getId()).thenReturn(1L);

    EquipmentInspectionItemRequest request =
        new EquipmentInspectionItemRequest(
            1L, "油漏れ", InspectionItemType.BOOLEAN, null, null, null, false, "油漏れの有無を確認する", 2, true);

    EquipmentInspectionItem savedItem = new EquipmentInspectionItem();
    savedItem.setId(2L);
    savedItem.setEquipment(equipment);
    savedItem.setName(request.name());
    savedItem.setType(request.type());
    savedItem.setNormalBooleanValue(request.normalBooleanValue());
    savedItem.setDescription(request.description());
    savedItem.setDisplayOrder(request.displayOrder());
    savedItem.setActive(request.active());

    when(equipmentRepository.findById(1L)).thenReturn(Optional.of(equipment));

    when(inspectionItemRepository.save(any(EquipmentInspectionItem.class))).thenReturn(savedItem);

    EquipmentInspectionItemResponse response = inspectionItemService.save(request);

    assertEquals(2L, response.id());
    assertEquals(InspectionItemType.BOOLEAN, response.type());
    assertEquals(false, response.normalBooleanValue());

    verify(equipmentRepository).findById(1L);
    verify(inspectionItemRepository).save(any(EquipmentInspectionItem.class));
  }
}
