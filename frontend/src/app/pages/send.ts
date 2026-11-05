import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { AccountView, Api, ApiError, Holder, TransferResult } from '../core/api';
import { Icon } from '../core/icons';
import { ClabePipe, MoneyPipe, formatFull, initials, isValidClabe } from '../core/format';
import { TraceView } from './trace-view';

type Step = 'form' | 'confirm' | 'done';

@Component({
  selector: 'll-send',
  imports: [FormsModule, RouterLink, Icon, MoneyPipe, ClabePipe, TraceView],
  template: `
    <main>
      @switch (step()) {
        @case ('form') {
          <div class="page-head">
            <div>
              <h1>Enviar dinero</h1>
              @if (account(); as a) { <p>Disponible {{ a.balance | money }}. Hoy aún puedes enviar {{ a.limits.remainingToday | money }}.</p> }
            </div>
          </div>
          <form class="panel" style="max-width:560px" (ngSubmit)="review()" novalidate>
            <div class="field">
              <label for="clabe">CLABE de destino</label>
              <input id="clabe" name="clabe" class="input" inputmode="numeric" autocomplete="off" maxlength="22"
                     [class.invalid]="clabeError()" [ngModel]="clabe()" (ngModelChange)="onClabe($event)"
                     aria-describedby="clabe-hint" />
              <span class="hint" id="clabe-hint">18 dígitos. Para la demo, prueba con la CLABE de Luis o de Ana.</span>
              @if (clabeError()) { <span class="field-error">{{ clabeError() }}</span> }
              @if (holder(); as h) {
                <div class="holder" role="status">
                  <span class="avatar">{{ initials(h.maskedName) }}</span>
                  <span>Titular: <strong>{{ h.maskedName }}</strong></span>
                </div>
              }
            </div>
            <div class="field">
              <label for="amount">Monto</label>
              <div class="input-money"><span>$</span>
                <input id="amount" name="amount" class="input" inputmode="decimal" autocomplete="off"
                       [class.invalid]="amountError()" [(ngModel)]="amountText" (ngModelChange)="amountError.set(null)" />
              </div>
              @if (amountError()) { <span class="field-error">{{ amountError() }}</span> }
            </div>
            <div class="field">
              <label for="concept">Concepto</label>
              <input id="concept" name="concept" class="input" maxlength="140" [(ngModel)]="concept" placeholder="Por ejemplo: renta de noviembre" />
            </div>
            @if (error()) { <div class="notice notice-error" role="alert" style="margin-bottom:16px"><ll-icon name="alert" /> {{ error() }}</div> }
            <button class="btn btn-primary" type="submit" [disabled]="!holder()">Revisar envío</button>
          </form>
        }

        @case ('confirm') {
          <div class="page-head"><div><h1>Revisa tu envío</h1><p>Así quedará registrado en el libro. Confirma para enviar.</p></div></div>
          <div style="max-width:560px" class="stack">
            <div class="entry">
              <div class="entry-head">
                <div><h3>{{ amount() | money }} para {{ holder()?.maskedName }}</h3><p>{{ concept }}</p></div>
              </div>
              <table>
                <caption class="sr-only">Movimientos del asiento</caption>
                <thead><tr><th scope="col">Cuenta</th><th scope="col" class="num">Movimiento</th></tr></thead>
                <tbody>
                  <tr><td>Tu cuenta · {{ account()?.clabe | clabe }}</td><td class="num">{{ -amount() | money: true }}</td></tr>
                  <tr><td>{{ holder()?.maskedName }} · {{ clabe() | clabe }}</td><td class="num credit">{{ amount() | money: true }}</td></tr>
                </tbody>
              </table>
            </div>
            @if (error()) {
              <div class="notice notice-error" role="alert"><ll-icon name="alert" /> <span>{{ error() }}
                @if (canRetry()) { Puedes reintentar sin riesgo: si el primer envío sí llegó, no se cobra dos veces. }</span></div>
            }
            <div class="row">
              <button class="btn btn-primary" type="button" (click)="send()" [disabled]="sending()">
                {{ sending() ? 'Enviando…' : canRetry() ? 'Reintentar envío' : 'Enviar ' + (amount() | money) }}
              </button>
              <button class="btn btn-ghost" type="button" (click)="edit()" [disabled]="sending()">Corregir datos</button>
            </div>
          </div>
        }

        @case ('done') {
          @if (result(); as r) {
            <div class="receipt stack">
              <div class="notice notice-ok" role="status"><ll-icon name="check" /> Enviado. {{ holder()?.maskedName }} ya tiene el dinero en su cuenta.</div>
              <section class="panel">
                <p class="muted">Comprobante de envío</p>
                <p class="receipt-amount">{{ r.amount | money }}</p>
                <p>{{ r.concept }}</p>
                <dl>
                  <dt>Para</dt><dd>{{ holder()?.maskedName }}</dd>
                  <dt>CLABE</dt><dd>{{ r.toClabe | clabe }}</dd>
                  <dt>Fecha</dt><dd>{{ full(r.createdAt) }}</dd>
                  <dt>Referencia</dt><dd>{{ r.id }}</dd>
                </dl>
                <div class="entry">
                  <table>
                    <caption class="sr-only">Asiento contable del envío</caption>
                    <thead><tr><th scope="col">Asiento</th><th scope="col" class="num">Movimiento</th></tr></thead>
                    <tbody>
                      <tr><td>Cargo a tu cuenta</td><td class="num">{{ -r.amount | money: true }}</td></tr>
                      <tr><td>Abono a {{ holder()?.maskedName }}</td><td class="num credit">{{ r.amount | money: true }}</td></tr>
                    </tbody>
                    <tfoot>
                      <tr><td>Suma</td><td class="num"><span class="zero"><ll-icon name="check" [size]="16" /> {{ 0 | money }}</span></td></tr>
                    </tfoot>
                  </table>
                </div>
              </section>
              <ll-trace [steps]="r.trace.steps" [total]="r.trace.totalMs" />

              <section class="panel replay">
                <h2>Prueba la idempotencia</h2>
                <p class="muted">Reenvía exactamente la misma petición, con la misma llave <span class="mono-id">{{ sentKey }}</span>. Es lo que pasa cuando se corta la red y la app reintenta.</p>
                <div class="replay-row">
                  <button class="btn btn-ghost" type="button" (click)="replay()" [disabled]="replaying()">
                    {{ replaying() ? 'Reenviando…' : 'Reenviar la misma petición' }}
                  </button>
                  @if (replayResult(); as rr) {
                    <span class="pill" [class.ok]="rr.replayed && rr.id === r.id" [class.bad]="!rr.replayed || rr.id !== r.id">
                      <ll-icon [name]="rr.replayed && rr.id === r.id ? 'check' : 'alert'" [size]="16" />
                      {{ rr.replayed && rr.id === r.id ? 'Misma transferencia, sin segundo cargo' : 'Se creó otra transferencia' }}
                    </span>
                  }
                </div>
                @if (replayResult(); as rr) {
                  <ll-trace [steps]="rr.trace.steps" [total]="rr.trace.totalMs" title="Respuesta al reintento" />
                }
              </section>

              <div class="row">
                <button class="btn btn-primary" type="button" (click)="reset()">Hacer otro envío</button>
                <a class="btn btn-ghost" routerLink="/">Volver al inicio</a>
              </div>
            </div>
          }
        }
      }
    </main>
  `,
})
export class Send {
  private api = inject(Api);

