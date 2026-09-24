package com.fraudetection.account_service.pix;

import com.fraudetection.account_service.pix.dto.PixKeyLookupRequest;
import com.fraudetection.account_service.pix.dto.PixKeyLookupResponse;
import com.fraudetection.account_service.pix.dto.PixKeyResponse;
import com.fraudetection.account_service.pix.dto.RegisterPixKeyRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/accounts")
@RequiredArgsConstructor
public class PixKeyController {

    private final PixKeyService pixKeyService;
    private final PixLookupService pixLookupService;

    @PostMapping("/{accountId}/pix-keys")
    public ResponseEntity<PixKeyResponse> register(@PathVariable UUID accountId,
                                                   @Valid @RequestBody RegisterPixKeyRequest request,
                                                   Authentication authentication) {
        PixKeyResponse response = pixKeyService.register(accountId, userId(authentication), request.type());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{accountId}/pix-keys")
    public ResponseEntity<List<PixKeyResponse>> list(@PathVariable UUID accountId, Authentication authentication) {
        return ResponseEntity.ok(pixKeyService.list(accountId, userId(authentication)));
    }

    @DeleteMapping("/{accountId}/pix-keys/{keyId}")
    public ResponseEntity<Void> delete(@PathVariable UUID accountId, @PathVariable UUID keyId,
                                       Authentication authentication) {
        pixKeyService.delete(accountId, keyId, userId(authentication));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/pix-keys/lookup")
    public ResponseEntity<PixKeyLookupResponse> lookup(@Valid @RequestBody PixKeyLookupRequest request,
                                                       Authentication authentication) {
        return ResponseEntity.ok(pixLookupService.lookup(userId(authentication), request.key()));
    }

    private static UUID userId(Authentication authentication) {
        return UUID.fromString(authentication.getName());
    }
}
