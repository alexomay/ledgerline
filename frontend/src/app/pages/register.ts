import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { Api, ApiError } from '../core/api';
import { Auth } from '../core/auth';
import { Icon } from '../core/icons';
import { AuthSide } from './auth-side';

@Component({
  selector: 'll-register',
  imports: [FormsModule, RouterLink, Icon, AuthSide],
  template: `
    <div class="auth">
      <ll-auth-side />
      <main class="auth-main">
        <form class="auth-form" (ngSubmit)="submit()">
          <h2>Abre tu cuenta</h2>
          <p>Te asignamos una CLABE al instante. ¿Ya tienes cuenta? <a routerLink="/entrar">Entra aquí</a>.</p>

          <div class="field">
            <label for="name">Nombre completo</label>
            <input id="name" name="name" class="input" autocomplete="name" [class.invalid]="fields()['fullName']" [(ngModel)]="fullName" />
            @if (fields()['fullName']) { <span class="field-error">{{ fields()['fullName'] }}</span> }
          </div>
          <div class="field">
            <label for="email">Correo</label>
            <input id="email" name="email" class="input" type="email" autocomplete="email" [class.invalid]="fields()['email']" [(ngModel)]="email" />
            @if (fields()['email']) { <span class="field-error">{{ fields()['email'] }}</span> }
          </div>
          <div class="field">
            <label for="password">Contraseña</label>
            <input id="password" name="password" class="input" type="password" autocomplete="new-password" [class.invalid]="fields()['password']" [(ngModel)]="password" aria-describedby="pw-hint" />
            <span class="hint" id="pw-hint">Mínimo 10 caracteres.</span>
            @if (fields()['password']) { <span class="field-error">{{ fields()['password'] }}</span> }
          </div>

          @if (error()) {
            <div class="notice notice-error" role="alert" style="margin-bottom:16px"><ll-icon name="alert" /> {{ error() }}</div>
          }

          <button class="btn btn-primary" style="width:100%" type="submit" [disabled]="busy()">
            {{ busy() ? 'Abriendo tu cuenta…' : 'Abrir cuenta' }}
          </button>
        </form>
      </main>
    </div>
  `,
})
export class Register {
  private api = inject(Api);
  private auth = inject(Auth);
  private router = inject(Router);

  protected fullName = '';
  protected email = '';
  protected password = '';
  protected busy = signal(false);
  protected error = signal<string | null>(null);
  protected fields = signal<Record<string, string>>({});

  protected async submit() {
    this.busy.set(true);
    this.error.set(null);
    this.fields.set({});
    try {
      const t = await this.api.register(this.fullName, this.email, this.password);
      this.auth.set(t.accessToken);
      this.router.navigateByUrl('/');
    } catch (e) {
      const err = e as ApiError;
      if (Object.keys(err.fields).length) this.fields.set(err.fields);
      else this.error.set(err.message);
    } finally {
      this.busy.set(false);
    }
  }
}