  protected step = signal<Step>('form');
  protected account = signal<AccountView | null>(null);
  protected clabe = signal('');
  protected holder = signal<Holder | null>(null);
  protected clabeError = signal<string | null>(null);
  protected amountText = '';
  protected amount = signal(0);
  protected amountError = signal<string | null>(null);
  protected concept = '';
  protected error = signal<string | null>(null);
  protected sending = signal(false);
  protected canRetry = signal(false);
  protected result = signal<TransferResult | null>(null);
  protected replayResult = signal<TransferResult | null>(null);
  protected replaying = signal(false);
  /** Llave del envío ya hecho, para la demostración de reintento. */
  protected sentKey = '';
  protected initials = initials;
  protected full = formatFull;

  /**
   * Una llave por envío. Se genera al revisar y se conserva en los reintentos:
   * si la primera petición sí llegó pero se perdió la respuesta, el servidor
   * devuelve la misma transferencia en lugar de cobrar otra vez.
   */
  private idempotencyKey: string | null = null;
  private lookupSeq = 0;

  constructor() {
    this.api.me().then((a) => this.account.set(a)).catch(() => undefined);
  }

  protected async onClabe(raw: string) {
    const digits = raw.replace(/\D/g, '').slice(0, 18);
    this.clabe.set(digits);
    this.holder.set(null);
    this.clabeError.set(null);
    if (digits.length < 18) return;
    if (!isValidClabe(digits)) {
      this.clabeError.set('El dígito verificador no coincide. Revisa que la CLABE esté completa y bien escrita.');
      return;
    }
    if (digits === this.account()?.clabe) {
      this.clabeError.set('Esa es tu propia CLABE.');
      return;
    }
    const seq = ++this.lookupSeq;
    try {
      const h = await this.api.lookup(digits);
      if (seq === this.lookupSeq) this.holder.set(h);
    } catch (e) {
      if (seq === this.lookupSeq) this.clabeError.set((e as ApiError).message);
    }
  }

