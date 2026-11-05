import { Component, computed, input } from '@angular/core';
import { Movement } from '../core/api';
import { MoneyPipe, dayKey, dayLabel, formatTime } from '../core/format';

/** Renglones del libro agrupados por día. El saldo después de cada movimiento va debajo del monto. */
@Component({
  selector: 'll-movement-list',
  imports: [MoneyPipe],
  template: `
    @for (group of groups(); track group.key) {
      <div class="day-head">{{ group.label }}</div>
      <ul class="ledger">
        @for (m of group.items; track m.id) {
          <li>
            <span class="sign" [class.in]="m.amount > 0" [class.out]="m.amount < 0" aria-hidden="true">
              {{ m.amount > 0 ? '+' : '−' }}
            </span>
            <span class="what">
              <strong>{{ title(m) }}</strong>
              <span>{{ subtitle(m) }}</span>
            </span>
            <span class="amount" [class.credit]="m.amount > 0" [class.debit]="m.amount < 0">
              <span class="sr-only">{{ m.amount > 0 ? 'Abono de' : 'Cargo de' }}</span>
              {{ m.amount | money: true }}
              <small>Saldo {{ m.balanceAfter | money }}</small>
            </span>
          </li>
        }
      </ul>
    }
  `,
})
export class MovementList {
  movements = input.required<Movement[]>();

  protected groups = computed(() => {
    const out: { key: string; label: string; items: Movement[] }[] = [];
    for (const m of this.movements()) {
      const key = dayKey(m.at);
      const last = out[out.length - 1];
      if (last && last.key === key) last.items.push(m);
      else out.push({ key, label: dayLabel(m.at), items: [m] });
    }
    return out;
  });

  protected title(m: Movement): string {
    if (m.kind === 'DEPOSIT') return `Depósito de ${m.counterparty?.replace(/\s*\(.*\)$/, '') ?? 'SPEI'}`;
    return m.amount < 0 ? `Enviaste a ${m.counterparty}` : `Recibiste de ${m.counterparty}`;
  }

  protected subtitle(m: Movement): string {
    const time = formatTime(m.at);
    if (m.kind === 'DEPOSIT') {
      const bank = m.counterparty?.match(/\((.*)\)$/)?.[1];
      return `${time} · SPEI${bank ? ' desde ' + bank : ''}`;
    }
    return `${time} · ${m.concept ?? 'Transferencia'}`;
  }
}
