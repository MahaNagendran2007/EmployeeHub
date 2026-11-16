package com.employeehub.config;

import com.employeehub.dto.EmployeeRequest;
import com.employeehub.model.EmploymentStatus;
import com.employeehub.repository.EmployeeRepository;
import com.employeehub.service.EmployeeService;
import java.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Development-only sample data. Runs ONLY when the "demo" Spring profile is active
 * (SPRING_PROFILES_ACTIVE=demo) and ONLY when the collection is empty.
 */
@Component
@Profile("demo")
public class DemoDataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    private final EmployeeRepository repository;
    private final EmployeeService service;

    public DemoDataSeeder(EmployeeRepository repository, EmployeeService service) {
        this.repository = repository;
        this.service = service;
    }

    @Override
    public void run(String... args) {
        if (repository.count() > 0) {
            log.info("Demo seeding skipped: employees collection is not empty");
            return;
        }
        String[][] rows = {
                {"EMP-1001", "Aarav Menon", "Engineering", "Backend Engineer", "Chennai", "2022-06-13"},
                {"EMP-1002", "Diya Raman", "Engineering", "Frontend Engineer", "Bengaluru", "2023-01-09"},
                {"EMP-1003", "Karthik Iyer", "Engineering", "QA Engineer", "Chennai", "2021-11-22"},
                {"EMP-1004", "Meera Nair", "Human Resources", "HR Manager", "Mumbai", "2020-03-02"},
                {"EMP-1005", "Rohan Gupta", "Sales", "Account Executive", "Delhi", "2024-02-19"},
                {"EMP-1006", "Sneha Pillai", "Sales", "Sales Lead", "Mumbai", "2019-08-05"},
                {"EMP-1007", "Vikram Das", "Finance", "Financial Analyst", "Chennai", "2022-09-12"},
                {"EMP-1008", "Ananya Reddy", "Design", "UI/UX Designer", "Hyderabad", "2023-05-15"},
                {"EMP-1009", "Imran Sheikh", "Operations", "Operations Coordinator", "Delhi", "2021-04-26"},
                {"EMP-1010", "Lakshmi Subramanian", "Finance", "Accounts Manager", "Bengaluru", "2018-10-01"},
                {"EMP-1011", "Neil Thomas", "Engineering", "DevOps Engineer", "Hyderabad", "2024-07-08"},
                {"EMP-1012", "Pooja Verma", "Human Resources", "Recruiter", "Chennai", "2023-10-30"}
        };
        EmploymentStatus[] statuses = {EmploymentStatus.ACTIVE, EmploymentStatus.ACTIVE, EmploymentStatus.ON_LEAVE,
                EmploymentStatus.ACTIVE, EmploymentStatus.ACTIVE, EmploymentStatus.ACTIVE, EmploymentStatus.INACTIVE,
                EmploymentStatus.ACTIVE, EmploymentStatus.ON_LEAVE, EmploymentStatus.ACTIVE, EmploymentStatus.ACTIVE,
                EmploymentStatus.ACTIVE};

        for (int i = 0; i < rows.length; i++) {
            String[] r = rows[i];
            String email = r[1].toLowerCase().replace(' ', '.') + "@example.com";
            service.create(new EmployeeRequest(r[0], r[1], email, r[2], r[3], r[4], null,
                    LocalDate.parse(r[5]), statuses[i], null, null, null));
        }
        log.info("Demo seeding inserted {} sample employees", rows.length);
    }
}
