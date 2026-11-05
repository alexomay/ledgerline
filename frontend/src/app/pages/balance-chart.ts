import { Component, ElementRef, computed, input, signal, viewChild } from '@angular/core';
import { Movement } from '../core/api';
import { formatMoney, formatShortDay, formatTime } from '../core/format';

interface Point {
  x: number;
  y: number;
  value: number;
  at: string;
}

/**
 * Saldo después de cada movimiento (una sola serie: sin leyenda, el título la
 * nombra). Escalón en lugar de línea inclinada, porque el saldo no cambia entre
 * movimientos. Cruz con tooltip al pasar el cursor o con las flechas del teclado.
 */
@Component({
  selector: 'll-balance-chart',
  template: `
    <div class="chart">
      <div class="axis-labels" aria-hidden="true">
        @for (t of ticks(); track t.value) {
          <span [style.top.%]="(t.y / H) * 100">{{ t.label }}</span>
        }
      </div>
      <div class="plot" #box (mouseleave)="active.set(null)">
        <svg [attr.viewBox]="'0 0 ' + W + ' ' + H" preserveAspectRatio="none" role="img"
             [attr.aria-label]="summary()" tabindex="0" (keydown)="onKey($event)"
             (mousemove)="onMove($event)" (focus)="active.set(points().length - 1)" (blur)="active.set(null)">
          <g class="grid">
            @for (t of ticks(); track t.value) {
              <line x1="0" [attr.x2]="W" [attr.y1]="t.y" [attr.y2]="t.y" vector-effect="non-scaling-stroke" />
            }
          </g>
          <path class="area" [attr.d]="area()" />
          <path class="line" [attr.d]="line()" vector-effect="non-scaling-stroke" />
          @if (activePoint(); as p) {
            <line class="cross" [attr.x1]="p.x" [attr.x2]="p.x" y1="0" [attr.y2]="H" vector-effect="non-scaling-stroke" />
          }
        </svg>
        @if (activePoint(); as p) {
          <span class="marker" [style.left.%]="(p.x / W) * 100" [style.top.%]="(p.y / H) * 100"></span>
          <div class="tooltip" [style.left.%]="clampPct((p.x / W) * 100)" [style.top.%]="(p.y / H) * 100">
            <strong>{{ money(p.value) }}</strong>, {{ day(p.at) }} {{ time(p.at) }}
          </div>
        }
      </div>
    </div>
  `,
  styles: [
    `
      .chart { position: relative; padding-left: 64px; }
      .plot { position: relative; }
      .axis-labels span {
        position: absolute; left: 0; width: 56px; transform: translateY(-50%);
        font-size: 12px; color: var(--ink-3); text-align: right;
      }
      .marker {
        position: absolute; width: 10px; height: 10px; border-radius: 50%; background: var(--chart);
        border: 2px solid var(--surface); transform: translate(-50%, -50%); pointer-events: none;
      }
    `,
  ],
})
export class BalanceChart {
  movements = input.required<Movement[]>();

  protected readonly W = 600;
  protected readonly H = 160;
  protected active = signal<number | null>(null);
  private box = viewChild.required<ElementRef<HTMLDivElement>>('box');

  protected money = formatMoney;
  protected day = formatShortDay;
  protected time = formatTime;

  private ordered = computed(() => [...this.movements()].sort((a, b) => a.id - b.id));

  private scale = computed(() => {
    const values = this.ordered().map((m) => m.balanceAfter);
    const max = Math.max(...values, 0);
    const step = niceStep(max / 4 || 1);
    const top = Math.max(step, Math.ceil(max / step) * step);
    return { top, step };
  });

  protected points = computed<Point[]>(() => {
    const ms = this.ordered();
    const { top } = this.scale();
    const n = ms.length;
    return ms.map((m, i) => ({
      x: n === 1 ? this.W / 2 : (i / (n - 1)) * this.W,
      y: this.H - (m.balanceAfter / top) * (this.H - 8),
      value: m.balanceAfter,
      at: m.at,
    }));
  });

  protected ticks = computed(() => {
    const { top, step } = this.scale();
    const out = [];
    for (let v = 0; v <= top + 1e-9; v += step) {
      out.push({ value: v, y: this.H - (v / top) * (this.H - 8), label: compact(v) });
    }
    return out;
  });

  protected line = computed(() => {
    const p = this.points();
    if (!p.length) return '';
    let d = `M${p[0].x},${p[0].y}`;
    for (let i = 1; i < p.length; i++) d += ` H${p[i].x} V${p[i].y}`;
    return d;
  });

  protected area = computed(() => {
    const p = this.points();
    if (!p.length) return '';
    return `${this.line()} V${this.H} H${p[0].x} Z`;
  });

  protected activePoint = computed(() => {
    const i = this.active();
    return i === null ? null : (this.points()[i] ?? null);
  });

  protected summary = computed(() => {
    const p = this.points();
    if (!p.length) return 'Sin movimientos';
    return `Saldo después de cada uno de los últimos ${p.length} movimientos. Último: ${formatMoney(p[p.length - 1].value)}. Usa las flechas para recorrerlos.`;
  });

  protected onMove(e: MouseEvent) {
    const rect = (e.currentTarget as SVGElement).getBoundingClientRect();
    const x = ((e.clientX - rect.left) / rect.width) * this.W;
    const pts = this.points();
    let best = 0;
    for (let i = 1; i < pts.length; i++) if (Math.abs(pts[i].x - x) < Math.abs(pts[best].x - x)) best = i;
    this.active.set(pts.length ? best : null);
  }

  protected onKey(e: KeyboardEvent) {
    const n = this.points().length;
    if (!n) return;
    const i = this.active() ?? n - 1;
    if (e.key === 'ArrowLeft') this.active.set(Math.max(0, i - 1));
    else if (e.key === 'ArrowRight') this.active.set(Math.min(n - 1, i + 1));
    else return;
    e.preventDefault();
  }

  protected clampPct(p: number) {
    const width = this.box().nativeElement.clientWidth || 1;
    const margin = (90 / width) * 100;
    return Math.min(100 - margin, Math.max(margin, p));
  }
}

function niceStep(raw: number): number {
  const exp = Math.pow(10, Math.floor(Math.log10(raw)));
  for (const m of [1, 2, 2.5, 5, 10]) if (m * exp >= raw) return m * exp;
  return 10 * exp;
}

function compact(v: number): string {
  if (v >= 1_000_000) return `$${(v / 1_000_000).toLocaleString('es-MX', { maximumFractionDigits: 1 })} M`;
  if (v >= 1_000) return `$${(v / 1_000).toLocaleString('es-MX', { maximumFractionDigits: 1 })} mil`;
  return `$${v.toLocaleString('es-MX')}`;
}
