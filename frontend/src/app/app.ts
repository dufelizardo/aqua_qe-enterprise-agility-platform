import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';

/** Raiz: a landing é pública e as telas internas ficam dentro do Shell (app.routes.ts). */
@Component({
  selector: 'app-root',
  imports: [RouterOutlet],
  template: '<router-outlet />',
})
export class App {}
