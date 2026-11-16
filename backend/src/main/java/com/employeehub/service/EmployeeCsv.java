package com.employeehub.service;

import com.employeehub.model.Employee;
import java.util.ArrayList;
import java.util.List;

/** Minimal RFC 4180 CSV writer/reader with spreadsheet formula-injection protection. */
public final class EmployeeCsv {

    public static final List<String> EXPORT_HEADERS = List.of("employeeCode", "fullName", "email", "department",
            "jobTitle", "location", "phone", "joiningDate", "employmentStatus", "reportingManager");

    private EmployeeCsv() { }

    /** Emergency-contact details are deliberately NOT exported. */
    public static String write(List<Employee> employees) {
        StringBuilder out = new StringBuilder(String.join(",", EXPORT_HEADERS)).append("\r\n");
        for (Employee e : employees) {
            out.append(String.join(",",
                    cell(e.getEmployeeCode()), cell(e.getFullName()), cell(e.getEmail()), cell(e.getDepartment()),
                    cell(e.getJobTitle()), cell(e.getLocation()), cell(e.getPhone()),
                    cell(e.getJoiningDate() == null ? null : e.getJoiningDate().toString()),
                    cell(e.getEmploymentStatus() == null ? null : e.getEmploymentStatus().name()),
                    cell(e.getReportingManager()))).append("\r\n");
        }
        return out.toString();
    }

    static String cell(String value) {
        if (value == null) {
            return "";
        }
        String v = value;
        if (!v.isEmpty() && "=+-@\t\r".indexOf(v.charAt(0)) >= 0) {
            v = "'" + v;                         // stops Excel/Sheets from executing the cell as a formula
        }
        if (v.contains(",") || v.contains("\"") || v.contains("\n") || v.contains("\r")) {
            v = "\"" + v.replace("\"", "\"\"") + "\"";
        }
        return v;
    }

    /** Reverses the formula-protection prefix so exported files can be re-imported unchanged. */
    static String unprotect(String value) {
        if (value.length() > 1 && value.charAt(0) == '\'' && "=+-@\t\r".indexOf(value.charAt(1)) >= 0) {
            return value.substring(1);
        }
        return value;
    }

    /** Parses CSV text into rows of cells. Skips completely empty lines. */
    public static List<List<String>> parse(String text) {
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean inQuotes = false;
        boolean cellStarted = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < text.length() && text.charAt(i + 1) == '"') { cell.append('"'); i++; }
                    else { inQuotes = false; }
                } else {
                    cell.append(c);
                }
            } else if (c == '"' && !cellStarted) {
                inQuotes = true;
                cellStarted = true;
            } else if (c == ',') {
                row.add(unprotect(cell.toString().trim()));
                cell.setLength(0);
                cellStarted = false;
            } else if (c == '\n' || c == '\r') {
                if (c == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') { i++; }
                row.add(unprotect(cell.toString().trim()));
                cell.setLength(0);
                cellStarted = false;
                if (!(row.size() == 1 && row.get(0).isEmpty())) { rows.add(row); }
                row = new ArrayList<>();
            } else {
                cell.append(c);
                cellStarted = true;
            }
        }
        if (cell.length() > 0 || !row.isEmpty()) {
            row.add(unprotect(cell.toString().trim()));
            if (!(row.size() == 1 && row.get(0).isEmpty())) { rows.add(row); }
        }
        return rows;
    }
}
