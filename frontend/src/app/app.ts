import { Component, computed, inject, signal } from '@angular/core';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { toSignal } from '@angular/core/rxjs-interop';
import { filter, map } from 'rxjs';
import { Auth } from './core/auth';
import { Icon } from './core/icons';

type Theme = 'light' | 'dark' | null;

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, Icon],
  template: `
    @if (showChrome()) {
      <header class="topbar">
        <div class="topbar-inner">
          <a class="logo" routerLink="/" aria-label="Ledgerline, inicio">
            <svg viewBox="0 0 32 32" aria-hidden="true"><rect width="32" height="32" rx="7" fill="var(--brand)"/><path d="M9 8v16h14" fill="none" stroke="var(--brand-ink)" stroke-width="3" stroke-linecap="round" stroke-linejoin="round"/><path d="M14 13h9M14 18h6" stroke="var(--credit)" stroke-width="2.4" stroke-linecap="round"/></svg>
            Ledgerline
          </a>
          <nav class="nav" aria-label="Principal">
            @if (auth.isAdmin()) {
              <a routerLink="/admin" routerLinkActive="active">Conciliación</a>
            } @else {
              <a routerLink="/" routerLinkActive="active" [routerLinkActiveOptions]="{ exact: true }">Inicio</a>
              <a routerLink="/enviar" routerLinkActive="active">Enviar</a>
              <a routerLink="/movimientos" routerLinkActive="active">Movimientos</a>
            }
          </nav>
          <div class="who">
            <button class="btn btn-ghost btn-small" type="button" (click)="toggleTheme()"
                    [attr.aria-label]="isDark() ? 'Usar tema claro' : 'Usar tema oscuro'">
              <ll-icon [name]="isDark() ? 'sun' : 'moon'" />
            </button>
            <span class="sr-only">Sesión de</span><span>{{ auth.firstName() }}</span>
            <button class="btn btn-ghost btn-small" type="button" (click)="logout()">Salir</button>
          </div>
        </div>
      </header>
      @if (!auth.isAdmin()) {
        <nav class="tabbar" aria-label="Principal">
          <a routerLink="/" routerLinkActive="active" [routerLinkActiveOptions]="{ exact: true }">Inicio</a>
          <a routerLink="/enviar" routerLinkActive="active">Enviar</a>
          <a routerLink="/movimientos" routerLinkActive="active">Movimientos</a>
        </nav>
      }
    }
    <router-outlet />
  `,
})
export class App {
  protected auth = inject(Auth);
  private router = inject(Router);

  private url = toSignal(
    this.router.events.pipe(
      filter((e) => e instanceof NavigationEnd),
      map((e) => (e as NavigationEnd).urlAfterRedirects),
    ),
    { initialValue: this.router.url },
  );

  protected showChrome = computed(() => {
    const u = this.url();
    return this.auth.loggedIn() && !u.startsWith('/entrar') && !u.startsWith('/registro');
  });

  private theme = signal<Theme>(this.storedTheme());
  protected isDark = computed(() => {
    const t = this.theme();
    return t ? t === 'dark' : matchMedia('(prefers-color-scheme: dark)').matches;
  });

  constructor() {
    this.applyTheme(this.theme());
  }

  protected toggleTheme() {
    const next: Theme = this.isDark() ? 'light' : 'dark';
    this.theme.set(next);
    this.applyTheme(next);
    try {
      localStorage.setItem('ledgerline.theme', next);
    } catch {
      /* preferencia solo para esta visita */
    }
  }

  protected logout() {
    this.auth.clear();
    this.router.navigateByUrl('/entrar');
  }

  private applyTheme(t: Theme) {
    if (t) document.documentElement.dataset['theme'] = t;
  }

  private storedTheme(): Theme {
    try {
      const v = localStorage.getItem('ledgerline.theme');
      return v === 'light' || v === 'dark' ? v : null;
    } catch {
      return null;
    }
  }
}
