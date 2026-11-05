import { Routes } from '@angular/router';
import { guestOnly, requireAdmin, requireUser } from './core/auth';

export const routes: Routes = [
  { path: 'entrar', canActivate: [guestOnly], title: 'Entrar · Ledgerline', loadComponent: () => import('./pages/login').then((m) => m.Login) },
  { path: 'registro', canActivate: [guestOnly], title: 'Abrir cuenta · Ledgerline', loadComponent: () => import('./pages/register').then((m) => m.Register) },
  { path: '', pathMatch: 'full', canActivate: [requireUser], title: 'Inicio · Ledgerline', loadComponent: () => import('./pages/home').then((m) => m.Home) },
  { path: 'enviar', canActivate: [requireUser], title: 'Enviar dinero · Ledgerline', loadComponent: () => import('./pages/send').then((m) => m.Send) },
  { path: 'movimientos', canActivate: [requireUser], title: 'Movimientos · Ledgerline', loadComponent: () => import('./pages/movements').then((m) => m.Movements) },
  { path: 'admin', canActivate: [requireAdmin], title: 'Conciliación · Ledgerline', loadComponent: () => import('./pages/admin').then((m) => m.Admin) },
  { path: '**', redirectTo: '' },
];
