package com.fraudetection.account_service.pix;

import com.fraudetection.account_service.AbstractIntegrationTest;
import com.fraudetection.account_service.clients.AuthServiceClient;
import com.fraudetection.account_service.dto.response.UserLookupResponse;
import com.fraudetection.account_service.entities.Account;
import com.fraudetection.account_service.pix.exceptions.PixKeyAlreadyRegisteredException;
import com.fraudetection.account_service.pix.exceptions.PixKeyLimitReachedException;
import com.fraudetection.account_service.repositories.AccountRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class PixKeyIntegrationTest extends AbstractIntegrationTest {

    @MockitoBean
    AuthServiceClient authServiceClient;

    @Autowired
    PixKeyService pixKeyService;

    @Autowired
    PixKeyRepository pixKeyRepository;

    @Autowired
    AccountRepository accountRepository;

    @Autowired
    PixLookupService pixLookupService;

    @Autowired
    org.springframework.data.redis.core.StringRedisTemplate redisTemplate;

    @AfterEach
    void cleanUp() {
        pixKeyRepository.deleteAll();
        accountRepository.deleteAll();
    }

    @AfterEach
    void clearRedis() {
        var keys = redisTemplate.keys("pix:*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    @Test
    void sameValueRegisteredConcurrentlyByTwoUsersIsStoredOnce() throws Exception {
        Account first = newAccount(UUID.randomUUID());
        Account second = newAccount(UUID.randomUUID());
        // Simulates two identities resolving to the same CPF, so only the unique constraint can stop it.
        when(authServiceClient.lookupById(any()))
                .thenReturn(new UserLookupResponse(UUID.randomUUID(), "Ana Souza", "ana@example.com", "52998224725"));

        List<Throwable> failures = concurrently(List.of(
                () -> pixKeyService.register(first.getId(), first.getOwnerId(), PixKeyType.CPF),
                () -> pixKeyService.register(second.getId(), second.getOwnerId(), PixKeyType.CPF)));

        assertThat(failures).hasSize(1).first().isInstanceOf(PixKeyAlreadyRegisteredException.class);
        assertThat(pixKeyRepository.findAll()).hasSize(1);
    }

    @Test
    void sixConcurrentRegistrationsOnOneAccountStoreExactlyFive() throws Exception {
        Account account = newAccount(UUID.randomUUID());
        List<Callable<Object>> calls = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            calls.add(() -> pixKeyService.register(account.getId(), account.getOwnerId(), PixKeyType.RANDOM));
        }

        List<Throwable> failures = concurrently(calls);

        assertThat(failures).hasSize(1).first().isInstanceOf(PixKeyLimitReachedException.class);
        assertThat(pixKeyRepository.countByAccount_Id(account.getId())).isEqualTo(5);
    }

    @Test
    void lookupThenResolveWorksOnlyForTheRequester() {
        Account recipient = newAccount(UUID.randomUUID());
        when(authServiceClient.lookupById(recipient.getOwnerId())).thenReturn(
                new UserLookupResponse(recipient.getOwnerId(), "Ana Souza", "ana@example.com", "52998224725"));
        pixKeyService.register(recipient.getId(), recipient.getOwnerId(), PixKeyType.EMAIL);
        UUID requester = UUID.randomUUID();

        UUID lookupId = pixLookupService.lookup(requester, "ANA@example.com").lookupId();

        assertThat(pixLookupService.resolve(lookupId, requester)).isEqualTo(recipient.getId());
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> pixLookupService.resolve(lookupId, UUID.randomUUID()))
                .isInstanceOf(com.fraudetection.account_service.pix.exceptions.PixLookupNotFoundException.class);
    }

    @Test
    void lookupIsStoredWithTheConfiguredTtl() {
        Account recipient = newAccount(UUID.randomUUID());
        pixKeyService.register(recipient.getId(), recipient.getOwnerId(), PixKeyType.RANDOM);
        String randomKey = pixKeyRepository.findAll().getFirst().getKeyValue();
        when(authServiceClient.lookupById(recipient.getOwnerId())).thenReturn(
                new UserLookupResponse(recipient.getOwnerId(), "Ana Souza", "ana@example.com", "52998224725"));

        UUID lookupId = pixLookupService.lookup(UUID.randomUUID(), randomKey).lookupId();

        assertThat(redisTemplate.getExpire("pix:lookup:" + lookupId)).isBetween(1L, 300L);
    }

    @Test
    void resolveStillReturnsTheAccountShownEvenIfTheKeyWasRemoved() {
        Account recipient = newAccount(UUID.randomUUID());
        when(authServiceClient.lookupById(recipient.getOwnerId())).thenReturn(
                new UserLookupResponse(recipient.getOwnerId(), "Ana Souza", "ana@example.com", "52998224725"));
        var key = pixKeyService.register(recipient.getId(), recipient.getOwnerId(), PixKeyType.EMAIL);
        UUID requester = UUID.randomUUID();
        UUID lookupId = pixLookupService.lookup(requester, "ana@example.com").lookupId();

        pixKeyService.delete(recipient.getId(), key.id(), recipient.getOwnerId());

        assertThat(pixLookupService.resolve(lookupId, requester)).isEqualTo(recipient.getId());
    }

    Account newAccount(UUID ownerId) {
        Account account = new Account();
        account.setOwnerId(ownerId);
        account.setOwnerName("Owner " + ownerId);
        return accountRepository.save(account);
    }

    /** Starts every call at the same instant and returns the exceptions thrown, unwrapped. */
    static List<Throwable> concurrently(List<Callable<Object>> calls) throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(calls.size());
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Object>> futures = new ArrayList<>();
            for (Callable<Object> call : calls) {
                futures.add(executor.submit(() -> {
                    start.await();
                    return call.call();
                }));
            }
            start.countDown();
            List<Throwable> failures = new ArrayList<>();
            for (Future<Object> future : futures) {
                try {
                    future.get();
                } catch (ExecutionException e) {
                    failures.add(e.getCause());
                }
            }
            return failures;
        } finally {
            executor.shutdownNow();
        }
    }
}
