# Unit Testing Patterns - Quick Reference

**JUnit 5 + Mockito patterns for Spring Boot services. Read this before writing tests.**

---

## Test Structure

### Service Tests
```java
@ExtendWith(MockitoExtension.class)
class ServiceTest {
    @Mock private DependencyService mockDependency;
    @InjectMocks private ServiceUnderTest service;

    @Test
    void methodName_scenario_expectedResult() {  // ← Naming convention
        // Given - setup
        when(mockDependency.method(input)).thenReturn(result);

        // When - execute
        ActualResult actual = service.methodUnderTest(input);

        // Then - verify
        assertThat(actual).isEqualTo(expected);
        verify(mockDependency).method(input);
    }
}
```

### Controller Tests
```java
@WebMvcTest(MyController.class)  // ← Only loads web layer, faster than full Spring context
class MyControllerTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockitoBean private MyService mockService;  // ← @MockitoBean for @WebMvcTest

    @Test
    void endpoint_ValidRequest_ReturnsOk() throws Exception {
        when(mockService.doSomething(any())).thenReturn(response);

        mockMvc.perform(get("/endpoint")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.field").value("expected"));
    }
}
```

### Test Data Builders
**Use existing builders from `testutil` package** - cleaner and reusable:
```java
Hangout hangout = new HangoutTestBuilder()
    .withTitle("Test Event")
    .withHost(userId)
    .build();
// Better than manual object construction!
```

### Organizing Tests with @Nested
Group related tests for better readability:
```java
@ExtendWith(MockitoExtension.class)
class ServiceTest {
    @Nested
    class CreateMethod {
        @Test void validInput_ReturnsSuccess() { ... }
        @Test void invalidInput_ThrowsException() { ... }
    }

    @Nested
    class UpdateMethod {
        @Test void validUpdate_SavesChanges() { ... }
        @Test void unauthorizedUpdate_ThrowsException() { ... }
    }
}
```

---

## Critical Pitfalls (Will Cause Test Failures)

### ❌ MockedStatic in @BeforeEach
```java
// WRONG - Mock closes immediately, causes NPE
@BeforeEach
void setUp() {
    try (MockedStatic<Twilio> mock = mockStatic(Twilio.class)) { ... }
} // ← Closed before tests run!

// CORRECT - Use in test method with try-with-resources
@Test
void test() {
    try (MockedStatic<Twilio> mock = mockStatic(Twilio.class)) {
        // Mock config and test here
    } // ← Closed after test
}
```

### ❌ Loops with MockedStatic
```java
// WRONG - Causes JUnit executionError
int[] codes = {400, 500, 503};
for (int code : codes) {
    try (MockedStatic<Api> mock = ...) { ... } // FAILS
}

// CORRECT - Separate test methods
@Test void test400() { try (MockedStatic<Api> mock = ...) { ... } }
@Test void test500() { try (MockedStatic<Api> mock = ...) { ... } }
@Test void test503() { try (MockedStatic<Api> mock = ...) { ... } }
```

### ❌ Builder Pattern - Chaining in One Line
```java
// WRONG - Type mismatch error
when(Verification.creator(sid, phone, "sms").create())
    .thenReturn(mockVerification);  // Error: creator() returns Builder, not Verification

// CORRECT - Mock each step separately
// Step 1: Import nested builder classes explicitly!
import com.twilio.rest.verify.v2.service.VerificationCreator; // Must import

@Mock private VerificationCreator mockCreator;
@Mock private Verification mockVerification;

try (MockedStatic<Verification> mock = mockStatic(Verification.class)) {
    // Step 2: Mock static factory returning builder
    mock.when(() -> Verification.creator(sid, phone, "sms"))
        .thenReturn(mockCreator);

    // Step 3: Mock builder methods returning builder
    when(mockCreator.setOption(val)).thenReturn(mockCreator);

    // Step 4: Mock final create() returning object
    when(mockCreator.create()).thenReturn(mockVerification);
}
```

---

## Common Patterns

### ArgumentCaptor (Verify Exact Arguments)
```java
ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
verify(mockRepo).save(captor.capture());
assertThat(captor.getValue().getStatus()).isEqualTo(ACTIVE);
```

### Exception Mocking
```java
when(mockService.method(input))
    .thenThrow(new RuntimeException("error"));
```

---

## Test Coverage Checklist

For each public method, test:
- ✅ Happy path (valid input → expected output)
- ✅ Null/empty input handling
- ✅ Exceptions from dependencies
- ✅ Business logic (status changes, authorization)
- ✅ Delegation (verify calls to dependencies with correct args)

---

## Key Principles

1. **Test delegation, not implementation** - Verify service calls dependencies correctly, not how it works internally
2. **Test business logic** - Status changes, authorization rules, state transitions
3. **Keep tests isolated** - No shared mutable state between tests
4. **Follow Given/When/Then** - Clear test structure

---

**Remember**: Import nested builder classes explicitly, use MockedStatic in test methods only, no loops with MockedStatic.
