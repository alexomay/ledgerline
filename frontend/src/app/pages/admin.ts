import { Component, OnDestroy, computed, inject, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { Api, ApiError, OutboxEvent, Overview, Reconciliation, StressResult } from '../core/api';
import { Icon } from '../core/icons';
import { MoneyPipe, formatFull, formatTime } from '../core/format';

interface Check {
  ok: boolean;
  title: string;
  detail: string;
}

@Component({
  selector: 'll-admin',
  imports: [Icon, MoneyPipe, DecimalPipe],
  template: `
    <main class="stack">
      <div class="page-head">
        <div>
          <h1>Conciliación</h1>
          <p>Comprueba que el libro cuadre. También corre sola cada hora.</p>
        </div>
        <button class="btn btn-primary" type="button" (click)="run()" [disabled]="running()">
          {{ running() ? 'Conciliando…' : 'Conciliar ahora' }}
        </button>
      </div>

      @if (error()) {
        <div class="notice notice-error" role="alert"><ll-icon name="alert" /> {{ error() }}</div>
      }

      @if (overview(); as o) {
        <div class="stats">
          <div class="stat"><span>Clientes</span><strong>{{ o.customers }}</strong></div>
          <div class="stat"><span>Fondos de clientes</span><strong>{{ o.customerFunds | money }}</strong></div>
          <div class="stat"><span>Transferencias, 24 h</span><strong>{{ o.transfersLast24h }}</strong></div>
          <div class="stat"><span>Volumen, 24 h</span><strong>{{ o.volumeLast24h | money }}</strong></div>
          <div class="stat"><span>Eventos por publicar</span><strong>{{ o.outboxPending }}</strong></div>
        </div>
      }

      <div class="two-col">
        <section class="panel" aria-labelledby="carga">
          <div class="panel-head">
            <h2 id="carga">Prueba de carga</h2>
            <button class="btn btn-ghost btn-small" type="button" (click)="runStress()" [disabled]="stressing()">
              {{ stressing() ? 'Ejecutando…' : 'Lanzar 100 transferencias' }}
            </button>
          </div>
          <p class="muted small">100 transferencias simultáneas desde 8 hilos entre las cuentas de Ana y Luis, en ambos sentidos.
            Al terminar se concilia el libro completo.</p>
          @if (stress(); as st) {
            <div class="stress-grid">
              <div class="stat"><span>Exitosas</span><strong>{{ st.succeeded }}/{{ st.requested }}</strong></div>
              <div class="stat"><span>Tiempo total</span><strong>{{ st.elapsedMs | number: '1.0-0' }} ms</strong></div>
              <div class="stat"><span>Por segundo</span><strong>{{ st.perSecond | number: '1.0-0' }}</strong></div>
            </div>
            <ul class="checks" style="margin-top:8px">
              <li [class.pass]="st.moneyConserved" [class.fail]="!st.moneyConserved">
                <span class="badge"><ll-icon [name]="st.moneyConserved ? 'check' : 'x'" /></span>
                <span><strong>El dinero se conservó</strong><p>Ana + Luis antes: {{ st.totalBefore }} · después: {{ st.totalAfter }}</p></span>
              </li>
              <li [class.pass]="st.reconciliationHealthy" [class.fail]="!st.reconciliationHealthy">
                <span class="badge"><ll-icon [name]="st.reconciliationHealthy ? 'check' : 'x'" /></span>
                <span><strong>La conciliación cuadra</strong><p>{{ rejectedText(st) }}</p></span>
              </li>
            </ul>
          }
        </section>

        <section class="panel" aria-labelledby="eventos">
          <div class="panel-head">
            <h2 id="eventos">Eventos del outbox</h2>
            <span class="small muted">Se publican cada segundo</span>
          </div>
          @if (events().length) {
            <ul class="events">
              @for (e of events(); track e.id) {
                <li>
                  <span><strong>{{ e.type }}</strong>&nbsp; <span class="when">#{{ e.id }} · {{ time(e.createdAt) }}</span></span>
                  <span class="pill" [class.ok]="e.publishedAt">{{ e.publishedAt ? 'Publicado' : 'Pendiente' }}</span>
                </li>
              }
            </ul>
          } @else {
            <p class="muted">Aún no hay eventos.</p>
          }
        </section>
      </div>

      @if (report(); as r) {
        <section class="panel" aria-labelledby="resultado">
          <div class="panel-head">
            <h2 id="resultado">{{ r.healthy ? 'El libro cuadra' : 'Hay diferencias en el libro' }}</h2>
            <span class="small muted">{{ checkedAt() }}</span>
          </div>
          <ul class="checks">
            @for (c of checks(); track c.title) {
              <li [class.pass]="c.ok" [class.fail]="!c.ok">
                <span class="badge"><ll-icon [name]="c.ok ? 'check' : 'x'" /></span>
                <span><strong>{{ c.title }}</strong><p>{{ c.detail }}</p></span>
              </li>
            }
          </ul>
        </section>

        <section class="entry" aria-labelledby="balanza">
          <div class="entry-head">
            <div><h3 id="balanza">Balanza</h3><p>Lo que tienen los clientes sale de la cuenta puente de SPEI: la suma total es cero.</p></div>
          </div>
          <table>
            <thead><tr><th scope="col">Cuenta</th><th scope="col" class="num">Saldo</th></tr></thead>
            <tbody>
              <tr><td>Cuentas de clientes ({{ overview()?.customers }})</td><td class="num">{{ overview()?.customerFunds ?? 0 | money }}</td></tr>
              <tr><td>Cuenta puente SPEI (lo que el banco nos debe)</td><td class="num">{{ overview()?.speiClearing ?? 0 | money }}</td></tr>
            </tbody>
            <tfoot>
              <tr><td>Total</td><td class="num">
                <span [class.zero]="r.totalBalance === 0"><ll-icon [name]="r.totalBalance === 0 ? 'check' : 'alert'" [size]="16" /> {{ r.totalBalance / 100 | money }}</span>
              </td></tr>
            </tfoot>
          </table>
        </section>
      }
    </main>
  `,
})
export class Admin implements OnDestroy {
  private api = inject(Api);

  protected report = signal<Reconciliation | null>(null);
  protected overview = signal<Overview | null>(null);
  protected running = signal(false);
  protected error = signal<string | null>(null);
  protected stress = signal<StressResult | null>(null);
  protected stressing = signal(false);
  protected events = signal<OutboxEvent[]>([]);
  protected time = formatTime;
  private timer = setInterval(() => this.loadEvents(), 3000);

  protected checkedAt = computed(() => {
    const r = this.report();
    return r ? 'Revisado el ' + formatFull(r.checkedAt) : '';
  });

  protected checks = computed<Check[]>(() => {
    const r = this.report();
    if (!r) return [];
    const n = (x: number) => x.toLocaleString('es-MX');
    return [
      {
        ok: r.balanceMismatches.length === 0,
        title: 'Saldos contra movimientos',
        detail: r.balanceMismatches.length === 0
          ? `Las ${n(r.accounts)} cuentas tienen un saldo igual a la suma de sus ${n(r.postings)} movimientos.`
          : `${r.balanceMismatches.length} cuentas no coinciden con sus movimientos.`,
      },
      {
        ok: r.unbalancedEntries.length === 0,
        title: 'Asientos en cero',
        detail: r.unbalancedEntries.length === 0
          ? `Los ${n(r.entries)} asientos suman cero: cada cargo tiene su abono.`
          : `${r.unbalancedEntries.length} asientos no suman cero.`,
      },
      {
        ok: r.totalBalance === 0,
        title: 'Total del sistema en cero',
        detail: r.totalBalance === 0
          ? 'No se ha creado ni perdido dinero: todo lo que tienen los clientes entró por SPEI.'
          : `El total es ${r.totalBalance} centavos en lugar de cero.`,
      },
      {
        ok: r.negativeCustomerAccounts === 0,
        title: 'Ninguna cuenta de cliente en negativo',
        detail: r.negativeCustomerAccounts === 0
          ? 'Todas las cuentas de clientes tienen saldo cero o positivo.'
          : `${r.negativeCustomerAccounts} cuentas de clientes están en negativo.`,
      },
    ];
  });

  constructor() {
    this.run();
    this.loadEvents();
  }

  ngOnDestroy() {
    clearInterval(this.timer);
  }

  protected rejectedText(st: StressResult) {
    const entries = Object.entries(st.rejected);
    if (!entries.length) return 'Ninguna transferencia fue rechazada.';
    const names: Record<string, string> = { INSUFFICIENT_FUNDS: 'saldo insuficiente', LIMIT_EXCEEDED: 'límite diario' };
    return 'Rechazadas por regla de negocio: ' + entries.map(([k, v]) => `${v} por ${names[k] ?? k}`).join(', ') + '.';
  }

  protected async runStress() {
    this.stressing.set(true);
    this.error.set(null);
    try {
      this.stress.set(await this.api.stress());
      await Promise.all([this.run(), this.loadEvents()]);
    } catch (e) {
      this.error.set((e as ApiError).message);
    } finally {
      this.stressing.set(false);
    }
  }

  private async loadEvents() {
    try {
      this.events.set(await this.api.events());
    } catch {
      /* el panel sigue funcionando sin la lista de eventos */
    }
  }

  protected async run() {
    this.running.set(true);
    try {
      const [r, o] = await Promise.all([this.api.reconciliation(), this.api.overview()]);
      this.report.set(r);
      this.overview.set(o);
      this.error.set(null);
    } catch (e) {
      this.error.set((e as ApiError).message);
    } finally {
      this.running.set(false);
    }
  }
}
