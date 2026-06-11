-- =====================================================================
-- RBAC permission matrix seed (Requirements 2.3, 2.7–2.12)
-- Defines discrete permissions and assigns them to roles per the design.
-- =====================================================================

-- ---------------------------------------------------------------------
-- Seed permissions representing discrete platform capabilities.
-- ---------------------------------------------------------------------
INSERT INTO permissions (name, description) VALUES
    -- Admin permissions (Req 2.7)
    ('ADMIN_MANAGE_USERS', 'Manage users: create, disable, delete accounts'),
    ('ADMIN_ASSIGN_ROLES', 'Assign or change a user role'),
    ('ADMIN_CONFIGURE_PLATFORM', 'Modify platform configuration and AI providers'),
    -- Project permissions (Req 2.9, 2.11)
    ('PROJECT_CREATE', 'Create new projects'),
    ('PROJECT_VIEW', 'View projects the user is authorized to access'),
    ('PROJECT_EDIT', 'Edit project name, description, requirements'),
    ('PROJECT_DELETE', 'Archive/delete projects'),
    ('PROJECT_MANAGE', 'Full project lifecycle management'),
    -- Blueprint / Design Artifact permissions (Req 2.8)
    ('BLUEPRINT_CREATE', 'Create design artifacts and blueprints'),
    ('BLUEPRINT_EDIT', 'Edit design artifacts'),
    ('BLUEPRINT_REVIEW', 'Review blueprints and design artifacts'),
    ('BLUEPRINT_APPROVE', 'Approve blueprints (approval gate)'),
    -- Requirement Analysis permissions
    ('REQUIREMENT_ANALYSIS_START', 'Initiate requirement analysis on a project'),
    ('REQUIREMENT_ANALYSIS_CONFIRM', 'Confirm analysis results'),
    -- Generation permissions (Req 2.8)
    ('GENERATION_START', 'Start blueprint generation'),
    -- Export permissions (Req 2.8)
    ('EXPORT', 'Export blueprints to PDF/Markdown'),
    -- Idea submission (Req 2.11)
    ('IDEA_SUBMIT', 'Submit a business idea'),
    -- Blueprint review for clients (Req 2.11)
    ('BLUEPRINT_CLIENT_REVIEW', 'Review blueprints as a client'),
    ('BLUEPRINT_CLIENT_APPROVE', 'Record business-level approval decisions'),
    -- Developer permissions (Req 2.10)
    ('BLUEPRINT_READ_APPROVED', 'Read approved blueprints'),
    ('FEEDBACK_SUBMIT', 'Submit implementation feedback'),
    -- Chat permissions
    ('CHAT_USE', 'Use the conversational AI chat interface'),
    -- Guest demo access (Req 2.12)
    ('DEMO_VIEW', 'Read-only access to demonstration content');

-- ---------------------------------------------------------------------
-- Assign permissions to roles per the RBAC matrix.
-- Uses subqueries to resolve ids for portability across environments.
-- ---------------------------------------------------------------------

-- ADMIN: full platform administration, user management, configuration (Req 2.7)
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.name = 'ADMIN' AND p.name IN (
    'ADMIN_MANAGE_USERS',
    'ADMIN_ASSIGN_ROLES',
    'ADMIN_CONFIGURE_PLATFORM',
    'PROJECT_CREATE',
    'PROJECT_VIEW',
    'PROJECT_EDIT',
    'PROJECT_DELETE',
    'PROJECT_MANAGE',
    'BLUEPRINT_CREATE',
    'BLUEPRINT_EDIT',
    'BLUEPRINT_REVIEW',
    'BLUEPRINT_APPROVE',
    'REQUIREMENT_ANALYSIS_START',
    'REQUIREMENT_ANALYSIS_CONFIRM',
    'GENERATION_START',
    'EXPORT',
    'IDEA_SUBMIT',
    'BLUEPRINT_CLIENT_REVIEW',
    'BLUEPRINT_CLIENT_APPROVE',
    'BLUEPRINT_READ_APPROVED',
    'FEEDBACK_SUBMIT',
    'CHAT_USE',
    'DEMO_VIEW'
);

-- ARCHITECT: create, edit, review, approve design artifacts and blueprints (Req 2.8)
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.name = 'ARCHITECT' AND p.name IN (
    'PROJECT_CREATE',
    'PROJECT_VIEW',
    'PROJECT_EDIT',
    'BLUEPRINT_CREATE',
    'BLUEPRINT_EDIT',
    'BLUEPRINT_REVIEW',
    'BLUEPRINT_APPROVE',
    'REQUIREMENT_ANALYSIS_START',
    'REQUIREMENT_ANALYSIS_CONFIRM',
    'GENERATION_START',
    'EXPORT',
    'CHAT_USE',
    'DEMO_VIEW'
);

-- PRODUCT_MANAGER_ROLE: create/manage projects, requirements, stories, prioritization (Req 2.9)
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.name = 'PRODUCT_MANAGER_ROLE' AND p.name IN (
    'PROJECT_CREATE',
    'PROJECT_VIEW',
    'PROJECT_EDIT',
    'PROJECT_DELETE',
    'PROJECT_MANAGE',
    'REQUIREMENT_ANALYSIS_START',
    'REQUIREMENT_ANALYSIS_CONFIRM',
    'GENERATION_START',
    'EXPORT',
    'CHAT_USE',
    'DEMO_VIEW'
);

-- DEVELOPER: read approved blueprints, submit feedback (Req 2.10)
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.name = 'DEVELOPER' AND p.name IN (
    'PROJECT_VIEW',
    'BLUEPRINT_READ_APPROVED',
    'FEEDBACK_SUBMIT',
    'CHAT_USE',
    'DEMO_VIEW'
);

-- CLIENT: submit ideas, review blueprints, record business approval (Req 2.11)
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.name = 'CLIENT' AND p.name IN (
    'IDEA_SUBMIT',
    'PROJECT_VIEW',
    'BLUEPRINT_CLIENT_REVIEW',
    'BLUEPRINT_CLIENT_APPROVE',
    'CHAT_USE',
    'DEMO_VIEW'
);

-- GUEST: read-only access to designated demo content only (Req 2.12)
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.name = 'GUEST' AND p.name IN (
    'DEMO_VIEW'
);
