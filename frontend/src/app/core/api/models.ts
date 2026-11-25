/** Types mirroring the backend DTOs (docs/architecture/API_DESIGN.md). */
export type Role = 'MANAGER' | 'ANALYST' | 'REVIEWER' | 'VIEWER';
export type AssessmentStatus = 'DRAFT' | 'PLANNED' | 'IN_PROGRESS' | 'TECHNICAL_REVIEW' | 'APPROVED' | 'DELIVERED'
  | 'RETESTING' | 'COMPLETED' | 'ARCHIVED';
export type Severity = 'CRITICAL' | 'HIGH' | 'MEDIUM' | 'LOW' | 'INFORMATIONAL' | 'NOT_APPLICABLE';
export type Remediation = 'OPEN' | 'IN_PROGRESS' | 'MITIGATED' | 'FIXED' | 'RISK_ACCEPTED' | 'FALSE_POSITIVE' | 'PENDING_RETEST';
export type Classification = 'PUBLIC' | 'INTERNAL' | 'CONFIDENTIAL' | 'RESTRICTED';

export const SEVERITIES: Severity[] = ['CRITICAL', 'HIGH', 'MEDIUM', 'LOW', 'INFORMATIONAL', 'NOT_APPLICABLE'];
export const REMEDIATIONS: Remediation[] = ['OPEN', 'IN_PROGRESS', 'MITIGATED', 'FIXED', 'RISK_ACCEPTED', 'FALSE_POSITIVE', 'PENDING_RETEST'];
export const STATUSES: AssessmentStatus[] = ['DRAFT', 'PLANNED', 'IN_PROGRESS', 'TECHNICAL_REVIEW', 'APPROVED', 'DELIVERED', 'RETESTING', 'COMPLETED', 'ARCHIVED'];
export const CLASSIFICATIONS: Classification[] = ['PUBLIC', 'INTERNAL', 'CONFIDENTIAL', 'RESTRICTED'];

export interface Page<T> { items: T[]; page: number; size: number; total: number; }
export interface UserSummary { id: string; username: string; displayName: string; locale: string; timeZone: string; admin: boolean; mfaEnabled: boolean; }
export interface WorkspaceView { id: string; name: string; role: Role; }
export interface Me { user: UserSummary; workspaces: WorkspaceView[]; }
export interface MemberView { userId: string; username: string; displayName: string; role: Role; }

export interface Contact { id?: string; name: string; email?: string | null; phone?: string | null; kind: 'TECHNICAL' | 'ADMINISTRATIVE' | 'OTHER'; }
export interface Client { id: string; name: string; legalName: string | null; website: string | null; notes: string | null;
  logoAttachmentId: string | null; contacts: Contact[]; updatedAt: string; }

export interface AssessmentType { code: string; usesCvss: boolean; }
export interface Assessment { id: string; code: string; title: string; description: string | null; typeCode: string; usesCvss: boolean;
  clientId: string | null; clientName: string | null; status: AssessmentStatus; nextStatuses: AssessmentStatus[];
  startDate: string | null; dueDate: string | null; completedDate: string | null; objectives: string | null; scope: string | null;
  outOfScope: string | null; rulesOfEngagement: string | null; methodology: string | null; tags: string[]; analystIds: string[];
  createdAt: string; updatedAt: string; }

export interface Finding { id: string; assessmentId: string; code: string; title: string; description: string | null;
  technicalDetails: string | null; severity: Severity; cvss31Vector: string | null; cvss31Score: number | null;
  cvss40Vector: string | null; cvss40Score: number | null; cwe: string[]; cve: string[]; impact: string | null;
  recommendation: string | null; references: string[]; tags: string[]; status: Remediation; discoveredOn: string | null;
  analystId: string | null; createdAt: string; updatedAt: string; }

export interface Attachment { id: string; originalName: string; mimeType: string; sizeBytes: number; sha256: string; createdAt: string; }

export interface ReportSummary { id: string; assessmentId: string; title: string; templateId: string | null; revision: number;
  classification: Classification; updatedAt: string; latestVersion: number | null; }
export interface ReportDetail { id: string; assessmentId: string; title: string; templateId: string | null; revision: number;
  classification: Classification; document: string; updatedAt: string; }
export interface ReportVersion { id: string; number: number; pdfAttachmentId: string; pageCount: number | null; finalized: boolean;
  createdBy: string; createdAt: string; }

export interface TemplateConfig { title: string; subtitle: string | null; paperSize: 'A4' | 'LETTER'; orientation: 'PORTRAIT' | 'LANDSCAPE';
  margins: { topMm: number; rightMm: number; bottomMm: number; leftMm: number }; primaryColor: string; accentColor: string;
  fontFamily: 'SANS' | 'SERIF'; baseFontSizePt: number; showCover: boolean; showOrganizationLogo: boolean; showClientLogo: boolean;
  organizationLogoAttachmentId: string | null; logoPosition: 'LEFT' | 'CENTER' | 'RIGHT'; headerText: string | null;
  footerText: string | null; pageNumbers: boolean; classification: Classification; watermarkText: string | null;
  showFindingsSummary: boolean; showCvss: boolean; }
export interface Template { id: string; name: string; builtIn: boolean; config: TemplateConfig; updatedAt: string; }

export interface SearchHit { entityType: string; entityId: string; assessmentId: string | null; title: string; snippet: string;
  tags: string[]; severity: string | null; status: string | null; typeCode: string | null; updatedAt: string; rank: number; }
export interface TagCount { tag: string; count: number; }

export interface TimeEntry { id: string; assessmentId: string; assessmentCode: string; assessmentTitle: string; userId: string;
  startedAt: string; endedAt: string | null; durationSeconds: number; running: boolean; billable: boolean; description: string | null; }

export interface Dashboard { assessmentsByStatus: Record<string, number>; assessmentsByType: Record<string, number>;
  openFindingsBySeverity: Record<string, number>; findingsByStatus: Record<string, number>; activeAssessments: number;
  completedAssessments: number; underReview: number; openFindings: number; totalHours: number;
  hoursByAssessment: { assessmentId: string; code: string; title: string; hours: number }[];
  upcomingDeadlines: { id: string; code: string; title: string; status: AssessmentStatus; dueDate: string }[];
  recentActivity: { action: string; entityType: string; entityId: string; at: string }[];
  recentReports: { id: string; title: string; assessmentId: string; updatedAt: string }[]; from: string; to: string; }

export interface AuditEvent { id: string; at: string; actorId: string | null; action: string; entityType: string | null; entityId: string | null; ip: string | null; }

/** RFC 9457 problem details from the backend. */
export interface Problem { title?: string; status?: number; detail?: string; errors?: { field: string; message: string }[]; mfaRequired?: boolean; }
