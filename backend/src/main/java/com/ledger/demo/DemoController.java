package com.ledger.demo;

import com.ledger.identity.AuthService;
import com.ledger.reporting.ReportingService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class DemoController {

    private final DemoSeedService demoSeedService;
    private final AuthService authService;
    private final ReportingService reportingService;

    public DemoController(
            DemoSeedService demoSeedService,
            AuthService authService,
            ReportingService reportingService) {
        this.demoSeedService = demoSeedService;
        this.authService = authService;
        this.reportingService = reportingService;
    }

    @PostMapping("/demo/start")
    public AuthService.AuthResponse startDemo(HttpServletRequest req, HttpServletResponse res) {
        DemoSeedService.DemoHandle handle = demoSeedService.ensureDemoWorkspace();
        return authService.startSessionForUser(handle.demoUser(), handle.demoOrganization().getId(), req, res);
    }

    /**
     * Public summary endpoint exposing non-sensitive aggregate demonstration figures
     * derived directly from the seeded synthetic demo organization's posted ledger.
     */
    @GetMapping("/public/demo-preview")
    public Map<String, Object> publicDemoPreview() {
        DemoSeedService.DemoHandle handle = demoSeedService.ensureDemoWorkspace();
        LocalDate today = LocalDate.now();
        var overview = reportingService.overview(handle.demoOrganization().getId(), today.minusMonths(5).withDayOfMonth(1), today);
        var tb = reportingService.trialBalance(handle.demoOrganization().getId(), today);
        var bs = reportingService.balanceSheet(handle.demoOrganization().getId(), today, null);
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("organizationName", overview.organizationName());
        result.put("demo", true);
        result.put("currencyCode", overview.currencyCode());
        result.put("cashBalance", overview.cashBalance());
        result.put("sixMonthRevenue", overview.periodRevenue());
        result.put("sixMonthExpenses", overview.periodExpenses());
        result.put("sixMonthNetProfit", overview.periodNetProfit());
        result.put("outstandingReceivables", overview.outstandingReceivables());
        result.put("outstandingPayables", overview.outstandingPayables());
        result.put("trialBalanceBalanced", tb.balanced());
        result.put("trialBalanceTotal", tb.totalDebit());
        result.put("balanceSheetBalanced", bs.balanced());
        result.put("monthlyTrend", overview.monthlyTrend());
        return result;
    }
}
