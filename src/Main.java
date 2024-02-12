import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

public class Main {
    public static void main(String[] args) {
        System.out.println("Hello world!");
        String localFilePath = "~/ideascale_dump.sql";


        File folder = new File("~/diff/base");
        for (File file : Objects.requireNonNull(folder.listFiles())) {
            Map<String, Map<String, String>> localTableMap = readFileAndGenerateMap(localFilePath);
            Map<String, Map<String, String>> prodTableMap = readFileAndGenerateMap(file.getAbsolutePath());
            String difference = populateTableDifference(localTableMap, prodTableMap);
            writeFile(difference, file.getName());
        }
    }

    private static String populateTableDifference(Map<String, Map<String, String>> localTableMap, Map<String, Map<String, String>> prodTableMap) {
        StringBuilder sb = new StringBuilder("TABLE_NAME,LOCAL,PRODUCTION\n");
        localTableMap.forEach((key, value) -> {
            if (prodTableMap.containsKey(key)) {
                String diff = populateColumnDifference(key, value, prodTableMap.get(key));
                sb.append(diff);
            }
        });

        localTableMap.keySet().removeIf(s -> prodTableMap.keySet().remove(s));

        if (!localTableMap.isEmpty()) {
            localTableMap.forEach((key, value) -> {
                sb.append(key);
                sb.append(";");
                sb.append(";");
                sb.append("Table not found!");
                sb.append(";\n");
            });
        }

        if (!prodTableMap.isEmpty()) {
            prodTableMap.forEach((key, value) -> {
                sb.append(key);
                sb.append(";");
                sb.append("Table not found!");
                sb.append(";");
                sb.append(";\n");
            });
        }
        return sb.toString();
    }

    private static String populateColumnDifference(String tableName, Map<String, String> localColumnMap, Map<String, String> prodColumnMap) {
        StringBuilder sb = new StringBuilder();
        String localLastLine = localColumnMap.get("LAST_LINE");
        String prodLastLine = prodColumnMap.get("LAST_LINE");

        if (!localLastLine.equals(prodLastLine) && ((localLastLine.contains("COLLATE") && !prodLastLine.contains("COLLATE")) ||
                (!localLastLine.contains("COLLATE") && prodLastLine.contains("COLLATE")))) {
            sb.append(tableName);
            sb.append(",");
            if ((localLastLine.contains("COLLATE") && !prodLastLine.contains("COLLATE"))) {
                int start  = localLastLine.indexOf("COLLATE");
                int end = localLastLine.indexOf(";");
                sb.append(localLastLine, start, end);
            }
            sb.append(",");
            if (!localLastLine.contains("COLLATE") && prodLastLine.contains("COLLATE")) {
                int start  = prodLastLine.indexOf("COLLATE");
                int end = prodLastLine.indexOf(";");
                sb.append(prodLastLine, start, end);
            }
            sb.append("\n");
        }
        localColumnMap.remove("LAST_LINE");
        prodColumnMap.remove("LAST_LINE");

        localColumnMap.forEach((localColumn, localColumnValue)-> {
            String prodColumnValue = prodColumnMap.get(localColumn);
            if (!localColumnValue.equals(prodColumnValue)) {
                sb.append(tableName);
                sb.append(",");
                sb.append(formatValue(localColumnValue));
                sb.append(",");
                if (prodColumnValue != null) {
                    sb.append(formatValue(prodColumnValue));
                }
                sb.append("\n");
            }
        });

        localColumnMap.keySet().removeIf(s -> prodColumnMap.keySet().remove(s));

        if (!localColumnMap.isEmpty()) {
            localColumnMap.forEach((key, value) -> {
                sb.append(tableName);
                sb.append(",");
                sb.append(formatValue(value));
                sb.append(",");
                sb.append("\n");
            });
        }

        if (!prodColumnMap.isEmpty()) {
            prodColumnMap.forEach((key, value) -> {
                sb.append(tableName);
                sb.append(",");
                sb.append(",");
                sb.append(formatValue(value));
                sb.append("\n");
            });
        }

        return sb.toString();
    }

    private static String formatValue(String value) {
        if (value.charAt(value.length() - 1) == ',') {
            value = value.substring(0, value.length() - 1);
        }
        return "\"" + value.replace("ZZZZ=", "").trim() + "\"";
    }

    private static Map<String, Map<String, String>> generateTableMap(List<String> allLines) {
        Map<String, Map<String, String>> tableMap = new TreeMap<>();
        String tableName = "";
        boolean isTable = false;
        List<String> tableLines = new ArrayList<>();
        for (String line : allLines) {

            if (line.contains("CREATE TABLE")) {
                tableName = getName(line);
                isTable = true;
            } else if (isTable) {
                tableLines.add(line);
                if (line.contains(")") && line.contains(";")) {
                    tableMap.put(tableName, generateColumnMap(tableLines));
                    isTable = false;
                    tableLines.clear();
                }
            }
        }
        return tableMap;
    }

    private static Map<String, String> generateColumnMap(List<String> tableLines) {
        Map<String, String> columnMap = new TreeMap<>();
        for (String line : tableLines) {
            if (line.contains(")") && line.contains(";")) {
                columnMap.put("LAST_LINE", line);
            } else if (line.trim().startsWith("KEY") || line.trim().startsWith("UNIQUE KEY")) {
                columnMap.put("ZZZZ=" + getName(line), line); // to put key at last line uder table name
            } else {
                columnMap.put(getName(line), line);
            }
        }
        return columnMap;
    }

    private static String getName(String line) {
        int start = line.indexOf('`');
        int end = line.indexOf('`', start + 1);
        return line.substring(start + 1, end);
    }

    private static Map<String, Map<String, String>> readFileAndGenerateMap(String localFilePath) {
        File localFile = new File(localFilePath);

        try {
            List<String> allLines = Files.readAllLines(localFile.toPath());
            return generateTableMap(allLines);
        } catch (IOException e) {
            System.out.println(e.getMessage());
        }
        return Collections.emptyMap();
    }

    private static void writeFile(String str, String name) {
        String fileName = "~/diff/result/" + name.replace(".sql",".csv");
        try (Writer writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(fileName), StandardCharsets.UTF_8))) {
            writer.write(str);
        } catch (IOException ex) {
            // Handle me
        }
    }
}
