package ge.kursi.settlement.service;

/** Semantic validation failure that cannot be expressed with Bean Validation annotations alone. */
public class InvalidFundingRequestException extends RuntimeException {

    public InvalidFundingRequestException(String message) {
        super(message);
    }
}
