package com.ledger.expense;

import com.ledger.accounting.AccountRepository;
import com.ledger.organization.Organization;
import com.ledger.organization.OrganizationInitializer;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@Order(2)
public class ExpenseCategoryInitializer implements OrganizationInitializer {

    private final ExpenseCategoryRepository categoryRepository;
    private final AccountRepository accountRepository;

    public ExpenseCategoryInitializer(ExpenseCategoryRepository categoryRepository, AccountRepository accountRepository) {
        this.categoryRepository = categoryRepository;
        this.accountRepository = accountRepository;
    }

    @Override
    public void initializeOrganization(Organization org, UUID actorUserId) {
        UUID orgId = org.getId();
        createIfAccountExists(orgId, "Software & Subscriptions", "SOFTWARE_EXPENSE");
        createIfAccountExists(orgId, "Contractors & Freelancers", "CONTRACTOR_EXPENSE");
        createIfAccountExists(orgId, "Marketing & Brand", "MARKETING_EXPENSE");
        createIfAccountExists(orgId, "Office & Supplies", "OFFICE_EXPENSE");
        createIfAccountExists(orgId, "Workspace & Rent", "RENT_EXPENSE");
        createIfAccountExists(orgId, "Bank Charges", "BANK_FEES");
        createIfAccountExists(orgId, "General Operations", "OPERATING_EXPENSE");
    }

    private void createIfAccountExists(UUID orgId, String categoryName, String systemCode) {
        accountRepository.findByOrganizationIdAndSystemCode(orgId, systemCode)
                .ifPresent(acc -> categoryRepository.save(new ExpenseCategory(orgId, categoryName, acc.getId())));
    }
}
