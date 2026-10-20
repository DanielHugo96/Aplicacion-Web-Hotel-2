import {
  Component,
  ElementRef,
  EventEmitter,
  HostListener,
  Input,
  OnChanges,
  Output,
  SimpleChanges,
  ViewChild,
} from '@angular/core';

/**
 * Diálogo de confirmación accesible: foco atrapado, `Esc` cierra salvo envío
 * en curso y el foco vuelve al disparador al cerrar (lo hace el padre con su
 * propio botón). Para bajas, el texto debe decir "Dar de baja", nunca "Eliminar".
 */
@Component({
  selector: 'app-confirm-dialog',
  standalone: true,
  templateUrl: './confirm-dialog.component.html',
})
export class ConfirmDialogComponent implements OnChanges {
  @Input() open = false;
  @Input() title = 'Confirmar acción';
  @Input() message = '';
  @Input() confirmLabel = 'Confirmar';
  @Input() busy = false;
  @Input() danger = true;

  @Output() confirm = new EventEmitter<void>();
  @Output() cancel = new EventEmitter<void>();

  @ViewChild('dialog') dialog?: ElementRef<HTMLElement>;
  @ViewChild('confirmBtn') confirmBtn?: ElementRef<HTMLButtonElement>;

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['open'] && this.open) {
      setTimeout(() => this.confirmBtn?.nativeElement.focus(), 0);
    }
  }

  @HostListener('document:keydown', ['$event'])
  onKeydown(event: KeyboardEvent): void {
    if (!this.open) return;
    if (event.key === 'Escape' && !this.busy) {
      event.preventDefault();
      this.cancel.emit();
      return;
    }
    if (event.key !== 'Tab') return;
    const root = this.dialog?.nativeElement;
    if (!root) return;
    const focusables = Array.from(
      root.querySelectorAll<HTMLElement>('button:not([disabled]), input, select, textarea, [href]')
    ).filter((el) => el.offsetParent !== null);
    if (focusables.length === 0) return;
    const first = focusables[0];
    const last = focusables[focusables.length - 1];
    if (event.shiftKey && document.activeElement === first) {
      event.preventDefault();
      last.focus();
    } else if (!event.shiftKey && document.activeElement === last) {
      event.preventDefault();
      first.focus();
    }
  }

  cerrar(): void {
    if (!this.busy) this.cancel.emit();
  }
}
