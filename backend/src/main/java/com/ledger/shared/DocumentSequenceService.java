package com.ledger.shared;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

/**
 * Generates sequential document numbers (e.g. INV-2026-0001) using row-level pessimistic locking
 * so concurrent transactions cannot generate duplicate numbers.
 */
@Service
public class DocumentSequenceService {

    private final DocumentSequenceRepository sequenceRepository;
    private final TransactionTemplate requiresNewTx;

    public DocumentSequenceService(DocumentSequenceRepository sequenceRepository,
                                   PlatformTransactionManager transactionManager) {
        this.sequenceRepository = sequenceRepository;
        this.requiresNewTx = new TransactionTemplate(transactionManager);
        this.requiresNewTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public String nextNumber(UUID organizationId, String sequenceType, String prefix, int year) {
        DocumentSequence seq = sequenceRepository.findForUpdate(organizationId, sequenceType, year)
                .orElseGet(() -> initializeSequence(organizationId, sequenceType, prefix, year));
        if (prefix != null && !prefix.isBlank() && !prefix.equals(seq.getPrefix())) {
            seq.setPrefix(prefix);
        }
        long val = seq.consumeNextValue();
        sequenceRepository.saveAndFlush(seq);
        return String.format("%s-%d-%04d", seq.getPrefix(), year, val);
    }

    private DocumentSequence initializeSequence(UUID organizationId, String sequenceType, String prefix, int year) {
        try {
            requiresNewTx.executeWithoutResult(status -> {
                if (sequenceRepository.findForUpdate(organizationId, sequenceType, year).isEmpty()) {
                    sequenceRepository.saveAndFlush(new DocumentSequence(organizationId, sequenceType, prefix, year));
                }
            });
        } catch (DataIntegrityViolationException ignored) {
            // Either a concurrent thread inserted the row first, or the parent Organization
            // row is not yet committed in the caller's transaction (e.g. initial demo seeding).
        }
        return sequenceRepository.findForUpdate(organizationId, sequenceType, year)
                .orElseGet(() -> sequenceRepository.saveAndFlush(
                        new DocumentSequence(organizationId, sequenceType, prefix, year)
                ));
    }
}
