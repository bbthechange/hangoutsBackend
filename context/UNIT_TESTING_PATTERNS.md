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

## Repository Test File Organization

**HangoutRepositoryImpl tests are split by feature domain for focused testing:**

| Test File | Coverage | Test Count | Token Size |
|-----------|----------|------------|------------|
| `HangoutRepositoryPollsTest` | Poll creation, options, votes, deletion transactions | 6 tests | ~3k tokens |
| `HangoutRepositoryCarpoolingTest` | Cars, riders, needs-ride requests | 14 tests | ~5k tokens |
| `HangoutRepositoryAttributesTest` | Generic attribute CRUD operations | 8 tests | ~3k tokens |
| `HangoutRepositorySeriesTest` | Series queries via SeriesIndex GSI | 3 tests | ~2k tokens |
| `HangoutRepositoryPointersTest` | Pointer batch retrieval, partial results handling | 5 tests | ~3k tokens |
| `HangoutRepositoryDeserializationTest` | Item type deserialization, SK pattern fallback | 7 tests | ~3k tokens |
| `HangoutRepositoryDetailDataTest` | Complete hangout detail retrieval | 3 tests | ~2k tokens |
| `HangoutRepositoryCreationTest` | Hangout creation, atomic operations with pointers/attributes | 11 tests | ~4k tokens |
| `HangoutRepositoryPaginationTest` | All pagination queries (upcoming, past, future, in-progress) | 33 tests | ~8k tokens |

**Common test setup:** All test classes extend `HangoutRepositoryTestBase` for shared mocking configuration.

### Quick Lookup for Agents

**Adding poll feature?** → Read `HangoutRepositoryPollsTest.java` only (~3k tokens)
**Modifying carpooling?** → Read `HangoutRepositoryCarpoolingTest.java` only (~5k tokens)
**Working on pagination?** → Read `HangoutRepositoryPaginationTest.java` only (~8k tokens)
**Adding attributes?** → Read `HangoutRepositoryAttributesTest.java` only (~3k tokens)
**Changing creation logic?** → Read `HangoutRepositoryCreationTest.java` only (~4k tokens)

**Don't grep** - Just look up the feature in the table above and go directly to the file.

**Efficiency gain:** 70-90% token reduction vs reading the original 31k token monolithic test file.

---

## Service Test File Organization

**HangoutServiceImpl tests are split by feature domain for focused testing:**

| Test File | Coverage | Test Count | Token Size |
|-----------|----------|------------|------------|
| `HangoutServiceCreationTest` | Hangout creation with fuzzy/exact time, attributes, polls, validation | 18 tests | ~6k tokens |
| `HangoutServiceUpdateTest` | Title, time, description, visibility updates, series integration | 12 tests | ~4k tokens |
| `HangoutServiceDeletionTest` | Deletion with series handling, authorization, group updates | 5 tests | ~2k tokens |
| `HangoutServiceInterestLevelTest` | Setting/removing interest levels, denormalization, authorization | 14 tests | ~5k tokens |
| `HangoutServiceAttributeTest` | Create/update/delete attributes with pointer propagation | 7 tests | ~3k tokens |
| `HangoutServiceGroupAssociationTest` | Associate/disassociate groups, timestamp handling | 5 tests | ~2k tokens |
| `HangoutServiceRetrievalTest` | Get hangout details, user hangouts, needs ride data, time formatting | 11 tests | ~4k tokens |
| `HangoutServicePointerUpdateTest` | Optimistic locking retries, pointer resync (@Nested class) | 8 tests | ~3k tokens |
| `HangoutServiceGroupLastModifiedTest` | Group lastModified timestamp updates (@Nested class) | 7 tests | ~3k tokens |

**Common test setup:** All test classes extend `HangoutServiceTestBase` for shared mocking and helper methods.

### Quick Lookup for Agents

**Creating hangouts?** → Read `HangoutServiceCreationTest.java` only (~6k tokens)
**Updating hangouts?** → Read `HangoutServiceUpdateTest.java` only (~4k tokens)
**Working on interest levels?** → Read `HangoutServiceInterestLevelTest.java` only (~5k tokens)
**Managing attributes?** → Read `HangoutServiceAttributeTest.java` only (~3k tokens)
**Retrieving hangout data?** → Read `HangoutServiceRetrievalTest.java` only (~4k tokens)
**Pointer updates with retries?** → Read `HangoutServicePointerUpdateTest.java` only (~3k tokens)

**Don't grep** - Just look up the feature in the table above and go directly to the file.

**Efficiency gain:** 75-85% token reduction vs reading the original 15k token monolithic test file.

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
