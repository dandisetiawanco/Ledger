package com.ledger.shared;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface DocumentSequenceRepository extends JpaRepository<DocumentSequence, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT ds FROM DocumentSequence ds WHERE ds.organizationId = :orgId AND ds.sequenceType = :type AND ds.seqYear = :year")
    Optional<DocumentSequence> findForUpdate(
            @Param("orgId") UUID organizationId,
            @Param("type") String sequenceType,
            @Param("year") int seqYear
    );
}
