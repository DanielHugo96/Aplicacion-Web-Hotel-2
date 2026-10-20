import { Injectable, signal } from '@angular/core';

/** Toast de éxito: pasado simple exacto del sistema de diseño. */
@Injectable({ providedIn: 'root' })
export class ToastService {
  readonly message = signal<string | null>(null);
  private timer: ReturnType<typeof setTimeout> | null = null;

  show(message: string): void {
    this.message.set(message);
    if (this.timer) clearTimeout(this.timer);
    this.timer = setTimeout(() => this.message.set(null), 5000);
  }

  clear(): void {
    this.message.set(null);
    if (this.timer) clearTimeout(this.timer);
  }
}
