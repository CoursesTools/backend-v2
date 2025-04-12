package com.winworld.coursestools.listener;

import com.winworld.coursestools.event.ReferralActivityEvent;
import com.winworld.coursestools.service.PartnershipService;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionalEventListener;

@Service
public class ReferralActivityListener {

    private final PartnershipService partnershipService;

    public ReferralActivityListener(PartnershipService partnershipService) {
        this.partnershipService = partnershipService;
    }

    @Async
    @TransactionalEventListener
    public void processActivity(ReferralActivityEvent event) {
        partnershipService.processReferralActivity(event);
    }
}
