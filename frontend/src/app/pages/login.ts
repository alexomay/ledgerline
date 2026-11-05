import { Component, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { Api, ApiError, DemoAccount, DemoInfo } from '../core/api';
import { Auth } from '../core/auth';
import { Icon } from '../core/icons';
import { initials } from '../core/format';
import { AuthSide } from './auth-side';

@Component({
  selector: 'll-login',
  imports: [FormsModule, RouterLink, Icon, AuthSide],
  template: `
    <div class="auth">
      <ll-auth-side />
      <main class="auth-main">
        <form class="auth-form" (ngSubmit)="submit()">
          <h2>Entra a tu cuenta</h2>
          <p>¿No tienes cuenta? <a routerLink="/registro">Abre una en un minuto</a>.</p>

          @if (expirada()) {
            <div class="notice notice-info" role="status" style="margin-bottom:16px">
              <ll-icon name="info" /> Tu sesión expiró. Vuelve a entrar.
            </div>
          }

          @if (demo()?.enabled) {
            <div>
              <strong>Prueba la demo</strong>
              <div class="demo-list">
                @for (a of demo()!.accounts; track a.email) {
                  <button type="button" class="demo-btn" (click)="enterAs(a)" [disabled]="busy()">
                    <span class="avatar">{{ initials(a.label) }}</span>
                    <span>Entrar como {{ a.label }}<small>{{ a.email }}</small></span>
                  </button>
                }
              </div>
            </div>
            <div class="divider">o con tu correo</div>
          }

          <div class="field">
            <label for="email">Correo</label>
            <input id="email" name="email" class="input" type="email" autocomplete="email" required [(ngModel)]="email" />
          </div>
          <div class="field">
            <label for="password">Contraseña</label>
            <input id="password" name="password" class="input" type="password" autocomplete="current-password" required [(ngModel)]="password" />
          </div>

          @if (error()) {
            <div class="notice notice-error" role="alert" style="margin-bottom:16px">
              <ll-icon name="alert" /> {{ error() }}
            </div>
          }

          <button class="btn btn-primary" style="width:100%" type="submit" [disabled]="busy()">
            {{ busy() ? 'Entrando…' : 'Entrar' }}
          </button>
        </form>
      </main>
    </div>
  `,
})
export class Login {
  private api = inject(Api);
  private auth = inject(Auth);
  private router = inject(Router);

  expirada = input<string>();

  protected email = '';
  protected password = '';
  protected busy = signal(false);
  protected error = signal<string | null>(null);
  protected demo = signal<DemoInfo | null>(null);
  protected initials = initials;

  constructor() {
    this.api.demo().then((d) => this.demo.set(d)).catch(() => this.demo.set(null));
  }

  protected enterAs(a: DemoAccount) {
    this.email = a.email;
    this.password = a.password;
    this.submit();
  }

  protected async submit() {
    if (!this.email || !this.password) {
      this.error.set('Escribe tu correo y tu contraseña.');
      return;
    }
    this.busy.set(true);
    this.error.set(null);
    try {
      const t = await this.api.login(this.email, this.password);
      this.auth.set(t.accessToken);
      this.router.navigateByUrl(this.auth.isAdmin() ? '/admin' : '/');
    } catch (e) {
      this.error.set((e as ApiError).message);
    } finally {
      this.busy.set(false);
    }
  }
}
