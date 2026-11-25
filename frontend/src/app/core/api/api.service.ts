import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import * as M from './models';

type Params = Record<string, string | number | boolean | string[] | null | undefined>;

function params(p: Params = {}): HttpParams {
  let hp = new HttpParams();
  for (const [k, v] of Object.entries(p)) {
    if (v === null || v === undefined || v === '') continue;
    if (Array.isArray(v)) v.forEach((x) => (hp = hp.append(k, x)));
    else hp = hp.set(k, String(v));
  }
  return hp;
}

/** Thin typed client for /api/v1. Session cookie + XSRF header are handled by HttpClient. */
@Injectable({ providedIn: 'root' })
export class Api {
  private readonly http = inject(HttpClient);
  private readonly base = '/api/v1';
  private ws = (w: string) => `${this.base}/workspaces/${w}`;

  // setup & auth
  setupStatus() { return this.http.get<{ pending: boolean; organizationName: string }>(`${this.base}/setup/status`); }
  completeSetup(body: Record<string, string>) { return this.http.post<{ username: string }>(`${this.base}/setup`, body); }
  csrf() { return this.http.get<void>(`${this.base}/auth/csrf`); }
  login(body: { username: string; password: string; totp?: string; recoveryCode?: string }) {
    return this.http.post<{ username: string }>(`${this.base}/auth/login`, body);
  }
  logout() { return this.http.post<void>(`${this.base}/auth/logout`, {}); }
  me() { return this.http.get<M.Me>(`${this.base}/auth/me`); }
  changePassword(currentPassword: string, newPassword: string) { return this.http.put<void>(`${this.base}/auth/password`, { currentPassword, newPassword }); }
  preferences(body: { locale?: string; timeZone?: string }) { return this.http.put<void>(`${this.base}/auth/preferences`, body); }
  mfaSetup() { return this.http.post<{ secret: string; otpauthUri: string }>(`${this.base}/auth/mfa/setup`, {}); }
  mfaEnable(code: string) { return this.http.post<{ recoveryCodes: string[] }>(`${this.base}/auth/mfa/enable`, { code }); }
  mfaDisable(password: string) { return this.http.post<void>(`${this.base}/auth/mfa/disable`, { password }); }
  createUser(body: { username: string; displayName: string; password: string; locale: string }) {
    return this.http.post<M.UserSummary>(`${this.base}/admin/users`, body);
  }

  // workspaces
  workspaces() { return this.http.get<M.WorkspaceView[]>(`${this.base}/workspaces`); }
  createWorkspace(name: string) { return this.http.post<M.WorkspaceView>(`${this.base}/workspaces`, { name }); }
  members(w: string) { return this.http.get<M.MemberView[]>(`${this.ws(w)}/members`); }
  addMember(w: string, username: string, role: M.Role) { return this.http.post<M.MemberView>(`${this.ws(w)}/members`, { username, role }); }
  changeRole(w: string, userId: string, role: M.Role) { return this.http.put<void>(`${this.ws(w)}/members/${userId}`, { role }); }
  removeMember(w: string, userId: string) { return this.http.delete<void>(`${this.ws(w)}/members/${userId}`); }

  // dashboard & audit
  dashboard(w: string, from?: string, to?: string) { return this.http.get<M.Dashboard>(`${this.ws(w)}/dashboard`, { params: params({ from, to }) }); }
  audit(w: string, page = 0) { return this.http.get<M.Page<M.AuditEvent>>(`${this.ws(w)}/audit`, { params: params({ page, size: 50 }) }); }

  // clients
  clients(w: string, q?: string, page = 0, size = 25) { return this.http.get<M.Page<M.Client>>(`${this.ws(w)}/clients`, { params: params({ q, page, size }) }); }
  client(w: string, id: string) { return this.http.get<M.Client>(`${this.ws(w)}/clients/${id}`); }
  saveClient(w: string, body: Partial<M.Client>, id?: string): Observable<M.Client> {
    return id ? this.http.put<M.Client>(`${this.ws(w)}/clients/${id}`, body) : this.http.post<M.Client>(`${this.ws(w)}/clients`, body);
  }
  deleteClient(w: string, id: string) { return this.http.delete<void>(`${this.ws(w)}/clients/${id}`); }

  // assessments
  assessmentTypes() { return this.http.get<M.AssessmentType[]>(`${this.base}/assessment-types`); }
  assessments(w: string, p: Params) { return this.http.get<M.Page<M.Assessment>>(`${this.ws(w)}/assessments`, { params: params(p) }); }
  assessment(w: string, id: string) { return this.http.get<M.Assessment>(`${this.ws(w)}/assessments/${id}`); }
  saveAssessment(w: string, body: Record<string, unknown>, id?: string): Observable<M.Assessment> {
    return id ? this.http.put<M.Assessment>(`${this.ws(w)}/assessments/${id}`, body) : this.http.post<M.Assessment>(`${this.ws(w)}/assessments`, body);
  }
  transition(w: string, id: string, status: M.AssessmentStatus) { return this.http.post<M.Assessment>(`${this.ws(w)}/assessments/${id}/transition`, { status }); }
  deleteAssessment(w: string, id: string) { return this.http.delete<void>(`${this.ws(w)}/assessments/${id}`); }

