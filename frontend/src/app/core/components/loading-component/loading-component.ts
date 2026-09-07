import { Component, inject } from '@angular/core';
import { LoadingService } from '../../services/loading-service';

@Component({
  imports: [],
  selector: 'app-loading-component',
  styleUrl: './loading-component.scss',
  templateUrl: './loading-component.html',
})
export class LoadingComponent {
  loadingService = inject(LoadingService);
}
