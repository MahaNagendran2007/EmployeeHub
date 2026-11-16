package com.employeehub.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.employeehub.model.Employee;
import com.employeehub.model.EmploymentStatus;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class EmployeeCsvTest {

    private static Employee employee(String name, String phone) {
        Employee e = new Employee();
        e.setEmployeeCode("EMP-1");
        e.setFullName(name);
        e.setEmail("a@example.com");
        e.setDepartment("Eng");
        e.setJobTitle("Dev");
        e.setLocation("Chennai");
        e.setPhone(phone);
        e.setJoiningDate(LocalDate.of(2024, 7, 1));
        e.setEmploymentStatus(EmploymentStatus.ACTIVE);
        e.setEmergencyContactName("Secret Contact");
        return e;
    }

    @Test
    void write_quotesCommasQuotesAndNewlines() {
        String csv = EmployeeCsv.write(List.of(employee("Doe, \"J\"\nJr", null)));

        assertThat(csv).contains("\"Doe, \"\"J\"\"\nJr\"");
    }

    @Test
    void write_neutralisesSpreadsheetFormulas() {
        String csv = EmployeeCsv.write(List.of(employee("=HYPERLINK(\"x\")", "+911234567")));

        assertThat(csv).contains("'=HYPERLINK").contains("'+911234567");
    }

    @Test
    void write_neverExportsEmergencyContactData() {
        assertThat(EmployeeCsv.write(List.of(employee("A", null)))).doesNotContain("Secret Contact");
    }

    @Test
    void parse_handlesQuotedFieldsEmbeddedNewlinesAndCrLf() {
        List<List<String>> rows = EmployeeCsv.parse("a,b\r\n\"x, y\",\"he said \"\"hi\"\"\"\r\n\r\nlast,row\n");

        assertThat(rows).hasSize(3);
        assertThat(rows.get(1)).containsExactly("x, y", "he said \"hi\"");
        assertThat(rows.get(2)).containsExactly("last", "row");
    }

    @Test
    void roundTrip_exportedFileCanBeParsedBackToTheSameValues() {
        String csv = EmployeeCsv.write(List.of(employee("=Evil", "+91 98765 43210")));

        List<String> row = EmployeeCsv.parse(csv).get(1);

        assertThat(row.get(1)).isEqualTo("=Evil");
        assertThat(row.get(6)).isEqualTo("+91 98765 43210");
    }
}
