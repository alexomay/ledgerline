import { Component, computed, input } from '@angular/core';
import { TraceStep } from '../core/api';

/**
 * Cascada de pasos del servidor, como la pestaña de red del navegador: cada
 * barra empieza donde terminó la anterior y su ancho es el tiempo real medido.
 */
@Component({
  selector: 'll-trace',
  template: `
    <div class="trace">
      <div class="trace-head">
        <span>{{ title() }}</span>
        <strong>{{ fmt(total()) }}</strong>
      </div>
      <ol>
        @for (s of rows(); track $index) {
          <li>
            <div class="trace-label">
              <strong>{{ s.name }}</strong>
              <span>{{ s.detail }}</span>
            </div>
            <div class="trace-bar" aria-hidden="true">
              <span [style.left.%]="s.offset" [style.width.%]="s.width"></span>
            </div>
            <div class="trace-ms">{{ fmt(s.ms) }}</div>
          </li>
        }
      </ol>
    </div>
  `,
})
export class TraceView {
  steps = input.required<TraceStep[]>();
  total = input.required<number>();
  title = input('Lo que hizo el servidor');

  protected rows = computed(() => {
    const total = Math.max(this.total(), 0.01);
    let acc = 0;
    return this.steps().map((s) => {
      const offset = (acc / total) * 100;
      acc += s.ms;
      return { ...s, offset, width: Math.max((s.ms / total) * 100, 0.8) };
    });
  });

  protected fmt(ms: number) {
    return ms < 1 ? `${ms.toFixed(2)} ms` : `${ms.toFixed(1)} ms`;
  }
}