  // findings
  allFindings(w: string, p: Params) { return this.http.get<M.Page<{ finding: M.Finding; assessmentCode: string; assessmentTitle: string }>>(`${this.ws(w)}/findings`, { params: params(p) }); }
  findings(w: string, assessmentId: string) { return this.http.get<M.Finding[]>(`${this.ws(w)}/assessments/${assessmentId}/findings`); }
  finding(w: string, id: string) { return this.http.get<M.Finding>(`${this.ws(w)}/findings/${id}`); }
  createFinding(w: string, assessmentId: string, body: Record<string, unknown>) { return this.http.post<M.Finding>(`${this.ws(w)}/assessments/${assessmentId}/findings`, body); }
  updateFinding(w: string, id: string, body: Record<string, unknown>) { return this.http.put<M.Finding>(`${this.ws(w)}/findings/${id}`, body); }
  deleteFinding(w: string, id: string) { return this.http.delete<void>(`${this.ws(w)}/findings/${id}`); }

  // attachments
  upload(w: string, file: Blob, name: string, kind: 'IMAGE' | 'PDF' = 'IMAGE') {
    const form = new FormData();
    form.append('file', file, name);
    return this.http.post<M.Attachment>(`${this.ws(w)}/attachments`, form, { params: params({ kind }) });
  }
  attachmentUrl(w: string, id: string, download = false) { return `${this.ws(w)}/attachments/${id}/content${download ? '?download=true' : ''}`; }

  // reports & templates
  reports(w: string, page = 0) { return this.http.get<M.Page<M.ReportSummary>>(`${this.ws(w)}/reports`, { params: params({ page }) }); }
  assessmentReports(w: string, assessmentId: string) { return this.http.get<M.ReportSummary[]>(`${this.ws(w)}/assessments/${assessmentId}/reports`); }
  createReport(w: string, assessmentId: string, title?: string, templateId?: string) {
    return this.http.post<M.ReportDetail>(`${this.ws(w)}/assessments/${assessmentId}/reports`, { title, templateId });
  }
  report(w: string, id: string) { return this.http.get<M.ReportDetail>(`${this.ws(w)}/reports/${id}`); }
  saveReport(w: string, id: string, body: { title: string; document: string; revision: number; templateId: string | null; classification: string }) {
    return this.http.put<M.ReportDetail>(`${this.ws(w)}/reports/${id}`, body);
  }
  render(w: string, id: string, finalized = false) { return this.http.post<M.ReportVersion>(`${this.ws(w)}/reports/${id}/render`, { finalized }); }
  versions(w: string, id: string) { return this.http.get<M.ReportVersion[]>(`${this.ws(w)}/reports/${id}/versions`); }
  templates(w: string) { return this.http.get<M.Template[]>(`${this.ws(w)}/templates`); }
  saveTemplate(w: string, name: string, config: M.TemplateConfig, id?: string) {
    return id ? this.http.put<M.Template>(`${this.ws(w)}/templates/${id}`, { name, config })
      : this.http.post<M.Template>(`${this.ws(w)}/templates`, { name, config });
  }
  duplicateTemplate(w: string, id: string) { return this.http.post<M.Template>(`${this.ws(w)}/templates/${id}/duplicate`, {}); }
  deleteTemplate(w: string, id: string) { return this.http.delete<void>(`${this.ws(w)}/templates/${id}`); }

  // search
  search(w: string, p: Params) { return this.http.get<M.Page<M.SearchHit>>(`${this.ws(w)}/search`, { params: params(p) }); }
  tags(w: string, prefix?: string) { return this.http.get<M.TagCount[]>(`${this.ws(w)}/tags`, { params: params({ prefix }) }); }

  // time
  timeEntries(w: string, p: Params) { return this.http.get<M.TimeEntry[]>(`${this.ws(w)}/time-entries`, { params: params(p) }); }
  addTime(w: string, body: Record<string, unknown>) { return this.http.post<M.TimeEntry>(`${this.ws(w)}/time-entries`, body); }
  deleteTime(w: string, id: string) { return this.http.delete<void>(`${this.ws(w)}/time-entries/${id}`); }
  timer(w: string) { return this.http.get<M.TimeEntry | null>(`${this.ws(w)}/time-entries/timer`); }
  startTimer(w: string, assessmentId: string, description?: string) {
    return this.http.post<M.TimeEntry>(`${this.ws(w)}/time-entries/timer/start`, { assessmentId, billable: true, description });
  }
  stopTimer(w: string) { return this.http.post<M.TimeEntry>(`${this.ws(w)}/time-entries/timer/stop`, {}); }
  timesheetUrl(w: string, from: string, to: string) { return `${this.ws(w)}/time-entries/export.csv?from=${from}&to=${to}`; }
}
