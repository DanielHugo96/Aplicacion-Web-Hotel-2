import { Component, computed, effect, inject, signal, untracked } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { ApiUiError, parseApiError } from '../../../core/api-error';
import { Habitacion, Persona } from '../../../core/models/catalogo.models';
import { CheckInInput, MedioPago } from '../../../core/models/recepcion.models';
import { MoneyPipe } from '../../../core/pipes/money.pipe';
import { RecepcionService } from '../../../core/services/recepcion.service';
import { SedeContextService } from '../../../core/services/sede-context.service';
import { SessionService } from '../../../core/services/session.service';
import { ToastService } from '../../../core/services/toast.service';
import { ErrorBannerComponent } from '../../../ui/error-banner/error-banner.component';

interface ErroresCheckIn {
  idCliente?: string;
  idHabitacion?: string;
  fechaSalida?: string;
  adelanto?: string;
  medioPago?: string;
  observacion?: string;
}

/**
 * `/app/recepcion/check-in`: búsqueda exacta de Cliente activo por documento,
 * selector de habitación de la sede y alta `POST /api/recepcion/registrar`.
 * La admisión final la decide el backend; un 409 se pinta junto al campo
 * `idHabitacion` con el mensaje del catálogo.
 */
@Component({
  selector: 'app-check-in',
  standalone: true,
  imports: [FormsModule, RouterLink, ErrorBannerComponent, MoneyPipe],
  templateUrl: './check-in.component.html',
  styleUrl: './check-in.component.css',
})
export class CheckInComponent {
  private readonly recepcion = inject(RecepcionService);
  private readonly sedesContext = inject(SedeContextService);
  private readonly router = inject(Router);
  private readonly session = inject(SessionService);
  private readonly toast = inject(ToastService);

  readonly sedeSeleccionada = computed(() => this.sedesContext.selectedId());
  readonly sedeNombre = computed(() => this.sedesContext.selected()?.nombre ?? null);

  // Buscador de cliente
  readonly documento = signal('');
  readonly buscando = signal(false);
  readonly resultados = signal<Persona[]>([]);
  readonly cliente = signal<Persona | null>(null);
  readonly errorCliente = signal<ApiUiError | null>(null);
  readonly noEncontrado = signal(false);

  // Habitaciones
  readonly habitaciones = signal<Habitacion[]>([]);
  readonly cargandoHabitaciones = signal(false);
  readonly errorHabitaciones = signal<ApiUiError | null>(null);

  // Formulario
  readonly idHabitacion = signal<number | null>(null);
  readonly fechaSalida = signal(fechaIsoLima(1));
  readonly adelanto = signal<number | null>(0);
  readonly medioPago = signal<MedioPago | null>(null);
  readonly observacion = signal('');

  readonly guardando = signal(false);
  readonly error = signal<ApiUiError | null>(null);
  readonly errores = signal<ErroresCheckIn>({});

  readonly habitacionSeleccionada = computed(
    () => this.habitaciones().find((h) => h.idHabitacion === this.idHabitacion()) ?? null
  );

  constructor() {
    effect(() => {
      const idHotel = this.sedeSeleccionada();
      untracked(() => {
        this.idHabitacion.set(null);
        if (!idHotel) {
          this.habitaciones.set([]);
          this.cargandoHabitaciones.set(false);
          return;
        }
        void this.cargarHabitaciones(idHotel);
      });
    });
  }

  async cargarHabitaciones(idHotel = this.sedeSeleccionada()): Promise<void> {
    if (!idHotel) return;
    this.cargandoHabitaciones.set(true);
    this.errorHabitaciones.set(null);
    try {
      this.habitaciones.set(await this.recepcion.listarHabitacionesDeSede(idHotel));
    } catch (err) {
      this.habitaciones.set([]);
      this.errorHabitaciones.set(parseApiError(err));
    } finally {
      this.cargandoHabitaciones.set(false);
    }
  }

