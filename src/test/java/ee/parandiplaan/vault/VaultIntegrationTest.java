package ee.parandiplaan.vault;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import ee.parandiplaan.TestDataSeeder;
import ee.parandiplaan.config.TestMinioConfig;
import org.junit.jupiter.api.BeforeEach;
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

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TestMinioConfig.class, TestDataSeeder.class})
class VaultIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private VaultCategoryRepository categoryRepository;

    private String accessToken;
    private String encryptionKey;
    private UUID categoryId;

    @BeforeEach
    void setUp() throws Exception {
        String email = "vault-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        encryptionKey = "TestPassword123";

        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", email,
                                "password", encryptionKey,
                                "fullName", "Vault Tester"
                        ))))
                .andExpect(status().isCreated())
                .andReturn();

        accessToken = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("accessToken").asText();

        categoryId = categoryRepository.findBySlug("banking").orElseThrow().getId();
    }

    @Test
    void createEntry_returnsCreated() throws Exception {
        mockMvc.perform(post("/api/v1/vault/entries")
                        .header("Authorization", "Bearer " + accessToken)
                        .header("X-Encryption-Key", encryptionKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "categoryId", categoryId.toString(),
                                "title", "My Bank Account",
                                "data", "{\"bank_name\":\"SEB\"}"
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.title").value("My Bank Account"))
                .andExpect(jsonPath("$.categorySlug").value("banking"));
    }

    @Test
    void listEntries_returnsOk() throws Exception {
        createTestEntry("Entry 1");
        createTestEntry("Entry 2");

        mockMvc.perform(get("/api/v1/vault/entries")
                        .header("Authorization", "Bearer " + accessToken)
                        .header("X-Encryption-Key", encryptionKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(2))));
    }

    @Test
    void getById_returnsOk() throws Exception {
        String entryId = createTestEntry("Get Me");

        mockMvc.perform(get("/api/v1/vault/entries/" + entryId)
                        .header("Authorization", "Bearer " + accessToken)
                        .header("X-Encryption-Key", encryptionKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Get Me"));
    }

    @Test
    void updateEntry_returnsOk() throws Exception {
        String entryId = createTestEntry("Original Title");

        mockMvc.perform(put("/api/v1/vault/entries/" + entryId)
                        .header("Authorization", "Bearer " + accessToken)
                        .header("X-Encryption-Key", encryptionKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", "Updated Title",
                                "data", "{\"bank_name\":\"Swedbank\"}"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Updated Title"));
    }

    @Test
    void deleteEntry_returnsOk() throws Exception {
        String entryId = createTestEntry("Delete Me");

        mockMvc.perform(delete("/api/v1/vault/entries/" + entryId)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk());

        // Verify it's gone
        mockMvc.perform(get("/api/v1/vault/entries/" + entryId)
                        .header("Authorization", "Bearer " + accessToken)
                        .header("X-Encryption-Key", encryptionKey))
                .andExpect(status().isBadRequest());
    }

    @Test
    void searchEntries_findsMatch() throws Exception {
        createTestEntry("Swedbank Savings");
        createTestEntry("SEB Checking");

        mockMvc.perform(get("/api/v1/vault/entries/search")
                        .param("q", "Swedbank")
                        .header("Authorization", "Bearer " + accessToken)
                        .header("X-Encryption-Key", encryptionKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].title").value("Swedbank Savings"));
    }

    @Test
    void getNonexistentEntry_returnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/vault/entries/" + UUID.randomUUID())
                        .header("Authorization", "Bearer " + accessToken)
                        .header("X-Encryption-Key", encryptionKey))
                .andExpect(status().isBadRequest());
    }

    // --- Helpers ---

    private String createTestEntry(String title) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/vault/entries")
                        .header("Authorization", "Bearer " + accessToken)
                        .header("X-Encryption-Key", encryptionKey)
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
