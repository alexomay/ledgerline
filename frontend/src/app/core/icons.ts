import { Component, input } from '@angular/core';

/** Íconos de trazo simple, heredan el color del texto. */
@Component({
  selector: 'll-icon',
  template: `
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"
         stroke-linejoin="round" aria-hidden="true" [style.width.px]="size()" [style.height.px]="size()">
      @switch (name()) {
        @case ('check') { <path d="M5 12.5l4.5 4.5L19 7.5" /> }
        @case ('x') { <path d="M6 6l12 12M18 6L6 18" /> }
        @case ('alert') { <path d="M12 8v5M12 16.5v.5" /><circle cx="12" cy="12" r="9" /> }
        @case ('info') { <path d="M12 11v6M12 7.5v.5" /><circle cx="12" cy="12" r="9" /> }
        @case ('copy') { <rect x="8" y="8" width="12" height="12" rx="2" /><path d="M16 8V6a2 2 0 0 0-2-2H6a2 2 0 0 0-2 2v8a2 2 0 0 0 2 2h2" /> }
        @case ('send') { <path d="M5 12h14M13 6l6 6-6 6" /> }
        @case ('plus') { <path d="M12 5v14M5 12h14" /> }
        @case ('download') { <path d="M12 4v11M7 10l5 5 5-5M5 20h14" /> }
        @case ('sun') { <circle cx="12" cy="12" r="4" /><path d="M12 2v2M12 20v2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M2 12h2M20 12h2M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4" /> }
        @case ('moon') { <path d="M20 14.5A8 8 0 0 1 9.5 4a8 8 0 1 0 10.5 10.5z" /> }
      }
    </svg>
  `,
  styles: [':host { display: inline-flex; }'],
})
export class Icon {
  name = input.required<string>();
  size = input(18);
}
