import { Pipe, PipeTransform } from '@angular/core';

const TZ = 'America/Mexico_City';

const money = new Intl.NumberFormat('es-MX', { style: 'currency', currency: 'MXN' });
const dayFmt = new Intl.DateTimeFormat('es-MX', { timeZone: TZ, weekday: 'long', day: 'numeric', month: 'long' });
const timeFmt = new Intl.DateTimeFormat('es-MX', { timeZone: TZ, hour: '2-digit', minute: '2-digit' });
const fullFmt = new Intl.DateTimeFormat('es-MX', { timeZone: TZ, dateStyle: 'long', timeStyle: 'short' });
const shortDay = new Intl.DateTimeFormat('es-MX', { timeZone: TZ, day: 'numeric', month: 'short' });
const keyFmt = new Intl.DateTimeFormat('en-CA', { timeZone: TZ });

export function formatMoney(value: number): string {
  return money.format(value);
}

/** "+$1,250.00" para abonos y "−$1,250.00" (signo menos real) para cargos. */
export function formatSigned(value: number): string {
  return (value < 0 ? '−' : '+') + money.format(Math.abs(value));
}

export function formatTime(iso: string): string {
  return timeFmt.format(new Date(iso));
}

export function formatFull(iso: string): string {
  return fullFmt.format(new Date(iso));
}

export function formatShortDay(iso: string | Date): string {
  return shortDay.format(typeof iso === 'string' ? new Date(iso) : iso);
}

/** Agrupa por día en horario de la Ciudad de México: "Hoy", "Ayer" o "jueves 5 de noviembre". */
export function dayLabel(iso: string): string {
  const key = keyFmt.format(new Date(iso));
  const today = keyFmt.format(new Date());
  const yesterday = keyFmt.format(new Date(Date.now() - 86_400_000));
  if (key === today) return 'Hoy';
  if (key === yesterday) return 'Ayer';
  const label = dayFmt.format(new Date(iso));
  return label.charAt(0).toUpperCase() + label.slice(1);
}

export function dayKey(iso: string): string {
  return keyFmt.format(new Date(iso));
}

/** "999 180 89805399273 5": banco, plaza, cuenta y dígito verificador. */
export function formatClabe(clabe: string): string {
  return clabe.length === 18 ? `${clabe.slice(0, 3)} ${clabe.slice(3, 6)} ${clabe.slice(6, 17)} ${clabe.slice(17)}` : clabe;
}

export function initials(name: string | null | undefined): string {
  if (!name) return '·';
  const parts = name.replace(/\(.*\)/, '').trim().split(/\s+/);
  return ((parts[0]?.[0] ?? '') + (parts[1]?.[0] ?? '')).toUpperCase();
}

@Pipe({ name: 'money' })
export class MoneyPipe implements PipeTransform {
  transform(value: number | null | undefined, signed = false): string {
    if (value === null || value === undefined) return '';
    return signed ? formatSigned(value) : formatMoney(value);
  }
}

@Pipe({ name: 'clabe' })
export class ClabePipe implements PipeTransform {
  transform(value: string | null | undefined): string {
    return value ? formatClabe(value) : '';
  }
}

/** Valida el dígito verificador de la CLABE igual que el servidor (pesos 3, 7, 1). */
export function isValidClabe(clabe: string): boolean {
  if (!/^\d{18}$/.test(clabe)) return false;
  const weights = [3, 7, 1];
  let sum = 0;
  for (let i = 0; i < 17; i++) sum += (Number(clabe[i]) * weights[i % 3]) % 10;
  return (10 - (sum % 10)) % 10 === Number(clabe[17]);
}
