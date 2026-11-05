import { Component } from '@angular/core';

/** Panel de presentación de las pantallas de acceso: un asiento real, cargo y abono que suman cero. */
@Component({
  selector: 'll-auth-side',
  template: `
    <aside class="auth-side">
      <div class="logo" style="color:var(--hero-ink)">
        <svg viewBox="0 0 32 32" aria-hidden="true"><rect width="32" height="32" rx="7" fill="var(--hero-ink)"/><path d="M9 8v16h14" fill="none" stroke="var(--hero)" stroke-width="3" stroke-linecap="round" stroke-linejoin="round"/><path d="M14 13h9M14 18h6" stroke="var(--credit)" stroke-width="2.4" stroke-linecap="round"/></svg>
        Ledgerline
      </div>
      <div>
        <h1>Cada peso que se mueve, cuadra.</h1>
        <p>Envía y recibe dinero con tu CLABE. Cada operación queda registrada con un cargo y un abono que suman cero, así que tu saldo siempre se puede comprobar.</p>
      </div>
      <div class="auth-entry" aria-label="Ejemplo de asiento contable">
        <div><span>Cargo a tu cuenta</span><span>−$350.50</span></div>
        <div><span>Abono a Luis H.</span><span>+$350.50</span></div>
        <div class="total"><span>Suma del asiento</span><span>$0.00</span></div>
      </div>
    </aside>
  `,
  styles: [':host { display: contents; }'],
})
export class AuthSide {}
