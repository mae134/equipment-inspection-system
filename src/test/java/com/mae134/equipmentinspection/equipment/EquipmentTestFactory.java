package com.mae134.equipmentinspection.equipment;

public final class EquipmentTestFactory {

  private EquipmentTestFactory() {}

  public static Equipment create(String equipmentCode, String name) {
    Equipment equipment = new Equipment();
    equipment.setEquipmentCode(equipmentCode);
    equipment.setName(name);
    equipment.setActive(true);
    return equipment;
  }
}
