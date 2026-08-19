package com.winworld.coursestools.repository.user;

import com.winworld.coursestools.enums.SubscriptionStatus;
import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import static org.assertj.core.api.Assertions.assertThat;

class SubscriptionRepositoryContractTest {

    @Test
    void paidExpiryQueryExplicitlyExcludesTrials() throws Exception {
        Query query = UserSubscriptionRepository.class
                .getMethod("findAllWithExpiredSubscriptionsByStatus", SubscriptionStatus.class)
                .getAnnotation(Query.class);

        assertThat(query.value()).contains("us.isTrial = false");
    }

    @Test
    void trialExpiryQueryIncludesEveryNonTerminatedState() throws Exception {
        Query query = UserSubscriptionRepository.class
                .getMethod("findAllWithExpiredTrialSubscription")
                .getAnnotation(Query.class);

        assertThat(query.value())
                .contains("us.isTrial = true")
                .contains("us.status <> 'TERMINATED'")
                .doesNotContain("us.status IN ('GRANTED')");
    }

    @Test
    void paymentSerializationLocksUserRow() throws Exception {
        Lock lock = UserRepository.class
                .getMethod("findByIdForUpdate", Integer.class)
                .getAnnotation(Lock.class);

        assertThat(lock.value()).isEqualTo(LockModeType.PESSIMISTIC_WRITE);
    }

    @Test
    void expiryRevalidationLocksSubscriptionRow() throws Exception {
        Lock lock = UserSubscriptionRepository.class
                .getMethod("findByIdWithUserDetailsForUpdate", int.class)
                .getAnnotation(Lock.class);

        assertThat(lock.value()).isEqualTo(LockModeType.PESSIMISTIC_WRITE);
    }
}
