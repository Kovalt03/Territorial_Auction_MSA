package com.territorial.auction.global.event;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CombatEventReceiptServiceTest {

    @InjectMocks private CombatEventReceiptService service;
    @Mock private CombatEventReceiptRepository repository;
    @Mock private Runnable action;

    @Test
    void processesAndStoresReceiptOnce() {
        given(repository.existsById("notification:event-1")).willReturn(false);

        service.processOnce("notification:event-1", action);

        then(action).should().run();
        then(repository).should().save(org.mockito.ArgumentMatchers.any(CombatEventReceipt.class));
    }

    @Test
    void duplicateReceiptSkipsSideEffect() {
        given(repository.existsById("notification:event-1")).willReturn(true);

        service.processOnce("notification:event-1", action);

        then(action).should(never()).run();
        then(repository).should(never()).save(org.mockito.ArgumentMatchers.any());
    }
}
