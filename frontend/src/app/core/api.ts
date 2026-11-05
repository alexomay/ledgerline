import { HttpClient, HttpErrorResponse, HttpHeaders, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';

export interface Limits {
  perTransfer: number;
  daily: number;
  usedToday: number;
  remainingToday: number;
}

export interface AccountView {
  id: string;
  holder: string;
  email: string;
  clabe: string;
  currency: string;
  balance: number;
  limits: Limits;
}

export interface Movement {
  id: number;
  at: string;
  kind: 'TRANSFER' | 'DEPOSIT';
  amount: number;
  balanceAfter: number;
  description: string;
  counterparty: string | null;
  concept: string | null;
  reference: string | null;
}

export interface MovementPage {
  items: Movement[];
  nextCursor: number | null;
}

export interface TransferResult {
  id: string;
  toClabe: string;
  amount: number;
  concept: string;
  createdAt: string;
  replayed: boolean;
}

export interface Holder {
  clabe: string;
  maskedName: string;
}

export interface DemoAccount {
  label: string;
  email: string;
  password: string;
}

export interface DemoInfo {
  enabled: boolean;
  maxDeposit: number | null;
  accounts: DemoAccount[];
}

export interface Token {
  accessToken: string;
  expiresAt: string;
}

export interface Reconciliation {
  checkedAt: string;
  healthy: boolean;
  accounts: number;
  entries: number;
  postings: number;
  totalBalance: number;
  balanceMismatches: { accountId: string; storedBalance: number; postingsSum: number }[];
  unbalancedEntries: string[];
  negativeCustomerAccounts: number;
}

export interface Overview {
  customers: number;
  customerFunds: number;
  speiClearing: number;
  transfersLast24h: number;
  volumeLast24h: number;
  outboxPending: number;
  outboxFailing: number;
}

/** Error de la API ya traducido a algo que se puede mostrar. */
export class ApiError extends Error {
  constructor(
    message: string,
    readonly status: number,
    readonly code: string | null,
    readonly fields: Record<string, string> = {},
  ) {
    super(message);
  }
}

@Injectable({ providedIn: 'root' })
export class Api {
  private http = inject(HttpClient);

  login(email: string, password: string) {
    return this.call(this.http.post<Token>('/api/auth/login', { email, password }));
  }

  register(fullName: string, email: string, password: string) {
    return this.call(this.http.post<Token>('/api/auth/register', { fullName, email, password }));
  }

  demo() {
    return this.call(this.http.get<DemoInfo>('/api/demo'));
  }

  me() {
    return this.call(this.http.get<AccountView>('/api/accounts/me'));
  }

  movements(before: number | null = null, limit = 20) {
    let params = new HttpParams().set('limit', limit);
    if (before !== null) params = params.set('before', before);
    return this.call(this.http.get<MovementPage>('/api/accounts/me/movements', { params }));
  }

  movementsCsv() {
    return this.call(this.http.get('/api/accounts/me/movements.csv', { responseType: 'blob' }));
  }

  lookup(clabe: string) {
    return this.call(this.http.get<Holder>('/api/accounts/lookup', { params: { clabe } }));
  }

  transfer(idempotencyKey: string, toClabe: string, amount: number, concept: string) {
    const headers = new HttpHeaders({ 'Idempotency-Key': idempotencyKey });
    return this.call(this.http.post<TransferResult>('/api/transfers', { toClabe, amount, concept }, { headers }));
  }

  simulateDeposit(amount: number) {
    return this.call(this.http.post<{ depositId: string; status: string }>('/api/demo/deposits', { amount }));
  }

  reconciliation() {
    return this.call(this.http.get<Reconciliation>('/api/admin/reconciliation'));
  }

  overview() {
    return this.call(this.http.get<Overview>('/api/admin/overview'));
  }

  private async call<T>(request: import('rxjs').Observable<T>): Promise<T> {
    try {
      return await firstValueFrom(request);
    } catch (e) {
      throw toApiError(e);
    }
  }
}

function toApiError(e: unknown): ApiError {
  if (e instanceof HttpErrorResponse) {
    if (e.status === 0) {
      return new ApiError('No hay conexión con el servidor. Revisa tu internet e intenta de nuevo.', 0, 'NETWORK');
    }
    const body = e.error && typeof e.error === 'object' ? e.error : {};
    const message = body.detail ?? 'Algo salió mal. Intenta de nuevo.';
    return new ApiError(message, e.status, body.code ?? null, body.errors ?? {});
  }
  return new ApiError('Algo salió mal. Intenta de nuevo.', -1, null);
}
