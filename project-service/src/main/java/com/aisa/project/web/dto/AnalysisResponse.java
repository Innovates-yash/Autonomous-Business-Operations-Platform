package com.aisa.project.web.dto;

import com.aisa.project.domain.Project;
import com.aisa.project.domain.Requirement;
import com.aisa.project.domain.UseCase;
import java.util.List;
import java.util.UUID;

/**
 * Response DTO for the analysis endpoint. Contains the Project state after analysis
 * and the generated requirements and use cases.
 */
public record AnalysisResponse(
        UUID projectId,
        String projectState,
        List<RequirementDto> requirements,
        List<UseCaseDto> useCases
) {

    public record RequirementDto(UUID id, String statement, String type) {
    }

    public record UseCaseDto(UUID id, String title, String description, List<UUID> requirementIds) {
    }

    public static AnalysisResponse from(Project project) {
        List<RequirementDto> requirements = project.getRequirements().stream()
                .map(r -> new RequirementDto(r.getId(), r.getStatement(), r.getType().name()))
                .toList();

        List<UseCaseDto> useCases = project.getUseCases().stream()
                .map(uc -> new UseCaseDto(
                        uc.getId(),
                        uc.getTitle(),
                        uc.getDescription(),
                        uc.getRequirements().stream()
                                .map(Requirement::getId)
                                .toList()))
                .toList();

        return new AnalysisResponse(
                project.getId(),
                project.getState().name(),
                requirements,
                useCases);
    }
}
