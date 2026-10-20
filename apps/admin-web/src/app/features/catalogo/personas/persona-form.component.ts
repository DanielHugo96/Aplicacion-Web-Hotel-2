import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { ApiUiError, parseApiError } from '../../../core/api-error';
import { CatalogoService } from '../../../core/services/catalogo.service';
import { SedeContextService } from '../../../core/services/sede-context.service';
import { SessionService } from '../../../core/services/session.service';
import { ToastService } from '../../../core/services/toast.service';
import {
  Persona,
  PersonaAlta,
  PersonaUpdate,
  TipoDocumento,
  TipoPersona,
} from '../../../core/models/catalogo.models';
import { ErrorBannerComponent } from '../../../ui/error-banner/error-banner.component';

const CORREO_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

@Component({
  selector: 'app-persona-form',
  standalone: true,
  imports: [FormsModule, RouterLink, ErrorBannerComponent],
  templateUrl: './persona-form.component.html',
  styleUrl: './persona-form.component.css',
})
export class PersonaFormComponent implements OnInit {
  private readonly catalogo = inject(CatalogoService);
  private readonly session = inject(SessionService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly toast = inject(ToastService);
  readonly sedesContext = inject(SedeContextService);

  readonly rol = this.session.rol;
  readonly esAdmin = computed(() => this.rol() === 'ADMIN');
  readonly esEmpleado = computed(() => this.rol() === 'EMPLEADO');

  readonly tipos = signal<TipoPersona[]>([]);
  readonly loading = signal(false);
  readonly guardando = signal(false);
  readonly error = signal<ApiUiError | null>(null);
  readonly cargando = signal(true);
  readonly persona = signal<Persona | null>(null);
  readonly id = signal<number | null>(null);
  readonly esEdicion = computed(() => this.id() !== null);

  tipoDocumento: TipoDocumento = 'DNI';
  documento = '';
  nombre = '';
  apellido = '';
  correo = '';
  idTipoPersona: number | null = null;
  sedesSeleccionadas: number[] = [];
  clave = '';
  estado = true;
  fotoUrl = '';

  errores: Record<string, string> = {};

  readonly actionLabel = computed(() => {
    const err = this.error();
    if (!err) return null;
    if (err.relogin) return 'Iniciar sesión de nuevo';
    if (err.reloadable) return 'Recargar';
    if (err.retryable) return 'Reintentar';
    return null;
  });

  async ngOnInit(): Promise<void> {
    await this.cargarTipos();
    const idParam = this.route.snapshot.paramMap.get('id');
    if (idParam) {
      this.id.set(Number(idParam));
      await this.cargarPersona();
    } else {
      this.idTipoPersona =
        this.esEmpleado()
          ? this.tipos().find((t) => t.codigo === 'CLIENTE')?.idTipoPersona ?? null
          : this.tipos().find((t) => t.codigo === 'CLIENTE')?.idTipoPersona ?? null;
      this.cargando.set(false);
    }
  }

  async cargarTipos(): Promise<void> {
    try {
      this.tipos.set(await this.catalogo.listarTiposPersona());
    } catch (err) {
      this.error.set(parseApiError(err));
    }
  }

  async cargarPersona(): Promise<void> {
    const id = this.id();
    if (!id) return;
    this.cargando.set(true);
    try {
      const res = await this.catalogo.buscarPersona(id);
      const p = res.data;
      this.persona.set(p);
      this.tipoDocumento = p.tipoDocumento;
      this.documento = p.documento;
      this.nombre = p.nombre;
      this.apellido = p.apellido;
      this.correo = p.correo;
      this.idTipoPersona = p.idTipoPersona;
      this.sedesSeleccionadas = [...p.sedes];
      this.estado = p.estado;
      this.fotoUrl = p.fotoUrl ?? '';
    } catch (err) {
      this.error.set(parseApiError(err));
    } finally {
      this.cargando.set(false);
    }
  }

  tipoSeleccionado(): TipoPersona | null {
    return this.tipos().find((t) => t.idTipoPersona === this.idTipoPersona) ?? null;
  }

  esClienteSeleccionado(): boolean {
    return this.tipoSeleccionado()?.codigo === 'CLIENTE';
  }

  esPersonalSeleccionado(): boolean {
    const codigo = this.tipoSeleccionado()?.codigo;
    return codigo === 'ADMIN' || codigo === 'EMPLEADO';
  }

  clienteBloqueado(tipo: TipoPersona): boolean {
    return this.esEmpleado() && tipo.codigo !== 'CLIENTE';
  }

  toggleSede(id: number, event: Event): void {
    const checked = (event.target as HTMLInputElement).checked;
    if (checked) {
      if (!this.sedesSeleccionadas.includes(id)) this.sedesSeleccionadas.push(id);
    } else {
      this.sedesSeleccionadas = this.sedesSeleccionadas.filter((s) => s !== id);
    }
  }

  campoInvalido(campo: string): boolean {
    return !!this.errores[campo];
  }

  mensajeCampo(campo: string): string {
    return this.errores[campo] ?? '';
  }

  private bytes(clave: string): number {
    return new TextEncoder().encode(clave).length;
  }

  private validar(): boolean {
    const e: Record<string, string> = {};
    if (!this.tipoDocumento) e['tipoDocumento'] = 'Selecciona el tipo de documento';
    const doc = this.documento.trim();
    if (this.tipoDocumento === 'DNI') {
      if (!/^\d{8}$/.test(doc)) e['documento'] = 'El DNI debe tener 8 dígitos';
    } else if (!/^[A-Za-z0-9]{6,20}$/.test(doc)) {
      e['documento'] = 'Documento inválido';
    }
    if (!this.nombre.trim() || this.nombre.trim().length > 100) e['nombre'] = 'Ingresa tu nombre';
    if (!this.apellido.trim() || this.apellido.trim().length > 100) e['apellido'] = 'Ingresa tu nombre';
    if (!this.correo.trim() || !CORREO_RE.test(this.correo.trim()) || this.correo.length > 254) {
      e['correo'] = 'Correo inválido';
    }
    if (!this.idTipoPersona) e['idTipoPersona'] = 'Selecciona el tipo de persona';
    if (this.esEmpleado() && !this.esClienteSeleccionado()) {
      e['idTipoPersona'] = 'No puedes crear personal';
    }
    if (this.esClienteSeleccionado() && !this.esEdicion()) {
      this.sedesSeleccionadas = [];
    }
    if (this.tipoSeleccionado()?.codigo === 'EMPLEADO' && this.sedesSeleccionadas.length === 0) {
      e['sedes'] = 'Asigna al menos una sede';
    }
    if (!this.esEdicion() && this.esPersonalSeleccionado() && !this.clave) {
      e['clave'] = 'Para crear personal, la clave es obligatoria';
    } else if (this.clave && (this.bytes(this.clave) < 12 || this.bytes(this.clave) > 72)) {
      e['clave'] = 'La contraseña debe tener entre 12 y 72 bytes (caracteres UTF-8)';
    }
    this.errores = e;
    return Object.keys(e).length === 0;
  }

  async onSubmit(event: Event): Promise<void> {
    event.preventDefault();
    this.error.set(null);
    if (!this.validar()) return;
    this.guardando.set(true);
    try {
      if (this.esEdicion()) {
        const id = this.id()!;
        const body: PersonaUpdate = {
          nombre: this.nombre.trim(),
          apellido: this.apellido.trim(),
          correo: this.correo.trim(),
          idTipoPersona: this.idTipoPersona!,
          sedes: this.esClienteSeleccionado() ? [] : this.sedesSeleccionadas,
          estado: this.estado,
          fotoUrl: this.fotoUrl.trim() || null,
        };
        await this.catalogo.actualizarPersona(id, body);
      } else {
        const body: PersonaAlta = {
          tipoDocumento: this.tipoDocumento,
          documento: this.documento.trim(),
          nombre: this.nombre.trim(),
          apellido: this.apellido.trim(),
          correo: this.correo.trim(),
          idTipoPersona: this.idTipoPersona!,
          sedes: this.esClienteSeleccionado() ? [] : this.sedesSeleccionadas,
        };
        if (this.clave) body.clave = this.clave;
        await this.catalogo.registrarPersona(body);
      }
      this.toast.show('Cambios guardados');
      await this.router.navigateByUrl('/app/personas');
    } catch (err) {
      const apiErr = parseApiError(err);
      this.error.set(apiErr);
      this.aplicarCamposServidor(apiErr);
    } finally {
      this.guardando.set(false);
    }
  }

  private aplicarCamposServidor(apiErr: ApiUiError): void {
    const e: Record<string, string> = { ...this.errores };
    for (const [campo, motivo] of Object.entries(apiErr.fields)) {
      const clave = campo.replace(/^persona\./, '');
      e[clave] = motivo;
    }
    this.errores = e;
  }

  accionError(): void {
    const err = this.error();
    if (!err) return;
    if (err.relogin) {
      this.session.logout();
      return;
    }
    if (err.reloadable) {
      this.cargarPersona();
      return;
    }
    this.onSubmit(new Event('submit'));
  }
}
