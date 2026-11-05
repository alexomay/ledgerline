import { HttpInterceptorFn } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';

const STORAGE_KEY = 'ledgerline.session';

interface Claims {
  sub: string;
  name: string;
  email: string;
  roles: string[];
  exp: number;
}

function decode(token: string): Claims | null {
  try {
    const payload = token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/');
    const json = decodeURIComponent(
      atob(payload)
        .split('')
        .map((c) => '%' + c.charCodeAt(0).toString(16).padStart(2, '0'))
        .join(''),
    );
    return JSON.parse(json) as Claims;
  } catch {
    return null;
  }
}

function readStored(): string | null {
  try {
    return sessionStorage.getItem(STORAGE_KEY);
  } catch {
    return null;
  }
}

/**
 * La sesión vive en sessionStorage: se borra al cerrar la pestaña, como en la
 * banca en línea. El token expira en el servidor de todos modos.
 */
@Injectable({ providedIn: 'root' })
export class Auth {
  private readonly token = signal<string | null>(readStored());

  readonly claims = computed(() => {
    const t = this.token();
    const c = t ? decode(t) : null;
    return c && c.exp * 1000 > Date.now() ? c : null;
  });
  readonly loggedIn = computed(() => this.claims() !== null);
  readonly isAdmin = computed(() => this.claims()?.roles?.includes('ADMIN') ?? false);
  readonly firstName = computed(() => this.claims()?.name.split(' ')[0] ?? '');

  accessToken(): string | null {
    return this.loggedIn() ? this.token() : null;
  }

  set(token: string) {
    this.token.set(token);
    try {
      sessionStorage.setItem(STORAGE_KEY, token);
    } catch {
      /* sin almacenamiento: la sesión dura lo que dure la página */
    }
  }

  clear() {
    this.token.set(null);
    try {
      sessionStorage.removeItem(STORAGE_KEY);
    } catch {
      /* nada que limpiar */
    }
  }
}

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(Auth);
  const router = inject(Router);
  const token = auth.accessToken();
  const request = token ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } }) : req;
  return next(request).pipe(
    catchError((err) => {
      if (err?.status === 401 && token) {
        auth.clear();
        router.navigate(['/entrar'], { queryParams: { expirada: 1 } });
      }
      return throwError(() => err);
    }),
  );
};

export const requireUser: CanActivateFn = () => {
  const auth = inject(Auth);
  const router = inject(Router);
  if (!auth.loggedIn()) return router.parseUrl('/entrar');
  if (auth.isAdmin()) return router.parseUrl('/admin');
  return true;
};

export const requireAdmin: CanActivateFn = () => {
  const auth = inject(Auth);
  const router = inject(Router);
  if (!auth.loggedIn()) return router.parseUrl('/entrar');
  return auth.isAdmin() ? true : router.parseUrl('/');
};

export const guestOnly: CanActivateFn = () => {
  const auth = inject(Auth);
  const router = inject(Router);
  if (!auth.loggedIn()) return true;
  return router.parseUrl(auth.isAdmin() ? '/admin' : '/');
};
