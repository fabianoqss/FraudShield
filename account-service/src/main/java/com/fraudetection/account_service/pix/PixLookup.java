package com.fraudetection.account_service.pix;

import java.util.UUID;

public record PixLookup(UUID accountId, UUID requesterId) {
}
