package ee.parandiplaan.vault;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import ee.parandiplaan.TestDataSeeder;
import ee.parandiplaan.config.TestMinioConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TestMinioConfig.class, TestDataSeeder.class})
class VaultSecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private VaultCategoryRepository categoryRepository;

    @Test
    void trialUser_canCreateUpTo10Entries() throws Exception {
        UserContext user = registerUser();
        UUID catId = categoryRepository.findBySlug("banking").orElseThrow().getId();

        for (int i = 1; i <= 10; i++) {
            mockMvc.perform(post("/api/v1/vault/entries")
                            .header("Authorization", "Bearer " + user.accessToken)
                            .header("X-Encryption-Key", user.password)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of(
                                    "categoryId", catId.toString(),
                                    "title", "Entry " + i,
                                    "data", "{\"bank_name\":\"Bank " + i + "\"}"
                            ))))
                    .andExpect(status().isCreated());
        }
    }

    @Test
    void trialUser_11thEntry_returnsConflict() throws Exception {
        UserContext user = registerUser();
        UUID catId = categoryRepository.findBySlug("insurance").orElseThrow().getId();

        // Create 10 entries (limit)
        for (int i = 1; i <= 10; i++) {
            mockMvc.perform(post("/api/v1/vault/entries")
                            .header("Authorization", "Bearer " + user.accessToken)
                            .header("X-Encryption-Key", user.password)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of(
                                    "categoryId", catId.toString(),
                                    "title", "Entry " + i,
                                    "data", "{\"provider\":\"Provider " + i + "\"}"
                            ))))
                    .andExpect(status().isCreated());
        }

        // 11th should fail with 409 Conflict
        mockMvc.perform(post("/api/v1/vault/entries")
                        .header("Authorization", "Bearer " + user.accessToken)
                        .header("X-Encryption-Key", user.password)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "categoryId", catId.toString(),
                                "title", "Entry 11",
                                "data", "{\"provider\":\"Provider 11\"}"
                        ))))
                .andExpect(status().isConflict());
    }

    @Test
    void crossUserRead_returnsBadRequest() throws Exception {
        UserContext userA = registerUser();
        UserContext userB = registerUser();
        UUID catId = categoryRepository.findBySlug("banking").orElseThrow().getId();

        // User A creates an entry
        String entryId = createEntry(userA, catId, "User A Secret");

        // User B tries to read it
        mockMvc.perform(get("/api/v1/vault/entries/" + entryId)
                        .header("Authorization", "Bearer " + userB.accessToken)
                        .header("X-Encryption-Key", userB.password))
                .andExpect(status().isBadRequest());
    }

    @Test
    void crossUserDelete_returnsBadRequest() throws Exception {
        UserContext userA = registerUser();
        UserContext userB = registerUser();
        UUID catId = categoryRepository.findBySlug("banking").orElseThrow().getId();

        // User A creates an entry
        String entryId = createEntry(userA, catId, "User A Data");

        // User B tries to delete it
        mockMvc.perform(delete("/api/v1/vault/entries/" + entryId)
                        .header("Authorization", "Bearer " + userB.accessToken))
                .andExpect(status().isBadRequest());
    }

    // --- Helpers ---

    private record UserContext(String accessToken, String password) {}

    private UserContext registerUser() throws Exception {
        String email = "sec-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        String password = "SecurePass123";

        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", email,
                                "password", password,
                                "fullName", "Security Tester"
                        ))))
                .andExpect(status().isCreated())
                .andReturn();

        String accessToken = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("accessToken").asText();

        return new UserContext(accessToken, password);
    }

    private String createEntry(UserContext user, UUID categoryId, String title) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/vault/entries")
                        .header("Authorization", "Bearer " + user.accessToken)
                        .header("X-Encryption-Key", user.password)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "categoryId", categoryId.toString(),
                                "title", title,
                                "data", "{\"bank_name\":\"Test Bank\"}"
                        ))))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        return json.get("id").asText();
    }
}
