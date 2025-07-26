package com.bbthechange.inviter.config;

import com.bbthechange.inviter.model.Event;
import com.bbthechange.inviter.model.Invite;
import com.bbthechange.inviter.model.User;
import com.bbthechange.inviter.repository.EventRepository;
import com.bbthechange.inviter.repository.InviteRepository;
import com.bbthechange.inviter.repository.UserRepository;
import com.bbthechange.inviter.service.JwtService;
import com.bbthechange.inviter.service.PasswordService;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.services.dynamodb.model.ResourceNotFoundException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

import java.util.UUID;

import static org.testcontainers.containers.localstack.LocalStackContainer.Service.DYNAMODB;

/**
 * Base class for integration tests that provides:
 * - TestContainers DynamoDB Local setup
 * - Spring Boot test configuration with real HTTP server
 * - JWT token generation for authenticated tests
 * - Test data cleanup utilities
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("integration")
@Testcontainers
@Import(IntegrationTestConfiguration.class)
public abstract class BaseIntegrationTest {

    @Container
    static LocalStackContainer localstack = new LocalStackContainer(DockerImageName.parse("localstack/localstack:3.0"))
            .withServices(DYNAMODB)
            .withEnv("DEBUG", "1");

    @LocalServerPort
    protected int port;

    @Autowired
    protected WebApplicationContext webApplicationContext;

    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected JwtService jwtService;

    @Autowired
    protected PasswordService passwordService;

    @Autowired
    protected UserRepository userRepository;

    @Autowired
    protected EventRepository eventRepository;

    @Autowired
    protected InviteRepository inviteRepository;

    @Autowired
    protected DynamoDbEnhancedClient dynamoDbEnhancedClient;

    protected String baseUrl;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("aws.dynamodb.endpoint", () -> localstack.getEndpointOverride(DYNAMODB).toString());
        registry.add("aws.region", () -> localstack.getRegion());
        registry.add("aws.accessKeyId", localstack::getAccessKey);
        registry.add("aws.secretAccessKey", localstack::getSecretKey);
    }

    @BeforeEach
    void setUp() {
        baseUrl = "http://localhost:" + port;
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        cleanupTestData();
    }

    /**
     * Clean up test data before each test
     */
    protected void cleanupTestData() {
        // Note: DynamoDB Enhanced Client repositories don't have findAll()
        // Cleanup is handled by TestContainers recreating fresh database for each test class
    }

    /**
     * Create a test user and return JWT token for authentication
     */
    protected String createUserAndGetToken(String phoneNumber, String username, String displayName, String password) {
        String encryptedPassword = passwordService.encryptPassword(password);
        User user = new User(phoneNumber, username, displayName, encryptedPassword);
        user.setId(UUID.randomUUID());
        User savedUser = userRepository.save(user);
        return jwtService.generateToken(savedUser.getId().toString());
    }

    /**
     * Create a test user with default values and return JWT token
     */
    protected String createDefaultUserAndGetToken() {
        return createUserAndGetToken("+1234567890", "testuser", "Test User", "password123");
    }

    /**
     * Create a second test user for multi-user scenarios
     */
    protected String createSecondUserAndGetToken() {
        return createUserAndGetToken("+0987654321", "testuser2", "Test User 2", "password456");
    }

    /**
     * Get user by phone number for test assertions
     */
    protected User getUserByPhoneNumber(String phoneNumber) {
        return userRepository.findByPhoneNumber(phoneNumber).orElse(null);
    }

    /**
     * Generate Authorization header with Bearer token
     */
    protected String authHeader(String token) {
        return "Bearer " + token;
    }
}