  async buscarCliente(): Promise<void> {
    const documento = this.documento().trim();
    if (!documento) {
      this.errores.update((e) => ({ ...e, idCliente: 'Ingresa el documento del cliente' }));
      return;
    }
    this.buscando.set(true);
    this.errorCliente.set(null);
    this.noEncontrado.set(false);
    this.cliente.set(null);
    this.resultados.set([]);
    this.errores.update((e) => ({ ...e, idCliente: undefined }));
    try {
      const encontrados = await this.recepcion.buscarClientePorDocumento(documento);
      this.resultados.set(encontrados);
      if (encontrados.length === 0) {
        this.noEncontrado.set(true);
      } else if (encontrados.length === 1) {
        this.cliente.set(encontrados[0]);
      }
    } catch (err) {
      const apiError = parseApiError(err);
      this.errorCliente.set(apiError);
      const mensajeCampo = apiError.fields['documento'];
      if (mensajeCampo) {
        this.errores.update((e) => ({ ...e, idCliente: mensajeCampo }));
      }
    } finally {
      this.buscando.set(false);
    }
  }

  seleccionarCliente(persona: Persona): void {
    this.cliente.set(persona);
    this.noEncontrado.set(false);
    this.errores.update((e) => ({ ...e, idCliente: undefined }));
  }

  seleccionarHabitacion(habitacion: Habitacion): void {
    this.idHabitacion.set(habitacion.idHabitacion);
    this.errores.update((e) => ({ ...e, idHabitacion: undefined }));
  }

  /**
   * Solo se bloquean los estados conocidos como no disponibles (ocupada o
   * físico distinto de LISTA). `DESCONOCIDA` sigue seleccionable y se muestra
   * «Sin datos»: la admisión final la decide el backend, que responde 409 si el
   * gate no está sincronizado. Nunca se traduce a libre/disponible.
   */
  seleccionable(h: Habitacion): boolean {
    return (
      h.estadoFisico?.codigo === 'LISTA' && h.ocupacion?.estadoEstadia !== 'OCUPADA'
    );
  }

  textoOcupacion(h: Habitacion): string {
    switch (h.ocupacion?.estadoEstadia) {
      case 'OCUPADA':
        return 'Ocupada';
      case 'LIBRE':
        return 'Libre';
      case 'DESCONOCIDA':
      default:
        return 'Sin datos';
    }
  }

  claseOcupacion(h: Habitacion): string {
    switch (h.ocupacion?.estadoEstadia) {
      case 'OCUPADA':
        return 'pill-atencion';
      case 'LIBRE':
        return 'pill-archivado';
      case 'DESCONOCIDA':
      default:
        return 'pill-archivado';
    }
  }

  noches(): number {
    return nochesEntre(fechaIsoLima(0), this.fechaSalida());
  }

  alojamiento(): number | null {
    const h = this.habitacionSeleccionada();
    if (!h) return null;
    const n = this.noches();
    if (n < 1) return null;
    return redondear(n * h.precio);
  }

  validar(): boolean {
    const errores: ErroresCheckIn = {};
    const cliente = this.cliente();
    if (!cliente) {
      errores.idCliente = 'Cliente no encontrado: créalo primero';
    }
    const habitacion = this.habitacionSeleccionada();
    if (!habitacion) {
      errores.idHabitacion = 'Selecciona una habitación de la sede';
    } else if (habitacion.ocupacion?.estadoEstadia === 'OCUPADA') {
      errores.idHabitacion = 'La habitación ya está ocupada.';
    } else if (!this.seleccionable(habitacion)) {
      errores.idHabitacion = 'La habitación no admite ingreso todavía.';
    }

    const fecha = this.fechaSalida().trim();
    if (!fecha) {
      errores.fechaSalida = 'La fecha de salida no puede ser anterior a hoy';
    } else if (fecha < fechaIsoLima(0)) {
      errores.fechaSalida = 'La fecha de salida no puede ser anterior a hoy';
    } else if (this.noches() > 365) {
      errores.fechaSalida = 'La estadía no puede superar 365 noches.';
    }

    const adelanto = this.adelanto() ?? 0;
    if (!Number.isFinite(adelanto) || adelanto < 0) {
      errores.adelanto = 'El adelanto no puede superar el alojamiento';
    } else if (this.alojamiento() !== null && adelanto > this.alojamiento()!) {
      errores.adelanto = 'El adelanto no puede superar el alojamiento';
    }
    if (adelanto > 0 && !this.medioPago()) {
      errores.medioPago = 'Selecciona el medio de pago del adelanto';
    }
    if (this.observacion().length > 500) {
      errores.observacion = 'Máximo 500 caracteres';
    }

    this.errores.set(errores);
    return Object.keys(errores).length === 0;
  }

