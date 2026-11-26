package com.ledger.integration;

import com.ledger.audit.AuditEventRepository;
import com.ledger.demo.DemoSeedService;
import com.ledger.journal.JournalEntryRepository;
import com.ledger.reporting.ReportingService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies Flyway migrations, Hibernate schema validation, double-entry postings,
 * and reconciled financial reports against a real PostgreSQL 16 database via Testcontainers.
 */
@Tag("postgres-integration")
@ActiveProfiles("postgres")
@Testcontainers
@SpringBootTest
class PostgresIntegrationIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("ledger_it")
            .withUsername("ledger")
            .withPassword("ledger_it_password");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.PostgreSQLDialect");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("ledger.demo.auto-seed", () -> "false");
    }

    @Autowired private DemoSeedService demoSeedService;
    @Autowired private ReportingService reportingService;
    @Autowired private JournalEntryRepository journalEntryRepository;
    @Autowired private AuditEventRepository auditEventRepository;

    @Test
    void runsMigrationsAndReconcilesLedgerOnRealPostgres() {
        var handle = demoSeedService.ensureDemoWorkspace();
        UUID orgId = handle.demoOrganization().getId();
        LocalDate today = LocalDate.now();

        var tb = reportingService.trialBalance(orgId, today);
        var bs = reportingService.balanceSheet(orgId, today, null);
        var cf = reportingService.cashFlow(orgId, today.minusMonths(5).withDayOfMonth(1), today);
        var ar = reportingService.receivablesAging(orgId, today);
        var ap = reportingService.payablesAging(orgId, today);
        var journals = journalEntryRepository.search(orgId, null, null, null, null, null, PageRequest.of(0, 10));
        var audits = auditEventRepository.searchByOrganization(orgId, null, null, null, PageRequest.of(0, 10));

        assertThat(tb.balanced()).isTrue();
        assertThat(bs.balanced()).isTrue();
        assertThat(cf.reconciled()).isTrue();
        assertThat(ar.reconciledWithGl()).isTrue();
        assertThat(ap.reconciledWithGl()).isTrue();
        assertThat(journals.getTotalElements()).isGreaterThan(10);
        assertThat(audits.getTotalElements()).isGreaterThan(10);
    }
}
