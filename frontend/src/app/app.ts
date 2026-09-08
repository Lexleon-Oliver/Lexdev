import { Component, signal } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { LoadingComponent } from './core/components/loading-component/loading-component';
import { ToastComponent } from './core/components/toast-component/toast-component';

@Component({
  imports: [RouterOutlet, LoadingComponent, ToastComponent],
  selector: 'app-root',
  styleUrl: './app.scss',
  templateUrl: './app.html',
})
export class App {
  protected readonly title = signal('frontend');
}
