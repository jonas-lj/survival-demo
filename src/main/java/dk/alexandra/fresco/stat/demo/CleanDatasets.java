package dk.alexandra.fresco.stat.demo;

import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;

public class CleanDatasets {

  public static void main(String[] arguments) throws IOException {

    Iterable<CSVRecord> records = CSVFormat.DEFAULT.withRecordSeparator(",")
        .parse(new FileReader("hmohiv.csv"));
    List<List<String>> data = StreamSupport.stream(records.spliterator(), false).skip(1).map(
        record -> StreamSupport.stream(record.spliterator(), false).collect(Collectors.toList()))
        .collect(Collectors.toList());
    BufferedWriter writer = Files.newBufferedWriter(
        Path.of("scaled2.csv"));

    CSVPrinter csvPrinter = new CSVPrinter(writer, CSVFormat.DEFAULT);

    for (List<String> row : data) {
      List<String> newRow = new ArrayList<>();
      newRow.add(""); //String.valueOf(Integer.valueOf(row.get(1))));
      newRow.add(""); //String.valueOf(Integer.valueOf(row.get(4))));
      newRow.add(""); //String.valueOf(Double.parseDouble(row.get(2)) / 10.0));
      newRow.add(String.valueOf(Integer.valueOf(row.get(3))));
      csvPrinter.printRecord(newRow);
    }

    csvPrinter.flush();
  }
}


