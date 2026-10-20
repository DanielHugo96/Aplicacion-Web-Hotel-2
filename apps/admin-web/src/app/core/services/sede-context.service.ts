import { computed, inject, Injectable, signal } from '@angular/core';
import { SessionService } from './session.service';
import { Hotel } from '../models/catalogo.models';
import { CatalogoService } from './catalogo.service';

/**
 * Selector de sede del topbar: es un filtro de trabajo, nunca un valor de
 * autorización. El backend revalida la sede en cada petición; aquí solo se
 * decide qué sede se propone por defecto en pisos y habitaciones.
 */
@Injectable({ providedIn: 'root' })
export class SedeContextService {
  private readonly session = inject(SessionService);
  private readonly catalogo = inject(CatalogoService);

  readonly sedes = signal<Hotel[]>([]);
  readonly selectedId = signal<number | null>(null);
  readonly selected = computed(
    () => this.sedes().find((s) => s.idHotel === this.selectedId()) ?? null
  );

  async cargar(): Promise<void> {
    try {
      const res = await this.catalogo.listarSedes(0, 100);
      const rol = this.session.getRol();
      const asignadas = this.session.sedes();
      const activas = res.data.filter((s) => s.estado);
      const visibles =
        rol === 'ADMIN'
          ? activas
          : activas.filter((s) => asignadas.includes(s.idHotel));
      this.sedes.set(visibles);
      if (!visibles.some((s) => s.idHotel === this.selectedId())) {
        this.selectedId.set(visibles[0]?.idHotel ?? null);
      }
    } catch {
      // El topbar no rompe la pantalla: cada listado vuelve a pedir sus sedes.
      this.sedes.set([]);
    }
  }

  seleccionar(id: number | null): void {
    this.selectedId.set(id);
  }
}
