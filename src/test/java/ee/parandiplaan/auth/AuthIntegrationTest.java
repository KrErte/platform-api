package ee.parandiplaan.auth;

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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TestMinioConfig.class, TestDataSeeder.class})
class AuthIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void register_returnsCreatedWithTokens() throws Exception {
        String email = uniqueEmail();

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", email,
                                "password", "Password123",
                                "fullName", "Test User"
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.fullName").value("Test User"));
    }

    @Test
    void register_duplicateEmail_returnsBadRequest() throws Exception {
        String email = uniqueEmail();

        // First registration succeeds
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", email,
                                "password", "Password123",
                                "fullName", "Test User"
                        ))))
                .andExpect(status().isCreated());

        // Second registration with same email fails
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", email,
                                "password", "Password123",
                                "fullName", "Another User"
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void login_validCredentials_returnsOkWithTokens() throws Exception {
        String email = uniqueEmail();
        register(email, "Password123", "Test User");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", email,
                                "password", "Password123"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.email").value(email));
    }

    @Test
    void login_wrongPassword_returnsBadRequest() throws Exception {
        String email = uniqueEmail();
        register(email, "Password123", "Test User");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", email,
                                "password", "WrongPassword"
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void refreshToken_withLoginToken_returnsOk() throws Exception {
        String email = uniqueEmail();
        register(email, "Password123", "Test User");

        // Login to create a separate session
        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", email,
                                "password", "Password123"
                        ))))
                .andExpect(status().isOk())
                .andReturn();

        String refreshToken = objectMapper.readTree(loginResult.getResponse().getContentAsString())
                .get("refreshToken").asText();

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "refreshToken", refreshToken
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty());
    }

    @Test
    void protectedEndpoint_noToken_returnsForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/vault/entries")
                        .header("X-Encryption-Key", "test-password"))
                .andExpect(status().isForbidden());
    }

    @Test
    void protectedEndpoint_validToken_returnsOk() throws Exception {
        String email = uniqueEmail();
        String accessToken = registerAndGetAccessToken(email, "Password123", "Test User");

        mockMvc.perform(get("/api/v1/vault/entries")
                        .header("Authorization", "Bearer " + accessToken)
                        .header("X-Encryption-Key", "Password123"))
                .andExpect(status().isOk());
    }

    // --- Helpers ---

    private String uniqueEmail() {
        return "test-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
    }

    private void register(String email, String password, String fullName) throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", email,
                                "password", password,
                                "fullName", fullName
                        ))))
                .andExpect(status().isCreated());
    }

    private String registerAndGetAccessToken(String email, String password, String fullName) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", email,
                                "password", password,
                                "fullName", fullName
                        ))))
                .andExpect(status().isCreated())
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("accessToken").asText();
    }
}
