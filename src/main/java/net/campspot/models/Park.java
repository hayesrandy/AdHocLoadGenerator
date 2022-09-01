package net.campspot.models;

import com.opencsv.bean.CsvBindByName;

public class Park {
  @CsvBindByName(column = "ParkID")
  public String id;
  @CsvBindByName
  public String name;

  public Park() {

  }

  public Park(String id, String name) {
    this.id = id;
    this.name = name;
  }

  public String toString() {
    return name;
  }
}
