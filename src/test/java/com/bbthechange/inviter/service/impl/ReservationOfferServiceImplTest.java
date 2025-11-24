package com.bbthechange.inviter.service.impl;

import com.bbthechange.inviter.dto.*;
import com.bbthechange.inviter.exception.*;
import com.bbthechange.inviter.model.*;
import com.bbthechange.inviter.repository.ParticipationRepository;
import com.bbthechange.inviter.repository.ReservationOfferRepository;
import com.bbthechange.inviter.service.HangoutService;
import com.bbthechange.inviter.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.math.BigDecimal;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ReservationOfferServiceImpl.
 * Tests CRUD operations, complete offers, claim/unclaim spot transactions.
 */
@ExtendWith(MockitoExtension.class)
class ReservationOfferServiceImplTest {

    @Mock
    private ReservationOfferRepository offerRepository;

    @Mock
    private ParticipationRepository participationRepository;

    @Mock
    private HangoutService hangoutService;

    @Mock
    private UserService userService;

    @Mock
    private DynamoDbClient dynamoDbClient;

    @InjectMocks
    private ReservationOfferServiceImpl service;

    private String hangoutId;
    private String userId;
    private String offerId;
    private User testUser;

    @BeforeEach
    void setUp() {
        hangoutId = UUID.randomUUID().toString();
        userId = UUID.randomUUID().toString();
        offerId = UUID.randomUUID().toString();

        testUser = new User();
        testUser.setId(UUID.fromString(userId));
        testUser.setDisplayName("Test User");
        testUser.setMainImagePath("path/to/image.jpg");
    }

    @Nested
    class CreateOfferTests {

        @Test
        void createOffer_ValidRequest_CreatesOfferWithDefaults() {
            // Given
            CreateReservationOfferRequest request = new CreateReservationOfferRequest();
            request.setType(OfferType.TICKET);
            // No status specified - should default to COLLECTING

            ReservationOffer savedOffer = new ReservationOffer(hangoutId, offerId, userId, OfferType.TICKET);

            when(userService.getUserById(UUID.fromString(userId))).thenReturn(Optional.of(testUser));
            when(offerRepository.save(any(ReservationOffer.class))).thenReturn(savedOffer);

            // When
            ReservationOfferDTO result = service.createOffer(hangoutId, request, userId);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getStatus()).isEqualTo(OfferStatus.COLLECTING);
            assertThat(result.getClaimedSpots()).isEqualTo(0);
            assertThat(result.getDisplayName()).isEqualTo("Test User");
            assertThat(result.getMainImagePath()).isEqualTo("path/to/image.jpg");

            verify(hangoutService).verifyUserCanAccessHangout(hangoutId, userId);
            verify(offerRepository).save(any(ReservationOffer.class));
        }

