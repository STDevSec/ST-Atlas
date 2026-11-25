import { Routes } from '@angular/router';
import { authGuard, setupGuard } from './core/guards';

export const routes: Routes = [
  { path: 'setup', canActivate: [setupGuard], loadComponent: () => import('./pages/setup.page').then((m) => m.SetupPage) },
  { path: 'login', loadComponent: () => import('./pages/login.page').then((m) => m.LoginPage) },
  {
    path: '',
    canActivate: [authGuard],
    loadComponent: () => import('./layout/shell.component').then((m) => m.ShellComponent),
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'dashboard' },
      { path: 'dashboard', loadComponent: () => import('./pages/dashboard.page').then((m) => m.DashboardPage) },
      { path: 'assessments', loadComponent: () => import('./pages/assessments.page').then((m) => m.AssessmentsPage) },
      { path: 'assessments/new', loadComponent: () => import('./pages/assessment-form.page').then((m) => m.AssessmentFormPage) },
      { path: 'assessments/:id', loadComponent: () => import('./pages/assessment-detail.page').then((m) => m.AssessmentDetailPage) },
      { path: 'assessments/:id/edit', loadComponent: () => import('./pages/assessment-form.page').then((m) => m.AssessmentFormPage) },
      { path: 'assessments/:assessmentId/findings/new', loadComponent: () => import('./pages/finding-form.page').then((m) => m.FindingFormPage) },
      { path: 'findings', loadComponent: () => import('./pages/findings.page').then((m) => m.FindingsPage) },
      { path: 'findings/:findingId', loadComponent: () => import('./pages/finding-form.page').then((m) => m.FindingFormPage) },
      { path: 'clients', loadComponent: () => import('./pages/clients.page').then((m) => m.ClientsPage) },
      { path: 'clients/new', loadComponent: () => import('./pages/client-form.page').then((m) => m.ClientFormPage) },
      { path: 'clients/:id', loadComponent: () => import('./pages/client-form.page').then((m) => m.ClientFormPage) },
      { path: 'reports', loadComponent: () => import('./pages/reports.page').then((m) => m.ReportsPage) },
      { path: 'reports/:id', loadComponent: () => import('./pages/report-studio.page').then((m) => m.ReportStudioPage) },
      { path: 'reports/:id/versions/:number', loadComponent: () => import('./pages/pdf-viewer.page').then((m) => m.PdfViewerPage) },
      { path: 'search', loadComponent: () => import('./pages/search.page').then((m) => m.SearchPage) },
      { path: 'time', loadComponent: () => import('./pages/time.page').then((m) => m.TimePage) },
      { path: 'templates', loadComponent: () => import('./pages/templates.page').then((m) => m.TemplatesPage) },
      { path: 'audit', loadComponent: () => import('./pages/audit.page').then((m) => m.AuditPage) },
      { path: 'settings', loadComponent: () => import('./pages/settings.page').then((m) => m.SettingsPage) },
    ],
  },
  { path: '**', redirectTo: '' },
];
