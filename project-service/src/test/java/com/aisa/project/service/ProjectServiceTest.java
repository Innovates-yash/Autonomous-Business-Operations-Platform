package com.aisa.project.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aisa.commons.domain.ProjectState;
import com.aisa.commons.domain.Role;
import com.aisa.project.domain.Project;
import com.aisa.project.repository.ProjectRepository;
import com.aisa.project.security.ProjectPrincipal;
import com.aisa.project.web.dto.CreateProjectRequest;
import com.aisa.project.web.dto.UpdateProjectRequest;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link ProjectService}: creation in DRAFT with owner and Idea
 * association (Requirements 3.1, 3.2), update persistence (Requirement 3.3),
 * authorized listing and ownership scoping (Requirement 3.6), and not-found for
 * absent or inaccessible Projects (Requirement 3.7).
 */
@ExtendWith(MockitoExtension.class)
class ProjectServiceTest {

    @Mock
    private ProjectRepository projectRepository;

    private ProjectService service;

    private final UUID ownerId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new ProjectService(projectRepository);
    }

    private ProjectPrincipal owner() {
        return new ProjectPrincipal(ownerId, Role.PRODUCT_MANAGER);
    }

    @Test
    void createsProjectInDraftWithOwnerAndIdea() {
        when(projectRepository.save(any(Project.class))).thenAnswer(inv -> inv.getArgument(0));

        Project created = service.create(
                new CreateProjectRequest("Food Delivery", "An app for ordering meals"), owner());

        ArgumentCaptor<Project> captor = ArgumentCaptor.forClass(Project.class);
        verify(projectRepository).save(captor.capture());
        Project saved = captor.getValue();

        assertThat(saved.getState()).isEqualTo(ProjectState.DRAFT);
        assertThat(saved.getOwnerId()).isEqualTo(ownerId);
        assertThat(saved.getName()).isEqualTo("Food Delivery");
        assertThat(saved.getDescription()).isEqualTo("An app for ordering meals");
        // The submitted name/description constitute the associated Idea (Requirement 3.1).
        assertThat(saved.getIdea()).isNotNull();
        assertThat(saved.getIdea().getName()).isEqualTo("Food Delivery");
        assertThat(saved.getIdea().getDescription()).isEqualTo("An app for ordering meals");
        assertThat(created).isSameAs(saved);
    }

    @Test
    void updatePersistsNewNameAndDescriptionForOwnedProject() {
        UUID projectId = UUID.randomUUID();
        Project existing = new Project("Old", "Old description", ownerId);
        when(projectRepository.findByIdAndOwnerId(projectId, ownerId))
                .thenReturn(Optional.of(existing));
        when(projectRepository.save(any(Project.class))).thenAnswer(inv -> inv.getArgument(0));

        Project updated = service.update(
                projectId, new UpdateProjectRequest("New", "New description"), owner());

        assertThat(updated.getName()).isEqualTo("New");
        assertThat(updated.getDescription()).isEqualTo("New description");
        verify(projectRepository).save(existing);
    }

    @Test
    void updateRejectsInaccessibleProjectAsNotFound() {
        UUID projectId = UUID.randomUUID();
        when(projectRepository.findByIdAndOwnerId(projectId, ownerId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(
                projectId, new UpdateProjectRequest("New", "New description"), owner()))
                .isInstanceOf(ProjectNotFoundException.class);

        verify(projectRepository, never()).save(any());
    }

    @Test
    void listForNonPrivilegedRoleReturnsOnlyOwnedProjects() {
        service.listAuthorized(owner());

        verify(projectRepository).findByOwnerIdOrderByCreatedAtDesc(ownerId);
        verify(projectRepository, never()).findAll();
    }

    @Test
    void listForPrivilegedRoleReturnsAllProjects() {
        ProjectPrincipal admin = new ProjectPrincipal(UUID.randomUUID(), Role.ADMIN);
        when(projectRepository.findAll()).thenReturn(List.of());

        service.listAuthorized(admin);

        verify(projectRepository).findAll();
        verify(projectRepository, never()).findByOwnerIdOrderByCreatedAtDesc(any());
    }

    @Test
    void getByIdReturnsOwnedProject() {
        UUID projectId = UUID.randomUUID();
        Project existing = new Project("Name", "Description", ownerId);
        when(projectRepository.findByIdAndOwnerId(projectId, ownerId))
                .thenReturn(Optional.of(existing));

        assertThat(service.getById(projectId, owner())).isSameAs(existing);
    }

    @Test
    void getByIdRaisesNotFoundForAbsentOrInaccessibleProject() {
        UUID projectId = UUID.randomUUID();
        when(projectRepository.findByIdAndOwnerId(projectId, ownerId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(projectId, owner()))
                .isInstanceOf(ProjectNotFoundException.class);
    }

    @Test
    void privilegedRoleCanGetAnyProjectById() {
        ProjectPrincipal architect = new ProjectPrincipal(UUID.randomUUID(), Role.ARCHITECT);
        UUID projectId = UUID.randomUUID();
        Project existing = new Project("Name", "Description", ownerId);
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(existing));

        assertThat(service.getById(projectId, architect)).isSameAs(existing);
        verify(projectRepository, never()).findByIdAndOwnerId(any(), any());
    }
}