        @Test
        void createOffer_UnauthorizedUser_ThrowsUnauthorizedException() {
            // Given
            CreateReservationOfferRequest request = new CreateReservationOfferRequest();
            request.setType(OfferType.TICKET);

            doThrow(new UnauthorizedException("Unauthorized"))
                    .when(hangoutService).verifyUserCanAccessHangout(hangoutId, userId);

            // When/Then
            assertThatThrownBy(() -> service.createOffer(hangoutId, request, userId))
                    .isInstanceOf(UnauthorizedException.class)
                    .hasMessageContaining("Unauthorized");

            verify(hangoutService).verifyUserCanAccessHangout(hangoutId, userId);
            verify(offerRepository, never()).save(any());
        }
    }

    @Nested
    class GetOffersTests {

        @Test
        void getOffers_MultipleOffers_ReturnsDenormalizedList() {
            // Given
            String userId2 = UUID.randomUUID().toString();
            User testUser2 = new User();
            testUser2.setId(UUID.fromString(userId2));
            testUser2.setDisplayName("User Two");
            testUser2.setMainImagePath("path/to/image2.jpg");

            ReservationOffer offer1 = new ReservationOffer(hangoutId, offerId, userId, OfferType.TICKET);
            ReservationOffer offer2 = new ReservationOffer(hangoutId, UUID.randomUUID().toString(), userId2, OfferType.RESERVATION);

            when(offerRepository.findByHangoutId(hangoutId)).thenReturn(Arrays.asList(offer1, offer2));
            when(userService.getUserById(UUID.fromString(userId))).thenReturn(Optional.of(testUser));
            when(userService.getUserById(UUID.fromString(userId2))).thenReturn(Optional.of(testUser2));

            // When
            List<ReservationOfferDTO> result = service.getOffers(hangoutId, userId);

            // Then
            assertThat(result).hasSize(2);
            assertThat(result.get(0).getDisplayName()).isEqualTo("Test User");
            assertThat(result.get(0).getMainImagePath()).isEqualTo("path/to/image.jpg");
            assertThat(result.get(1).getDisplayName()).isEqualTo("User Two");
            assertThat(result.get(1).getMainImagePath()).isEqualTo("path/to/image2.jpg");

            verify(hangoutService).verifyUserCanAccessHangout(hangoutId, userId);
            verify(userService).getUserById(UUID.fromString(userId));
            verify(userService).getUserById(UUID.fromString(userId2));
        }
    }

    @Nested
    class GetOfferTests {

        @Test
        void getOffer_ExistingOffer_ReturnsDenormalizedDTO() {
            // Given
            ReservationOffer offer = new ReservationOffer(hangoutId, offerId, userId, OfferType.TICKET);

            when(offerRepository.findById(hangoutId, offerId)).thenReturn(Optional.of(offer));
            when(userService.getUserById(UUID.fromString(userId))).thenReturn(Optional.of(testUser));

            // When
            ReservationOfferDTO result = service.getOffer(hangoutId, offerId, userId);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getDisplayName()).isEqualTo("Test User");
            assertThat(result.getMainImagePath()).isEqualTo("path/to/image.jpg");

            verify(hangoutService).verifyUserCanAccessHangout(hangoutId, userId);
            verify(userService).getUserById(UUID.fromString(userId));
        }

        @Test
        void getOffer_NonExistentOffer_ThrowsReservationOfferNotFoundException() {
            // Given
            when(offerRepository.findById(hangoutId, offerId)).thenReturn(Optional.empty());

            // When/Then
            assertThatThrownBy(() -> service.getOffer(hangoutId, offerId, userId))
                    .isInstanceOf(ReservationOfferNotFoundException.class)
                    .hasMessageContaining("Reservation offer not found");

            verify(hangoutService).verifyUserCanAccessHangout(hangoutId, userId);
        }
    }

    @Nested
    class UpdateOfferTests {

        @Test
        void updateOffer_NoUpdates_ThrowsIllegalArgumentException() {
            // Given
            UpdateReservationOfferRequest request = new UpdateReservationOfferRequest();
            // All fields are null - hasUpdates() will return false

            // When/Then
            assertThatThrownBy(() -> service.updateOffer(hangoutId, offerId, request, userId))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("No updates provided");

            verify(hangoutService, never()).verifyUserCanAccessHangout(any(), any());
        }

        @Test
        void updateOffer_ValidUpdates_AppliesOnlyProvidedFields() {
            // Given
            ReservationOffer existingOffer = new ReservationOffer(hangoutId, offerId, userId, OfferType.TICKET);
            existingOffer.setSection("Section A");
            existingOffer.setCapacity(10);

            UpdateReservationOfferRequest request = new UpdateReservationOfferRequest();
            request.setSection("Section B"); // Only update section, leave capacity unchanged

            ReservationOffer updatedOffer = new ReservationOffer(hangoutId, offerId, userId, OfferType.TICKET);
            updatedOffer.setSection("Section B");
            updatedOffer.setCapacity(10); // Unchanged

            when(offerRepository.findById(hangoutId, offerId)).thenReturn(Optional.of(existingOffer));
            when(offerRepository.save(any(ReservationOffer.class))).thenReturn(updatedOffer);
            when(userService.getUserById(UUID.fromString(userId))).thenReturn(Optional.of(testUser));

            // When
            ReservationOfferDTO result = service.updateOffer(hangoutId, offerId, request, userId);

            // Then
            ArgumentCaptor<ReservationOffer> captor = ArgumentCaptor.forClass(ReservationOffer.class);
            verify(offerRepository).save(captor.capture());

            ReservationOffer savedOffer = captor.getValue();
            assertThat(savedOffer.getSection()).isEqualTo("Section B");
            assertThat(savedOffer.getCapacity()).isEqualTo(10); // Unchanged

            verify(hangoutService).verifyUserCanAccessHangout(hangoutId, userId);
        }

        @Test
        void updateOffer_AllFields_AppliesAllUpdates() {
            // Given
            ReservationOffer existingOffer = new ReservationOffer(hangoutId, offerId, userId, OfferType.TICKET);

            UpdateReservationOfferRequest request = new UpdateReservationOfferRequest();
            TimeInfo newBuyDate = new TimeInfo();
            newBuyDate.setPeriodGranularity("DAY");
            newBuyDate.setPeriodStart("2025-12-25T15:00:00Z");
            request.setBuyDate(newBuyDate);
            request.setSection("VIP");
            request.setCapacity(20);
            request.setStatus(OfferStatus.CANCELLED);

            when(offerRepository.findById(hangoutId, offerId)).thenReturn(Optional.of(existingOffer));
            when(offerRepository.save(any(ReservationOffer.class))).thenReturn(existingOffer);
            when(userService.getUserById(UUID.fromString(userId))).thenReturn(Optional.of(testUser));

            // When
            service.updateOffer(hangoutId, offerId, request, userId);

            // Then
            ArgumentCaptor<ReservationOffer> captor = ArgumentCaptor.forClass(ReservationOffer.class);
            verify(offerRepository).save(captor.capture());

            ReservationOffer savedOffer = captor.getValue();
            assertThat(savedOffer.getBuyDate()).isEqualTo(newBuyDate);
            assertThat(savedOffer.getSection()).isEqualTo("VIP");
            assertThat(savedOffer.getCapacity()).isEqualTo(20);
            assertThat(savedOffer.getStatus()).isEqualTo(OfferStatus.CANCELLED);
        }

        @Test
        void updateOffer_NonCreatorEditing_AllowsEdit() {
            // Given
            String creatorId = UUID.randomUUID().toString();
            String editorId = UUID.randomUUID().toString();
            User editorUser = new User();
            editorUser.setId(UUID.fromString(creatorId));
            editorUser.setDisplayName("Creator");
            editorUser.setMainImagePath("creator/image.jpg");

            ReservationOffer existingOffer = new ReservationOffer(hangoutId, offerId, creatorId, OfferType.TICKET);

            UpdateReservationOfferRequest request = new UpdateReservationOfferRequest();
            request.setSection("Section C");

            when(offerRepository.findById(hangoutId, offerId)).thenReturn(Optional.of(existingOffer));
            when(offerRepository.save(any(ReservationOffer.class))).thenReturn(existingOffer);
            when(userService.getUserById(UUID.fromString(creatorId))).thenReturn(Optional.of(editorUser));

            // When
            ReservationOfferDTO result = service.updateOffer(hangoutId, offerId, request, editorId);

            // Then - non-creator can edit (any group member can)
            assertThat(result).isNotNull();
            assertThat(result.getDisplayName()).isEqualTo("Creator");
            verify(hangoutService).verifyUserCanAccessHangout(hangoutId, editorId);
        }

        @Test
        void updateOffer_OfferNotFound_ThrowsReservationOfferNotFoundException() {
            // Given
            UpdateReservationOfferRequest request = new UpdateReservationOfferRequest();
            request.setSection("Section D");

            when(offerRepository.findById(hangoutId, offerId)).thenReturn(Optional.empty());

            // When/Then
            assertThatThrownBy(() -> service.updateOffer(hangoutId, offerId, request, userId))
                    .isInstanceOf(ReservationOfferNotFoundException.class)
                    .hasMessageContaining("Reservation offer not found");

            verify(hangoutService).verifyUserCanAccessHangout(hangoutId, userId);
        }

        @Test
        void updateOffer_UserNotFound_ThrowsUserNotFoundException() {
            // Given
            ReservationOffer existingOffer = new ReservationOffer(hangoutId, offerId, userId, OfferType.TICKET);

            UpdateReservationOfferRequest request = new UpdateReservationOfferRequest();
            request.setSection("Section E");

            when(offerRepository.findById(hangoutId, offerId)).thenReturn(Optional.of(existingOffer));
            when(offerRepository.save(any(ReservationOffer.class))).thenReturn(existingOffer);
            when(userService.getUserById(UUID.fromString(userId))).thenReturn(Optional.empty());

            // When/Then
            assertThatThrownBy(() -> service.updateOffer(hangoutId, offerId, request, userId))
                    .isInstanceOf(UserNotFoundException.class)
                    .hasMessageContaining("User not found");
        }

        @Test
        void updateOffer_UnauthorizedUser_ThrowsUnauthorizedException() {
            // Given
            UpdateReservationOfferRequest request = new UpdateReservationOfferRequest();
            request.setSection("Section F");

            doThrow(new UnauthorizedException("Unauthorized"))
                    .when(hangoutService).verifyUserCanAccessHangout(hangoutId, userId);

            // When/Then
            assertThatThrownBy(() -> service.updateOffer(hangoutId, offerId, request, userId))
                    .isInstanceOf(UnauthorizedException.class)
                    .hasMessageContaining("Unauthorized");

            verify(offerRepository, never()).findById(any(), any());
        }
    }

    @Nested
    class DeleteOfferTests {

        @Test
        void deleteOffer_ExistingOffer_CallsRepositoryDelete() {
            // Given - no setup needed, delete is idempotent

            // When
            service.deleteOffer(hangoutId, offerId, userId);

            // Then
            verify(hangoutService).verifyUserCanAccessHangout(hangoutId, userId);
            verify(offerRepository).delete(hangoutId, offerId);
        }
    }

    @Nested
    class CompleteOfferTests {

        @Test
        void completeOffer_ConvertAll_ConvertsAllTicketNeededParticipations() {
            // Given
            ReservationOffer offer = new ReservationOffer(hangoutId, offerId, userId, OfferType.TICKET);

            Participation p1 = new Participation(hangoutId, UUID.randomUUID().toString(), userId, ParticipationType.TICKET_NEEDED);
            p1.setReservationOfferId(offerId);
            Participation p2 = new Participation(hangoutId, UUID.randomUUID().toString(), userId, ParticipationType.TICKET_NEEDED);
            p2.setReservationOfferId(offerId);
            Participation p3 = new Participation(hangoutId, UUID.randomUUID().toString(), userId, ParticipationType.CLAIMED_SPOT);
            p3.setReservationOfferId(offerId);

            CompleteReservationOfferRequest request = new CompleteReservationOfferRequest();
            request.setConvertAll(true);
            request.setTicketCount(2);
            request.setTotalPrice(new BigDecimal("100.00"));

            when(offerRepository.findById(hangoutId, offerId)).thenReturn(Optional.of(offer));
            when(participationRepository.findByOfferId(hangoutId, offerId))
                    .thenReturn(Arrays.asList(p1, p2, p3));
            when(offerRepository.save(any(ReservationOffer.class))).thenReturn(offer);
            when(userService.getUserById(UUID.fromString(userId))).thenReturn(Optional.of(testUser));
            when(dynamoDbClient.transactWriteItems(any(TransactWriteItemsRequest.class)))
                    .thenReturn(TransactWriteItemsResponse.builder().build());

            // When
            ReservationOfferDTO result = service.completeOffer(hangoutId, offerId, request, userId);

            // Then
            verify(hangoutService).verifyUserCanAccessHangout(hangoutId, userId);

            ArgumentCaptor<ReservationOffer> offerCaptor = ArgumentCaptor.forClass(ReservationOffer.class);
            verify(offerRepository).save(offerCaptor.capture());

            ReservationOffer savedOffer = offerCaptor.getValue();
            assertThat(savedOffer.getStatus()).isEqualTo(OfferStatus.COMPLETED);
            assertThat(savedOffer.getCompletedDate()).isNotNull();
            assertThat(savedOffer.getTicketCount()).isEqualTo(2);
            assertThat(savedOffer.getTotalPrice()).isEqualByComparingTo(new BigDecimal("100.00"));

            // Verify transaction called once for 2 TICKET_NEEDED participations
            verify(dynamoDbClient, times(1)).transactWriteItems(any(TransactWriteItemsRequest.class));
        }

        @Test
        void completeOffer_ConvertSpecificIds_ConvertsOnlySpecified() {
            // Given
            ReservationOffer offer = new ReservationOffer(hangoutId, offerId, userId, OfferType.TICKET);

            String p1Id = UUID.randomUUID().toString();
            String p2Id = UUID.randomUUID().toString();

            Participation p1 = new Participation(hangoutId, p1Id, userId, ParticipationType.TICKET_NEEDED);
            p1.setReservationOfferId(offerId);
            Participation p2 = new Participation(hangoutId, p2Id, userId, ParticipationType.TICKET_NEEDED);
            p2.setReservationOfferId(offerId);

            CompleteReservationOfferRequest request = new CompleteReservationOfferRequest();
            request.setConvertAll(false);
            request.setParticipationIds(Collections.singletonList(p1Id)); // Only convert p1
            request.setTicketCount(1);
            request.setTotalPrice(new BigDecimal("50.00"));

            when(offerRepository.findById(hangoutId, offerId)).thenReturn(Optional.of(offer));
            when(participationRepository.findById(hangoutId, p1Id)).thenReturn(Optional.of(p1));
            when(offerRepository.save(any(ReservationOffer.class))).thenReturn(offer);
            when(userService.getUserById(UUID.fromString(userId))).thenReturn(Optional.of(testUser));
            when(dynamoDbClient.transactWriteItems(any(TransactWriteItemsRequest.class)))
                    .thenReturn(TransactWriteItemsResponse.builder().build());

            // When
            service.completeOffer(hangoutId, offerId, request, userId);

            // Then
            verify(participationRepository).findById(hangoutId, p1Id);
            verify(dynamoDbClient, times(1)).transactWriteItems(any(TransactWriteItemsRequest.class));
        }

        @Test
        void completeOffer_ConvertAllFalseWithoutIds_ThrowsValidationException() {
            // Given
            ReservationOffer offer = new ReservationOffer(hangoutId, offerId, userId, OfferType.TICKET);

            CompleteReservationOfferRequest request = new CompleteReservationOfferRequest();
            request.setConvertAll(false);
            request.setParticipationIds(null); // Missing required field

            when(offerRepository.findById(hangoutId, offerId)).thenReturn(Optional.of(offer));

            // When/Then
            assertThatThrownBy(() -> service.completeOffer(hangoutId, offerId, request, userId))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("participationIds required when convertAll=false");
        }

        @Test
        void completeOffer_LargeParticipationList_BatchesCorrectly() {
            // Given
            ReservationOffer offer = new ReservationOffer(hangoutId, offerId, userId, OfferType.TICKET);

            // Create 150 TICKET_NEEDED participations
            List<Participation> participations = new ArrayList<>();
            for (int i = 0; i < 150; i++) {
                Participation p = new Participation(hangoutId, UUID.randomUUID().toString(), userId, ParticipationType.TICKET_NEEDED);
                p.setReservationOfferId(offerId);
                participations.add(p);
            }

            CompleteReservationOfferRequest request = new CompleteReservationOfferRequest();
            request.setConvertAll(true);
            request.setTicketCount(150);
            request.setTotalPrice(new BigDecimal("1500.00"));

            when(offerRepository.findById(hangoutId, offerId)).thenReturn(Optional.of(offer));
            when(participationRepository.findByOfferId(hangoutId, offerId)).thenReturn(participations);
            when(offerRepository.save(any(ReservationOffer.class))).thenReturn(offer);
            when(userService.getUserById(UUID.fromString(userId))).thenReturn(Optional.of(testUser));
            when(dynamoDbClient.transactWriteItems(any(TransactWriteItemsRequest.class)))
                    .thenReturn(TransactWriteItemsResponse.builder().build());

            // When
            service.completeOffer(hangoutId, offerId, request, userId);

            // Then
            // 150 participations should be batched into 2 transactions: 90 + 60
            verify(dynamoDbClient, times(2)).transactWriteItems(any(TransactWriteItemsRequest.class));
        }

        @Test
        void completeOffer_UpdatesOfferStatus_SetsCompletedFields() {
            // Given
            ReservationOffer offer = new ReservationOffer(hangoutId, offerId, userId, OfferType.TICKET);

            CompleteReservationOfferRequest request = new CompleteReservationOfferRequest();
            request.setConvertAll(true);
            request.setTicketCount(5);
            request.setTotalPrice(new BigDecimal("250.00"));

            when(offerRepository.findById(hangoutId, offerId)).thenReturn(Optional.of(offer));
            when(participationRepository.findByOfferId(hangoutId, offerId)).thenReturn(Collections.emptyList());
            when(offerRepository.save(any(ReservationOffer.class))).thenReturn(offer);
            when(userService.getUserById(UUID.fromString(userId))).thenReturn(Optional.of(testUser));

            // When
            service.completeOffer(hangoutId, offerId, request, userId);

            // Then
            ArgumentCaptor<ReservationOffer> captor = ArgumentCaptor.forClass(ReservationOffer.class);
            verify(offerRepository).save(captor.capture());

            ReservationOffer savedOffer = captor.getValue();
            assertThat(savedOffer.getStatus()).isEqualTo(OfferStatus.COMPLETED);
            assertThat(savedOffer.getCompletedDate()).isNotNull();
            assertThat(savedOffer.getTicketCount()).isEqualTo(5);
            assertThat(savedOffer.getTotalPrice()).isEqualByComparingTo(new BigDecimal("250.00"));
        }
    }

    @Nested
    class ClaimSpotTests {

        @Test
        void claimSpot_AvailableCapacity_CreatesParticipationAndIncrementsClaimedSpots() {
            // Given
            ReservationOffer offer = new ReservationOffer(hangoutId, offerId, userId, OfferType.TICKET);
            offer.setCapacity(10);
            offer.setClaimedSpots(5);
            offer.setVersion(1L);

            when(offerRepository.findById(hangoutId, offerId)).thenReturn(Optional.of(offer));
            when(userService.getUserById(UUID.fromString(userId))).thenReturn(Optional.of(testUser));
            when(dynamoDbClient.transactWriteItems(any(TransactWriteItemsRequest.class)))
                    .thenReturn(TransactWriteItemsResponse.builder().build());

            // When
            ParticipationDTO result = service.claimSpot(hangoutId, offerId, userId);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getType()).isEqualTo(ParticipationType.CLAIMED_SPOT);
            assertThat(result.getReservationOfferId()).isEqualTo(offerId);
            assertThat(result.getDisplayName()).isEqualTo("Test User");

            verify(hangoutService).verifyUserCanAccessHangout(hangoutId, userId);
            verify(dynamoDbClient).transactWriteItems(any(TransactWriteItemsRequest.class));
        }

        @Test
        void claimSpot_FullCapacity_ThrowsCapacityExceededException() {
            // Given
            ReservationOffer offer = new ReservationOffer(hangoutId, offerId, userId, OfferType.TICKET);
            offer.setCapacity(10);
            offer.setClaimedSpots(10); // Full

            when(offerRepository.findById(hangoutId, offerId)).thenReturn(Optional.of(offer));

            // When/Then
            assertThatThrownBy(() -> service.claimSpot(hangoutId, offerId, userId))
                    .isInstanceOf(CapacityExceededException.class)
                    .hasMessageContaining("This reservation is full")
                    .hasMessageContaining("10/10 spots claimed");

            verify(hangoutService).verifyUserCanAccessHangout(hangoutId, userId);
            verify(dynamoDbClient, never()).transactWriteItems(any(TransactWriteItemsRequest.class));
        }

        @Test
        void claimSpot_UnlimitedCapacity_ThrowsIllegalOperationException() {
            // Given
            ReservationOffer offer = new ReservationOffer(hangoutId, offerId, userId, OfferType.TICKET);
            offer.setCapacity(null); // Unlimited

            when(offerRepository.findById(hangoutId, offerId)).thenReturn(Optional.of(offer));

            // When/Then
            assertThatThrownBy(() -> service.claimSpot(hangoutId, offerId, userId))
                    .isInstanceOf(IllegalOperationException.class)
                    .hasMessageContaining("Cannot claim spot on offer with unlimited capacity")
                    .hasMessageContaining("Create a participation directly instead");

            verify(hangoutService).verifyUserCanAccessHangout(hangoutId, userId);
        }

        @Test
        void claimSpot_VersionConflict_RetriesUntilSuccess() {
            // Given
            ReservationOffer offer = new ReservationOffer(hangoutId, offerId, userId, OfferType.TICKET);
            offer.setCapacity(10);
            offer.setClaimedSpots(5);
            offer.setVersion(1L);

            when(offerRepository.findById(hangoutId, offerId)).thenReturn(Optional.of(offer));
            when(userService.getUserById(UUID.fromString(userId))).thenReturn(Optional.of(testUser));

            // First attempt fails with version conflict, second succeeds
            when(dynamoDbClient.transactWriteItems(any(TransactWriteItemsRequest.class)))
                    .thenThrow(TransactionCanceledException.builder()
                            .message("ConditionalCheckFailed")
                            .build())
                    .thenReturn(TransactWriteItemsResponse.builder().build());

            // When
            ParticipationDTO result = service.claimSpot(hangoutId, offerId, userId);

            // Then
            assertThat(result).isNotNull();
            verify(dynamoDbClient, times(2)).transactWriteItems(any(TransactWriteItemsRequest.class));
            // 3 calls: attempt 1 fetch, catch block refetch to check capacity, attempt 2 fetch
            verify(offerRepository, times(3)).findById(hangoutId, offerId);
        }

        @Test
        void claimSpot_VersionConflictThenCapacityFull_ThrowsCapacityExceeded() {
            // Given
            ReservationOffer offer = new ReservationOffer(hangoutId, offerId, userId, OfferType.TICKET);
            offer.setCapacity(10);
            offer.setClaimedSpots(5);
            offer.setVersion(1L);

            ReservationOffer fullOffer = new ReservationOffer(hangoutId, offerId, userId, OfferType.TICKET);
            fullOffer.setCapacity(10);
            fullOffer.setClaimedSpots(10); // Now full
            fullOffer.setVersion(2L);

            when(offerRepository.findById(hangoutId, offerId))
                    .thenReturn(Optional.of(offer))
                    .thenReturn(Optional.of(fullOffer));

            when(dynamoDbClient.transactWriteItems(any(TransactWriteItemsRequest.class)))
                    .thenThrow(TransactionCanceledException.builder()
                            .message("ConditionalCheckFailed")
                            .build());

            // When/Then
            assertThatThrownBy(() -> service.claimSpot(hangoutId, offerId, userId))
                    .isInstanceOf(CapacityExceededException.class)
                    .hasMessageContaining("This reservation is full")
                    .hasMessageContaining("10/10 spots claimed");

            verify(dynamoDbClient, times(1)).transactWriteItems(any(TransactWriteItemsRequest.class));
        }

        @Test
        void claimSpot_MaxRetriesExceeded_ThrowsRuntimeException() {
            // Given
            ReservationOffer offer = new ReservationOffer(hangoutId, offerId, userId, OfferType.TICKET);
            offer.setCapacity(10);
            offer.setClaimedSpots(5);
            offer.setVersion(1L);

            when(offerRepository.findById(hangoutId, offerId)).thenReturn(Optional.of(offer));

            // All attempts fail with version conflict
            when(dynamoDbClient.transactWriteItems(any(TransactWriteItemsRequest.class)))
                    .thenThrow(TransactionCanceledException.builder()
                            .message("ConditionalCheckFailed")
                            .build());

            // When/Then
            assertThatThrownBy(() -> service.claimSpot(hangoutId, offerId, userId))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Failed to claim spot after 5 attempts")
                    .hasMessageContaining("concurrent modifications");

            verify(dynamoDbClient, times(5)).transactWriteItems(any(TransactWriteItemsRequest.class));
        }
    }

    @Nested
    class UnclaimSpotTests {

        @Test
        void unclaimSpot_ExistingClaimedSpot_DeletesAndDecrementsAtomically() {
            // Given
            ReservationOffer offer = new ReservationOffer(hangoutId, offerId, userId, OfferType.TICKET);
            offer.setCapacity(10);
            offer.setClaimedSpots(6);
            offer.setVersion(1L);

            Participation claimedSpot = new Participation(hangoutId, UUID.randomUUID().toString(), userId, ParticipationType.CLAIMED_SPOT);
            claimedSpot.setReservationOfferId(offerId);

            when(offerRepository.findById(hangoutId, offerId)).thenReturn(Optional.of(offer));
            when(participationRepository.findByOfferId(hangoutId, offerId))
                    .thenReturn(Collections.singletonList(claimedSpot));
            when(dynamoDbClient.transactWriteItems(any(TransactWriteItemsRequest.class)))
                    .thenReturn(TransactWriteItemsResponse.builder().build());

            // When
            service.unclaimSpot(hangoutId, offerId, userId);

            // Then
            verify(hangoutService).verifyUserCanAccessHangout(hangoutId, userId);
            verify(dynamoDbClient).transactWriteItems(any(TransactWriteItemsRequest.class));
        }

        @Test
        void unclaimSpot_NoClaimedSpot_ThrowsNotFoundException() {
            // Given
            ReservationOffer offer = new ReservationOffer(hangoutId, offerId, userId, OfferType.TICKET);

            when(offerRepository.findById(hangoutId, offerId)).thenReturn(Optional.of(offer));
            when(participationRepository.findByOfferId(hangoutId, offerId))
                    .thenReturn(Collections.emptyList()); // No claimed spot found

            // When/Then
            assertThatThrownBy(() -> service.unclaimSpot(hangoutId, offerId, userId))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessageContaining("You have not claimed a spot in this reservation");

            verify(hangoutService).verifyUserCanAccessHangout(hangoutId, userId);
            verify(dynamoDbClient, never()).transactWriteItems(any(TransactWriteItemsRequest.class));
        }

        @Test
        void unclaimSpot_VersionConflict_RetriesUntilSuccess() {
            // Given
            ReservationOffer offer = new ReservationOffer(hangoutId, offerId, userId, OfferType.TICKET);
            offer.setCapacity(10);
            offer.setClaimedSpots(6);
            offer.setVersion(1L);

            Participation claimedSpot = new Participation(hangoutId, UUID.randomUUID().toString(), userId, ParticipationType.CLAIMED_SPOT);
            claimedSpot.setReservationOfferId(offerId);

            when(offerRepository.findById(hangoutId, offerId)).thenReturn(Optional.of(offer));
            when(participationRepository.findByOfferId(hangoutId, offerId))
                    .thenReturn(Collections.singletonList(claimedSpot));

            // First attempt fails, second succeeds
            when(dynamoDbClient.transactWriteItems(any(TransactWriteItemsRequest.class)))
                    .thenThrow(TransactionCanceledException.builder()
                            .message("ConditionalCheckFailed")
                            .build())
                    .thenReturn(TransactWriteItemsResponse.builder().build());

            // When
            service.unclaimSpot(hangoutId, offerId, userId);

            // Then
            verify(dynamoDbClient, times(2)).transactWriteItems(any(TransactWriteItemsRequest.class));
            verify(offerRepository, times(2)).findById(hangoutId, offerId);
        }

        @Test
        void unclaimSpot_MaxRetriesExceeded_ThrowsRuntimeException() {
            // Given
            ReservationOffer offer = new ReservationOffer(hangoutId, offerId, userId, OfferType.TICKET);
            offer.setCapacity(10);
            offer.setClaimedSpots(6);
            offer.setVersion(1L);

            Participation claimedSpot = new Participation(hangoutId, UUID.randomUUID().toString(), userId, ParticipationType.CLAIMED_SPOT);
            claimedSpot.setReservationOfferId(offerId);

            when(offerRepository.findById(hangoutId, offerId)).thenReturn(Optional.of(offer));
            when(participationRepository.findByOfferId(hangoutId, offerId))
                    .thenReturn(Collections.singletonList(claimedSpot));

            // All attempts fail
            when(dynamoDbClient.transactWriteItems(any(TransactWriteItemsRequest.class)))
                    .thenThrow(TransactionCanceledException.builder()
                            .message("ConditionalCheckFailed")
                            .build());

            // When/Then
            assertThatThrownBy(() -> service.unclaimSpot(hangoutId, offerId, userId))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Failed to unclaim spot after 5 attempts")
                    .hasMessageContaining("concurrent modifications");

            verify(dynamoDbClient, times(5)).transactWriteItems(any(TransactWriteItemsRequest.class));
        }

        @Test
        void unclaimSpot_OfferNotFound_ThrowsReservationOfferNotFoundException() {
            // Given
            when(offerRepository.findById(hangoutId, offerId)).thenReturn(Optional.empty());

            // When/Then
            assertThatThrownBy(() -> service.unclaimSpot(hangoutId, offerId, userId))
                    .isInstanceOf(ReservationOfferNotFoundException.class)
                    .hasMessageContaining("Reservation offer not found");

            verify(hangoutService).verifyUserCanAccessHangout(hangoutId, userId);
        }

        @Test
        void unclaimSpot_UnauthorizedUser_ThrowsUnauthorizedException() {
            // Given
            doThrow(new UnauthorizedException("Unauthorized"))
                    .when(hangoutService).verifyUserCanAccessHangout(hangoutId, userId);

            // When/Then
            assertThatThrownBy(() -> service.unclaimSpot(hangoutId, offerId, userId))
                    .isInstanceOf(UnauthorizedException.class)
                    .hasMessageContaining("Unauthorized");

            verify(offerRepository, never()).findById(any(), any());
        }

        @Test
        void unclaimSpot_MultipleParticipations_FindsCorrectClaimedSpot() {
            // Given
            ReservationOffer offer = new ReservationOffer(hangoutId, offerId, userId, OfferType.TICKET);
            offer.setCapacity(10);
            offer.setClaimedSpots(3);
            offer.setVersion(1L);

            String otherUserId = UUID.randomUUID().toString();

            // Multiple participations: TICKET_NEEDED, other user's CLAIMED_SPOT, and our user's CLAIMED_SPOT
            Participation ticketNeeded = new Participation(hangoutId, UUID.randomUUID().toString(), userId, ParticipationType.TICKET_NEEDED);
            ticketNeeded.setReservationOfferId(offerId);

            Participation otherClaimedSpot = new Participation(hangoutId, UUID.randomUUID().toString(), otherUserId, ParticipationType.CLAIMED_SPOT);
            otherClaimedSpot.setReservationOfferId(offerId);

            Participation ourClaimedSpot = new Participation(hangoutId, UUID.randomUUID().toString(), userId, ParticipationType.CLAIMED_SPOT);
            ourClaimedSpot.setReservationOfferId(offerId);

            when(offerRepository.findById(hangoutId, offerId)).thenReturn(Optional.of(offer));
            when(participationRepository.findByOfferId(hangoutId, offerId))
                    .thenReturn(Arrays.asList(ticketNeeded, otherClaimedSpot, ourClaimedSpot));
            when(dynamoDbClient.transactWriteItems(any(TransactWriteItemsRequest.class)))
                    .thenReturn(TransactWriteItemsResponse.builder().build());

            // When
            service.unclaimSpot(hangoutId, offerId, userId);

            // Then - successfully found and unclaimed the correct participation
            verify(hangoutService).verifyUserCanAccessHangout(hangoutId, userId);
            verify(dynamoDbClient).transactWriteItems(any(TransactWriteItemsRequest.class));
        }
    }
}
