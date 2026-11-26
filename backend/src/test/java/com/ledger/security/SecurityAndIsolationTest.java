package com.ledger.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ledger.organization.OrgRole;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityAndIsolationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @Test
    void enforcesCsrfProtectionViewerPermissionsCrossOrgIsolationAndSeparationOfDuties() throws Exception {
        String s = UUID.randomUUID().toString().substring(0, 8);

        // Register Owner A with Org A
        MvcResult regA = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"sec-a-%s@test.local","password":"Password123!","fullName":"Owner A","organizationName":"Alpha Studio %s","baseCurrency":"IDR"}
                                """.formatted(s, s)))
                .andExpect(status().isCreated())
                .andReturn();
        Cookie sessionA = regA.getResponse().getCookie("LEDGER_SESSION");
        assertThat(sessionA).isNotNull();
        assertThat(sessionA.isHttpOnly()).isTrue();
        String orgAId = objectMapper.readTree(regA.getResponse().getContentAsString())
                .path("activeOrganization").path("organizationId").asText();

        // Fetch CSRF token
        MvcResult csrfResA = mockMvc.perform(get("/api/v1/auth/csrf").cookie(sessionA))
                .andExpect(status().isOk()).andReturn();
        Cookie xsrfA = csrfResA.getResponse().getCookie("XSRF-TOKEN");
        String tokenA = objectMapper.readTree(csrfResA.getResponse().getContentAsString()).path("token").asText();

        // 1. CSRF check: authenticated state-changing request WITHOUT X-XSRF-TOKEN must be rejected (403)
        mockMvc.perform(post("/api/v1/accounts")
                        .cookie(sessionA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"5990","name":"No Csrf Test","accountType":"EXPENSE"}
                                """))
                .andExpect(status().isForbidden());

        // 2. With valid CSRF token, Owner A can create a contact and upload an attachment
        MvcResult contactRes = mockMvc.perform(post("/api/v1/contacts")
                        .cookie(sessionA, xsrfA)
                        .header("X-XSRF-TOKEN", tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"contactType":"SUPPLIER","name":"Private Vendor","currencyCode":"IDR"}
                                """))
                .andExpect(status().isCreated()).andReturn();
        String supplierId = objectMapper.readTree(contactRes.getResponse().getContentAsString()).path("id").asText();

        MockMultipartFile receipt = new MockMultipartFile("file", "receipt.pdf", "application/pdf", "%PDF-1.4 test".getBytes());
        MvcResult uploadRes = mockMvc.perform(multipart("/api/v1/attachments")
                        .file(receipt)
                        .param("entityType", "EXPENSE")
                        .param("entityId", supplierId)
                        .cookie(sessionA, xsrfA)
                        .header("X-XSRF-TOKEN", tokenA))
                .andExpect(status().isCreated()).andReturn();
        String attachmentId = objectMapper.readTree(uploadRes.getResponse().getContentAsString()).path("id").asText();

        // 3. Add a VIEWER member to Org A
        mockMvc.perform(post("/api/v1/organizations/current/members")
                        .cookie(sessionA, xsrfA)
                        .header("X-XSRF-TOKEN", tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"viewer-%s@test.local","fullName":"Read Only Viewer","temporaryPassword":"Password123!","role":"%s"}
                                """.formatted(s, OrgRole.VIEWER.name())))
                .andExpect(status().isCreated());

        // Sign in as Viewer
        MvcResult loginViewer = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"viewer-%s@test.local","password":"Password123!"}
                                """.formatted(s)))
                .andExpect(status().isOk()).andReturn();
        Cookie viewerCookie = loginViewer.getResponse().getCookie("LEDGER_SESSION");
        MvcResult csrfV = mockMvc.perform(get("/api/v1/auth/csrf").cookie(viewerCookie)).andReturn();
        Cookie xsrfV = csrfV.getResponse().getCookie("XSRF-TOKEN");
        String tokenV = objectMapper.readTree(csrfV.getResponse().getContentAsString()).path("token").asText();

        // Viewer can read accounts, but CANNOT create accounts or expenses (403)
        mockMvc.perform(get("/api/v1/accounts").cookie(viewerCookie)).andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/accounts")
                        .cookie(viewerCookie, xsrfV)
                        .header("X-XSRF-TOKEN", tokenV)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"5991","name":"Blocked Viewer","accountType":"EXPENSE"}
                                """))
                .andExpect(status().isForbidden());

        // 4. Register Owner B in separate Org B -> verify cross-org isolation on attachment download & header spoofing
        MvcResult regB = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"sec-b-%s@test.local","password":"Password123!","fullName":"Owner B","organizationName":"Beta Studio %s","baseCurrency":"IDR"}
                                """.formatted(s, s)))
                .andExpect(status().isCreated()).andReturn();
        Cookie sessionB = regB.getResponse().getCookie("LEDGER_SESSION");

        // Owner B cannot download Org A's private attachment
        mockMvc.perform(get("/api/v1/attachments/" + attachmentId + "/download").cookie(sessionB))
                .andExpect(status().isNotFound());

        // Owner B spoofing X-Organization-Id header for Org A is denied
        mockMvc.perform(get("/api/v1/accounts").cookie(sessionB).header("X-Organization-Id", orgAId))
                .andExpect(status().isBadRequest());

        // 5. Separation of duties: Owner A creates & submits expense, cannot self-approve (403)
        MvcResult expRes = mockMvc.perform(post("/api/v1/expenses")
                        .cookie(sessionA, xsrfA)
                        .header("X-XSRF-TOKEN", tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"expenseType":"IMMEDIATE_EXPENSE","title":"Large Software License","netAmount":5000000,"postImmediately":false}
                                """))
                .andExpect(status().isCreated()).andReturn();
        String expId = objectMapper.readTree(expRes.getResponse().getContentAsString()).path("id").asText();

        MvcResult subRes = mockMvc.perform(post("/api/v1/approvals/submit")
                        .cookie(sessionA, xsrfA)
                        .header("X-XSRF-TOKEN", tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"entityType":"EXPENSE","entityId":"%s"}
                                """.formatted(expId)))
                .andExpect(status().isCreated()).andReturn();
        String approvalId = objectMapper.readTree(subRes.getResponse().getContentAsString()).path("id").asText();

        mockMvc.perform(post("/api/v1/approvals/" + approvalId + "/decide")
                        .cookie(sessionA, xsrfA)
                        .header("X-XSRF-TOKEN", tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"decision":"APPROVED","reason":"Self approval should fail"}
                                """))
                .andExpect(status().isForbidden());

        // 6. Logout revokes session
        mockMvc.perform(post("/api/v1/auth/logout").cookie(sessionA, xsrfA).header("X-XSRF-TOKEN", tokenA))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/auth/me").cookie(sessionA))
                .andExpect(status().isUnauthorized());
    }
}