  protected review() {
    this.error.set(null);
    const text = String(this.amountText).replace(/[,\s$]/g, '');
    if (!/^\d+(\.\d{1,2})?$/.test(text) || Number(text) <= 0) {
      this.amountError.set('Escribe un monto mayor a cero, con máximo dos decimales.');
      return;
    }
    const value = Number(text);
    const a = this.account();
    if (a && value > a.balance) {
      this.amountError.set(`Tu saldo disponible es ${new Intl.NumberFormat('es-MX', { style: 'currency', currency: 'MXN' }).format(a.balance)}.`);
      return;
    }
    if (!this.concept.trim()) this.concept = 'Transferencia';
    this.amount.set(value);
    this.idempotencyKey = crypto.randomUUID();
    this.canRetry.set(false);
    this.step.set('confirm');
  }

  protected async send() {
    if (!this.idempotencyKey) return;
    this.sending.set(true);
    this.error.set(null);
    try {
      const r = await this.api.transfer(this.idempotencyKey, this.clabe(), this.amount(), this.concept.trim());
      this.result.set(r);
      this.sentKey = this.idempotencyKey;
      this.replayResult.set(null);
      this.idempotencyKey = null;
      this.step.set('done');
    } catch (e) {
      const err = e as ApiError;
      this.error.set(err.message);
      // Error de red o del servidor: el envío pudo o no haberse hecho; reintentar con la misma llave es seguro.
      this.canRetry.set(err.status === 0 || err.status >= 500);
    } finally {
      this.sending.set(false);
    }
  }

  protected async replay() {
    this.replaying.set(true);
    try {
      this.replayResult.set(await this.api.transfer(this.sentKey, this.clabe(), this.amount(), this.concept.trim()));
      this.api.me().then((a) => this.account.set(a)).catch(() => undefined);
    } catch (e) {
      this.error.set((e as ApiError).message);
    } finally {
      this.replaying.set(false);
    }
  }

  protected edit() {
    this.idempotencyKey = null;
    this.error.set(null);
    this.step.set('form');
  }

  protected reset() {
    this.clabe.set('');
    this.holder.set(null);
    this.amountText = '';
    this.concept = '';
    this.result.set(null);
    this.replayResult.set(null);
    this.step.set('form');
    this.api.me().then((a) => this.account.set(a)).catch(() => undefined);
  }
}
