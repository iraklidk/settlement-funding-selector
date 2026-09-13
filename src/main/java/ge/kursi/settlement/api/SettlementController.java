package ge.kursi.settlement.api;

import ge.kursi.settlement.api.dto.CandidateInstructionDto;
import ge.kursi.settlement.api.dto.FundingRequestDto;
import ge.kursi.settlement.api.dto.FundingResultResponse;
import ge.kursi.settlement.api.dto.FundingRunSummaryResponse;
import ge.kursi.settlement.api.dto.PageResponse;
import ge.kursi.settlement.domain.CandidateInstruction;
import ge.kursi.settlement.domain.FundingResult;
import ge.kursi.settlement.domain.FundingRunSummary;
import ge.kursi.settlement.service.SettlementFundingService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping(path = "/api/v1/settlement", produces = "application/json")
public class SettlementController {

    public static final int MAX_PAGE_SIZE = 100;

    private final SettlementFundingService service;

    public SettlementController(SettlementFundingService service) {
        this.service = service;
    }

    @PostMapping(path = "/fund", consumes = "application/json")
    public ResponseEntity<FundingResultResponse> fund(@Valid @RequestBody FundingRequestDto request) {
        List<CandidateInstruction> candidates = request.candidateInstructions().stream()
                .map(dto -> new CandidateInstruction(dto.instructionReference(), dto.instructionAmount(), dto.expectedFee()))
                .toList();

        FundingResult result = service.fund(request.availableSettlementBalance(), candidates);

        URI location = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/v1/settlement/{requestId}")
                .buildAndExpand(result.requestId())
                .toUri();
        return ResponseEntity.created(location).body(toResponse(result));
    }

    @GetMapping("/{requestId}")
    public FundingResultResponse getById(@PathVariable UUID requestId) {
        return toResponse(service.getById(requestId));
    }

    @GetMapping
    public PageResponse<FundingRunSummaryResponse> list(
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "page must be 0 or greater") int page,
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "size must be at least 1")
            @Max(value = MAX_PAGE_SIZE, message = "size must be at most " + MAX_PAGE_SIZE) int size) {
        // Ordering is fixed by the repository query (newest first), so only page/size are exposed.
        return PageResponse.from(service.list(PageRequest.of(page, size)).map(SettlementController::toSummary));
    }

    private static FundingResultResponse toResponse(FundingResult result) {
        return new FundingResultResponse(
                result.requestId(),
                result.availableSettlementBalance(),
                result.candidateInstructions().stream().map(SettlementController::toDto).toList(),
                result.selectedInstructions().stream().map(SettlementController::toDto).toList(),
                result.totalSettlementConsumed(),
                result.totalExpectedFee(),
                result.createdAt());
    }

    private static CandidateInstructionDto toDto(CandidateInstruction instruction) {
        return new CandidateInstructionDto(
                instruction.instructionReference(),
                instruction.instructionAmount(),
                instruction.expectedFee());
    }

    private static FundingRunSummaryResponse toSummary(FundingRunSummary summary) {
        return new FundingRunSummaryResponse(
                summary.requestId(),
                summary.availableSettlementBalance(),
                summary.totalSettlementConsumed(),
                summary.totalExpectedFee(),
                summary.candidateCount(),
                summary.selectedCount(),
                summary.createdAt());
    }
}
