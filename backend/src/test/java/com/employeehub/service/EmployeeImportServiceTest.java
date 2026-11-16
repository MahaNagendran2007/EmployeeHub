package com.employeehub.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.employeehub.dto.EmployeeRequest;
import com.employeehub.dto.FieldErrorDetail;
import com.employeehub.dto.ImportResult;
import com.employeehub.exception.BadRequestException;
import com.employeehub.exception.DuplicateResourceException;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EmployeeImportServiceTest {

    private static final String HEADER = "employeeCode,fullName,email,department,jobTitle,location,joiningDate,employmentStatus\n";

    @Mock private EmployeeService employees;
    @Mock private ActivityService activity;
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
    private EmployeeImportService service;

    @BeforeEach
    void setUp() {
        service = new EmployeeImportService(employees, validator, activity, 5);
    }

    @Test
    void validRowsAreImportedAndStatusDefaultsToActive() {
        ImportResult result = service.importCsv(HEADER + "EMP-1,Priya Sharma,p@example.com,Eng,Dev,Chennai,2024-07-01,\n");

        ArgumentCaptor<EmployeeRequest> captor = ArgumentCaptor.forClass(EmployeeRequest.class);
        verify(employees).create(captor.capture());
        assertThat(captor.getValue().employmentStatus().name()).isEqualTo("ACTIVE");
        assertThat(result.imported()).isEqualTo(1);
        assertThat(result.skipped()).isZero();
        assertThat(result.errors()).isEmpty();
    }

    @Test
    void invalidRowsAreReportedWithRowNumbersAndGoodRowsStillImport() {
        String csv = HEADER
                + "EMP-1,Priya,not-an-email,Eng,Dev,Chennai,2024-07-01,ACTIVE\n"
                + "EMP-2,Ravi,r@example.com,Eng,Dev,Chennai,01/02/2024,ACTIVE\n"
                + "EMP-3,Meena,m@example.com,Eng,Dev,Chennai,2024-07-01,RETIRED\n"
                + "EMP-4,Sam,s@example.com,Eng,Dev,Chennai,2024-07-01,ON LEAVE\n";

        ImportResult result = service.importCsv(csv);

        assertThat(result.totalRows()).isEqualTo(4);
        assertThat(result.imported()).isEqualTo(1);
        assertThat(result.skipped()).isEqualTo(3);
        assertThat(result.errors()).extracting(ImportResult.RowError::row).containsExactly(2, 3, 4);
        assertThat(result.errors().get(0).message()).containsIgnoringCase("email");
        assertThat(result.errors().get(1).message()).contains("yyyy-MM-dd");
    }

    @Test
    void duplicatesInsideTheFileAreSkipped() {
        String csv = HEADER
                + "EMP-1,A,a@example.com,Eng,Dev,Chennai,2024-07-01,ACTIVE\n"
                + "emp-1,B,b@example.com,Eng,Dev,Chennai,2024-07-01,ACTIVE\n"
                + "EMP-3,C,A@example.com,Eng,Dev,Chennai,2024-07-01,ACTIVE\n";

        ImportResult result = service.importCsv(csv);

        assertThat(result.imported()).isEqualTo(1);
        assertThat(result.errors()).hasSize(2);
        verify(employees, times(1)).create(any());
    }

    @Test
    void duplicatesAlreadyInTheDatabaseAreReportedNotImported() {
        when(employees.create(any())).thenThrow(new DuplicateResourceException(
                List.of(new FieldErrorDetail("email", "Email 'a@example.com' is already in use"))));

        ImportResult result = service.importCsv(HEADER + "EMP-1,A,a@example.com,Eng,Dev,Chennai,2024-07-01,ACTIVE\n");

        assertThat(result.imported()).isZero();
        assertThat(result.errors().get(0).message()).contains("already in use");
    }

    @Test
    void missingRequiredColumnIsRejectedUpFront() {
        assertThatThrownBy(() -> service.importCsv("employeeCode,fullName\nEMP-1,A\n"))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("email");
        verify(employees, never()).create(any());
    }

    @Test
    void tooManyRowsAreRejected() {
        StringBuilder csv = new StringBuilder(HEADER);
        for (int i = 0; i < 6; i++) {
            csv.append("EMP-").append(i).append(",A,a").append(i).append("@example.com,Eng,Dev,Chennai,2024-07-01,ACTIVE\n");
        }

        assertThatThrownBy(() -> service.importCsv(csv.toString())).isInstanceOf(BadRequestException.class)
                .hasMessageContaining("limit");
    }

    @Test
    void emptyFileIsRejected() {
        assertThatThrownBy(() -> service.importCsv("")).isInstanceOf(BadRequestException.class);
    }
}
