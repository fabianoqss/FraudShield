package com.fraudetection.account_service.pix;

import com.fraudetection.account_service.pix.dto.PixLookupResolutionResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Service-to-service route used by transaction-service with the end user's token. The gateway does not
 * route /internal/**, so the destination account never reaches clients.
 */
@RestController
@RequestMapping("/internal/pix-keys")
@RequiredArgsConstructor
public class InternalPixLookupController {

    private final PixLookupService pixLookupService;

    @GetMapping("/lookups/{lookupId}")
    public ResponseEntity<PixLookupResolutionResponse> resolve(@PathVariable UUID lookupId,
                                                               Authentication authentication) {
        UUID requesterId = UUID.fromString(authentication.getName());
        return ResponseEntity.ok(new PixLookupResolutionResponse(pixLookupService.resolve(lookupId, requesterId)));
    }
}
