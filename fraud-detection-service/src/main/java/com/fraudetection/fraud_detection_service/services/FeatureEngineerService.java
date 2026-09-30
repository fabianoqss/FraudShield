package com.fraudetection.fraud_detection_service.services;

import com.fraudetection.fraud_detection_service.dto.event.TransactionCreatedPayload;
import com.fraudetection.fraud_detection_service.dto.request.MlPredictRequest;
import com.fraudetection.fraud_detection_service.repositories.FraudAnalysisRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class FeatureEngineerService {

    private static final Pattern IPV4_PATTERN = Pattern.compile(
            "^((25[0-5]|2[0-4][0-9]|1[0-9][0-9]|[1-9][0-9]|[0-9])\\.){3}(25[0-5]|2[0-4][0-9]|1[0-9][0-9]|[1-9][0-9]|[0-9])$"
    );

    private final FraudAnalysisRepository fraudAnalysisRepository;

    public MlPredictRequest buildFeatures(TransactionCreatedPayload payload) {
        Instant now = Instant.now();

        boolean isNewDevice = !fraudAnalysisRepository.existsBySourceAccountIdAndDeviceId(
                payload.sourceAccountId(), payload.deviceId());

        boolean isForeignIp = isForeignIp(payload.ipAddress());

        long transactionsLastHour = fraudAnalysisRepository.countBySourceAccountIdAndAnalyzedAtAfter(
                payload.sourceAccountId(), now.minus(1, ChronoUnit.HOURS));

        long transactionsLast24Hours = fraudAnalysisRepository.countBySourceAccountIdAndAnalyzedAtAfter(
                payload.sourceAccountId(), now.minus(24, ChronoUnit.HOURS));

        BigDecimal avgAmountLast30Days = fraudAnalysisRepository.findAverageAmountBySourceAccountIdSince(
                payload.sourceAccountId(), now.minus(30, ChronoUnit.DAYS));

        return new MlPredictRequest(
                payload.amount(),
                payload.createdAt().getHour(),
                payload.createdAt().getDayOfWeek().getValue(),
                payload.type(),
                isNewDevice,
                isForeignIp,
                transactionsLastHour,
                transactionsLast24Hours,
                avgAmountLast30Days != null ? avgAmountLast30Days : BigDecimal.ZERO
        );
    }

    boolean isForeignIp(String ipAddress) {
        if (ipAddress == null || (!IPV4_PATTERN.matcher(ipAddress).matches()
                && !ipAddress.contains(":"))) {
            return false;
        }

        try {
            InetAddress address = InetAddress.getByName(ipAddress);
            boolean ipv6 = address instanceof Inet6Address;
            boolean uniqueLocal = ipv6 && (address.getAddress()[0] & 0xfe) == 0xfc;
            return !(address.isSiteLocalAddress() || address.isLoopbackAddress()
                    || (ipv6 && address.isLinkLocalAddress()) || uniqueLocal);
        } catch (UnknownHostException e) {
            return false;
        }
    }

}

