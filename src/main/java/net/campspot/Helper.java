package net.campspot;

import com.opencsv.CSVReader;
import com.opencsv.bean.CsvToBeanBuilder;
import com.opencsv.bean.CsvToBeanFilter;
import net.campspot.models.Campsite;
import net.campspot.models.Park;

import java.awt.*;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Helper {
  private static void openWebpage(URI uri) {
    Desktop desktop = Desktop.isDesktopSupported() ? Desktop.getDesktop() : null;
    if (desktop != null && desktop.isSupported(Desktop.Action.BROWSE)) {
      try {
        desktop.browse(uri);
      } catch (Exception e) {
        e.printStackTrace();
      }
    }
  }

  /**
   * Open url in default browser.
   *
   * @param url url
   */
  public static void openWebpage(URL url) {
    try {
      openWebpage(url.toURI());
    } catch (URISyntaxException e) {
      e.printStackTrace();
    }
  }

  /**
   * Read parks.csv to tableModel.
   *
   * @return result list of parks
   */
  public static List<Park> readParks() {
    try {
      return new CsvToBeanBuilder<Park>(new FileReader("parks.csv"))
          .withType(Park.class).build().parse();
    } catch (FileNotFoundException e) {
      e.printStackTrace();
    }
    return new ArrayList<>();
  }

  /**
   * Read all campsites to a map with key as parkId, value as list of campsites
   * @param parksMap list of parks
   * @return map of list of campsites
   */
  public static Map<String, List<Campsite>> readCampsites(Map<String, Park> parksMap) {
    // convert parks list to map list and initiate the result map
    Map<String, List<Campsite>> result = new HashMap<>();
    for (String id : parksMap.keySet()) {
      result.put(id, new ArrayList<>());
    }

    // parse all-campsites.csv, only accept campsites belong to existing parks
    CSVReader reader;
    try {
      reader = new CSVReader(new FileReader("all-campsites.csv"));
    } catch (FileNotFoundException e) {
      throw new RuntimeException(e);
    }
    CsvToBeanFilter filter = line -> {
      boolean blankLine = line.length == 0 || line[0].isEmpty() || parksMap.get(line[2]) == null;
      return !blankLine;
    };
    List<Campsite> campsites = new CsvToBeanBuilder<Campsite>(reader)
        .withType(Campsite.class).withFilter(filter).build().parse();

    // return result
    for (Campsite i : campsites) {
      result.get(i.parkId).add(i);
    }
    return result;
  }
}
