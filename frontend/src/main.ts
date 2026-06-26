import { DatePipe, DecimalPipe } from '@angular/common';
import { provideHttpClient } from '@angular/common/http';
import { bootstrapApplication } from '@angular/platform-browser';
import { AppComponent } from './app/app.component';

bootstrapApplication(AppComponent, {
  providers: [
    provideHttpClient(),
    DatePipe,
    DecimalPipe
  ]
}).catch((error) => console.error(error));
