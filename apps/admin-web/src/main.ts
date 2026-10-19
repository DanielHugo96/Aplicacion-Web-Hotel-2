import { bootstrapApplication } from '@angular/platform-browser';
import { registerLocaleData } from '@angular/common';
import localeEsPe from '@angular/common/locales/es-PE';
import { appConfig } from './app/app.config';
import { App } from './app/app';

registerLocaleData(localeEsPe, 'es-PE');

bootstrapApplication(App, appConfig)
  .catch((err) => console.error(err));
