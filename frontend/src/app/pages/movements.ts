import { Component, inject, signal } from '@angular/core';
import { Api, ApiError, Movement } from '../core/api';
import { Icon } from '../core/icons';
import { MovementList } from './movement-list';

@Component({
  selector: 'll-movements',
  imports: [Icon, MovementList],
  template: `
    <main>
      <div class="page-head">
        <div>
          <h1>Movimientos</h1>
          <p>Cada renglón muestra el saldo que quedó después del movimiento.</p>
        </div>
        <button class="btn btn-ghost" type="button" (click)="download()" [disabled]="downloading()">
          <ll-icon name="download" /> {{ downloading() ? 'Preparando…' : 'Descargar CSV' }}
        </button>
      </div>
      @if (error()) {
        <div class="notice notice-error" role="alert" style="margin-bottom:16px"><ll-icon name="alert" /> {{ error() }}</div>
      }
      <section class="panel" aria-label="Estado de cuenta">
        @if (items().length) {
          <ll-movement-list [movements]="items()" />
          @if (cursor() !== null) {
            <div style="text-align:center;margin-top:16px">
              <button class="btn btn-ghost" type="button" (click)="more()" [disabled]="loading()">
                {{ loading() ? 'Cargando…' : 'Cargar movimientos anteriores' }}
              </button>
            </div>
          }
        } @else if (!loading()) {
          <div class="empty"><p>Todavía no hay movimientos en tu cuenta.</p></div>
        }
      </section>
    </main>
  `,
})
export class Movements {
  private api = inject(Api);

  protected items = signal<Movement[]>([]);
  protected cursor = signal<number | null>(null);
  protected loading = signal(false);
  protected downloading = signal(false);
  protected error = signal<string | null>(null);

  constructor() {
    this.fetch(null);
  }

  protected more() {
    this.fetch(this.cursor());
  }

  private async fetch(before: number | null) {
    this.loading.set(true);
    try {
      const page = await this.api.movements(before, 25);
      this.items.update((cur) => (before === null ? page.items : [...cur, ...page.items]));
      this.cursor.set(page.nextCursor);
      this.error.set(null);
    } catch (e) {
      this.error.set((e as ApiError).message);
    } finally {
      this.loading.set(false);
    }
  }

  protected async download() {
    this.downloading.set(true);
    try {
      const blob = await this.api.movementsCsv();
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = 'estado-de-cuenta.csv';
      a.click();
      URL.revokeObjectURL(url);
    } catch (e) {
      this.error.set((e as ApiError).message);
    } finally {
      this.downloading.set(false);
    }
  }
}
