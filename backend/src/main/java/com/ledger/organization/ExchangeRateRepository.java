package com.ledger.organization;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ExchangeRateRepository extends JpaRepository<ExchangeRate, UUID> {

    List<ExchangeRate> findByOrganizationIdOrderByEffectiveDateDesc(UUID organizationId);

    Optional<ExchangeRate> findByOrganizationIdAndFromCurrencyAndToCurrencyAndEffectiveDate(
            UUID organizationId, String fromCurrency, String toCurrency, LocalDate effectiveDate
    );

    @Query("""
            SELECT er FROM ExchangeRate er
            WHERE er.organizationId = :orgId
              AND er.fromCurrency = :fromCurrency
              AND er.toCurrency = :toCurrency
              AND er.effectiveDate <= :asOfDate
            ORDER BY er.effectiveDate DESC
            """)
    List<ExchangeRate> findEffectiveRates(
            @Param("orgId") UUID organizationId,
            @Param("fromCurrency") String fromCurrency,
            @Param("toCurrency") String toCurrency,
            @Param("asOfDate") LocalDate asOfDate
    );
}
