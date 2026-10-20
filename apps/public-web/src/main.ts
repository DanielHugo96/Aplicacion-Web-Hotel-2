import { registerLocaleData } from '@angular/common';
import localeEsPe from '@angular/common/locales/es-PE';
import { bootstrapApplication } from '@angular/platform-browser';
import { appConfig } from './app/app.config';
import { App } from './app/app';

// Locale es-PE registrado antes de arrancar: moneda y fechas de la landing.
registerLocaleData(localeEsPe);

bootstrapApplication(App, appConfig)
  .catch((err) => console.error(err));
