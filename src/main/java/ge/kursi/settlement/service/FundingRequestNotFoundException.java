package ge.kursi.settlement.service;

import java.util.UUID;

public class FundingRequestNotFoundException extends RuntimeException {

    public FundingRequestNotFoundException(UUID requestId) {
        super("Funding request not found: " + requestId);
    }
}