  async registrar(): Promise<void> {
    if (this.guardando()) return;
    if (!this.validar()) return;
    const idHotel = this.sedeSeleccionada();
    const cliente = this.cliente();
    const habitacion = this.habitacionSeleccionada();
    if (!idHotel || !cliente || !habitacion) return;

    const adelanto = this.adelanto() ?? 0;
    const observacion = this.observacion().trim();
    const cuerpo: CheckInInput = {
      idHotel,
      idCliente: cliente.idPersona,
      idHabitacion: habitacion.idHabitacion,
      fechaSalida: this.fechaSalida().trim(),
      adelanto: redondear(adelanto),
      ...(adelanto > 0 && this.medioPago() ? { medioPago: this.medioPago()! } : {}),
      ...(observacion ? { observacion } : {}),
    };

    this.guardando.set(true);
    this.error.set(null);
    try {
      const creada = await this.recepcion.registrarCheckIn(cuerpo);
      this.toast.show('Check-in registrado');
      await this.router.navigateByUrl(`/app/recepcion/${creada.idRecepcion}`);
    } catch (err) {
      const apiError = parseApiError(err);
      this.error.set(apiError);
      const campos: ErroresCheckIn = {};
      // El contrato documenta `idHabitacion` como campo afectado, pero el
      // servicio real puede devolver `fields` vacío: el mensaje del catálogo
      // se pinta igual junto al selector.
      if (
        ['ROOM_OCCUPIED', 'ROOM_NOT_READY', 'ROOM_NOT_SYNCED'].includes(apiError.code) ||
        apiError.fields['idHabitacion']
      ) {
        campos.idHabitacion = apiError.message;
      }
      if (apiError.fields['fechaSalida']) {
        campos.fechaSalida = apiError.message;
      }
      if (apiError.fields['adelanto']) {
        campos.adelanto = apiError.message;
      }
      if (apiError.fields['idCliente']) {
        campos.idCliente = apiError.message;
      }
      this.errores.set(campos);
    } finally {
      this.guardando.set(false);
    }
  }

  actionLabel(): string | null {
    const err = this.error();
    if (!err) return null;
    if (err.retryable) return 'Reintentar';
    if (err.reloadable) return 'Recargar';
    return null;
  }

  async accionError(): Promise<void> {
    const err = this.error();
    if (err?.relogin) {
      this.session.logout();
      return;
    }
    if (err?.retryable) {
      await this.registrar();
      return;
    }
    if (err?.reloadable) {
      this.idHabitacion.set(null);
      await this.cargarHabitaciones();
      return;
    }
    this.error.set(null);
  }

  contadorObservacion(): string {
    return `${this.observacion().length}/500`;
  }

  hoyLima(): string {
    return fechaIsoLima(0);
  }

  cambiarAdelanto(valor: string | number | null): void {
    this.adelanto.set(valor === null || valor === '' ? null : Number(valor));
  }
}

/** Fecha ISO (YYYY-MM-DD) en America/Lima, desplazada `dias` desde hoy. */
export function fechaIsoLima(dias: number): string {
  const base = new Date(Date.now() + dias * 86_400_000);
  const partes = new Intl.DateTimeFormat('en-CA', {
    timeZone: 'America/Lima',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).formatToParts(base);
  const valor = (tipo: string) => partes.find((p) => p.type === tipo)?.value ?? '';
  return `${valor('year')}-${valor('month')}-${valor('day')}`;
}

export function nochesEntre(entrada: string, salida: string): number {
  const a = new Date(`${entrada}T00:00:00`);
  const b = new Date(`${salida}T00:00:00`);
  return Math.round((b.getTime() - a.getTime()) / 86_400_000);
}

function redondear(valor: number): number {
  return Math.round((valor + Number.EPSILON) * 100) / 100;
}
