package net.campspot.models;

import com.opencsv.bean.CsvBindByName;

public class Campsite {
  @CsvBindByName
  public String id;
  @CsvBindByName
  public String name;
  @CsvBindByName(column = "ParkID")
  public String parkId;
  @CsvBindByName
  public String type;

  public Campsite() {

  }

  public String toString() {
    return name;
  }
}
