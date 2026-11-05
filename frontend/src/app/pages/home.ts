import { Component, ElementRef, OnDestroy, computed, inject, signal, viewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { AccountView, Api, ApiError, DemoInfo, Movement } from '../core/api';
import { Icon } from '../core/icons';
import { ClabePipe, MoneyPipe, formatMoney } from '../core/format';
import { BalanceChart } from './balance-chart';
import { MovementList } from './movement-list';

@Component({
  selector: 'll-home',
  imports: [RouterLink, FormsModule, Icon, MoneyPipe, ClabePipe, BalanceChart, MovementList],
  template: `
    <main>
      @if (loadError()) {
        <div class="notice notice-error" role="alert"><ll-icon name="alert" /> {{ loadError() }}
          <button class="btn-link" type="button" (click)="load()">Reintentar</button></div>
      }
      @if (account(); as a) {
        <div class="grid-home">
          <div class="stack">
            <section class="panel balance-card" aria-labelledby="saldo">
              <p class="label" id="saldo">Saldo disponible</p>
              <p class="balance">{{ whole() }}<span class="cents">{{ cents() }}</span></p>
              <div class="clabe-row">
                <span>CLABE</span>
                <span class="clabe">{{ a.clabe | clabe }}</span>
                <button class="btn btn-ghost btn-small" type="button" (click)="copy(a.clabe)">
                  <ll-icon name="copy" [size]="16" /> {{ copied() ? 'Copiada' : 'Copiar' }}
                </button>
              </div>
              <div class="actions">
                <a class="btn btn-primary" routerLink="/enviar"><ll-icon name="send" /> Enviar dinero</a>
                @if (demo()?.enabled) {
                  <button class="btn btn-ghost" type="button" (click)="openDeposit()"><ll-icon name="plus" /> Simular depósito</button>
                }
              </div>
            </section>

            <section class="panel" aria-labelledby="limite">
              <div class="panel-head"><h2 id="limite">Límite diario de envíos</h2></div>
              <div class="meter">
                <div class="meter-track" role="meter" [attr.aria-valuenow]="a.limits.usedToday" aria-valuemin="0"
                     [attr.aria-valuemax]="a.limits.daily" [attr.aria-valuetext]="'Usado hoy ' + (a.limits.usedToday | money)">
                  <div class="meter-fill" [style.width.%]="usedPct()"></div>
                </div>
                <div class="meter-legend">
                  <span>Enviado hoy {{ a.limits.usedToday | money }}</span>
                  <span>Disponible {{ a.limits.remainingToday | money }} de {{ a.limits.daily | money }}</span>
                </div>
                <p class="small muted" style="margin-top:10px">Máximo {{ a.limits.perTransfer | money }} por envío. El límite se reinicia a medianoche, hora del centro de México.</p>
              </div>
            </section>
          </div>

          <div class="stack">
            @if (movements().length > 1) {
              <section class="panel" aria-labelledby="evolucion">
                <div class="panel-head"><h2 id="evolucion">Tu saldo en los últimos movimientos</h2></div>
                <ll-balance-chart [movements]="movements()" />
              </section>
            }
            <section class="panel" aria-labelledby="recientes">
              <div class="panel-head">
                <h2 id="recientes">Movimientos recientes</h2>
                @if (movements().length) { <a routerLink="/movimientos">Ver todos</a> }
              </div>
              @if (movements().length) {
                <ll-movement-list [movements]="recent()" />
              } @else {
                <div class="empty">
                  <p>Aún no tienes movimientos. Comparte tu CLABE para recibir dinero{{ demo()?.enabled ? ' o simula un depósito' : '' }}.</p>
                  @if (demo()?.enabled) {
                    <button class="btn btn-primary" type="button" (click)="openDeposit()">Simular depósito</button>
                  }
                </div>
              }
            </section>
          </div>
        </div>
      }
    </main>

    <dialog #depositDialog aria-labelledby="dep-title" (close)="depositError.set(null)">
      <form (ngSubmit)="deposit()">
        <h2 id="dep-title" style="margin-bottom:6px">Simular depósito SPEI</h2>
        <p class="muted small" style="margin-bottom:16px">
          Pasa por el mismo camino que un depósito real: el simulador firma la notificación como lo haría el banco y
          el servidor verifica la firma antes de abonar.
        </p>
        <div class="field">
          <label for="dep-amount">Monto</label>
          <div class="input-money"><span>$</span>
            <input id="dep-amount" name="amount" class="input" inputmode="decimal" [(ngModel)]="depositAmount" />
          </div>
          <span class="hint">Máximo {{ demo()?.maxDeposit ?? 0 | money }} por depósito.</span>
        </div>
        @if (depositError()) {
          <div class="notice notice-error" role="alert" style="margin-bottom:12px"><ll-icon name="alert" /> {{ depositError() }}</div>
        }
        <div class="dialog-actions">
          <button class="btn btn-ghost" type="button" (click)="closeDeposit()">Cancelar</button>
          <button class="btn btn-primary" type="submit" [disabled]="depositing()">{{ depositing() ? 'Depositando…' : 'Depositar' }}</button>
        </div>
      </form>
    </dialog>
  `,
})
export class Home implements OnDestroy {
  private api = inject(Api);

  protected account = signal<AccountView | null>(null);
  protected movements = signal<Movement[]>([]);
  protected recent = computed(() => this.movements().slice(0, 6));
  protected demo = signal<DemoInfo | null>(null);
  protected loadError = signal<string | null>(null);
  protected copied = signal(false);

  protected depositAmount = '1500';
  protected depositing = signal(false);
  protected depositError = signal<string | null>(null);
  private dialog = viewChild.required<ElementRef<HTMLDialogElement>>('depositDialog');

  protected whole = computed(() => {
    const b = this.account()?.balance ?? 0;
    return formatMoney(Math.trunc(b)).replace(/\.00$/, '');
  });
  protected cents = computed(() => '.' + ((this.account()?.balance ?? 0).toFixed(2).split('.')[1] ?? '00'));
  protected usedPct = computed(() => {
    const l = this.account()?.limits;
    return l ? Math.min(100, (l.usedToday / l.daily) * 100) : 0;
  });

  /** Actualiza saldo y movimientos cada 10 s para que se vea llegar una transferencia. */
  private timer = setInterval(() => {
    if (document.visibilityState === 'visible') this.load(true);
  }, 10_000);

  constructor() {
    this.load();
    this.api.demo().then((d) => this.demo.set(d)).catch(() => undefined);
  }

  ngOnDestroy() {
    clearInterval(this.timer);
  }

  async load(quiet = false) {
    try {
      const [a, m] = await Promise.all([this.api.me(), this.api.movements(null, 30)]);
      this.account.set(a);
      this.movements.set(m.items);
      this.loadError.set(null);
    } catch (e) {
      if (!quiet) this.loadError.set((e as ApiError).message);
    }
  }

  protected async copy(clabe: string) {
    try {
      await navigator.clipboard.writeText(clabe);
      this.copied.set(true);
      setTimeout(() => this.copied.set(false), 2000);
    } catch {
      /* el portapapeles puede estar bloqueado; la CLABE sigue visible */
    }
  }

  protected openDeposit() {
    this.dialog().nativeElement.showModal();
  }

  protected closeDeposit() {
    this.dialog().nativeElement.close();
  }

  protected async deposit() {
    const amount = Number(String(this.depositAmount).replace(/[,\s$]/g, ''));
    if (!Number.isFinite(amount) || amount <= 0) {
      this.depositError.set('Escribe un monto mayor a cero.');
      return;
    }
    this.depositing.set(true);
    this.depositError.set(null);
    try {
      await this.api.simulateDeposit(amount);
      this.closeDeposit();
      await this.load();
    } catch (e) {
      this.depositError.set((e as ApiError).message);
    } finally {
      this.depositing.set(false);
    }
  }
}
