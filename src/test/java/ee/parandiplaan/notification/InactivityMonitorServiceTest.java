package ee.parandiplaan.notification;

import ee.parandiplaan.trust.HandoverRequest;
import ee.parandiplaan.trust.HandoverRequestRepository;
import ee.parandiplaan.trust.TrustedContact;
import ee.parandiplaan.trust.TrustedContactRepository;
import ee.parandiplaan.user.User;
import ee.parandiplaan.user.UserRepository;
import ee.parandiplaan.vault.SharedVaultService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InactivityMonitorServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private TrustedContactRepository contactRepository;
    @Mock private InactivityCheckRepository checkRepository;
    @Mock private HandoverRequestRepository handoverRepository;
    @Mock private EmailService emailService;
    @Mock private SmsService smsService;
    @Mock private SharedVaultService sharedVaultService;

    @InjectMocks
    private InactivityMonitorService service;

    private User user;
    private TrustedContact inactivityContact;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "appUrl", "http://localhost:8080");

        user = new User();
        ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
        user.setEmail("test@example.com");
        user.setFullName("Test User");
        user.setNotifyInactivityWarnings(true);
        user.setNotifySms(false);
        user.setCreatedAt(Instant.now().minus(200, ChronoUnit.DAYS));

        inactivityContact = new TrustedContact();
        ReflectionTestUtils.setField(inactivityContact, "id", UUID.randomUUID());
        inactivityContact.setUser(user);
        inactivityContact.setFullName("Contact Person");
        inactivityContact.setEmail("contact@example.com");
        inactivityContact.setActivationMode("INACTIVITY");
        inactivityContact.setInactivityDays(90);
        inactivityContact.setInviteAccepted(true);
        inactivityContact.setAccessLevel("FULL");
    }

    // --- WARNING_1 stage: 14 days before deadline (day 76-82 of 90) ---

    @Test
    void checkInactivity_warning1_sendsEmailWhenInactiveEnough() {
        // User inactive for 76 days (90 - 14 = 76 => triggers WARNING_1)
        user.setLastLoginAt(Instant.now().minus(76, ChronoUnit.DAYS));

        when(userRepository.findAll()).thenReturn(List.of(user));
        when(contactRepository.findByUserIdOrderByCreatedAtDesc(user.getId()))
                .thenReturn(List.of(inactivityContact));
        when(checkRepository.findUnrespondedByUserAndType(user.getId(), "WARNING_1"))
                .thenReturn(List.of()); // No existing warning

        InactivityCheck savedCheck = new InactivityCheck();
        savedCheck.setUser(user);
        savedCheck.setCheckType("WARNING_1");
        ReflectionTestUtils.setField(savedCheck, "responseToken", UUID.randomUUID());
        when(checkRepository.save(any(InactivityCheck.class))).thenReturn(savedCheck);

        service.checkInactivity();

        verify(checkRepository).save(any(InactivityCheck.class));
        verify(emailService).sendEmail(eq("test@example.com"), anyString(), anyString());
    }

    @Test
    void checkInactivity_warning1_skipsWhenAlreadySent() {
        user.setLastLoginAt(Instant.now().minus(78, ChronoUnit.DAYS));

        InactivityCheck existingCheck = new InactivityCheck();
        existingCheck.setUser(user);
        existingCheck.setCheckType("WARNING_1");

        when(userRepository.findAll()).thenReturn(List.of(user));
        when(contactRepository.findByUserIdOrderByCreatedAtDesc(user.getId()))
                .thenReturn(List.of(inactivityContact));
        when(checkRepository.findUnrespondedByUserAndType(user.getId(), "WARNING_1"))
                .thenReturn(List.of(existingCheck)); // Already sent

        service.checkInactivity();

        verify(checkRepository, never()).save(any(InactivityCheck.class));
        verify(emailService, never()).sendEmail(anyString(), anyString(), anyString());
    }

    // --- WARNING_2 stage: 7 days before deadline (day 83-89 of 90) ---

    @Test
    void checkInactivity_warning2_sendsEmailAt83Days() {
        user.setLastLoginAt(Instant.now().minus(83, ChronoUnit.DAYS));

        when(userRepository.findAll()).thenReturn(List.of(user));
        when(contactRepository.findByUserIdOrderByCreatedAtDesc(user.getId()))
                .thenReturn(List.of(inactivityContact));
        when(checkRepository.findUnrespondedByUserAndType(user.getId(), "WARNING_2"))
                .thenReturn(List.of());

        InactivityCheck savedCheck = new InactivityCheck();
        savedCheck.setUser(user);
        savedCheck.setCheckType("WARNING_2");
        ReflectionTestUtils.setField(savedCheck, "responseToken", UUID.randomUUID());
        when(checkRepository.save(any(InactivityCheck.class))).thenReturn(savedCheck);

        service.checkInactivity();

        ArgumentCaptor<InactivityCheck> captor = ArgumentCaptor.forClass(InactivityCheck.class);
        verify(checkRepository).save(captor.capture());
        assertThat(captor.getValue().getCheckType()).isEqualTo("WARNING_2");
    }

    // --- FINAL stage: deadline reached (day >= 90) ---

    @Test
    void checkInactivity_final_sendsFinalWarningOnFirstOccurrence() {
        user.setLastLoginAt(Instant.now().minus(90, ChronoUnit.DAYS));

        when(userRepository.findAll()).thenReturn(List.of(user));
        when(contactRepository.findByUserIdOrderByCreatedAtDesc(user.getId()))
                .thenReturn(List.of(inactivityContact));
        when(checkRepository.findUnrespondedByUserAndType(user.getId(), "FINAL"))
                .thenReturn(List.of()); // No existing FINAL

        InactivityCheck savedCheck = new InactivityCheck();
        savedCheck.setUser(user);
        savedCheck.setCheckType("FINAL");
        ReflectionTestUtils.setField(savedCheck, "responseToken", UUID.randomUUID());
        when(checkRepository.save(any(InactivityCheck.class))).thenReturn(savedCheck);

        service.checkInactivity();

        ArgumentCaptor<InactivityCheck> captor = ArgumentCaptor.forClass(InactivityCheck.class);
        verify(checkRepository).save(captor.capture());
        assertThat(captor.getValue().getCheckType()).isEqualTo("FINAL");
        verify(emailService).sendEmail(eq("test@example.com"), anyString(), anyString());
    }

    @Test
    void checkInactivity_autoHandover_triggersAfter48Hours() {
        user.setLastLoginAt(Instant.now().minus(95, ChronoUnit.DAYS));

        InactivityCheck finalCheck = new InactivityCheck();
        finalCheck.setUser(user);
        finalCheck.setCheckType("FINAL");
        // sentAt was 49 hours ago => more than 48h => auto-trigger
        ReflectionTestUtils.setField(finalCheck, "sentAt", Instant.now().minus(49, ChronoUnit.HOURS));

        when(userRepository.findAll()).thenReturn(List.of(user));
        when(contactRepository.findByUserIdOrderByCreatedAtDesc(user.getId()))
                .thenReturn(List.of(inactivityContact));
        when(checkRepository.findUnrespondedByUserAndType(user.getId(), "FINAL"))
                .thenReturn(List.of(finalCheck));
        when(handoverRepository.findByUserIdAndStatusOrderByCreatedAtDesc(user.getId(), "PENDING"))
                .thenReturn(List.of());
        when(handoverRepository.findByUserIdAndStatusOrderByCreatedAtDesc(user.getId(), "AUTO_APPROVED"))
                .thenReturn(List.of());

        HandoverRequest savedHandover = new HandoverRequest();
        savedHandover.setTrustedContact(inactivityContact);
        savedHandover.setUser(user);
        savedHandover.setStatus("AUTO_APPROVED");
        when(handoverRepository.save(any(HandoverRequest.class))).thenReturn(savedHandover);
        when(sharedVaultService.createSharedAccess(any())).thenReturn("test-raw-token");

        service.checkInactivity();

        verify(handoverRepository).save(any(HandoverRequest.class));
        verify(sharedVaultService).createSharedAccess(any(HandoverRequest.class));
        verify(sharedVaultService).sendSharedAccessEmail(eq(inactivityContact), eq("Test User"), eq("test-raw-token"));
    }

    @Test
    void checkInactivity_autoHandover_doesNotTriggerBefore48Hours() {
        user.setLastLoginAt(Instant.now().minus(92, ChronoUnit.DAYS));

        InactivityCheck finalCheck = new InactivityCheck();
        finalCheck.setUser(user);
        finalCheck.setCheckType("FINAL");
        // sentAt was only 24 hours ago => not yet 48h
        ReflectionTestUtils.setField(finalCheck, "sentAt", Instant.now().minus(24, ChronoUnit.HOURS));

        when(userRepository.findAll()).thenReturn(List.of(user));
        when(contactRepository.findByUserIdOrderByCreatedAtDesc(user.getId()))
                .thenReturn(List.of(inactivityContact));
        when(checkRepository.findUnrespondedByUserAndType(user.getId(), "FINAL"))
                .thenReturn(List.of(finalCheck));

        service.checkInactivity();

        verify(handoverRepository, never()).save(any(HandoverRequest.class));
    }

    // --- Confirm alive ---

    @Test
    void confirmAlive_validToken_resetsInactivityTimer() {
        UUID responseToken = UUID.randomUUID();
        InactivityCheck check = new InactivityCheck();
        check.setUser(user);
        check.setCheckType("WARNING_1");
        ReflectionTestUtils.setField(check, "responseToken", responseToken);

        when(checkRepository.findByResponseToken(responseToken)).thenReturn(Optional.of(check));
        when(checkRepository.findUnrespondedByUser(user.getId())).thenReturn(List.of());

        service.confirmAlive(responseToken);

        assertThat(check.getRespondedAt()).isNotNull();
        verify(checkRepository).save(check);
        verify(userRepository).save(user);
        assertThat(user.getLastLoginAt()).isNotNull();
    }

    @Test
    void confirmAlive_alreadyResponded_doesNothing() {
        UUID responseToken = UUID.randomUUID();
        InactivityCheck check = new InactivityCheck();
        check.setUser(user);
        check.setCheckType("WARNING_1");
        check.setRespondedAt(Instant.now().minus(1, ChronoUnit.HOURS)); // Already responded

        when(checkRepository.findByResponseToken(responseToken)).thenReturn(Optional.of(check));

        service.confirmAlive(responseToken);

        verify(userRepository, never()).save(any());
    }

    @Test
    void confirmAlive_invalidToken_throwsException() {
        UUID badToken = UUID.randomUUID();
        when(checkRepository.findByResponseToken(badToken)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.confirmAlive(badToken))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // --- Edge cases ---

    @Test
    void checkInactivity_skipsUsersWithNoInactivityContacts() {
        user.setLastLoginAt(Instant.now().minus(100, ChronoUnit.DAYS));

        // Contact with MANUAL mode (not INACTIVITY)
        TrustedContact manualContact = new TrustedContact();
        manualContact.setActivationMode("MANUAL");
        manualContact.setInviteAccepted(true);

        when(userRepository.findAll()).thenReturn(List.of(user));
        when(contactRepository.findByUserIdOrderByCreatedAtDesc(user.getId()))
                .thenReturn(List.of(manualContact));

        service.checkInactivity();

        verify(checkRepository, never()).save(any());
        verify(emailService, never()).sendEmail(anyString(), anyString(), anyString());
    }

    @Test
    void checkInactivity_skipsWarningsWhenNotifyDisabled() {
        user.setLastLoginAt(Instant.now().minus(78, ChronoUnit.DAYS));
        user.setNotifyInactivityWarnings(false); // Disabled

        when(userRepository.findAll()).thenReturn(List.of(user));
        when(contactRepository.findByUserIdOrderByCreatedAtDesc(user.getId()))
                .thenReturn(List.of(inactivityContact));

        service.checkInactivity();

        verify(checkRepository, never()).save(any());
        verify(emailService, never()).sendEmail(anyString(), anyString(), anyString());
    }

    @Test
    void checkInactivity_sendsSmsWhenEnabled() {
        user.setLastLoginAt(Instant.now().minus(76, ChronoUnit.DAYS));
        user.setNotifySms(true);
        user.setPhone("+37255512345");

        when(userRepository.findAll()).thenReturn(List.of(user));
        when(contactRepository.findByUserIdOrderByCreatedAtDesc(user.getId()))
                .thenReturn(List.of(inactivityContact));
        when(checkRepository.findUnrespondedByUserAndType(user.getId(), "WARNING_1"))
                .thenReturn(List.of());

        InactivityCheck savedCheck = new InactivityCheck();
        savedCheck.setUser(user);
        savedCheck.setCheckType("WARNING_1");
        ReflectionTestUtils.setField(savedCheck, "responseToken", UUID.randomUUID());
        when(checkRepository.save(any(InactivityCheck.class))).thenReturn(savedCheck);

        service.checkInactivity();

        verify(smsService).sendSms(eq("+37255512345"), anyString());
    }

    @Test
    void checkInactivity_usesShortestInactivityPeriod() {
        // Two contacts: one with 90 days, one with 60 days
        TrustedContact shortContact = new TrustedContact();
        ReflectionTestUtils.setField(shortContact, "id", UUID.randomUUID());
        shortContact.setUser(user);
        shortContact.setActivationMode("INACTIVITY");
        shortContact.setInactivityDays(60);
        shortContact.setInviteAccepted(true);
        shortContact.setAccessLevel("FULL");

        // User inactive for 46 days => within WARNING_1 range for 60-day contact (60-14=46)
        user.setLastLoginAt(Instant.now().minus(46, ChronoUnit.DAYS));

        when(userRepository.findAll()).thenReturn(List.of(user));
        when(contactRepository.findByUserIdOrderByCreatedAtDesc(user.getId()))
                .thenReturn(List.of(inactivityContact, shortContact));
        when(checkRepository.findUnrespondedByUserAndType(user.getId(), "WARNING_1"))
                .thenReturn(List.of());

        InactivityCheck savedCheck = new InactivityCheck();
        savedCheck.setUser(user);
        savedCheck.setCheckType("WARNING_1");
        ReflectionTestUtils.setField(savedCheck, "responseToken", UUID.randomUUID());
        when(checkRepository.save(any(InactivityCheck.class))).thenReturn(savedCheck);

        service.checkInactivity();

        // Should trigger WARNING_1 because shortest contact period is 60 and 46 >= (60-14)
        verify(checkRepository).save(any(InactivityCheck.class));
    }
}
